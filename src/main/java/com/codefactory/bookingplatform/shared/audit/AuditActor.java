package com.codefactory.bookingplatform.shared.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Provides the actor for audit triggers on the same connection and transaction as the business change. */
@Component
public class AuditActor {

    private final JdbcClient jdbc;

    public AuditActor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void identify(UUID id, String role) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            jdbc.sql("""
                    select set_config('booking.audit_user', :id, true) as user_context,
                           set_config('booking.audit_role', :role, true) as role_context
                    """).param("id", id.toString()).param("role", role).query().singleRow();
        }
    }
}
