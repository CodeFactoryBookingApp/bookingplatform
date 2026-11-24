package com.codefactory.bookingplatform.scheduling.domain.model;

import java.time.Instant;

/** One bookable slot of an agenda: [startsAt, endsAt) in UTC (HU-008). */
public record AvailableSlot(Instant startsAt, Instant endsAt) {

    public boolean overlaps(Instant from, Instant to) {
        return startsAt.isBefore(to) && from.isBefore(endsAt);
    }
}