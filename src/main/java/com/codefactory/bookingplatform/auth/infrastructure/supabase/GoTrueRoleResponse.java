package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record GoTrueRoleResponse(UUID id, @JsonProperty("app_metadata") Map<String, Object> appMetadata,
                          @JsonProperty("confirmed_at") Instant confirmedAt,
                          @JsonProperty("banned_until") Instant bannedUntil,
                          @JsonProperty("deleted_at") Instant deletedAt) {

    PlatformUser toUser() {
        if (id == null) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "Missing identity provider user ID");
        }
        Object role = appMetadata == null ? null : appMetadata.get("role");
        AppRole appRole = Arrays.stream(AppRole.values())
                .filter(value -> value.name().equalsIgnoreCase(String.valueOf(role)))
                .findFirst().orElse(null);
        return new PlatformUser(id, appRole, confirmedAt, bannedUntil, deletedAt);
    }

    record Page(List<GoTrueRoleResponse> users) {

        List<PlatformUser> toUsers() {
            if (users == null) {
                throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "Missing identity provider user list");
            }
            return users.stream().map(GoTrueRoleResponse::toUser).toList();
        }
    }
}
