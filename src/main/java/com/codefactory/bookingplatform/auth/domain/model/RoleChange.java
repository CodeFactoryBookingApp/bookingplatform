package com.codefactory.bookingplatform.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RoleChange(UUID userId, UUID revision, AppRole role, Instant completedAt) {

    public static final String REVISION_CLAIM = "booking_role_revision";

    public boolean pending() {
        return completedAt == null;
    }
}
