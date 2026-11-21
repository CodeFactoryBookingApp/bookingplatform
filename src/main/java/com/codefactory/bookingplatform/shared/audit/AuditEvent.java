package com.codefactory.bookingplatform.shared.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(Instant occurredAt, UUID actorId, String actorRole, String action,
                         String resourceType, UUID resourceId, UUID organizationId, String result,
                         UUID relatedResourceId) {

    public AuditEvent(Instant occurredAt, UUID actorId, String actorRole, String action,
                      String resourceType, UUID resourceId, UUID organizationId, String result) {
        this(occurredAt, actorId, actorRole, action, resourceType, resourceId, organizationId, result, null);
    }
}
