package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-008/HU-011: the slots an agenda offers on a date, in the local time of its location, once
 * exceptions (HU-007), maintenance (HU-009) and existing bookings are taken into account.
 *
 * <p>Techniques: equivalence partitioning over the kind of date (outside validity, normal, CLOSED,
 * SPECIAL_HOURS) and boundary values on the end of a block (a slot that would end past it, or
 * past midnight, is not offered).
 */
class AvailabilityCalculatorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 11, 1);
    /** A Monday inside the agenda's validity. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    /** Monday 08:00-09:45 every 30 min: 08:00, 08:30 and 09:00 fit; 09:30 would end past 09:45. */
    private static Agenda agenda() {
        return Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30, VALID_FROM,
                null, List.of(UUID.randomUUID()),
                List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 45))));
    }

    private static Instant at(int hour, int minute) {
        return MONDAY.atTime(hour, minute).atZone(BOGOTA).toInstant();
    }

    private static List<Instant> starts(List<AvailableSlot> slots) {
        return slots.stream().map(AvailableSlot::startsAt).toList();
    }

    @Test
    @DisplayName("HU-008: the weekly block is cut into whole slots; a tail shorter than a slot is not offered")
    void blockIsCutIntoWholeSlots() {
        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda(), MONDAY, BOGOTA, null, List.of());

        // Assert
        assertEquals(List.of(at(8, 0), at(8, 30), at(9, 0)), starts(slots));
        assertEquals(at(8, 30), slots.get(0).endsAt());
    }

    @Test
    @DisplayName("HU-008: a date before the agenda's validity offers nothing, even on a weekday that has a block")
    void dateOutsideValidityOffersNothing() {
        // Arrange
        LocalDate mondayBeforeValidity = MONDAY.minusWeeks(1);

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda(), mondayBeforeValidity, BOGOTA, null,
                List.of());

        // Assert
        assertTrue(slots.isEmpty());
    }

    @Test
    @DisplayName("HU-007: a closed date offers nothing even though the week has a block that day")
    void closedDateOffersNothing() {
        // Arrange
        Agenda agenda = agenda();
        AgendaException closure = AgendaException.closed(agenda.getId(), MONDAY, "Festivo");

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda, MONDAY, BOGOTA, closure, List.of());

        // Assert
        assertTrue(slots.isEmpty());
    }

    @Test
    @DisplayName("HU-007: special hours replace the weekly blocks of that date")
    void specialHoursReplaceTheWeek() {
        // Arrange
        Agenda agenda = agenda();
        AgendaException special = AgendaException.specialHours(agenda.getId(), MONDAY, LocalTime.of(15, 0),
                LocalTime.of(16, 0), null);

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda, MONDAY, BOGOTA, special, List.of());

        // Assert
        assertEquals(List.of(at(15, 0), at(15, 30)), starts(slots));
    }

    @Test
    @DisplayName("HU-009: a slot that overlaps a maintenance window is not offered")
    void maintenanceRemovesOverlappingSlots() {
        // Arrange
        MaintenanceWindow maintenance = new MaintenanceWindow(UUID.randomUUID(), at(8, 15), at(8, 45), "Arreglo");

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda(), MONDAY, BOGOTA, null,
                List.of(maintenance));

        // Assert
        assertEquals(List.of(at(9, 0)), starts(slots));
    }

    @Test
    @DisplayName("HU-011: free slots are the possible ones that no booking overlaps")
    void bookedSlotsAreNotFree() {
        // Arrange
        BookedSlot booking = new BookedSlot(UUID.randomUUID(), at(8, 30), at(9, 0));

        // Act
        List<AvailableSlot> free = AvailabilityCalculator.freeSlotsFor(agenda(), MONDAY, BOGOTA, null, List.of(),
                List.of(booking));

        // Assert
        assertEquals(List.of(at(8, 0), at(9, 0)), starts(free));
    }

    @Test
    @DisplayName("HU-008: only the blocks of the date's weekday are cut into slots")
    void onlyTheBlocksOfThatWeekdayAreCut() {
        // Arrange
        Agenda agenda = Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda", 30,
                VALID_FROM, null, List.of(UUID.randomUUID()),
                List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                        new TimeBlock(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(15, 0))));

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(agenda, MONDAY, BOGOTA, null, List.of());

        // Assert
        assertEquals(List.of(at(8, 0), at(8, 30)), starts(slots));
    }

    @Test
    @DisplayName("HU-008: a slot that would end after midnight is not offered (block 22:00-23:59 in 60-minute slots)")
    void slotCrossingMidnightIsNotOffered() {
        // Arrange
        Agenda lateAgenda = Agenda.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Noche", 60,
                VALID_FROM, null, List.of(UUID.randomUUID()),
                List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(23, 59))));

        // Act
        List<AvailableSlot> slots = AvailabilityCalculator.possibleSlots(lateAgenda, MONDAY, BOGOTA, null, List.of());

        // Assert
        assertEquals(List.of(new AvailableSlot(at(22, 0), at(23, 0))), slots);
    }
}
