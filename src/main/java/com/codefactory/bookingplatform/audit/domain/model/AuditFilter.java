package com.codefactory.bookingplatform.audit.domain.model;

import java.time.Instant;
import java.util.UUID;

public record AuditFilter(Instant from, Instant to, UUID actorId, String action) {
}
