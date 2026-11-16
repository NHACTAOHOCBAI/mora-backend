package com.mora.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.mora.backend.exception.*;
import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryMail {
    private final JavaMailSender sender;
    @Value("${password-recovery.enabled:false}") private boolean enabled;
    @Value("${password-recovery.frontend-url:http://localhost:5173}") private String frontendUrl;
    @Value("${password-recovery.from:}") private String from;

    @PostConstruct
    void validate() {
        if (!enabled) return;
        URI uri = URI.create(frontendUrl);
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                || from.isBlank()) {
            throw new IllegalStateException("Configure PASSWORD_RECOVERY_FRONTEND_URL and MAIL_FROM");
        }
    }

    public void requireAvailable() {
        if (!enabled) throw new AppException(ErrorCode.RECOVERY_UNAVAILABLE);
    }

    @Async("passwordRecoveryExecutor")
    public void send(String email, String token) {
        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Mora · Đặt lại mật khẩu");
            String plain = """
                    Bạn đã yêu cầu đặt lại mật khẩu Mora.

                    Đặt lại mật khẩu: %s

                    Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần.
                    Nếu bạn không yêu cầu, hãy bỏ qua email này. Mật khẩu của bạn chưa thay đổi.
                    """.formatted(link);
            String html = """
                    <html lang="vi"><body style="font-family:Arial,sans-serif;color:#171717">
                    <h2>Đặt lại mật khẩu Mora</h2>
                    <p>Bạn đã yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần.</p>
                    <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#171717;color:#fff;border-radius:8px;text-decoration:none">Đặt lại mật khẩu</a></p>
                    <p>Nếu nút không hoạt động, sao chép liên kết này vào trình duyệt:<br>%s</p>
                    <p>Nếu bạn không yêu cầu, hãy bỏ qua email này. Mật khẩu của bạn chưa thay đổi.</p>
                    </body></html>
                    """.formatted(link, link);
            helper.setText(plain, html);
            sender.send(message);
        } catch (org.springframework.mail.MailException | jakarta.mail.MessagingException error) {
            // Never log recipient, raw token, message body or SMTP credentials.
            log.error("Password recovery email delivery failed ({})", error.getClass().getSimpleName());
        }
    }
}
