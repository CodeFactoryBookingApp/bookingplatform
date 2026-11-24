package com.codefactory.bookingplatform.booking;

import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingCancellationIT extends JwtIntegrationTestBase {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final LocalDate day = LocalDate.now(ZONE).plusDays(2);
    private final Instant start = day.atTime(10, 0).atZone(ZONE).toInstant();
    private final List<UUID> scopes = new ArrayList<>();

    @Autowired private JdbcTemplate jdbc;

    record Offer(Provider provider, UUID service, UUID resource) {}

    @AfterEach
    void cleanReservations() {
        for (UUID scope : scopes) {
            jdbc.update("delete from booking_status_changes where booking_id in (select id from bookings where organization_id=?)", scope);
            jdbc.update("delete from bookings where organization_id=?", scope);
        }
    }

    @Test
    void clientCancelsWithinFreeWindowWithoutPenaltyReleasesResourcesAndNotifiesProvider() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var secondClient = client("ACTIVE");
        UUID booking = createBooking(offer, client);

        // Act
        var unconfirmed = cancelByClient(booking, client, "{\"confirm\":false}");
        var otherClient = cancelByClient(booking, secondClient, "{\"confirm\":true}");
        var confirmed = cancelByClient(booking, client, "{\"confirm\":true,\"reason\":\"Viaje imprevisto\"}");
        var slotReopened = availability(offer, secondClient);
        var rebooked = submit(offer, secondClient, preview(offer, secondClient));

        // Assert
        unconfirmed.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.details.penaltyApplies").value("false"))
                .andExpect(jsonPath("$.details.lateCancellation").value("false"))
                .andExpect(jsonPath("$.details.freeCancellationWindowMinutes").value("60"));
        otherClient.andExpect(status().isNotFound());
        confirmed.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lateCancellation").value(false));
        slotReopened.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        rebooked.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select count(*) from booking_resources where booking_id=?",
                Integer.class, booking)).isZero();
        assertThat(jdbc.queryForObject("select late_cancellation from bookings where id=?",
                Boolean.class, booking)).isFalse();
        assertThat(jdbc.queryForObject("select reason from booking_status_changes where booking_id=? and to_status='CANCELLED'",
                String.class, booking)).isEqualTo("Viaje imprevisto");
        assertThat(jdbc.queryForList("select recipient from booking_notices where booking_id=? and kind='CANCELLED'",
                String.class, booking)).contains("PROVIDER", "CLIENT");
    }

    @Test
    void clientCancelsOutsideFreeWindowWithPenaltyAndMarksLateCancellation() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID booking = createBooking(offer, client);
        // Set free cancellation window to 10000 minutes (~7 days) so a booking in 2 days is outside the free window
        jdbc.update("update organization_policies set free_cancellation_window_minutes=10000 where id=?",
                offer.provider().policyId());

        // Act
        var warning = cancelByClient(booking, client, null);
        var confirmed = cancelByClient(booking, client, "{\"confirm\":true}");

        // Assert
        warning.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.details.penaltyApplies").value("true"))
                .andExpect(jsonPath("$.details.lateCancellation").value("true"))
                .andExpect(jsonPath("$.details.freeCancellationWindowMinutes").value("10000"));
        confirmed.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lateCancellation").value(true));
        assertThat(jdbc.queryForObject("select late_cancellation from bookings where id=?",
                Boolean.class, booking)).isTrue();
        assertThat(jdbc.queryForObject("select reason from booking_status_changes where booking_id=? and to_status='CANCELLED'",
                String.class, booking)).isEqualTo("Cancelación tardía del cliente");
    }

    @ParameterizedTest
    @ValueSource(strings = {"STARTED", "COMPLETED", "NO_SHOW"})
    void nonCancellableBookingsAreRejected(String state) throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID booking = createBooking(offer, client);
        if ("STARTED".equals(state)) {
            jdbc.update("""
                    update bookings set starts_at = now() - interval '10 minutes',
                                        ends_at   = now() + interval '20 minutes'
                     where id = ?
                    """, booking);
        } else {
            jdbc.update("update bookings set status = ? where id = ?", state, booking);
        }

        // Act
        var clientAttempt = cancelByClient(booking, client, "{\"confirm\":true}");
        var providerAttempt = cancelByProvider(offer, booking, offer.provider().ownerId(), "{\"reason\":\"Cierre\"}");

        // Assert
        clientAttempt.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        providerAttempt.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void providerCancellationRequiresMandatoryReasonEnforcesScopeAndAppliesNoPenalty() throws Exception {
        // Arrange
        var offer = offer();
        var outsider = offer();
        var client = client("ACTIVE");
        UUID booking = createBooking(offer, client);
        jdbc.update("update organization_policies set free_cancellation_window_minutes=10000 where id=?",
                offer.provider().policyId());
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");
        UUID manager = fixtures.addMember(offer.provider().organizationId(), "MANAGER");

        // Act
        var blankReason = cancelByProvider(offer, booking, manager, "{\"reason\":\"   \"}");
        var outsiderAttempt = cancelByProvider(offer, booking, outsider.provider().ownerId(), "{\"reason\":\"Falla\"}");
        var wrongOrgAttempt = cancelByProvider(outsider, booking, outsider.provider().ownerId(), "{\"reason\":\"Falla\"}");
        var staffAttempt = cancelByProvider(offer, booking, staff, "{\"reason\":\"Falla\"}");
        var managerCancel = cancelByProvider(offer, booking, manager, "{\"reason\":\"  Mantenimiento de emergencia  \"}");

        // Assert
        blankReason.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        outsiderAttempt.andExpect(status().isNotFound());
        wrongOrgAttempt.andExpect(status().isNotFound());
        staffAttempt.andExpect(status().isForbidden());
        managerCancel.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lateCancellation").value(false));
        assertThat(jdbc.queryForObject("select late_cancellation from bookings where id=?",
                Boolean.class, booking)).isFalse();
        assertThat(jdbc.queryForObject("select reason from booking_status_changes where booking_id=? and to_status='CANCELLED'",
                String.class, booking)).isEqualTo("Mantenimiento de emergencia");
        assertThat(jdbc.queryForObject("select count(*) from booking_resources where booking_id=?",
                Integer.class, booking)).isZero();
        assertThat(jdbc.queryForList("select recipient from booking_notices where booking_id=? and kind='CANCELLED'",
                String.class, booking)).contains("CLIENT", "PROVIDER");
    }

    @Test
    void cancelledBookingCannotBeReactivatedOrConfirmedAndRequiresNewBooking() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var stranger = client("ACTIVE");
        UUID booking = createBooking(offer, client);
        cancelByClient(booking, client, "{\"confirm\":true}").andExpect(status().isOk());

        // Act
        var strangerReactivate = mockMvc.perform(post("/api/v1/bookings/{id}/reactivate", booking)
                .header("Authorization", token(stranger, "CLIENT")));
        var reactivate = mockMvc.perform(post("/api/v1/bookings/{id}/reactivate", booking)
                .header("Authorization", token(client, "CLIENT")));
        var providerConfirm = mockMvc.perform(post("/api/v1/organizations/{org}/bookings/{id}/confirm",
                offer.provider().organizationId(), booking)
                .header("Authorization", token(offer.provider().ownerId(), "PROVIDER")));
        var cancelAgain = cancelByClient(booking, client, "{\"confirm\":true}");
        var replacement = submit(offer, client, preview(offer, client));

        // Assert
        strangerReactivate.andExpect(status().isNotFound());
        reactivate.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        providerConfirm.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        cancelAgain.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        replacement.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select status from bookings where id=?",
                String.class, booking)).isEqualTo("CANCELLED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("update bookings set status='CONFIRMED', cancelled_at=null, late_cancellation=false where id=?", booking))
                .hasMessageContaining("HU-013: a cancelled booking cannot be reactivated");
    }

    @Test
    void pendingReservedBookingCanAlsoBeCancelledByClient() throws Exception {
        // Arrange
        var offer = offer();
        jdbc.update("update organization_policies set manual_confirmation=true where id=?", offer.provider().policyId());
        var client = client("ACTIVE");
        UUID booking = createBooking(offer, client);

        // Act
        var cancelled = cancelByClient(booking, client, "{\"confirm\":true}");

        // Assert
        cancelled.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lateCancellation").value(false));
        assertThat(jdbc.queryForObject("select from_status from booking_status_changes where booking_id=? and to_status='CANCELLED'",
                String.class, booking)).isEqualTo("RESERVED");
    }

    private UUID createBooking(Offer offer, UUID client) throws Exception {
        String response = submit(offer, client, preview(offer, client))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions cancelByClient(UUID booking, UUID client, String body) throws Exception {
        var req = post("/api/v1/bookings/{id}/cancel", booking)
                .header("Authorization", token(client, "CLIENT"));
        if (body != null) {
            req.contentType("application/json").content(body);
        }
        return mockMvc.perform(req);
    }

    private ResultActions cancelByProvider(Offer offer, UUID booking, UUID actor, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{org}/bookings/{id}/cancel",
                offer.provider().organizationId(), booking)
                .header("Authorization", token(actor, "PROVIDER"))
                .contentType("application/json").content(body));
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

    private ResultActions availability(Offer offer, UUID client) throws Exception {
        return mockMvc.perform(get("/api/v1/booking/availability").header("Authorization", token(client, "CLIENT"))
                .param("serviceId", offer.service().toString()).param("locationId", offer.provider().locationId().toString())
                .param("date", day.toString()));
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
