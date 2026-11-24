package com.codefactory.bookingplatform.booking;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingRescheduleIT extends JwtIntegrationTestBase {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final LocalDate day = LocalDate.now(ZONE).plusDays(2);
    private final Instant firstSlot = day.atTime(10, 0).atZone(ZONE).toInstant();
    private final Instant secondSlot = day.atTime(10, 30).atZone(ZONE).toInstant();
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
    void confirmedBookingMovesToAnotherSlotPreservingCodeReleasingOldSlotAndAssigningNew() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var secondClient = client("ACTIVE");
        String created = submit(offer, client, firstSlot, preview(offer, client, firstSlot))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(JsonPath.read(created, "$.id"));
        String code = JsonPath.read(created, "$.code");

        // Act
        var rescheduled = reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(secondSlot));
        var oldSlotBookedByAnother = submit(offer, secondClient, firstSlot, preview(offer, secondClient, firstSlot));

        // Assert
        rescheduled.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startsAt").value(secondSlot.toString()))
                .andExpect(jsonPath("$.rescheduleCount").value(1))
                .andExpect(jsonPath("$.lateReschedule").value(false));
        oldSlotBookedByAnother.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select starts_at from bookings where id=?",
                Instant.class, bookingId)).isEqualTo(secondSlot);
        assertThat(jdbc.queryForObject("select starts_at from booking_resources where booking_id=?",
                Instant.class, bookingId)).isEqualTo(secondSlot);
        assertThat(jdbc.queryForList("select recipient from booking_notices where booking_id=? and kind='RESCHEDULED'",
                String.class, bookingId)).containsExactlyInAnyOrder("CLIENT", "PROVIDER");
    }

    @Test
    void unavailableNewSlotKeepsOriginalBookingIntactAndExplainsReason() throws Exception {
        // Arrange
        var offer = offer();
        var firstClient = client("ACTIVE");
        var secondClient = client("ACTIVE");
        UUID bookingId = createBooking(offer, firstClient, firstSlot);
        createBooking(offer, secondClient, secondSlot);

        // Act
        var failed = reschedule(bookingId, firstClient, """
                {"startsAt":"%s"}
                """.formatted(secondSlot));

        // Assert
        failed.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("select starts_at from bookings where id=?",
                Instant.class, bookingId)).isEqualTo(firstSlot);
        assertThat(jdbc.queryForObject("select reschedule_count from bookings where id=?",
                Integer.class, bookingId)).isZero();
        assertThat(jdbc.queryForList("select resource_id from booking_resources where booking_id=? and starts_at=?",
                UUID.class, bookingId, firstSlot.atZone(ZONE).toOffsetDateTime())).containsExactly(offer.resource());
    }

    @Test
    void rescheduleLimitIsEnforcedAndSuggestsCancelAndCreateNew() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client, firstSlot);
        reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(secondSlot)).andExpect(status().isOk());

        // Act
        var exceeded = reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(firstSlot));

        // Assert
        exceeded.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESCHEDULE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.maxReschedules").value("1"))
                .andExpect(jsonPath("$.details.rescheduleCount").value("1"))
                .andExpect(jsonPath("$.details.suggestedAction").value("CANCEL_AND_CREATE_NEW"));
        assertThat(jdbc.queryForObject("select starts_at from bookings where id=?",
                Instant.class, bookingId)).isEqualTo(secondSlot);
    }

    @Test
    void serviceCannotChangeWhenReschedulingWhileLocationAndResourceCanChange() throws Exception {
        // Arrange
        var offer = offer();
        var providers = new ProviderFixture(jdbc);
        UUID otherService = providers.activeService(offer.provider());
        UUID secondLocation = providers.addLocation(offer.provider().organizationId());
        providers.offerAt(offer.provider(), offer.service(), secondLocation);
        UUID secondResource = UUID.randomUUID();
        jdbc.update("""
                insert into resources (id, resource_type_id, location_id, organization_id, name)
                select ?, resource_type_id, ?, organization_id, 'Recurso sede 2' from resources where id=?
                """, secondResource, secondLocation, offer.resource());
        UUID thirdResource = UUID.randomUUID();
        jdbc.update("""
                insert into resources (id, resource_type_id, location_id, organization_id, name)
                select ?, resource_type_id, ?, organization_id, 'Recurso alternativo sede 2' from resources where id=?
                """, thirdResource, secondLocation, offer.resource());
        UUID secondAgenda = UUID.randomUUID();
        jdbc.update("insert into agendas (id,organization_id,location_id,name,slot_minutes,valid_from) values (?,?,?,'Agenda sede 2',30,?)",
                secondAgenda, offer.provider().organizationId(), secondLocation, day);
        jdbc.update("insert into agenda_services values (?,?,?)", secondAgenda, offer.service(), secondLocation);
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','11:00')",
                secondAgenda, day.getDayOfWeek().getValue());

        var client = client("ACTIVE");
        var stranger = client("ACTIVE");
        UUID bookingId = createBooking(offer, client, firstSlot);

        // Act
        var wrongClient = reschedule(bookingId, stranger, """
                {"startsAt":"%s"}
                """.formatted(secondSlot));
        var noOp = reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(firstSlot));
        var serviceChange = reschedule(bookingId, client, """
                {"startsAt":"%s","serviceId":"%s"}
                """.formatted(secondSlot, otherService));
        var invalidLocation = reschedule(bookingId, client, """
                {"startsAt":"%s","locationId":"%s"}
                """.formatted(secondSlot, UUID.randomUUID()));
        var movedLocationAndResource = reschedule(bookingId, client, """
                {"startsAt":"%s","locationId":"%s","resourceId":"%s","serviceId":"%s"}
                """.formatted(secondSlot, secondLocation, thirdResource, offer.service()));

        // Assert
        wrongClient.andExpect(status().isNotFound());
        noOp.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        serviceChange.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.serviceId").value("cannot be changed when rescheduling"));
        invalidLocation.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        movedLocationAndResource.andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(secondLocation.toString()))
                .andExpect(jsonPath("$.startsAt").value(secondSlot.toString()));
        assertThat(jdbc.queryForList("select resource_id from booking_resources where booking_id=?",
                UUID.class, bookingId)).containsExactly(thirdResource);
        assertThatThrownBy(() -> jdbc.update("update bookings set service_id=? where id=?", otherService, bookingId))
                .hasMessageContaining("HU-014: booking service and organization cannot be changed when rescheduling");
    }

    @Test
    void reschedulingOutsideFreeCancellationWindowMarksBookingAsLateReschedule() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client, firstSlot);
        jdbc.update("update organization_policies set free_cancellation_window_minutes=10000 where id=?",
                offer.provider().policyId());

        // Act
        var rescheduled = reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(secondSlot));
        jdbc.update("update bookings set status='CANCELLED', cancelled_at=now() where id=?", bookingId);
        var cancelledReschedule = reschedule(bookingId, client, """
                {"startsAt":"%s"}
                """.formatted(firstSlot));

        // Assert
        rescheduled.andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value(secondSlot.toString()))
                .andExpect(jsonPath("$.lateReschedule").value(true));
        cancelledReschedule.andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("select late_reschedule from bookings where id=?",
                Boolean.class, bookingId)).isTrue();
        assertThat(jdbc.queryForObject("""
                select reason from booking_status_changes
                 where booking_id=? and from_status='CONFIRMED' and to_status='CONFIRMED'
                """, String.class, bookingId)).isEqualTo("Reprogramación tardía del cliente");
    }

    private UUID createBooking(Offer offer, UUID client, Instant slot) throws Exception {
        String response = submit(offer, client, slot, preview(offer, client, slot))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions reschedule(UUID booking, UUID client, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/{id}/reschedule", booking)
                .header("Authorization", token(client, "CLIENT"))
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
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','11:00')",
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

    private String preview(Offer offer, UUID client, Instant slot) throws Exception {
        var result = mockMvc.perform(get("/api/v1/booking/preview").header("Authorization", token(client, "CLIENT"))
                .param("serviceId", offer.service().toString()).param("locationId", offer.provider().locationId().toString())
                .param("startsAt", slot.toString())).andExpect(status().isOk());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.termsVersion");
    }

    private ResultActions submit(Offer offer, UUID client, Instant slot, String terms) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", token(client, "CLIENT"))
                .contentType("application/json").content("""
                        {"serviceId":"%s","locationId":"%s","startsAt":"%s","termsVersion":"%s"}
                        """.formatted(offer.service(), offer.provider().locationId(), slot, terms)));
    }

    private static String token(UUID user, String role) {
        return "Bearer " + jwks().accessToken(user, user + "@example.com", role);
    }
}
