package com.codefactory.bookingplatform.notification.domain.model;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public record ReminderPreferences(boolean remindersEnabled, LocalTime quietFrom, LocalTime quietUntil, String timezone) {

    public static final ReminderPreferences DEFAULTS =
            new ReminderPreferences(true, LocalTime.of(22, 0), LocalTime.of(7, 0), null);

    public Optional<Instant> quietUntil(Instant now, String appointmentTimezone) {
        ZoneId zone = ZoneId.of(timezone == null ? appointmentTimezone : timezone);
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        boolean crossesMidnight = quietUntil.isBefore(quietFrom);
        boolean afterStart = !time.isBefore(quietFrom);
        boolean beforeEnd = time.isBefore(quietUntil);
        boolean quiet = crossesMidnight ? afterStart || beforeEnd : afterStart && beforeEnd;
        if (!quiet) {
            return Optional.empty();
        }
        var date = local.toLocalDate();
        if (crossesMidnight && afterStart) {
            date = date.plusDays(1);
        }
        // In an overlap, the later offset keeps the quiet period in force until the repeated hour ends.
        return Optional.of(date.atTime(quietUntil).atZone(zone).withLaterOffsetAtOverlap().toInstant());
    }
}
