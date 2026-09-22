package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-021 - sliding-window lock after repeated failed logins.
 *
 * <p>Black box: equivalence partitions over the number of failures inside the window (below / at /
 * above the threshold) and over the position of an attempt relative to the window (before, on the
 * lower edge, inside, on {@code now}, after {@code now}); boundary value analysis at
 * {@code now - lockWindow}, at {@code now} and at {@code max - 1 / max / max + 1}. White box: both
 * outcomes of the constructor guard and both operands of the compound filter condition
 * {@code !isBefore(windowStart) && !isAfter(now)}, plus both outcomes of {@code count >= max}.</p>
 */
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

    /** Builds {@code count} distinct failures all comfortably inside the window. */
    private List<Instant> failuresInsideWindow(int count) {
        List<Instant> failures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            failures.add(now.minus(Duration.ofMinutes(i)));
        }
        return failures;
    }

    // ----------------------------------------------------------------------------------------
    // Constructor guard
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor guard")
    class ConstructorGuard {

        @ParameterizedTest(name = "maxFailedAttempts = {0} is rejected")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("A non-positive maximum of failed attempts is rejected, because a lock that triggers at zero failures would block everyone")
        void nonPositiveMaximumIsRejected(int maxFailedAttempts) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LoginLockPolicy(maxFailedAttempts, Duration.ofMinutes(15)));
        }

        @Test
        @DisplayName("The rejection message names the offending parameter")
        void rejectionMessageNamesTheParameter() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new LoginLockPolicy(0, Duration.ofMinutes(15)));
            assertEquals("maxFailedAttempts must be positive", thrown.getMessage());
        }

        @ParameterizedTest(name = "maxFailedAttempts = {0} is accepted")
        @ValueSource(ints = {1, 2, 5, Integer.MAX_VALUE})
        @DisplayName("Any positive maximum of failed attempts is accepted, one being the strictest allowed policy")
        void positiveMaximumIsAccepted(int maxFailedAttempts) {
            assertDoesNotThrow(() -> new LoginLockPolicy(maxFailedAttempts, Duration.ofMinutes(15)));
        }

        @Test
        @DisplayName("A policy of one attempt blocks on the very first failure")
        void policyOfOneBlocksOnFirstFailure() {
            LoginLockPolicy strict = new LoginLockPolicy(1, Duration.ofMinutes(15));
            assertTrue(strict.isBlocked(List.of(now.minus(Duration.ofSeconds(1))), now));
        }

        @Test
        @DisplayName("A policy of one attempt does not block when there are no failures")
        void policyOfOneDoesNotBlockWithoutFailures() {
            LoginLockPolicy strict = new LoginLockPolicy(1, Duration.ofMinutes(15));
            assertFalse(strict.isBlocked(List.of(), now));
        }
    }

    // ----------------------------------------------------------------------------------------
    // Configuration accessors
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Configuration accessors")
    class ConfigurationAccessors {

        @Test
        @DisplayName("The policy exposes the configured maximum of failed attempts")
        void exposesMaxFailedAttempts() {
            assertEquals(5, policy.maxFailedAttempts());
        }

        @Test
        @DisplayName("The policy exposes the configured lock window")
        void exposesLockWindow() {
            assertEquals(Duration.ofMinutes(15), policy.lockWindow());
        }
    }

    // ----------------------------------------------------------------------------------------
    // Threshold: boundary value analysis at max - 1, max and max + 1
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Threshold")
    class Threshold {

        @Test
        @DisplayName("An empty list of failures never blocks")
        void emptyListDoesNotBlock() {
            assertFalse(policy.isBlocked(List.of(), now));
        }

        @ParameterizedTest(name = "{0} failures inside the window -> blocked = {1}")
        @CsvSource({
                "0, false",
                "1, false",
                "4, false",
                "5, true",
                "6, true",
                "20, true"
        })
        @DisplayName("The account is blocked as soon as the failures inside the window reach the configured maximum")
        void blocksFromTheThresholdUpwards(int failureCount, boolean expectedBlocked) {
            assertEquals(expectedBlocked, policy.isBlocked(failuresInsideWindow(failureCount), now));
        }

        @Test
        @DisplayName("Exactly one failure short of the maximum does not block")
        void oneBelowTheThresholdDoesNotBlock() {
            assertFalse(policy.isBlocked(failuresInsideWindow(4), now));
        }

        @Test
        @DisplayName("Exactly the maximum blocks, so the threshold is inclusive")
        void exactlyTheThresholdBlocks() {
            assertTrue(policy.isBlocked(failuresInsideWindow(5), now));
        }

        @Test
        @DisplayName("One failure past the maximum still blocks")
        void oneAboveTheThresholdBlocks() {
            assertTrue(policy.isBlocked(failuresInsideWindow(6), now));
        }
    }

    // ----------------------------------------------------------------------------------------
    // Sliding window: boundary value analysis at now - lockWindow and at now
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Sliding window")
    class SlidingWindow {

        private final LoginLockPolicy singleFailurePolicy = new LoginLockPolicy(1, Duration.ofMinutes(15));

        @Test
        @DisplayName("A failure exactly at now minus the lock window is counted, so the lower edge is inclusive")
        void lowerEdgeIsInclusive() {
            Instant onTheEdge = now.minus(Duration.ofMinutes(15));
            assertTrue(singleFailurePolicy.isBlocked(List.of(onTheEdge), now));
        }

        @Test
        @DisplayName("A failure one millisecond before the lock window starts is discarded")
        void justBeforeTheLowerEdgeIsExcluded() {
            Instant justOutside = now.minus(Duration.ofMinutes(15)).minusMillis(1);
            assertFalse(singleFailurePolicy.isBlocked(List.of(justOutside), now));
        }

        @Test
        @DisplayName("A failure one millisecond after the lock window starts is counted")
        void justInsideTheLowerEdgeIsCounted() {
            Instant justInside = now.minus(Duration.ofMinutes(15)).plusMillis(1);
            assertTrue(singleFailurePolicy.isBlocked(List.of(justInside), now));
        }

        @Test
        @DisplayName("A failure stamped exactly at now is counted, so the upper edge is inclusive")
        void upperEdgeIsInclusive() {
            assertTrue(singleFailurePolicy.isBlocked(List.of(now), now));
        }

        @Test
        @DisplayName("A failure stamped one millisecond after now is discarded as a future attempt")
        void futureAttemptsAreExcluded() {
            Instant future = now.plusMillis(1);
            assertFalse(singleFailurePolicy.isBlocked(List.of(future), now));
        }

        @Test
        @DisplayName("Future failures never contribute to the lock, no matter how many there are")
        void manyFutureAttemptsStillDoNotBlock() {
            List<Instant> future = List.of(
                    now.plusSeconds(1),
                    now.plusSeconds(2),
                    now.plusSeconds(3),
                    now.plusSeconds(4),
                    now.plusSeconds(5),
                    now.plusSeconds(6));
            assertFalse(policy.isBlocked(future, now));
        }

        @Test
        @DisplayName("Only the failures inside the window count towards the threshold when old, current and future ones are mixed")
        void onlyAttemptsInsideTheWindowAreCounted() {
            List<Instant> mixed = List.of(
                    now.minus(Duration.ofHours(1)),
                    now.minus(Duration.ofMinutes(16)),
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(1)),
                    now,
                    now.plusSeconds(30));
            assertFalse(policy.isBlocked(mixed, now),
                    "Only three of the six attempts fall inside the window, which is below the maximum of five");
        }

        @Test
        @DisplayName("Five failures inside the window still block when they are mixed with out-of-window ones")
        void insideWindowFailuresBlockDespiteNoise() {
            List<Instant> mixed = new ArrayList<>(failuresInsideWindow(5));
            mixed.add(now.minus(Duration.ofHours(3)));
            mixed.add(now.plusSeconds(90));
            assertTrue(policy.isBlocked(mixed, now));
        }

        @Test
        @DisplayName("A zero-length window only counts failures stamped exactly at now")
        void zeroLengthWindowCountsOnlyNow() {
            LoginLockPolicy instantaneous = new LoginLockPolicy(1, Duration.ZERO);
            assertTrue(instantaneous.isBlocked(List.of(now), now));
        }

        @Test
        @DisplayName("A zero-length window discards a failure from one millisecond ago")
        void zeroLengthWindowDiscardsThePast() {
            LoginLockPolicy instantaneous = new LoginLockPolicy(1, Duration.ZERO);
            assertFalse(instantaneous.isBlocked(List.of(now.minusMillis(1)), now));
        }
    }
}
