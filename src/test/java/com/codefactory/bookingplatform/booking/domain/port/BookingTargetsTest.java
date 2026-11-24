package com.codefactory.bookingplatform.booking.domain.port;

import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingRecord;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleTarget;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The rules carried by the locked views of a booking (HU-013, HU-014, HU-016, HU-017).
 *
 * <p>Techniques: decision tables over status x start time (state transition guard), and boundary
 * values around the free cancellation window (window - 1 s, window, window + 1 s), the reschedule
 * limit (count = max - 1, max, max + 1) and the 48 h closure correction window.
 */
class BookingTargetsTest {

    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");
    private static final int WINDOW_MINUTES = 60;
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID RESOURCE = UUID.randomUUID();

    private static CancellationTarget cancellation(BookingStatus status, Instant startsAt) {
        return new CancellationTarget(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status, startsAt,
                WINDOW_MINUTES);
    }

    private static RescheduleTarget reschedule(BookingStatus status, Instant startsAt, int max, int count) {
        return new RescheduleTarget(UUID.randomUUID(), "RES-1", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LOCATION, UUID.randomUUID(), status, startsAt, startsAt.plusSeconds(1800),
                WINDOW_MINUTES, max, count, false, List.of(RESOURCE));
    }

    private static ClientBookingRecord record(BookingStatus status, Instant startsAt) {
        return new ClientBookingRecord(UUID.randomUUID(), "RES-1", UUID.randomUUID(), "Barbería", UUID.randomUUID(),
                "Corte", LOCATION, "Sede", "Calle 1", ZoneId.of("America/Bogota"), startsAt,
                startsAt.plusSeconds(1800), 1, new BigDecimal("45000.00"), "COP", status, WINDOW_MINUTES, 1, 0,
                false, false, List.of("Silla 1"));
    }

    @Nested
    @DisplayName("HU-013 cancellation")
    class Cancellation {

        @ParameterizedTest(name = "{0}, starts in {1} s -> cancellable={2}")
        @CsvSource({
                "RESERVED, 3600, true",
                "CONFIRMED, 3600, true",
                "CONFIRMED, 1, true",
                "CONFIRMED, 0, false",
                "CONFIRMED, -600, false",
                "CANCELLED, 3600, false",
                "COMPLETED, 3600, false",
                "NO_SHOW, 3600, false"
        })
        @DisplayName("HU-013: only a pending or confirmed booking that has not started can be cancelled")
        void cancellableOnlyWhileActiveAndNotStarted(BookingStatus status, long secondsToStart, boolean expected) {
            // Arrange
            CancellationTarget target = cancellation(status, NOW.plusSeconds(secondsToStart));

            // Act
            boolean cancellable = target.cancellable(NOW);

            // Assert
            assertEquals(expected, cancellable);
        }

        @ParameterizedTest(name = "starts {0} s after now -> late={1}")
        @CsvSource({
                "3599, true",
                "3600, false",
                "3601, false"
        })
        @DisplayName("HU-013: cancelling with less time left than the free window is late; exactly the window is still free")
        void lateCancellationBoundary(long secondsToStart, boolean expected) {
            // Arrange
            CancellationTarget target = cancellation(BookingStatus.CONFIRMED, NOW.plusSeconds(secondsToStart));

            // Act
            boolean late = target.lateCancellation(NOW);

            // Assert
            assertEquals(expected, late);
        }
    }

    @Nested
    @DisplayName("HU-014 reschedule")
    class Reschedule {

        @ParameterizedTest(name = "{0}, starts in {1} s -> reschedulable={2}")
        @CsvSource({
                "RESERVED, 3600, true",
                "CONFIRMED, 1, true",
                "CONFIRMED, 0, false",
                "CANCELLED, 3600, false",
                "COMPLETED, 3600, false",
                "NO_SHOW, 3600, false"
        })
        @DisplayName("HU-014: only a pending or confirmed booking that has not started can be rescheduled")
        void reschedulableOnlyWhileActiveAndNotStarted(BookingStatus status, long secondsToStart, boolean expected) {
            // Arrange
            RescheduleTarget target = reschedule(status, NOW.plusSeconds(secondsToStart), 1, 0);

            // Act
            boolean reschedulable = target.reschedulable(NOW);

            // Assert
            assertEquals(expected, reschedulable);
        }

        @ParameterizedTest(name = "max={0}, count={1} -> limit reached={2}")
        @CsvSource({
                "2, 1, false",
                "2, 2, true",
                "2, 3, true",
                "0, 0, true"
        })
        @DisplayName("HU-014: the reschedule limit is reached when the count equals the provider's maximum")
        void limitBoundary(int max, int count, boolean expected) {
            // Arrange
            RescheduleTarget target = reschedule(BookingStatus.CONFIRMED, NOW.plusSeconds(86400), max, count);

            // Act
            boolean reached = target.limitReached();

            // Assert
            assertEquals(expected, reached);
        }

        @ParameterizedTest(name = "starts {0} s after now -> late={1}")
        @CsvSource({
                "3599, true",
                "3600, false",
                "3601, false"
        })
        @DisplayName("HU-014: rescheduling inside the free cancellation window is marked as late")
        void lateRescheduleBoundary(long secondsToStart, boolean expected) {
            // Arrange
            RescheduleTarget target = reschedule(BookingStatus.CONFIRMED, NOW.plusSeconds(secondsToStart), 1, 0);

            // Act
            boolean late = target.lateRescheduleAt(NOW);

            // Assert
            assertEquals(expected, late);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "same location time and resource, 0, 0, false, true",
                "other start, 1800, 1800, false, false",
                "other end, 0, 900, false, false",
                "other location, 0, 0, true, false"
        })
        @DisplayName("HU-014: a reschedule must change the date, time, location or resource")
        void unchangedOnlyWhenEverythingMatches(String label, long startShift, long endShift, boolean otherLocation,
                                                boolean expected) {
            // Arrange
            Instant start = NOW.plusSeconds(86400);
            RescheduleTarget target = reschedule(BookingStatus.CONFIRMED, start, 1, 0);
            Slot slot = new Slot(start.plusSeconds(startShift), start.plusSeconds(1800 + endShift), UUID.randomUUID(),
                    List.of(RESOURCE));

            // Act
            boolean unchanged = target.unchanged(otherLocation ? UUID.randomUUID() : LOCATION, slot);

            // Assert
            assertEquals(expected, unchanged);
        }

        @Test
        @DisplayName("HU-014: the same time and place with a different resource is a real change")
        void otherResourceIsAChange() {
            // Arrange
            Instant start = NOW.plusSeconds(86400);
            RescheduleTarget target = reschedule(BookingStatus.CONFIRMED, start, 1, 0);
            Slot slot = new Slot(start, start.plusSeconds(1800), UUID.randomUUID(), List.of(UUID.randomUUID()));

            // Act
            boolean unchanged = target.unchanged(LOCATION, slot);

            // Assert
            assertFalse(unchanged);
        }
    }

    @Nested
    @DisplayName("HU-016 attendance")
    class Attendance {

        private AttendanceTarget target(BookingStatus status, Instant startsAt, Instant closedAt) {
            return new AttendanceTarget(UUID.randomUUID(), "RES-1", UUID.randomUUID(), UUID.randomUUID(), status,
                    startsAt, startsAt.plusSeconds(1800), closedAt);
        }

        @ParameterizedTest(name = "starts {0} s after now -> started={1}")
        @CsvSource({
                "1, false",
                "0, true",
                "-1, true"
        })
        @DisplayName("HU-016: attendance can be recorded from the exact start time on")
        void hasStartedBoundary(long secondsToStart, boolean expected) {
            // Arrange
            AttendanceTarget booking = target(BookingStatus.CONFIRMED, NOW.plusSeconds(secondsToStart), null);

            // Act
            boolean started = booking.hasStarted(NOW);

            // Assert
            assertEquals(expected, started);
        }

        @ParameterizedTest(name = "{0} -> closed={1}")
        @CsvSource({
                "RESERVED, false",
                "CONFIRMED, false",
                "CANCELLED, false",
                "COMPLETED, true",
                "NO_SHOW, true"
        })
        @DisplayName("HU-016: a booking is closed once it is COMPLETED or NO_SHOW")
        void closedStatuses(BookingStatus status, boolean expected) {
            // Arrange
            AttendanceTarget booking = target(status, NOW.minusSeconds(7200), null);

            // Act
            boolean closed = booking.closed();

            // Assert
            assertEquals(expected, closed);
        }

        @ParameterizedTest(name = "closed {0} s ago -> expired={1}")
        @CsvSource({
                "172799, false",
                "172800, false",
                "172801, true"
        })
        @DisplayName("HU-016: a closure can be corrected up to 48 hours after it was recorded")
        void correctionWindowBoundary(long secondsSinceClosure, boolean expected) {
            // Arrange
            AttendanceTarget booking = target(BookingStatus.NO_SHOW, NOW.minusSeconds(400000),
                    NOW.minusSeconds(secondsSinceClosure));

            // Act
            boolean expired = booking.correctionWindowExpired(NOW);

            // Assert
            assertEquals(expected, expired);
        }

        @Test
        @DisplayName("HU-016: an open booking has no correction window that can expire")
        void openBookingHasNoCorrectionWindow() {
            // Arrange
            AttendanceTarget booking = target(BookingStatus.CONFIRMED, NOW.minusSeconds(400000),
                    NOW.minusSeconds(300000));

            // Act
            boolean expired = booking.correctionWindowExpired(NOW);

            // Assert
            assertFalse(expired);
        }
    }

    @Nested
    @DisplayName("HU-017 client history")
    class History {

        @ParameterizedTest(name = "{0}, starts in {1} s -> upcoming={2}")
        @CsvSource({
                "RESERVED, 60, true",
                "CONFIRMED, 1, true",
                "CONFIRMED, 0, false",
                "CANCELLED, 3600, false",
                "COMPLETED, -3600, false",
                "NO_SHOW, -3600, false"
        })
        @DisplayName("HU-017: upcoming are the active bookings that have not started; everything else is history")
        void upcomingPartition(BookingStatus status, long secondsToStart, boolean expected) {
            // Arrange
            ClientBookingRecord booking = record(status, NOW.plusSeconds(secondsToStart));

            // Act
            boolean upcoming = booking.upcoming(NOW);

            // Assert
            assertEquals(expected, upcoming);
        }

        @ParameterizedTest(name = "starts {0} s after now -> within window={1}")
        @CsvSource({
                "3599, false",
                "3600, true",
                "3601, true"
        })
        @DisplayName("HU-017: the free window agrees with HU-013, exactly the window left is still free")
        void freeWindowBoundary(long secondsToStart, boolean expected) {
            // Arrange
            ClientBookingRecord booking = record(BookingStatus.CONFIRMED, NOW.plusSeconds(secondsToStart));

            // Act
            boolean within = booking.withinFreeWindow(NOW);

            // Assert
            assertEquals(expected, within);
        }
    }
}
