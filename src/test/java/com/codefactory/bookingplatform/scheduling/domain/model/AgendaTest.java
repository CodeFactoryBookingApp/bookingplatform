package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-006 agenda rules: what a valid agenda is, the half-open time blocks, the check of future
 * bookings against new opening hours (in the local time of the location), the copy of a day and
 * the granularity change.
 */
class AgendaTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    /** Monday 2026-11-02 10:00 in Bogota (UTC-5). */
    private static final Instant MONDAY_10 = Instant.parse("2026-11-02T15:00:00Z");

    private static TimeBlock block(DayOfWeek day, String start, String end) {
        return new TimeBlock(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Agenda agenda(int slotMinutes, List<TimeBlock> blocks) {
        return Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda Poblado",
                slotMinutes, FROM, null, List.of(UUID.randomUUID()), blocks);
    }

    private static Agenda mondayAgenda() {
        return agenda(30, List.of(block(MONDAY, "08:00", "12:00"), block(MONDAY, "14:00", "18:00")));
    }

    private static BookedSlot bookingAt(Instant start, int minutes) {
        return new BookedSlot(UUID.randomUUID(), start, start.plusSeconds(minutes * 60L));
    }

    @Nested
    @DisplayName("Time blocks")
    class Blocks {

        @ParameterizedTest(name = "[{index}] 10:00-{0}")
        @ValueSource(strings = {"10:00", "09:59"})
        @DisplayName("HU-006: a block whose end is not after its start cannot be saved (boundary: equal)")
        void endNotAfterStartIsRejected(String end) {
            // Act
            Executable act = () -> block(MONDAY, "10:00", end);

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class, act).errorCode());
        }

        @Test
        @DisplayName("Blocks are half-open: one ending at 12:00 does not overlap one starting at 12:00")
        void adjacentBlocksDoNotOverlap() {
            // Act
            boolean overlaps = block(MONDAY, "08:00", "12:00").overlaps(block(MONDAY, "12:00", "14:00"));

            // Assert
            assertFalse(overlaps);
            assertFalse(block(MONDAY, "12:00", "14:00").overlaps(block(MONDAY, "08:00", "12:00")), "in either order");
        }

        @Test
        @DisplayName("The same hours on different days do not overlap")
        void differentDaysDoNotOverlap() {
            // Act
            boolean overlaps = block(MONDAY, "08:00", "12:00").overlaps(block(TUESDAY, "08:00", "12:00"));

            // Assert
            assertFalse(overlaps);
        }
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @ParameterizedTest(name = "[{index}] {0} minutes")
        @ValueSource(ints = {15, 30, 60})
        @DisplayName("HU-006: a valid agenda with a granularity of 15, 30 or 60 minutes is created")
        void validGranularitiesAreAccepted(int minutes) {
            // Act
            Agenda agenda = agenda(minutes, List.of(block(MONDAY, "08:00", "12:00")));

            // Assert
            assertEquals(minutes, agenda.getSlotMinutes());
        }

        @ParameterizedTest(name = "[{index}] {0} minutes")
        @ValueSource(ints = {0, 20, 45, 90})
        @DisplayName("Any other granularity is rejected")
        void otherGranularitiesAreRejected(int minutes) {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agenda(minutes, List.of(block(MONDAY, "08:00", "12:00"))));

            // Assert
            assertTrue(ex.details().containsKey("slotMinutes"));
        }

        @Test
        @DisplayName("HU-006: two blocks of the same day that overlap are detected and the agenda is not created")
        void overlappingBlocksAreRejected() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agenda(30, List.of(block(MONDAY, "08:00", "12:00"), block(MONDAY, "11:00", "13:00"))));

            // Assert
            assertEquals("MONDAY 08:00-12:00 overlaps MONDAY 11:00-13:00", ex.details().get("blocks"));
        }

        @Test
        @DisplayName("An agenda needs a service and a block, and its validity cannot end before it starts")
        void requiredPartsAreChecked() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> Agenda.create(UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30, FROM, FROM.minusDays(1), List.of(),
                    List.of(block(MONDAY, "08:00", "12:00"))));

            // Assert
            assertEquals(Map.of("validUntil", "must not be before validFrom",
                    "serviceIds", "at least one service is required"), ex.details());
        }

        @Test
        @DisplayName("An agenda without blocks is rejected")
        void blocksAreRequired() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda(30, List.of()));

            // Assert
            assertTrue(ex.details().containsKey("blocks"));
        }

        @Test
        @DisplayName("Validity includes both ends; without an end the agenda has none")
        void validityIncludesBothEnds() {
            // Arrange
            Agenda bounded = Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30,
                    FROM, FROM.plusDays(9), List.of(UUID.randomUUID()), List.of(block(MONDAY, "08:00", "12:00")));

            // Act
            boolean lastDay = bounded.isValidOn(FROM.plusDays(9));

            // Assert
            assertTrue(lastDay);
            assertFalse(bounded.isValidOn(FROM.plusDays(10)));
            assertFalse(bounded.isValidOn(FROM.minusDays(1)));
            assertTrue(mondayAgenda().isValidOn(FROM.plusYears(5)));
        }
    }

    @Nested
    @DisplayName("Changing the opening hours")
    class OpeningHours {

        @Test
        @DisplayName("HU-006: new hours that keep every future booking inside them are saved")
        void newHoursKeepingBookingsAreSaved() {
            // Arrange
            Agenda agenda = mondayAgenda();
            List<TimeBlock> newBlocks = List.of(block(MONDAY, "09:00", "13:00"));

            // Act
            agenda.replaceBlocks(newBlocks, List.of(bookingAt(MONDAY_10, 30)), BOGOTA);

            // Assert
            assertEquals(newBlocks, agenda.getBlocks());
        }

        @Test
        @DisplayName("HU-006: new hours that leave a booking outside are refused, listing that booking; nothing changes")
        void newHoursStrandingABookingAreRefused() {
            // Arrange
            Agenda agenda = mondayAgenda();
            BookedSlot booking = bookingAt(MONDAY_10, 30);
            List<TimeBlock> before = agenda.getBlocks();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.replaceBlocks(
                    List.of(block(TUESDAY, "08:00", "12:00"), block(MONDAY, "14:00", "18:00")), List.of(booking), BOGOTA));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            assertEquals(Map.of(booking.bookingId().toString(), "2026-11-02 10:00-10:30 (MONDAY)"), ex.details());
            assertEquals(before, agenda.getBlocks());
        }

        @Test
        @DisplayName("Bookings are compared in the local time of the location, not in UTC")
        void bookingsAreComparedInLocalTime() {
            // Arrange: 15:00 UTC is 10:00 in Bogota, inside 08:00-12:00 but outside it in UTC
            Agenda agenda = mondayAgenda();

            // Act
            Executable act = () -> agenda.replaceBlocks(List.of(block(MONDAY, "08:00", "12:00")),
                    List.of(bookingAt(MONDAY_10, 30)), BOGOTA);

            // Assert
            assertDoesNotThrow(act);
        }

        @Test
        @DisplayName("A booking must fit in one block: straddling two adjacent blocks is not inside them")
        void bookingAcrossTwoBlocksDoesNotFit() {
            // Arrange: 11:45-12:15 crosses from one block into the next
            Agenda agenda = mondayAgenda();
            BookedSlot booking = bookingAt(Instant.parse("2026-11-02T16:45:00Z"), 30);

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.replaceBlocks(
                    List.of(block(MONDAY, "08:00", "12:00"), block(MONDAY, "12:00", "18:00")), List.of(booking), BOGOTA));

            // Assert
            assertTrue(ex.details().containsKey(booking.bookingId().toString()));
        }

        @Test
        @DisplayName("A booking on a date outside the validity of the agenda is not its concern")
        void bookingOutsideTheValidityIsIgnored() {
            // Arrange
            Agenda agenda = mondayAgenda();
            BookedSlot beforeValidity = bookingAt(Instant.parse("2026-10-26T15:00:00Z"), 30);

            // Act
            Executable act = () -> agenda.replaceBlocks(List.of(block(TUESDAY, "08:00", "12:00")),
                    List.of(beforeValidity), BOGOTA);

            // Assert
            assertDoesNotThrow(act);
        }

        @Test
        @DisplayName("HU-006: copying a day gives the target days exactly its blocks and leaves the source as it was")
        void copyingADayDuplicatesItsBlocks() {
            // Arrange
            Agenda agenda = agenda(30, List.of(block(MONDAY, "08:00", "12:00"), block(WEDNESDAY, "07:00", "09:00")));

            // Act
            agenda.copyDay(MONDAY, Set.of(WEDNESDAY, FRIDAY, MONDAY), List.of(), BOGOTA);

            // Assert
            assertEquals(List.of(block(MONDAY, "08:00", "12:00"), block(WEDNESDAY, "08:00", "12:00"),
                    block(FRIDAY, "08:00", "12:00")), agenda.getBlocks());
        }

        @Test
        @DisplayName("Copying a day without blocks is rejected")
        void copyingAnEmptyDayIsRejected() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agenda.copyDay(TUESDAY, Set.of(FRIDAY), List.of(), BOGOTA));

            // Assert
            assertEquals(Map.of("fromDay", "TUESDAY has no time blocks"), ex.details());
        }
    }

    @Nested
    @DisplayName("Granularity")
    class Granularity {

        @Test
        @DisplayName("HU-006: the granularity of an agenda with future bookings cannot change")
        void changeWithFutureBookingsIsRefused() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.changeGranularity(15, true));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            assertEquals(30, agenda.getSlotMinutes());
        }

        @Test
        @DisplayName("Without future bookings the granularity changes")
        void changeWithoutBookingsIsApplied() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            agenda.changeGranularity(15, false);

            // Assert
            assertEquals(15, agenda.getSlotMinutes());
        }

        @Test
        @DisplayName("Setting the granularity it already has is not a change, even with bookings")
        void sameGranularityIsNotAChange() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            Executable act = () -> agenda.changeGranularity(30, true);

            // Assert
            assertDoesNotThrow(act);
        }

        @Test
        @DisplayName("An invalid granularity is rejected before looking at bookings")
        void invalidGranularityIsRejected() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.changeGranularity(20, false));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        }

        @ParameterizedTest(name = "[{index}] 30 -> {0} minutes")
        @ValueSource(ints = {14, 16, 29, 31, 45, 59, 61})
        @DisplayName("HU-006 boundaries: changing to n-1 or n+1 of 15, 30 or 60 is rejected and the granularity stays")
        void changeToANonGranularityIsRejected(int minutes) {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.changeGranularity(minutes, false));

            // Assert
            assertEquals(Map.of("slotMinutes", "must be 15, 30 or 60"), ex.details());
            assertEquals(30, agenda.getSlotMinutes());
        }
    }

    /**
     * HU-006 "Crear una agenda válida": the parts the audit found unverified. Technique: equivalence
     * partitioning (missing / repeated / valid) and boundary values (n-1, n, n+1) on the granularity
     * and on the validity period.
     */
    @Nested
    @DisplayName("Creation: required parts")
    class RequiredParts {

        private Agenda create(LocalDate validFrom, LocalDate validUntil, List<UUID> services, List<TimeBlock> blocks) {
            return Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30,
                    validFrom, validUntil, services, blocks);
        }

        @Test
        @DisplayName("HU-006: the same service listed twice is rejected, naming serviceIds")
        void repeatedServiceIsRejected() {
            // Arrange
            UUID service = UUID.randomUUID();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> create(FROM, null,
                    List.of(service, UUID.randomUUID(), service), List.of(block(MONDAY, "08:00", "12:00"))));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("serviceIds", "a service is listed twice"), ex.details());
        }

        @Test
        @DisplayName("HU-006: an agenda without validFrom is rejected, and an end date alone is not compared with it")
        void validFromIsRequired() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> create(null, FROM,
                    List.of(UUID.randomUUID()), List.of(block(MONDAY, "08:00", "12:00"))));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("validFrom", "is required"), ex.details());
        }

        @Test
        @DisplayName("HU-006: a missing list of services is rejected like an empty one")
        void missingServicesAreRejected() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> create(FROM, null, null, List.of(block(MONDAY, "08:00", "12:00"))));

            // Assert
            assertEquals(Map.of("serviceIds", "at least one service is required"), ex.details());
        }

        @Test
        @DisplayName("HU-006: a missing list of blocks is a validation error, not a crash")
        void missingBlocksAreRejected() {
            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> create(FROM, null, List.of(UUID.randomUUID()), null));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("blocks", "at least one time block is required"), ex.details());
        }

        @Test
        @DisplayName("Boundary: an agenda valid for a single day (validUntil = validFrom) is created")
        void singleDayValidityIsAccepted() {
            // Act
            Agenda agenda = create(FROM, FROM, List.of(UUID.randomUUID()), List.of(block(MONDAY, "08:00", "12:00")));

            // Assert
            assertEquals(FROM, agenda.getValidUntil());
            assertTrue(agenda.isValidOn(FROM));
        }

        @ParameterizedTest(name = "[{index}] {0} minutes -> accepted={1}")
        @CsvSource({"14, false", "15, true", "16, false", "29, false", "30, true", "31, false",
                "59, false", "60, true", "61, false"})
        @DisplayName("HU-006 boundaries: only 15, 30 and 60 minutes are a granularity; n-1 and n+1 are not")
        void granularityBoundaries(int minutes, boolean accepted) {
            // Act
            Executable act = () -> agenda(minutes, List.of(block(MONDAY, "08:00", "12:00")));

            // Assert
            if (accepted) {
                assertDoesNotThrow(act);
            } else {
                assertEquals(Map.of("slotMinutes", "must be 15, 30 or 60"),
                        assertThrows(BusinessException.class, act).details());
            }
        }
    }

    /** The blocks are always kept ordered by day and then by start time, however they arrive. */
    @Nested
    @DisplayName("Order of the blocks")
    class Order {

        @Test
        @DisplayName("An agenda read from storage gets its blocks ordered by day and then by start time")
        void reconstitutedBlocksAreSorted() {
            // Act
            Agenda agenda = new Agenda(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30,
                    FROM, null, List.of(UUID.randomUUID()), List.of(block(WEDNESDAY, "08:00", "10:00"),
                    block(MONDAY, "14:00", "16:00"), block(MONDAY, "08:00", "10:00")));

            // Assert
            assertEquals(List.of(block(MONDAY, "08:00", "10:00"), block(MONDAY, "14:00", "16:00"),
                    block(WEDNESDAY, "08:00", "10:00")), agenda.getBlocks());
        }

        @Test
        @DisplayName("New blocks are stored ordered by day and then by start time")
        void replacedBlocksAreSorted() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            agenda.replaceBlocks(List.of(block(THURSDAY, "09:00", "11:00"), block(TUESDAY, "15:00", "17:00"),
                    block(TUESDAY, "07:00", "09:00")), List.of(), BOGOTA);

            // Assert
            assertEquals(List.of(block(TUESDAY, "07:00", "09:00"), block(TUESDAY, "15:00", "17:00"),
                    block(THURSDAY, "09:00", "11:00")), agenda.getBlocks());
        }
    }

    /**
     * HU-006 "Bloques solapados en el mismo día" also applies to new opening hours, not only to a
     * new agenda. Technique: equivalence partitioning (overlapping / empty / replaced target day).
     */
    @Nested
    @DisplayName("New opening hours are validated")
    class NewBlocksValidation {

        @Test
        @DisplayName("HU-006: new hours with two overlapping blocks on the same day are refused and nothing changes")
        void overlappingNewBlocksAreRefused() {
            // Arrange
            Agenda agenda = mondayAgenda();
            List<TimeBlock> before = agenda.getBlocks();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.replaceBlocks(
                    List.of(block(TUESDAY, "08:00", "12:00"), block(TUESDAY, "11:59", "13:00")), List.of(), BOGOTA));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals(Map.of("blocks", "TUESDAY 08:00-12:00 overlaps TUESDAY 11:59-13:00"), ex.details());
            assertEquals(before, agenda.getBlocks());
        }

        @Test
        @DisplayName("HU-006: the overlap is found between any pair, not only between neighbours in the list")
        void overlapBetweenFirstAndLastIsFound() {
            // Arrange
            Agenda agenda = mondayAgenda();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> agenda.replaceBlocks(
                    List.of(block(FRIDAY, "08:00", "10:00"), block(MONDAY, "08:00", "12:00"),
                            block(FRIDAY, "09:00", "11:00")), List.of(), BOGOTA));

            // Assert
            assertEquals(Map.of("blocks", "FRIDAY 08:00-10:00 overlaps FRIDAY 09:00-11:00"), ex.details());
        }

        @Test
        @DisplayName("New hours with no block at all are refused and nothing changes")
        void emptyNewBlocksAreRefused() {
            // Arrange
            Agenda agenda = mondayAgenda();
            List<TimeBlock> before = agenda.getBlocks();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agenda.replaceBlocks(List.of(), List.of(), BOGOTA));

            // Assert
            assertEquals(Map.of("blocks", "at least one time block is required"), ex.details());
            assertEquals(before, agenda.getBlocks());
        }

        @Test
        @DisplayName("HU-006: copying a day onto a day with other hours replaces them, so no overlap can arise")
        void copyReplacesTheHoursOfTheTargetDay() {
            // Arrange: Tuesday 10:00-14:00 would overlap the copied 08:00-12:00 if it were kept
            Agenda agenda = agenda(30, List.of(block(MONDAY, "08:00", "12:00"), block(TUESDAY, "10:00", "14:00")));

            // Act
            agenda.copyDay(MONDAY, Set.of(TUESDAY), List.of(), BOGOTA);

            // Assert
            assertEquals(List.of(block(MONDAY, "08:00", "12:00"), block(TUESDAY, "08:00", "12:00")),
                    agenda.getBlocks());
        }

        @Test
        @DisplayName("HU-006: a copy that leaves a future booking of the target day outside is refused, listing it")
        void copyStrandingABookingIsRefused() {
            // Arrange: Tuesday 2026-11-03 15:00-15:30 in Bogota fits the current 14:00-18:00, not the copied 08:00-12:00
            Agenda agenda = agenda(30, List.of(block(MONDAY, "08:00", "12:00"), block(TUESDAY, "14:00", "18:00")));
            BookedSlot booking = bookingAt(Instant.parse("2026-11-03T20:00:00Z"), 30);
            List<TimeBlock> before = agenda.getBlocks();

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agenda.copyDay(MONDAY, Set.of(TUESDAY), List.of(booking), BOGOTA));

            // Assert
            assertEquals(ErrorCode.HAS_FUTURE_BOOKINGS, ex.errorCode());
            assertEquals(Map.of(booking.bookingId().toString(), "2026-11-03 15:00-15:30 (TUESDAY)"), ex.details());
            assertEquals(before, agenda.getBlocks());
        }
    }

    @Test
    @DisplayName("HU-006: a booking that runs past local midnight does not fit a block that ends before midnight")
    void bookingAcrossMidnightDoesNotFit() {
        // Arrange: Monday 23:30 to Tuesday 00:30 in Bogota; by the clock alone 00:30 is "before" 23:45
        Agenda agenda = mondayAgenda();
        BookedSlot booking = bookingAt(Instant.parse("2026-11-03T04:30:00Z"), 60);

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> agenda.replaceBlocks(
                List.of(block(MONDAY, "18:00", "23:45")), List.of(booking), BOGOTA));

        // Assert
        assertEquals(Map.of(booking.bookingId().toString(), "2026-11-02 23:30-00:30 (MONDAY)"), ex.details());
    }
}
