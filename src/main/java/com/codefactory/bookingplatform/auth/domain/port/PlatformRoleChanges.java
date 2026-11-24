package com.codefactory.bookingplatform.auth.domain.port;

import com.codefactory.bookingplatform.auth.domain.model.RoleChange;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PlatformRoleChanges {

    void lock();

    Optional<RoleChange> find(UUID userId);

    void reserve(RoleChange change);

    void complete(RoleChange change, Instant at);
}
