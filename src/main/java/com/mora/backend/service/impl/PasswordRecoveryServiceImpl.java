package com.mora.backend.service.impl;

import com.mora.backend.exception.*;
import com.mora.backend.model.entity.PasswordResetToken;
import com.mora.backend.repository.*;
import com.mora.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final PasswordRecoveryMail mail;
    private final PasswordRecoveryRateLimiter limiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @Override
    @Transactional
    public void request(String email, String remoteAddress) {
        mail.requireAvailable();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        limiter.check("request-ip:" + hash(remoteAddress), 5, Duration.ofMinutes(15));
        limiter.check("email:" + hash(normalized), 1, Duration.ofSeconds(60));
        var found = users.findByEmailIgnoreCase(normalized);
        if (found.isEmpty() || !found.get().isActive()) return;
        var user = users.findForPasswordReset(found.get().getId()).orElseThrow(
                () -> new AppException(ErrorCode.RESET_LINK_INVALID));
        if (!user.isActive()) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.deleteExpired(now.minus(Duration.ofDays(1)));
        tokens.save(PasswordResetToken.builder().user(user).tokenHash(hash(raw))
                .credentialVersion(user.getCredentialVersion()).createdAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(30))).build());
        String recipient = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    mail.send(recipient, raw);
                } catch (org.springframework.core.task.TaskRejectedException error) {
                    log.error("Password recovery email queue is full");
                }
            }
        });
    }

    @Override
    @Transactional
    public void reset(String raw, String newPassword, String remoteAddress) {
        limiter.check("reset-ip:" + hash(remoteAddress), 20, Duration.ofMinutes(15));
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppException(ErrorCode.RESET_PASSWORD_INVALID);
        }
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw new AppException(ErrorCode.RESET_LINK_INVALID);
        String digest = hash(raw);
        var initial = tokens.findByTokenHash(digest).orElseThrow(() -> new AppException(ErrorCode.RESET_LINK_INVALID));
        var user = users.findForPasswordReset(initial.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESET_LINK_INVALID));
        var token = tokens.findForUpdate(digest).orElseThrow(() -> new AppException(ErrorCode.RESET_LINK_INVALID));
        Instant now = clock.instant();
        if (!user.isActive() || token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)
                || token.getCredentialVersion() != user.getCredentialVersion()) {
            throw new AppException(ErrorCode.RESET_LINK_INVALID);
        }
        user.setPassword(encoder.encode(newPassword));
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        token.setUsedAt(now);
        tokens.deleteByUserIdAndIdNot(user.getId(), token.getId());
        users.save(user);
        tokens.save(token);
    }
}
