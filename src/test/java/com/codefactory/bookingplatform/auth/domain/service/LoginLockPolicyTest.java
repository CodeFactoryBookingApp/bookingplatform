package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Every test follows Arrange-Act-Assert with a single Act per test.</p>
 */
class LoginLockPolicyTest {

    private final LoginLockPolicy policy = new LoginLockPolicy(5, Duration.ofMinutes(15));
    private final Instant now = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    @DisplayName("Failures outside the sliding window are ignored")
    void oldFailuresAreIgnored() {
        // Arrange
        List<Instant> failures = List.of(
                now.minus(Duration.ofMinutes(60)),
                now.minus(Duration.ofMinutes(50)),
                now.minus(Duration.ofMinutes(40)),
                now.minus(Duration.ofMinutes(30)),
                now.minus(Duration.ofMinutes(20)));

        // Act
        boolean blocked = policy.isBlocked(failures, now);

        // Assert
        assertFalse(blocked);
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
        @ValueSource(ints = {0, -1})
        @DisplayName("A non-positive maximum of failed attempts is rejected, because a lock that triggers at zero failures would block everyone")
        void nonPositiveMaximumIsRejected(int maxFailedAttempts) {
            // Act
            Executable act = () -> new LoginLockPolicy(maxFailedAttempts, Duration.ofMinutes(15));

            // Assert
            assertThrows(IllegalArgumentException.class, act);
        }

        @Test
        @DisplayName("The rejection message names the offending parameter")
        void rejectionMessageNamesTheParameter() {
            // Act
            Executable act = () -> new LoginLockPolicy(0, Duration.ofMinutes(15));

            // Assert
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, act);
            assertEquals("maxFailedAttempts must be positive", thrown.getMessage());
        }

        @Test
        @DisplayName("A policy of one attempt blocks on the very first failure")
        void policyOfOneBlocksOnFirstFailure() {
            // Arrange
            LoginLockPolicy strict = new LoginLockPolicy(1, Duration.ofMinutes(15));
            List<Instant> failures = List.of(now.minus(Duration.ofSeconds(1)));

            // Act
            boolean blocked = strict.isBlocked(failures, now);

            // Assert
            assertTrue(blocked);
        }

        @Test
        @DisplayName("A policy of one attempt does not block when there are no failures")
        void policyOfOneDoesNotBlockWithoutFailures() {
            // Arrange
            LoginLockPolicy strict = new LoginLockPolicy(1, Duration.ofMinutes(15));

            // Act
            boolean blocked = strict.isBlocked(List.of(), now);

            // Assert
            assertFalse(blocked);
        }
    }

    // ----------------------------------------------------------------------------------------
    // Threshold: boundary value analysis at max - 1, max and max + 1
    // ----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Threshold")
    class Threshold {

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
            // Arrange
            List<Instant> failures = failuresInsideWindow(failureCount);

            // Act
            boolean blocked = policy.isBlocked(failures, now);

            // Assert
            assertEquals(expectedBlocked, blocked);
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
            // Arrange
            Instant onTheEdge = now.minus(Duration.ofMinutes(15));

            // Act
            boolean blocked = singleFailurePolicy.isBlocked(List.of(onTheEdge), now);

            // Assert
            assertTrue(blocked);
        }

        @Test
        @DisplayName("A failure one millisecond before the lock window starts is discarded")
        void justBeforeTheLowerEdgeIsExcluded() {
            // Arrange
            Instant justOutside = now.minus(Duration.ofMinutes(15)).minusMillis(1);

            // Act
            boolean blocked = singleFailurePolicy.isBlocked(List.of(justOutside), now);

            // Assert
            assertFalse(blocked);
        }

        @Test
        @DisplayName("A failure one millisecond after the lock window starts is counted")
        void justInsideTheLowerEdgeIsCounted() {
            // Arrange
            Instant justInside = now.minus(Duration.ofMinutes(15)).plusMillis(1);

            // Act
            boolean blocked = singleFailurePolicy.isBlocked(List.of(justInside), now);

            // Assert
            assertTrue(blocked);
        }

        @Test
        @DisplayName("A failure stamped exactly at now is counted, so the upper edge is inclusive")
        void upperEdgeIsInclusive() {
            // Act
            boolean blocked = singleFailurePolicy.isBlocked(List.of(now), now);

            // Assert
            assertTrue(blocked);
        }

        @Test
        @DisplayName("A failure stamped one millisecond after now is discarded as a future attempt")
        void futureAttemptsAreExcluded() {
            // Arrange
            Instant future = now.plusMillis(1);

            // Act
            boolean blocked = singleFailurePolicy.isBlocked(List.of(future), now);

            // Assert
            assertFalse(blocked);
        }

        @Test
        @DisplayName("Future failures never contribute to the lock, no matter how many there are")
        void manyFutureAttemptsStillDoNotBlock() {
            // Arrange
            List<Instant> future = List.of(
                    now.plusSeconds(1),
                    now.plusSeconds(2),
                    now.plusSeconds(3),
                    now.plusSeconds(4),
                    now.plusSeconds(5),
                    now.plusSeconds(6));

            // Act
            boolean blocked = policy.isBlocked(future, now);

            // Assert
            assertFalse(blocked);
        }

        @Test
        @DisplayName("Only the failures inside the window count towards the threshold when old, current and future ones are mixed")
        void onlyAttemptsInsideTheWindowAreCounted() {
            // Arrange
            List<Instant> mixed = List.of(
                    now.minus(Duration.ofHours(1)),
                    now.minus(Duration.ofMinutes(16)),
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(1)),
                    now,
                    now.plusSeconds(30));

            // Act
            boolean blocked = policy.isBlocked(mixed, now);

            // Assert
            assertFalse(blocked,
                    "Only three of the six attempts fall inside the window, which is below the maximum of five");
        }

        @Test
        @DisplayName("Five failures inside the window still block when they are mixed with out-of-window ones")
        void insideWindowFailuresBlockDespiteNoise() {
            // Arrange
            List<Instant> mixed = new ArrayList<>(failuresInsideWindow(5));
            mixed.add(now.minus(Duration.ofHours(3)));
            mixed.add(now.plusSeconds(90));

            // Act
            boolean blocked = policy.isBlocked(mixed, now);

            // Assert
            assertTrue(blocked);
        }
    }
}
