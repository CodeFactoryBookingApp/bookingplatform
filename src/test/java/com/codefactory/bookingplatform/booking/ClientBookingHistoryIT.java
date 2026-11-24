package com.codefactory.bookingplatform.booking;

import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientBookingHistoryIT extends JwtIntegrationTestBase {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final LocalDate firstDay = LocalDate.now(ZONE).plusDays(2);
    private final LocalDate secondDay = LocalDate.now(ZONE).plusDays(3);
    private final Instant day1Slot1 = firstDay.atTime(10, 0).atZone(ZONE).toInstant();
    private final Instant day1Slot2 = firstDay.atTime(10, 30).atZone(ZONE).toInstant();
    private final Instant day2Slot1 = secondDay.atTime(10, 0).atZone(ZONE).toInstant();
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
    void separatesUpcomingAscendingFromHistoryDescending() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");

        UUID upcomingLater = createBooking(offer, client, day2Slot1);
        UUID upcomingSooner = createBooking(offer, client, day1Slot1);

        UUID completedOldest = createBooking(offer, client, day1Slot2);
        moveBookingToPastAndStatus(completedOldest,
                Instant.now().minusSeconds(72 * 3600L),
                Instant.now().minusSeconds(71 * 3600L),
                "COMPLETED");

        UUID cancelledRecent = createBooking(offer, client, day1Slot2);
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", cancelledRecent)
                        .header("Authorization", token(client, "CLIENT"))
                        .contentType("application/json").content("{\"confirm\":true}"))
                .andExpect(status().isOk());
        jdbc.update("update bookings set starts_at=?, ends_at=? where id=?",
                Instant.now().minusSeconds(24 * 3600L).atOffset(ZoneOffset.UTC),
                Instant.now().minusSeconds(23 * 3600L).atOffset(ZoneOffset.UTC),
                cancelledRecent);

        // Act
        var response = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(client, "CLIENT")));

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.upcoming[0].id").value(upcomingSooner.toString()))
                .andExpect(jsonPath("$.upcoming[1].id").value(upcomingLater.toString()))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].id").value(cancelledRecent.toString()))
                .andExpect(jsonPath("$.history[1].id").value(completedOldest.toString()));
    }

    @Test
    void upcomingBookingWithinWindowAllowsDirectRescheduleAndCancel() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID bookingId = createBooking(offer, client, day1Slot1);

        // Act
        var listed = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(client, "CLIENT")));
        var rescheduled = mockMvc.perform(post("/api/v1/bookings/{id}/reschedule", bookingId)
                .header("Authorization", token(client, "CLIENT"))
                .contentType("application/json").content("""
                        {"startsAt":"%s"}
                        """.formatted(day1Slot2)));
        var cancelled = mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                .header("Authorization", token(client, "CLIENT"))
                .contentType("application/json").content("{\"confirm\":true}"));

        // Assert
        listed.andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].id").value(bookingId.toString()))
                .andExpect(jsonPath("$.upcoming[0].actions.canCancel").value(true))
                .andExpect(jsonPath("$.upcoming[0].actions.cancelDisabledReason").isEmpty())
                .andExpect(jsonPath("$.upcoming[0].actions.canReschedule").value(true))
                .andExpect(jsonPath("$.upcoming[0].actions.rescheduleDisabledReason").isEmpty())
                .andExpect(jsonPath("$.upcoming[0].actions.canRebook").value(false));
        rescheduled.andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value(day1Slot2.toString()));
        cancelled.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rebookFromHistoryPreloadsServiceLocationAndAvailableSlotsForOneClickBooking() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        UUID completedBooking = createBooking(offer, client, day1Slot1);
        moveBookingToPastAndStatus(completedBooking,
                Instant.now().minusSeconds(48 * 3600L),
                Instant.now().minusSeconds(47 * 3600L),
                "COMPLETED");

        // Act
        var historyView = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(client, "CLIENT")));
        var rebookDefaultDate = mockMvc.perform(get("/api/v1/bookings/{id}/rebook", completedBooking)
                .header("Authorization", token(client, "CLIENT")));
        var rebookTargetDay = mockMvc.perform(get("/api/v1/bookings/{id}/rebook", completedBooking)
                .header("Authorization", token(client, "CLIENT"))
                .param("date", firstDay.toString()));
        var newBooking = submit(offer, client, day1Slot1, preview(offer, client, day1Slot1));

        // Assert
        historyView.andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].id").value(completedBooking.toString()))
                .andExpect(jsonPath("$.history[0].actions.canRebook").value(true));
        rebookDefaultDate.andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBookingId").value(completedBooking.toString()))
                .andExpect(jsonPath("$.serviceId").value(offer.service().toString()));
        rebookTargetDay.andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBookingId").value(completedBooking.toString()))
                .andExpect(jsonPath("$.organizationId").value(offer.provider().organizationId().toString()))
                .andExpect(jsonPath("$.serviceId").value(offer.service().toString()))
                .andExpect(jsonPath("$.locationId").value(offer.provider().locationId().toString()))
                .andExpect(jsonPath("$.date").value(firstDay.toString()))
                .andExpect(jsonPath("$.availableSlots.length()").value(2));
        newBooking.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void clientOnlySeesOwnBookingsAndCannotAccessAnotherClientsDetailRebookOrReceipt() throws Exception {
        // Arrange
        var offer = offer();
        var firstClient = client("ACTIVE");
        var secondClient = client("ACTIVE");
        UUID firstBooking = createBooking(offer, firstClient, day1Slot1);
        UUID secondBooking = createBooking(offer, secondClient, day1Slot2);

        // Act
        var firstListing = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(firstClient, "CLIENT")));
        var ownDetail = mockMvc.perform(get("/api/v1/bookings/{id}", firstBooking)
                .header("Authorization", token(firstClient, "CLIENT")));
        var otherDetail = mockMvc.perform(get("/api/v1/bookings/{id}", secondBooking)
                .header("Authorization", token(firstClient, "CLIENT")));
        var otherRebook = mockMvc.perform(get("/api/v1/bookings/{id}/rebook", secondBooking)
                .header("Authorization", token(firstClient, "CLIENT")));
        var otherReceipt = mockMvc.perform(get("/api/v1/bookings/{id}/receipt", secondBooking)
                .header("Authorization", token(firstClient, "CLIENT")));

        // Assert
        firstListing.andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(1))
                .andExpect(jsonPath("$.upcoming[0].id").value(firstBooking.toString()))
                .andExpect(jsonPath("$.history").isEmpty());
        ownDetail.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstBooking.toString()));
        otherDetail.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        otherRebook.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        otherReceipt.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void downloadsReadablePdfReceiptForSelectedBooking() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        String created = submit(offer, client, day1Slot1, preview(offer, client, day1Slot1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(JsonPath.read(created, "$.id"));
        String code = JsonPath.read(created, "$.code");

        // Act
        var pdfResult = mockMvc.perform(get("/api/v1/bookings/{id}/receipt", bookingId)
                .header("Authorization", token(client, "CLIENT")));

        // Assert
        byte[] bytes = pdfResult.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"comprobante-" + code + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        String pdfText = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(pdfText)
                .startsWith("%PDF-1.4")
                .contains("COMPROBANTE DE RESERVA")
                .contains("Codigo de reserva: " + code)
                .contains("Confirmada \\(CONFIRMED\\)")
                .contains("COP")
                .endsWith("%%EOF\n");
    }

    @Test
    void clientWithoutBookingsSeesEmptyMessageAndCatalogAccess() throws Exception {
        // Arrange
        var newClient = client("ACTIVE");

        // Act
        var result = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(newClient, "CLIENT")));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.emptyMessage").value("Aún no tienes reservas"))
                .andExpect(jsonPath("$.catalogPath").value("/api/v1/catalog/services"))
                .andExpect(jsonPath("$.upcoming").isEmpty())
                .andExpect(jsonPath("$.history").isEmpty());
    }

    @Test
    void actionsOutsideWindowOrAfterRescheduleLimitAppearDisabledWithReason() throws Exception {
        // Arrange
        var offerOutsideWindow = offer();
        var offerLimitReached = offer();
        var client = client("ACTIVE");

        UUID bookingOutsideWindow = createBooking(offerOutsideWindow, client, day1Slot1);
        jdbc.update("update organization_policies set free_cancellation_window_minutes=10000 where id=?",
                offerOutsideWindow.provider().policyId());

        UUID bookingAtRescheduleLimit = createBooking(offerLimitReached, client, day2Slot1);
        jdbc.update("update bookings set reschedule_count = 1 where id = ?", bookingAtRescheduleLimit);

        // Act
        var result = mockMvc.perform(get("/api/v1/bookings")
                .header("Authorization", token(client, "CLIENT")));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.upcoming[0].id").value(bookingOutsideWindow.toString()))
                .andExpect(jsonPath("$.upcoming[0].actions.canCancel").value(false))
                .andExpect(jsonPath("$.upcoming[0].actions.cancelDisabledReason")
                        .value("Fuera de la ventana de cancelación sin penalidad"))
                .andExpect(jsonPath("$.upcoming[0].actions.canReschedule").value(false))
                .andExpect(jsonPath("$.upcoming[0].actions.rescheduleDisabledReason")
                        .value("Fuera de la ventana de reprogramación sin penalidad"))
                .andExpect(jsonPath("$.upcoming[1].id").value(bookingAtRescheduleLimit.toString()))
                .andExpect(jsonPath("$.upcoming[1].actions.canCancel").value(true))
                .andExpect(jsonPath("$.upcoming[1].actions.cancelDisabledReason").isEmpty())
                .andExpect(jsonPath("$.upcoming[1].actions.canReschedule").value(false))
                .andExpect(jsonPath("$.upcoming[1].actions.rescheduleDisabledReason")
                        .value("Alcanzaste el número máximo de reprogramaciones permitido"));
    }

    private void moveBookingToPastAndStatus(UUID bookingId, Instant pastStart, Instant pastEnd, String status) {
        jdbc.update("update bookings set starts_at=?, ends_at=?, status=? where id=?",
                pastStart.atOffset(ZoneOffset.UTC), pastEnd.atOffset(ZoneOffset.UTC), status, bookingId);
    }

    private UUID createBooking(Offer offer, UUID client, Instant slot) throws Exception {
        String response = submit(offer, client, slot, preview(offer, client, slot))
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
                agenda, provider.organizationId(), provider.locationId(), firstDay);
        jdbc.update("insert into agenda_services values (?,?,?)", agenda, service, provider.locationId());
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','11:00')",
                agenda, firstDay.getDayOfWeek().getValue());
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','11:00')",
                agenda, secondDay.getDayOfWeek().getValue());
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
