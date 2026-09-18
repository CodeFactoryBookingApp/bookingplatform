package com.codefactory.bookingplatform.auth.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Business rule (HU-021): temporary lock after repeated failed login attempts.
 * Pure sliding-window policy; configuration comes from app.auth-policy.
 */
public final class LoginLockPolicy {

    private final int maxFailedAttempts;
    private final Duration lockWindow;

    public LoginLockPolicy(int maxFailedAttempts, Duration lockWindow) {
        if (maxFailedAttempts <= 0) {
            throw new IllegalArgumentException("maxFailedAttempts must be positive");
        }
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockWindow = lockWindow;
    }

    public boolean isBlocked(List<Instant> recentFailures, Instant now) {
        Instant windowStart = now.minus(lockWindow);
        long failuresInWindow = recentFailures.stream()
                .filter(attempt -> !attempt.isBefore(windowStart) && !attempt.isAfter(now))
                .count();
        return failuresInWindow >= maxFailedAttempts;
    }

    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    public Duration lockWindow() {
        return lockWindow;
    }
}
