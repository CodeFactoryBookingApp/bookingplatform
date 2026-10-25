package com.codefactory.bookingplatform.identity.domain.port;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;

import java.util.UUID;

/** HU-003: every status change of a client is stored with its reason and who made it. */
public interface ClientStatusChanges {

    void record(UUID clientId, ClientStatus from, ClientStatus to, String reason, UUID changedBy);
}
