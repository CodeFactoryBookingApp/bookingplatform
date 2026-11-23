package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-008 "Generación de franjas": construye, para un día concreto, las franjas que una agenda
 * (HU-006) ofrece — y cuáles de ellas están realmente libres — a partir de sus bloques semanales,
 * sus excepciones (HU-007), los mantenimientos del recurso si la agenda es de uno (HU-009) y sus
 * reservas ya hechas. Función pura: sin frameworks, sin E/S.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /**
     * Toda la franja que la agenda ofrecería ese día, sin descontar reservas. HU-019 la usa como
     * el total frente al cual medir la ocupación.
     *
     * @param exception    la excepción de esa fecha, si la agenda tiene una (HU-007); {@code null} si no
     * @param maintenances los mantenimientos del recurso de la agenda (HU-009); vacío si la
     *                     agenda es de toda la sede, o si el recurso no tiene ninguno
     */
    public static List<AvailableSlot> possibleSlots(Agenda agenda, LocalDate date, ZoneId zone,
                                                     AgendaException exception,
                                                     List<MaintenanceWindow> maintenances) {
        if (!agenda.isValidOn(date)) {
            return List.of();
        }
        if (exception != null && exception.type() == AgendaException.Type.CLOSED) {
            return List.of();
        }
        List<TimeBlock> blocksOfDay = exception != null && exception.type() == AgendaException.Type.SPECIAL_HOURS
                ? List.of(exception.asBlock())
                : agenda.getBlocks().stream().filter(block -> block.day().equals(date.getDayOfWeek())).toList();

        List<AvailableSlot> slots = new ArrayList<>();
        for (TimeBlock block : blocksOfDay) {
            LocalTime cursor = block.start();
            while (cursor.isBefore(block.end())) {
                LocalTime next = cursor.plusMinutes(agenda.getSlotMinutes());
                // !next.isAfter(cursor): protects against a slot that would cross midnight
                // (LocalTime wraps instead of overflowing); blocks never cross midnight (TimeBlock).
                if (!next.isAfter(cursor) || next.isAfter(block.end())) {
                    break;
                }
                Instant start = LocalDateTime.of(date, cursor).atZone(zone).toInstant();
                Instant end = LocalDateTime.of(date, next).atZone(zone).toInstant();
                if (maintenances.stream().noneMatch(window -> window.overlaps(start, end))) {
                    slots.add(new AvailableSlot(start, end));
                }
                cursor = next;
            }
        }
        return slots;
    }

    /** The slots of {@code possible} that do not overlap any booking already made (HU-008). */
    public static List<AvailableSlot> freeSlots(List<AvailableSlot> possible, List<BookedSlot> booked) {
        return possible.stream()
                .filter(slot -> booked.stream().noneMatch(b -> slot.overlaps(b.startsAt(), b.endsAt())))
                .toList();
    }

    /** Convenience: the slots of the agenda that are actually free that day (HU-008 + HU-011). */
    public static List<AvailableSlot> freeSlotsFor(Agenda agenda, LocalDate date, ZoneId zone,
                                                    AgendaException exception, List<MaintenanceWindow> maintenances,
                                                    List<BookedSlot> booked) {
        return freeSlots(possibleSlots(agenda, date, zone, exception, maintenances), booked);
    }
}