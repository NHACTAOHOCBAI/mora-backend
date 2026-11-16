package com.mora.backend.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import com.mora.backend.exception.AppException;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryRateLimiterTest {
    @Test void blocksRepeatedEmailAndExcessRequestsFromSameIp() {
        var limiter = new PasswordRecoveryRateLimiter(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        limiter.check("email-hash", 1, Duration.ofSeconds(60));
        assertThrows(AppException.class, () -> limiter.check("email-hash", 1, Duration.ofSeconds(60)));
        for (int i = 0; i < 5; i++) limiter.check("ip", 5, Duration.ofMinutes(15));
        assertThrows(AppException.class, () -> limiter.check("ip", 5, Duration.ofMinutes(15)));
    }
}
