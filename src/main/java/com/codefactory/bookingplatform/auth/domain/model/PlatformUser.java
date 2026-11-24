package com.codefactory.bookingplatform.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PlatformUser(UUID id, AppRole role, Instant confirmedAt, Instant bannedUntil, Instant deletedAt) {

    public boolean isActiveAdministrator(Instant now) {
        return role == AppRole.ADMIN && confirmedAt != null && deletedAt == null
                && (bannedUntil == null || !bannedUntil.isAfter(now));
    }
}
