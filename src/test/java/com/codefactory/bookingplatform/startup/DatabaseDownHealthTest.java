package com.codefactory.bookingplatform.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What {@code /actuator/health} reports when the database stops answering.
 *
 * <p>The indicator wired by the application is exercised against a datasource
 * that refuses connections, which is what a Supabase pooler at its 15 client
 * limit looks like from inside the application. The aggregate status turns
 * DOWN, so {@code /actuator/health} answers 503 — while
 * {@code /actuator/health/readiness}, the path configured in
 * {@code render.yaml}, stays UP because the readiness group has no database
 * member (pinned in {@code ActuatorHealthIT}).
 */
class DatabaseDownHealthTest {

    private static DataSource refusingDataSource(String message) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException(message));
        return dataSource;
    }

    @Test
    @DisplayName("An unreachable database turns the db indicator DOWN")
    void unreachableDatabaseIsDown() throws SQLException {
        DataSourceHealthIndicator indicator =
                new DataSourceHealthIndicator(refusingDataSource("Connection refused"));

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    @DisplayName("RISK: the details name the generic JDBC failure, the driver's root cause is dropped")
    void rootCauseIsNotReported() throws SQLException {
        DataSourceHealthIndicator indicator = new DataSourceHealthIndicator(
                refusingDataSource("FATAL: (EMAXCONNSESSION) max clients reached"));

        Health health = indicator.health();
        String error = String.valueOf(health.getDetails().get("error"));

        assertNotNull(health.getDetails().get("error"));
        assertEquals("org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection",
                error);
        assertFalse(error.contains("EMAXCONNSESSION"),
                "diagnosing a pooler exhaustion needs the application log, the endpoint does not carry it");
    }

    @Test
    @DisplayName("RISK: the details exist but management.endpoint.health.show-details is never, so the caller only sees DOWN")
    void detailsAreNotExposedToTheCaller() throws SQLException {
        DataSourceHealthIndicator indicator =
                new DataSourceHealthIndicator(refusingDataSource("Connection refused"));

        assertFalse(indicator.health().getDetails().isEmpty(),
                "the indicator does produce details; it is the endpoint configuration that hides them");
    }
}
