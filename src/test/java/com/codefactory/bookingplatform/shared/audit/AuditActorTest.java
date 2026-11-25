package com.codefactory.bookingplatform.shared.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ADR-0010: the actor of a write is handed to the audit triggers as transaction-local settings, so
 * it is only set inside a read-write transaction (outside one, or in a read-only one, there is no
 * write to attribute and a session-wide setting would leak to the next user of the connection).
 * Decision table over "transaction active" x "read-only". The SQL itself runs in the ITs.
 */
class AuditActorTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private JdbcClient jdbc;
    private JdbcClient.StatementSpec statement;
    private JdbcClient.ResultQuerySpec result;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcClient.class);
        statement = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        result = mock(JdbcClient.ResultQuerySpec.class);
        when(statement.query()).thenReturn(result);
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @ParameterizedTest(name = "[{index}] active={0} readOnly={1}")
    @CsvSource({"false, false", "false, true", "true, true"})
    @DisplayName("ADR-0010: without a read-write transaction no actor is set")
    void noActorOutsideAReadWriteTransaction(boolean active, boolean readOnly) {
        // Arrange
        TransactionSynchronizationManager.setActualTransactionActive(active);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);

        // Act
        new AuditActor(jdbc).identify(USER, "OWNER");

        // Assert
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("ADR-0010: inside a read-write transaction the user id and role are set as transaction-local settings")
    void actorIsSetInsideAReadWriteTransaction() {
        // Arrange
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        // Act
        new AuditActor(jdbc).identify(USER, "OWNER");

        // Assert
        verify(jdbc).sql(contains("set_config('booking.audit_user', :id, true)"));
        verify(statement).param("id", USER.toString());
        verify(statement).param("role", "OWNER");
        verify(result).singleRow();
    }
}
