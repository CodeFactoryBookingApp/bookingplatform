package com.codefactory.bookingplatform.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The booking rules that live in {@code fn_bookings_reglas}, as V3 left them.
 *
 * <p>There is no booking module yet, so the trigger is exercised with plain SQL. The container is
 * private to this class and migrated with the Flyway API, not shared with the Spring-based ITs:
 * those wipe {@code clients} before each test, which the bookings created here would block.
 *
 * <p>Arrange-Act-Assert: each test builds its own provider with {@link #newWorld}, so no test
 * depends on the rows another one left behind.
 */
class BookingRulesIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CURRENCY_ID = UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        sql("insert into currencies (id, code, name) values ('" + CURRENCY_ID + "', 'COP', 'Peso colombiano')");
    }

    /** A provider, with its client, location, policy and one service, ready to take bookings. */
    private record World(String clientId, String organizationId, String locationId, String policyId,
                         String serviceId) {
    }

    private static World newWorld(int serviceCapacity) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String cityId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        World world = new World(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        sql("insert into cities (id, name, region) values ('" + cityId + "', 'Ciudad " + suffix + "', 'Región')");
        sql("insert into organization_categories (id, name) values ('" + categoryId + "', 'Categoría " + suffix + "')");
        sql("insert into clients values ('" + world.clientId() + "', 'Cliente', 'DOC-" + suffix + "', '1990-01-01', "
                + "'cliente-" + suffix + "@example.com', '3001112233', 'Medellín', 'EMAIL', 'ACTIVE', "
                + "localtimestamp, localtimestamp)");
        sql("insert into organizations (id, category_id, name, nit, contact_email, contact_phone, status, approved_at) "
                + "values ('" + world.organizationId() + "', '" + categoryId + "', 'Proveedor', 'NIT-" + suffix + "', "
                + "'proveedor@example.com', '6041112233', 'ACTIVE', now())");
        sql("insert into organization_policies (id, organization_id, version, booking_notice_minutes_min, "
                + "booking_notice_minutes_max, free_cancellation_window_minutes) "
                + "values ('" + world.policyId() + "', '" + world.organizationId() + "', 1, 0, 43200, 60)");
        sql("insert into locations (id, organization_id, name, address, city_id) values ('" + world.locationId()
                + "', '" + world.organizationId() + "', 'Sede', 'Calle 1', '" + cityId + "')");
        sql("insert into services (id, organization_id, name, duration_minutes, price, currency_id, "
                + "customer_capacity, status) values ('" + world.serviceId() + "', '" + world.organizationId()
                + "', 'Servicio', 30, 1000, '" + CURRENCY_ID + "', " + serviceCapacity + ", 'ACTIVE')");
        sql("insert into service_locations (service_id, location_id, organization_id) values ('"
                + world.serviceId() + "', '" + world.locationId() + "', '" + world.organizationId() + "')");
        return world;
    }

    private static String bookingInsert(World world, String sessionId, int attendees, String status) {
        return "insert into bookings (id, client_id, organization_id, service_id, location_id, policy_id, "
                + "session_id, starts_at, ends_at, attendees, total_price, status) values ('"
                + UUID.randomUUID() + "', '" + world.clientId() + "', '" + world.organizationId() + "', '"
                + world.serviceId() + "', '" + world.locationId() + "', '" + world.policyId() + "', '"
                + sessionId + "', now() + interval '2 days', now() + interval '2 days 30 minutes', "
                + attendees + ", 1000, '" + status + "') returning id";
    }

    private static String book(World world, String status) {
        return queryString(bookingInsert(world, UUID.randomUUID().toString(), 1, status));
    }

    /** HU-003: the reason goes in the same transaction as the change, as the trigger demands. */
    private static void suspend(World world) {
        sql("insert into account_status_changes (subject_type, organization_id, from_status, to_status, reason) "
                + "values ('ORGANIZATION', '" + world.organizationId() + "', 'ACTIVE', 'SUSPENDED', 'Prueba'); "
                + "update organizations set status = 'SUSPENDED' where id = '" + world.organizationId() + "'");
    }

    private static void deactivateService(World world) {
        sql("update services set status = 'INACTIVE' where id = '" + world.serviceId() + "'");
    }

    private static String statusOf(String bookingId) {
        return queryString("select status from bookings where id = '" + bookingId + "'");
    }

    @Test
    @DisplayName("A new service starts INACTIVE when no status is given (HU-004)")
    void newServiceStartsInactive() {
        // Arrange
        World world = newWorld(1);

        // Act
        String status = queryString("insert into services (organization_id, name, duration_minutes, price, "
                + "currency_id) values ('" + world.organizationId() + "', 'Sin estado', 30, 1000, '"
                + CURRENCY_ID + "') returning status");

        // Assert
        assertEquals("INACTIVE", status);
    }

    @Test
    @DisplayName("A future booking of a suspended provider can still be cancelled (HU-003, HU-013)")
    void bookingOfSuspendedProviderCanBeCancelled() {
        // Arrange
        World world = newWorld(1);
        String bookingId = book(world, "CONFIRMED");
        suspend(world);

        // Act
        sql("update bookings set status = 'CANCELLED', cancelled_at = now() where id = '" + bookingId + "'");

        // Assert
        assertEquals("CANCELLED", statusOf(bookingId));
    }

    @Test
    @DisplayName("A pending booking of a deactivated service can still be confirmed (HU-004)")
    void pendingBookingOfDeactivatedServiceCanBeConfirmed() {
        // Arrange
        World world = newWorld(1);
        String bookingId = book(world, "RESERVED");
        deactivateService(world);

        // Act
        sql("update bookings set status = 'CONFIRMED' where id = '" + bookingId + "'");

        // Assert
        assertEquals("CONFIRMED", statusOf(bookingId));
    }

    @Test
    @DisplayName("Moving a booking of a deactivated service to another time is rejected (HU-004)")
    void bookingOfDeactivatedServiceCannotBeMoved() {
        // Arrange
        World world = newWorld(1);
        String bookingId = book(world, "CONFIRMED");
        deactivateService(world);

        // Act
        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> sql(
                "update bookings set starts_at = starts_at + interval '1 day', ends_at = ends_at + interval '1 day' "
                        + "where id = '" + bookingId + "'"));

        // Assert
        assertTrue(rejected.getCause().getMessage().contains("HU-004"), rejected.getCause().getMessage());
    }

    @Test
    @DisplayName("A suspended provider still takes no new bookings (HU-002)")
    void suspendedProviderTakesNoNewBookings() {
        // Arrange
        World world = newWorld(1);
        suspend(world);

        // Act
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> book(world, "RESERVED"));

        // Assert
        assertTrue(rejected.getCause().getMessage().contains("HU-002"), rejected.getCause().getMessage());
    }

    @Test
    @DisplayName("Two simultaneous bookings for the last seat of a group session: exactly one gets in (HU-010)")
    void lastSeatGoesToExactlyOneOfTwoConcurrentBookings() throws Exception {
        // Arrange: capacity 2, one seat already taken
        World world = newWorld(2);
        String sessionId = UUID.randomUUID().toString();
        queryString(bookingInsert(world, sessionId, 1, "RESERVED"));

        try (Connection first = connect(); Connection second = connect()) {
            first.setAutoCommit(false);
            execute(first, bookingInsert(world, sessionId, 1, "RESERVED"));

            // Act: the second booking starts while the first one is still open
            CompletableFuture<Void> secondBooking = CompletableFuture.runAsync(() -> {
                try {
                    execute(second, bookingInsert(world, sessionId, 1, "RESERVED"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            awaitAdvisoryLockWaiter();
            first.commit();

            // Assert
            Exception rejected = assertThrows(Exception.class, () -> secondBooking.get(10, TimeUnit.SECONDS));
            assertTrue(rejected.getCause().getCause().getMessage().contains("aforo superado"),
                    rejected.getCause().getCause().getMessage());
        }
        assertEquals("2", queryString("select sum(attendees)::text from bookings where session_id = '"
                + sessionId + "' and status in ('RESERVED', 'CONFIRMED')"));
    }

    /** Without the advisory lock the second insert never waits, and this times out. */
    private static void awaitAdvisoryLockWaiter() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            if (!"0".equals(queryString(
                    "select count(*)::text from pg_locks where locktype = 'advisory' and not granted"))) {
                return;
            }
            Thread.sleep(50);
        }
        fail("the second booking never waited for the first: the capacity check is not serialized");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(Connection connection, String statement) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(statement);
        }
    }

    private static void sql(String statement) {
        try (Connection connection = connect()) {
            execute(connection, statement);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String queryString(String query) {
        try (Connection connection = connect();
             PreparedStatement st = connection.prepareStatement(query);
             ResultSet rows = st.executeQuery()) {
            rows.next();
            return rows.getString(1);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
