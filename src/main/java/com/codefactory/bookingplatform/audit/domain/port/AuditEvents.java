package com.codefactory.bookingplatform.audit.domain.port;

import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditEvents {

    void append(AuditEvent event);

    long count(UUID organizationId, AuditFilter filter);

    List<AuditEntry> page(UUID organizationId, AuditFilter filter, int size, long offset);

    List<AuditEntry> findAll(UUID organizationId, AuditFilter filter);
}
