package com.mora.backend.service;

import com.mora.backend.exception.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.time.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PasswordRecoveryRateLimiter {
    private final Clock clock;
    private final Map<String, List<Instant>> buckets = new LinkedHashMap<>();

    public synchronized void check(String key, int limit, Duration window) {
        Instant now = clock.instant();
        buckets.values().forEach(times -> times.removeIf(time -> time.isBefore(now.minus(Duration.ofMinutes(15)))));
        buckets.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!buckets.containsKey(key) && buckets.size() >= 10000) {
            throw new AppException(ErrorCode.RECOVERY_RATE_LIMITED);
        }
        var times = buckets.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (times.stream().filter(time -> time.isAfter(now.minus(window))).count() >= limit) {
            throw new AppException(ErrorCode.RECOVERY_RATE_LIMITED);
        }
        times.add(now);
    }
}
