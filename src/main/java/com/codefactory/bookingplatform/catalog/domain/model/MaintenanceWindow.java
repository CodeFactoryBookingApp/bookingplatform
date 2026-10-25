package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A period, from {@code startsAt} included to {@code endsAt} excluded, in which a resource is out of service (HU-009). */
public record MaintenanceWindow(UUID id, Instant startsAt, Instant endsAt, String reason) {

    public MaintenanceWindow {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid maintenance period",
                    Map.of("endsAt", "must be after startsAt"));
        }
    }

    public boolean overlaps(Instant from, Instant to) {
        return startsAt.isBefore(to) && from.isBefore(endsAt);
    }

    public boolean covers(Instant at) {
        return !at.isBefore(startsAt) && at.isBefore(endsAt);
    }
}
