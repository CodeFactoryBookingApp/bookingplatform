package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

/**
 * Opening hours of an agenda on one day of the week, from {@code start} included to {@code end}
 * excluded, in the local time of its location (HU-006).
 */
public record TimeBlock(DayOfWeek day, LocalTime start, LocalTime end) {

    public TimeBlock {
        if (day == null || start == null || end == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Incomplete time block",
                    Map.of("blocks", "every block needs a day, a start time and an end time"));
        }
        // HU-006 "Hora de fin anterior a la de inicio": the block cannot be saved
        if (!end.isAfter(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid time block",
                    Map.of("blocks", day + " " + start + "-" + end + ": the end must be after the start"));
        }
    }

    public boolean overlaps(TimeBlock other) {
        return day.equals(other.day) && start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** Whether [from, to) on that day fits inside the block. */
    public boolean contains(DayOfWeek onDay, LocalTime from, LocalTime to) {
        return day.equals(onDay) && !from.isBefore(start) && !to.isAfter(end);
    }

    public TimeBlock on(DayOfWeek otherDay) {
        return new TimeBlock(otherDay, start, end);
    }

    @Override
    public String toString() {
        return day + " " + start + "-" + end;
    }
}
