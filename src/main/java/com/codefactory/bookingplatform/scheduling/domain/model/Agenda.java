package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The agenda of a location, or of one resource in it (HU-006). Pure domain model.
 *
 * <p>It says when the location or resource can be booked: weekly time blocks, cut into slots of
 * 15, 30 or 60 minutes, between {@code validFrom} and {@code validUntil} (open-ended when null),
 * for the services listed. The availability engine (HU-008) builds the slots from it.
 */
@Getter
public class Agenda {

    public static final Set<Integer> GRANULARITIES = Set.of(15, 30, 60);

    private static final Comparator<TimeBlock> BY_DAY_AND_TIME =
            Comparator.comparing(TimeBlock::day).thenComparing(TimeBlock::start);

    private final UUID id;
    private final UUID organizationId;
    private final UUID locationId;
    private final UUID resourceId;
    private final String name;
    private int slotMinutes;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final List<UUID> serviceIds;
    private List<TimeBlock> blocks;

    @SuppressWarnings("java:S107") // reconstitution from storage needs every field
    public Agenda(UUID id, UUID organizationId, UUID locationId, UUID resourceId, String name, int slotMinutes,
                  LocalDate validFrom, LocalDate validUntil, List<UUID> serviceIds, List<TimeBlock> blocks) {
        this.id = id;
        this.organizationId = organizationId;
        this.locationId = locationId;
        this.resourceId = resourceId;
        this.name = name;
        this.slotMinutes = slotMinutes;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.serviceIds = List.copyOf(serviceIds);
        this.blocks = sorted(blocks);
    }

    /**
     * HU-006 "Crear una agenda válida": location, granularity, validity, at least one service
     * and its time blocks.
     */
    @SuppressWarnings("java:S107")
    public static Agenda create(UUID id, UUID organizationId, UUID locationId, UUID resourceId, String name,
                                int slotMinutes, LocalDate validFrom, LocalDate validUntil, List<UUID> serviceIds,
                                List<TimeBlock> blocks) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!GRANULARITIES.contains(slotMinutes)) {
            errors.put("slotMinutes", "must be 15, 30 or 60");
        }
        if (validFrom == null) {
            errors.put("validFrom", "is required");
        } else if (validUntil != null && validUntil.isBefore(validFrom)) {
            errors.put("validUntil", "must not be before validFrom");
        }
        if (serviceIds == null || serviceIds.isEmpty()) {
            errors.put("serviceIds", "at least one service is required");
        } else if (new HashSet<>(serviceIds).size() != serviceIds.size()) {
            errors.put("serviceIds", "a service is listed twice");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid agenda", errors);
        }
        requireValidBlocks(blocks);
        return new Agenda(id, organizationId, locationId, resourceId, name, slotMinutes, validFrom, validUntil,
                serviceIds, blocks);
    }

    /** HU-006 "Bloques solapados en el mismo día": detected when saving, never stored. */
    private static void requireValidBlocks(List<TimeBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid agenda",
                    Map.of("blocks", "at least one time block is required"));
        }
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                if (blocks.get(i).overlaps(blocks.get(j))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Overlapping time blocks",
                            Map.of("blocks", blocks.get(i) + " overlaps " + blocks.get(j)));
                }
            }
        }
    }

    private static List<TimeBlock> sorted(List<TimeBlock> blocks) {
        List<TimeBlock> copy = new ArrayList<>(blocks);
        copy.sort(BY_DAY_AND_TIME);
        return List.copyOf(copy);
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    /**
     * HU-006 "Modificación que deja reservas fuera de horario": new blocks are saved only if every
     * future booking of the agenda still fits in one of them. Otherwise the conflicting bookings
     * are listed and nothing changes; they have to be resolved first.
     */
    public void replaceBlocks(List<TimeBlock> newBlocks, List<BookedSlot> futureBookings, ZoneId zone) {
        requireValidBlocks(newBlocks);
        Map<String, String> conflicts = new LinkedHashMap<>();
        for (BookedSlot booking : futureBookings) {
            LocalDateTime start = LocalDateTime.ofInstant(booking.startsAt(), zone);
            LocalDateTime end = LocalDateTime.ofInstant(booking.endsAt(), zone);
            if (isValidOn(start.toLocalDate()) && !fits(newBlocks, start, end)) {
                conflicts.put(booking.bookingId().toString(),
                        start.toLocalDate() + " " + start.toLocalTime() + "-" + end.toLocalTime()
                                + " (" + start.getDayOfWeek() + ")");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.HAS_FUTURE_BOOKINGS,
                    "The new opening hours leave future bookings outside them; resolve them first", conflicts);
        }
        this.blocks = sorted(newBlocks);
    }

    private static boolean fits(List<TimeBlock> blocks, LocalDateTime start, LocalDateTime end) {
        return start.toLocalDate().equals(end.toLocalDate())
                && blocks.stream().anyMatch(block ->
                block.contains(start.getDayOfWeek(), start.toLocalTime(), end.toLocalTime()));
    }

    /**
     * HU-006 "Duplicar la configuración de un día": the target days end up with exactly the
     * blocks of the source day. It is a change of the opening hours like any other, so future
     * bookings are checked the same way.
     */
    public void copyDay(DayOfWeek from, Set<DayOfWeek> to, List<BookedSlot> futureBookings, ZoneId zone) {
        List<TimeBlock> source = blocks.stream().filter(block -> block.day() == from).toList();
        if (source.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nothing to copy",
                    Map.of("fromDay", from + " has no time blocks"));
        }
        Set<DayOfWeek> targets = new HashSet<>(to);
        targets.remove(from);
        List<TimeBlock> result = new ArrayList<>(blocks.stream().filter(block -> !targets.contains(block.day())).toList());
        for (DayOfWeek day : targets) {
            source.forEach(block -> result.add(block.on(day)));
        }
        replaceBlocks(result, futureBookings, zone);
    }

    /**
     * HU-006 "Cambio de granularidad con reservas futuras": not allowed, because the existing
     * bookings were made on the old slots.
     */
    public void changeGranularity(int newSlotMinutes, boolean hasFutureBookings) {
        if (!GRANULARITIES.contains(newSlotMinutes)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid granularity",
                    Map.of("slotMinutes", "must be 15, 30 or 60"));
        }
        if (newSlotMinutes == slotMinutes) {
            return;
        }
        if (hasFutureBookings) {
            throw new BusinessException(ErrorCode.HAS_FUTURE_BOOKINGS,
                    "The agenda has future bookings made on " + slotMinutes + "-minute slots; "
                            + "its granularity cannot change");
        }
        this.slotMinutes = newSlotMinutes;
    }
}
