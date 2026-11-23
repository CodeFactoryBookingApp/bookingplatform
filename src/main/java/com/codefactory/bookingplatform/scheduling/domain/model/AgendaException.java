package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An exception to the weekly opening hours of an agenda, on one date (HU-007). Pure domain model.
 *
 * <p>Two kinds: {@code CLOSED} — the agenda does not operate at all that date (a holiday, a
 * closure) — and {@code SPECIAL_HOURS} — that date replaces the weekly {@link TimeBlock}s of the
 * agenda with the hours given here (e.g. a shorter schedule on Christmas Eve). At most one
 * exception per agenda per date (V14).
 */
public record AgendaException(UUID id, UUID agendaId, LocalDate date, Type type, LocalTime specialStart,
                              LocalTime specialEnd, String reason) {

    public enum Type { CLOSED, SPECIAL_HOURS }

    public AgendaException {
        Map<String, String> errors = new LinkedHashMap<>();
        if (agendaId == null) {
            errors.put("agendaId", "is required");
        }
        if (date == null) {
            errors.put("date", "is required");
        }
        if (type == null) {
            errors.put("type", "is required");
        } else if (type == Type.CLOSED && (specialStart != null || specialEnd != null)) {
            errors.put("specialStart", "a CLOSED exception has no hours of its own");
        } else if (type == Type.SPECIAL_HOURS) {
            if (specialStart == null || specialEnd == null) {
                errors.put("specialStart", "SPECIAL_HOURS requires specialStart and specialEnd");
            } else if (!specialEnd.isAfter(specialStart)) {
                errors.put("specialEnd", "must be after specialStart");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid agenda exception", errors);
        }
    }

    public static AgendaException closed(UUID agendaId, LocalDate date, String reason) {
        return new AgendaException(UUID.randomUUID(), agendaId, date, Type.CLOSED, null, null, reason);
    }

    public static AgendaException specialHours(UUID agendaId, LocalDate date, LocalTime start, LocalTime end,
                                               String reason) {
        return new AgendaException(UUID.randomUUID(), agendaId, date, Type.SPECIAL_HOURS, start, end, reason);
    }

    /** The block this exception replaces the agenda's weekly blocks with. Only for SPECIAL_HOURS. */
    public TimeBlock asBlock() {
        if (type != Type.SPECIAL_HOURS) {
            throw new IllegalStateException("Only a SPECIAL_HOURS exception has a block");
        }
        return new TimeBlock(date.getDayOfWeek(), specialStart, specialEnd);
    }
}