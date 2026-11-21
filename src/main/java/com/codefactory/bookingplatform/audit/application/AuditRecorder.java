package com.codefactory.bookingplatform.audit.application;

import com.codefactory.bookingplatform.audit.domain.port.AuditEvents;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {

    private final AuditEvents events;

    public AuditRecorder(AuditEvents events) {
        this.events = events;
    }

    @EventListener
    public void record(AuditEvent event) {
        // Synchronous so a database-backed operation and its audit entry commit together.
        events.append(event);
    }
}
