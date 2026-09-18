package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginLockPolicyTest {

    private final LoginLockPolicy policy = new LoginLockPolicy(5, Duration.ofMinutes(15));
    private final Instant now = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    @DisplayName("Fewer failures than the maximum do not block")
    void belowThresholdIsNotBlocked() {
        List<Instant> failures = List.of(
                now.minus(Duration.ofMinutes(4)),
                now.minus(Duration.ofMinutes(3)),
                now.minus(Duration.ofMinutes(2)),
                now.minus(Duration.ofMinutes(1)));
        assertFalse(policy.isBlocked(failures, now));
    }

    @Test
    @DisplayName("Five failures within the window block the account")
    void reachingThresholdBlocks() {
        List<Instant> failures = List.of(
                now.minus(Duration.ofMinutes(5)),
                now.minus(Duration.ofMinutes(4)),
                now.minus(Duration.ofMinutes(3)),
                now.minus(Duration.ofMinutes(2)),
                now.minus(Duration.ofMinutes(1)));
        assertTrue(policy.isBlocked(failures, now));
    }

    @Test
    @DisplayName("Failures outside the sliding window are ignored")
    void oldFailuresAreIgnored() {
        List<Instant> failures = List.of(
                now.minus(Duration.ofMinutes(60)),
                now.minus(Duration.ofMinutes(50)),
                now.minus(Duration.ofMinutes(40)),
                now.minus(Duration.ofMinutes(30)),
                now.minus(Duration.ofMinutes(20)));
        assertFalse(policy.isBlocked(failures, now));
    }
}
