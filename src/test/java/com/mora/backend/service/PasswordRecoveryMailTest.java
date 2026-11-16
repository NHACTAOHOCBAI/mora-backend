package com.mora.backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryMailTest {
    @Test void createsUtf8MultipartEmailAndDoesNotExposeSmtpFailure() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var mail = new PasswordRecoveryMail(sender);
        ReflectionTestUtils.setField(mail, "enabled", true);
        ReflectionTestUtils.setField(mail, "frontendUrl", "https://mora.example");
        ReflectionTestUtils.setField(mail, "from", "no-reply@mora.example");
        mail.validate();
        mail.send("user@example.com", "sample-token");
        verify(sender).send(message);
        assertTrue(message.getSubject().contains("Đặt lại mật khẩu"));
        assertEquals("user@example.com", message.getAllRecipients()[0].toString());
        var output = new java.io.ByteArrayOutputStream();
        message.writeTo(output);
        assertTrue(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("multipart"));
        doThrow(new MailSendException("SMTP failure")).when(sender).send(message);
        assertDoesNotThrow(() -> mail.send("user@example.com", "sample-token"));
    }

    @Test void unavailableAndInvalidConfigurationFailClearly() {
        var mail = new PasswordRecoveryMail(mock(JavaMailSender.class));
        assertThrows(com.mora.backend.exception.AppException.class, mail::requireAvailable);
        ReflectionTestUtils.setField(mail, "enabled", true);
        ReflectionTestUtils.setField(mail, "frontendUrl", "https://mora.example/reset?untrusted=1");
        ReflectionTestUtils.setField(mail, "from", "no-reply@mora.example");
        assertThrows(IllegalStateException.class, mail::validate);
    }
}
