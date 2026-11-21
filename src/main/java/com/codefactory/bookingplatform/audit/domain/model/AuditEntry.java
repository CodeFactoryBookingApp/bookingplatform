package com.codefactory.bookingplatform.audit.domain.model;

import com.codefactory.bookingplatform.shared.audit.AuditEvent;

import java.util.UUID;

public record AuditEntry(UUID id, AuditEvent event) {
}
