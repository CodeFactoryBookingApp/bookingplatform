package com.codefactory.bookingplatform.booking;

import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase;
import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingAttendanceIT extends JwtIntegrationTestBase {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final LocalDate day = LocalDate.now(ZONE).plusDays(2);
    private final Instant start = day.atTime(10, 0).atZone(ZONE).toInstant();
    private final List<UUID> scopes = new ArrayList<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookAppointmentUseCase bookingsUseCase;

    record Offer(Provider provider, UUID service, UUID resource) {}

    @AfterEach
    void cleanReservations() {
        for (UUID scope : scopes) {
            jdbc.update("delete from booking_status_changes where booking_id in (select id from bookings where organization_id=?)", scope);
            jdbc.update("delete from bookings where organization_id=?", scope);
        }
    }

    @Test
    void providerStaffMarksStartedBookingAsCompletedRecordingWhoAndWhen() throws Exception {
        // Arrange
        var offer = offer();
        var outsider = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client);
        moveBookingToPast(bookingId, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");

        // Act
        var outsiderAttempt = attendance(offer, bookingId, outsider.provider().ownerId(), """
                {"status":"COMPLETED"}
                """);
        var invalidStatusAttempt = attendance(offer, bookingId, staff, """
                {"status":"CONFIRMED"}
                """);
        var completed = attendance(offer, bookingId, staff, """
                {"status":"COMPLETED","observation":"Cliente atendido a tiempo"}
                """);

        // Assert
        outsiderAttempt.andExpect(status().isNotFound());
        invalidStatusAttempt.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.status").value("must be COMPLETED or NO_SHOW"));
        completed.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closedBy").value(staff.toString()))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.observation").value("Cliente atendido a tiempo"))
                .andExpect(jsonPath("$.clientNoShowCount").value(0));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, bookingId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select changed_by from booking_status_changes where booking_id=? and to_status='COMPLETED'
                """, UUID.class, bookingId)).isEqualTo(staff);
        assertThat(jdbc.queryForObject("""
                select changed_at from booking_status_changes where booking_id=? and to_status='COMPLETED'
                """, Instant.class, bookingId)).isNotNull();
        assertThat(jdbc.queryForList("""
                select action from audit_events where resource_id=? and actor_id=?
                """, String.class, bookingId, staff)).contains("BOOKING_COMPLETED");
    }

    @Test
    void markingNoShowWithOptionalObservationUpdatesClientNoShowCounter() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID firstBooking = createBooking(offer, client);
        moveBookingToPast(firstBooking, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(5400));
        UUID secondBooking = createBooking(offer, client);
        moveBookingToPast(secondBooking, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");

        // Act
        var firstNoShow = attendance(offer, firstBooking, staff, """
                {"status":"NO_SHOW","observation":"  No contestó llamada de confirmación  "}
                """);
        var secondNoShow = attendance(offer, secondBooking, staff, """
                {"status":"NO_SHOW"}
                """);

        // Assert
        firstNoShow.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.closedBy").value(staff.toString()))
                .andExpect(jsonPath("$.observation").value("No contestó llamada de confirmación"))
                .andExpect(jsonPath("$.clientNoShowCount").value(1));
        secondNoShow.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.observation").isEmpty())
                .andExpect(jsonPath("$.clientNoShowCount").value(2));
        assertThat(jdbc.queryForObject("""
                select reason from booking_status_changes where booking_id=? and to_status='NO_SHOW'
                """, String.class, firstBooking)).isEqualTo("No contestó llamada de confirmación");
    }

    @Test
    void cannotMarkAttendanceBeforeBookingStartTime() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client);
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");

        // Act
        var earlyCompleted = attendance(offer, bookingId, staff, """
                {"status":"COMPLETED"}
                """);
        var earlyNoShow = attendance(offer, bookingId, staff, """
                {"status":"NO_SHOW"}
                """);

        // Assert
        earlyCompleted.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        earlyNoShow.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, bookingId)).isEqualTo("CONFIRMED");
    }

    @Test
    void unmarkedBookingsAutoCloseAfter24HoursFromEndTimeWithProvidersDefaultStatus() throws Exception {
        // Arrange
        var firstOffer = offer();
        var secondOffer = offer();
        jdbc.update("update organization_policies set default_closure_status='NO_SHOW' where id=?",
                secondOffer.provider().policyId());
        var client1 = client("ACTIVE");
        var client2 = client("ACTIVE");
        var client3 = client("ACTIVE");

        UUID overdueCompleted = createBooking(firstOffer, client1);
        moveBookingToPast(overdueCompleted,
                Instant.now().minusSeconds(26 * 3600L),
                Instant.now().minusSeconds(25 * 3600L));

        UUID overdueNoShow = createBooking(secondOffer, client2);
        moveBookingToPast(overdueNoShow,
                Instant.now().minusSeconds(26 * 3600L),
                Instant.now().minusSeconds(24 * 3600L + 60));

        UUID recentEnded = createBooking(firstOffer, client3);
        moveBookingToPast(recentEnded,
                Instant.now().minusSeconds(12 * 3600L),
                Instant.now().minusSeconds(11 * 3600L));

        // Act
        int closedCount = bookingsUseCase.autoCloseUnmarked();

        // Assert
        assertThat(closedCount).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, overdueCompleted)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, overdueNoShow)).isEqualTo("NO_SHOW");
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, recentEnded)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("""
                select reason from booking_status_changes where booking_id=? and to_status='COMPLETED'
                """, String.class, overdueCompleted)).isEqualTo("Cierre automático por vencimiento de plazo");
        assertThat(jdbc.queryForObject("""
                select actor_role from audit_events where resource_id=? and action='BOOKING_NO_SHOW'
                """, String.class, overdueNoShow)).isEqualTo("SYSTEM");
    }

    @Test
    void correctingClosureStatusWithin48HoursUpdatesStatusAndLeavesAuditTrail() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client);
        moveBookingToPast(bookingId, Instant.now().minusSeconds(4 * 3600L), Instant.now().minusSeconds(3 * 3600L));
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");
        UUID manager = fixtures.addMember(offer.provider().organizationId(), "MANAGER");

        attendance(offer, bookingId, staff, """
                {"status":"NO_SHOW","observation":"Marcado por error"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientNoShowCount").value(1));

        // Act
        var duplicateSameStatus = attendance(offer, bookingId, manager, """
                {"status":"NO_SHOW"}
                """);
        var corrected = attendance(offer, bookingId, manager, """
                {"status":"COMPLETED","observation":"Corrección: el cliente sí asistió"}
                """);

        // Assert
        duplicateSameStatus.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        corrected.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closedBy").value(manager.toString()))
                .andExpect(jsonPath("$.observation").value("Corrección: el cliente sí asistió"))
                .andExpect(jsonPath("$.clientNoShowCount").value(0));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, bookingId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("""
                select from_status || '->' || to_status from booking_status_changes
                 where booking_id=? and from_status is not null order by changed_at
                """, String.class, bookingId)).containsExactly("CONFIRMED->NO_SHOW", "NO_SHOW->COMPLETED");
    }

    @Test
    void cancelledBookingCannotBeMarkedAsCompletedOrNoShow() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client);
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                        .header("Authorization", token(client, "CLIENT"))
                        .contentType("application/json").content("{\"confirm\":true}"))
                .andExpect(status().isOk());
        moveBookingToPast(bookingId, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800));
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");

        // Act
        var completedAttempt = attendance(offer, bookingId, staff, """
                {"status":"COMPLETED"}
                """);
        var noShowAttempt = attendance(offer, bookingId, staff, """
                {"status":"NO_SHOW"}
                """);

        // Assert
        completedAttempt.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        noShowAttempt.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, bookingId)).isEqualTo("CANCELLED");
    }

    @Test
    void correctingClosureStatusAfter48HoursIsRejected() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client);
        moveBookingToPast(bookingId, Instant.now().minusSeconds(72 * 3600L), Instant.now().minusSeconds(71 * 3600L));
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");

        attendance(offer, bookingId, staff, """
                {"status":"NO_SHOW","observation":"Cierre inicial"}
                """).andExpect(status().isOk());
        jdbc.update("""
                update booking_status_changes set changed_at = now() - interval '48 hours 1 minute'
                 where booking_id = ? and to_status = 'NO_SHOW'
                """, bookingId);

        // Act
        var lateCorrection = attendance(offer, bookingId, staff, """
                {"status":"COMPLETED","observation":"Intento fuera de plazo"}
                """);

        // Assert
        lateCorrection.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLOSURE_CORRECTION_WINDOW_EXPIRED"));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, bookingId)).isEqualTo("NO_SHOW");
    }

    private void moveBookingToPast(UUID bookingId, Instant pastStart, Instant pastEnd) {
        jdbc.update("update bookings set starts_at=?, ends_at=? where id=?",
                pastStart.atOffset(ZoneOffset.UTC), pastEnd.atOffset(ZoneOffset.UTC), bookingId);
    }

    private ResultActions attendance(Offer offer, UUID bookingId, UUID actor, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{org}/bookings/{id}/attendance",
                offer.provider().organizationId(), bookingId)
                .header("Authorization", token(actor, "PROVIDER"))
                .contentType("application/json").content(body));
    }

    private UUID createBooking(Offer offer, UUID client) throws Exception {
        String response = submit(offer, client, preview(offer, client))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private Offer offer() {
        var providers = new ProviderFixture(jdbc);
        var provider = providers.activeProvider();
        scopes.add(provider.organizationId());
        var service = providers.activeService(provider);
        var resource = new BookingFixture(jdbc).resource(provider);
        jdbc.update("""
                insert into service_resource_requirements (service_id,resource_type_id,organization_id)
                select ?,resource_type_id,organization_id from resources where id=?
                """, service, resource);
        UUID agenda = UUID.randomUUID();
        jdbc.update("insert into agendas (id,organization_id,location_id,name,slot_minutes,valid_from) values (?,?,?,'Agenda',30,?)",
                agenda, provider.organizationId(), provider.locationId(), day);
        jdbc.update("insert into agenda_services values (?,?,?)", agenda, service, provider.locationId());
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','10:30')",
                agenda, day.getDayOfWeek().getValue());
        return new Offer(provider, service, resource);
    }

    private UUID client(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into clients values (?, 'Cliente', ?, '1990-01-01', ?, '3001112233', 'Cali', 'EMAIL', ?,
                                            localtimestamp, localtimestamp)
                """, id, id.toString().substring(0, 23), id + "@example.com", status);
        return id;
    }

    private String preview(Offer offer, UUID client) throws Exception {
        var result = mockMvc.perform(get("/api/v1/booking/preview").header("Authorization", token(client, "CLIENT"))
                .param("serviceId", offer.service().toString()).param("locationId", offer.provider().locationId().toString())
                .param("startsAt", start.toString())).andExpect(status().isOk());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.termsVersion");
    }

    private ResultActions submit(Offer offer, UUID client, String terms) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", token(client, "CLIENT"))
                .contentType("application/json").content("""
                        {"serviceId":"%s","locationId":"%s","startsAt":"%s","termsVersion":"%s"}
                        """.formatted(offer.service(), offer.provider().locationId(), start, terms)));
    }

    private static String token(UUID user, String role) {
        return "Bearer " + jwks().accessToken(user, user + "@example.com", role);
    }
}
