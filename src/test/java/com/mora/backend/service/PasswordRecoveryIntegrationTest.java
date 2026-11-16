package com.mora.backend.service;

import com.mora.backend.exception.*;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.security.*;
import com.mora.backend.service.impl.PasswordRecoveryServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.show-sql=false"
})
@Import({PasswordRecoveryServiceImpl.class, PasswordRecoveryRateLimiter.class, PasswordRecoveryIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordRecoveryIntegrationTest {
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired MutableClock clock;
    @MockitoBean PasswordRecoveryMail mail;
    private TransactionTemplate tx;
    private Long userId;
    private String email;
    private static final AtomicInteger sequence = new AtomicInteger();

    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-16T00:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time.get(); }
    }
    @TestConfiguration
    static class Beans {
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
    }

    @BeforeEach void createAccount() {
        tx = new TransactionTemplate(manager);
        email = "recovery" + sequence.incrementAndGet() + "@example.com";
        userId = tx.execute(status -> users.saveAndFlush(User.builder().email(email).fullName("Recovery Test")
                .password(encoder.encode("old-password")).role(Role.ROLE_USER).build()).getId());
    }

    private String issue(String ip) {
        recovery.request(email, ip);
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), capture.capture());
        return capture.getValue();
    }

    private JwtTokenProvider jwt() {
        var jwt = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwt, "secret", "a-very-long-test-key-for-password-reset-1234567890");
        ReflectionTestUtils.setField(jwt, "jwtExpiration", 600000L);
        return jwt;
    }

    @Test void storesOnlyHashWithThirtyMinuteExpiryAndSendsAfterCommit() {
        String raw = issue("1");
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"));
        var stored = tokens.findByTokenHash(PasswordRecoveryServiceImpl.hash(raw)).orElseThrow();
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(raw, stored.getTokenHash());
        assertEquals(Duration.ofMinutes(30), Duration.between(stored.getCreatedAt(), stored.getExpiresAt()));
        assertNull(stored.getUsedAt());
        clearInvocations(mail);
        tx.executeWithoutResult(status -> {
            clock.time.updateAndGet(time -> time.plusSeconds(61));
            recovery.request(email, "rollback");
            verify(mail, never()).send(anyString(), anyString());
            status.setRollbackOnly();
        });
        verify(mail, never()).send(anyString(), anyString());
    }

    @Test void unknownOrDisabledAccountsDoNotSendEmail() {
        recovery.request("missing" + sequence.get() + "@example.com", "2");
        verify(mail, never()).send(anyString(), anyString());
        tx.executeWithoutResult(status -> users.findById(userId).orElseThrow().setActive(false));
        recovery.request(email, "3");
        verify(mail, never()).send(anyString(), anyString());
    }

    @Test void caseInsensitiveEmailIsThrottledOnServer() {
        recovery.request(email.toUpperCase(java.util.Locale.ROOT), "4");
        verify(mail).send(eq(email), anyString());
        assertEquals(ErrorCode.RECOVERY_RATE_LIMITED,
                assertThrows(AppException.class, () -> recovery.request(email, "different-ip")).getErrorCode());
    }

    @Test void expiredMalformedAndOversizedPasswordsCannotConsumeToken() {
        String raw = issue("5");
        assertThrows(AppException.class, () -> recovery.reset(raw, "界".repeat(25), "5"));
        assertThrows(AppException.class, () -> recovery.reset("malformed", "new-password", "5"));
        tx.executeWithoutResult(status -> tokens.findByTokenHash(PasswordRecoveryServiceImpl.hash(raw)).orElseThrow()
                .setExpiresAt(clock.instant()));
        assertEquals(ErrorCode.RESET_LINK_INVALID,
                assertThrows(AppException.class, () -> recovery.reset(raw, "new-password", "5")).getErrorCode());
        assertTrue(encoder.matches("old-password", users.findById(userId).orElseThrow().getPassword()));
    }

    @Test void successfulResetRevokesJwtAndOtherRecoveryLinks() {
        String first = issue("6");
        clock.time.updateAndGet(time -> time.plusSeconds(61));
        String second = issue("6");
        var jwt = jwt();
        String oldJwt = jwt.generateToken(new AccountPrincipal(users.findById(userId).orElseThrow()));
        recovery.reset(first, " new-password ", "6");
        User updated = users.findById(userId).orElseThrow();
        assertTrue(encoder.matches(" new-password ", updated.getPassword()));
        assertFalse(encoder.matches("old-password", updated.getPassword()));
        assertEquals(1, updated.getCredentialVersion());
        assertFalse(jwt.isTokenValid(oldJwt, new AccountPrincipal(updated)));
        assertTrue(jwt.isTokenValid(jwt.generateToken(new AccountPrincipal(updated)), new AccountPrincipal(updated)));
        assertThrows(AppException.class, () -> recovery.reset(first, "another-password", "6"));
        assertThrows(AppException.class, () -> recovery.reset(second, "another-password", "6"));
    }

    @Test void concurrentResetSucceedsOnlyOnce() throws Exception {
        String raw = issue("7");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> reset = () -> {
                start.await();
                try { recovery.reset(raw, "new-password", "7"); return true; }
                catch (AppException error) { assertEquals(ErrorCode.RESET_LINK_INVALID, error.getErrorCode()); return false; }
            };
            var first = pool.submit(reset); var second = pool.submit(reset); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, users.findById(userId).orElseThrow().getCredentialVersion());
    }
}
