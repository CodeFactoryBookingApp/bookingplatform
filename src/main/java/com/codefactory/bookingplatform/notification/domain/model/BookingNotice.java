package com.codefactory.bookingplatform.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class BookingNotice {

    private BookingNotice() {
    }

    public enum Kind {
        CREATED, CONFIRMED, CANCELLED, RESCHEDULED, REMINDER
    }

    public record Candidate(UUID id, UUID bookingId) {
    }

    public record Delivery(UUID id, Kind kind, UUID recipientId, String recipientEmail, String code, String serviceName,
                           String locationName, Instant appointmentAt, String appointmentStatus,
                           String appointmentTimezone, String currentStatus, ReminderPreferences preferences) {
    }
}
