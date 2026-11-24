package com.codefactory.bookingplatform.scheduling;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HU-007 exceptions, HU-008/HU-011 availability calendar and HU-019 occupancy, end to end: HTTP,
 * the organization scope of ADR-0008 and PostgreSQL with V16.
 *
 * <p>The agenda opens 09:00-11:00 every day with 30-minute slots, so a normal day offers four.
 */
class AgendaAvailabilityIT extends JwtIntegrationTestBase {

    private static final String EVERY_DAY = """
            [{"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "TUESDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "WEDNESDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "THURSDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "FRIDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "SATURDAY", "startTime": "09:00", "endTime": "11:00"},
             {"dayOfWeek": "SUNDAY", "startTime": "09:00", "endTime": "11:00"}]
            """;

    @Autowired
    private JdbcTemplate jdbc;

    private ProviderFixture fixture;
    private Provider provider;
    private UUID service;
    private String agendaId;
    private final LocalDate day = LocalDate.now().plusDays(10);
    private final List<UUID> bookings = new ArrayList<>();

    @BeforeEach
    void createProviderAndAgenda() throws Exception {
        fixture = new ProviderFixture(jdbc);
        provider = fixture.activeProvider();
        service = fixture.activeService(provider);
        MockHttpServletResponse response = send(post("/api/v1/organizations/{org}/agendas", provider.organizationId()), """
                {"name": "Agenda diaria", "locationId": "%s", "slotMinutes": 30, "validFrom": "%s",
                 "serviceIds": ["%s"], "blocks": %s}
                """.formatted(provider.locationId(), LocalDate.now().minusDays(1), service, EVERY_DAY), ownerToken());
        assertEquals(201, response.getStatus(), response.getContentAsString());
        agendaId = JsonPath.read(response.getContentAsString(), "$.id");
    }

    @AfterEach
    void deleteBookings() {
        bookings.forEach(fixture::deleteBooking);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static String token(UUID userId) {
        return "Bearer " + jwks().accessToken(userId, "user-" + userId + "@example.com", "PROVIDER");
    }

    private String ownerToken() {
        return token(provider.ownerId());
    }

    private String staffToken() {
        return token(fixture.addMember(provider.organizationId(), "STAFF"));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, String body, String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", token)).andReturn().getResponse();
    }

    private MockHttpServletResponse close(LocalDate date, String token) throws Exception {
        return send(post("/api/v1/organizations/{org}/agendas/{agenda}/exceptions/closures",
                provider.organizationId(), agendaId), """
                {"date": "%s", "reason": "Festivo"}
                """.formatted(date), token);
    }

    private MockHttpServletResponse specialHours(LocalDate date, String start, String end) throws Exception {
        return send(post("/api/v1/organizations/{org}/agendas/{agenda}/exceptions/special-hours",
                provider.organizationId(), agendaId), """
                {"date": "%s", "startTime": "%s", "endTime": "%s", "reason": "Horario corto"}
                """.formatted(date, start, end), ownerToken());
    }

    private MockHttpServletResponse availability(LocalDate from, LocalDate to) throws Exception {
        return call(get("/api/v1/organizations/{org}/agendas/{agenda}/availability", provider.organizationId(), agendaId)
                .param("from", from.toString()).param("to", to.toString()), ownerToken());
    }

    private MockHttpServletResponse occupancy(LocalDate from, LocalDate to, String token) throws Exception {
        return call(get("/api/v1/organizations/{org}/reports/occupancy", provider.organizationId())
                .param("from", from.toString()).param("to", to.toString()), token);
    }

    private ZoneId zone() {
        return ZoneId.of(jdbc.queryForObject("""
                select coalesce(l.timezone, o.timezone) from locations l
                  join organizations o on o.id = l.organization_id where l.id = ?
                """, String.class, provider.locationId()));
    }

    private Instant local(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(zone()).toInstant();
    }

    /** A CONFIRMED 30-minute booking that starts exactly on a slot of the agenda's grid. */
    private void bookAt(Instant start) {
        UUID client = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        jdbc.update("""
                insert into clients values (?, 'Cliente', ?, '1990-01-01', ?, '3001112233', 'Medellín', 'EMAIL',
                                            'ACTIVE', localtimestamp, localtimestamp)
                """, client, client.toString().substring(0, 20), client + "@example.com");
        jdbc.update("""
                insert into bookings (id, client_id, organization_id, service_id, location_id, policy_id, session_id,
                                      starts_at, ends_at, total_price, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1000, 'CONFIRMED')
                """, booking, client, provider.organizationId(), service, provider.locationId(), provider.policyId(),
                booking, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)));
        bookings.add(booking);
    }

    private static String text(MockHttpServletResponse response, String path) throws Exception {
        return String.valueOf((Object) JsonPath.read(response.getContentAsString(), path));
    }

    private static int count(MockHttpServletResponse response, String path) throws Exception {
        List<?> items = JsonPath.read(response.getContentAsString(), path);
        return items.size();
    }

    // ---- HU-007: exceptions -------------------------------------------------------------------

    @Test
    @DisplayName("HU-007: the owner closes the agenda on a date")
    void ownerClosesADate() throws Exception {
        // Act
        MockHttpServletResponse response = close(day, ownerToken());

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("CLOSED", text(response, "$.type"));
        assertEquals(day.toString(), text(response, "$.date"));
        assertEquals("Festivo", text(response, "$.reason"));
    }

    @Test
    @DisplayName("HU-007: the owner sets special hours on a date")
    void ownerSetsSpecialHours() throws Exception {
        // Act
        MockHttpServletResponse response = specialHours(day, "15:00", "16:00");

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("SPECIAL_HOURS", text(response, "$.type"));
        assertTrue(text(response, "$.specialStart").startsWith("15:00"));
        assertTrue(text(response, "$.specialEnd").startsWith("16:00"));
    }

    @Test
    @DisplayName("HU-007: a second exception on the same date is a conflict")
    void secondExceptionOnTheSameDateIsRejected() throws Exception {
        // Arrange
        close(day, ownerToken());

        // Act
        MockHttpServletResponse response = specialHours(day, "15:00", "16:00");

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("OVERLAPPING_PERIOD", text(response, "$.errorCode"));
    }

    @Test
    @DisplayName("HU-007: special hours that end at their start are rejected")
    void specialHoursEndingAtTheirStartAreRejected() throws Exception {
        // Act
        MockHttpServletResponse response = specialHours(day, "15:00", "15:00");

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", text(response, "$.errorCode"));
    }

    @Test
    @DisplayName("HU-007: the exceptions of an agenda are listed by date")
    void exceptionsAreListedByDate() throws Exception {
        // Arrange
        close(day.plusDays(2), ownerToken());
        specialHours(day.plusDays(1), "15:00", "16:00");

        // Act
        MockHttpServletResponse response = call(get("/api/v1/organizations/{org}/agendas/{agenda}/exceptions",
                provider.organizationId(), agendaId), ownerToken());

        // Assert
        assertEquals(200, response.getStatus());
        List<String> dates = JsonPath.read(response.getContentAsString(), "$[*].date");
        assertEquals(List.of(day.plusDays(1).toString(), day.plusDays(2).toString()), dates);
    }

    @Test
    @DisplayName("HU-007: the owner removes an exception")
    void ownerRemovesAnException() throws Exception {
        // Arrange
        String id = text(close(day, ownerToken()), "$.id");

        // Act
        MockHttpServletResponse response = call(delete("/api/v1/organizations/{org}/agendas/{agenda}/exceptions/{id}",
                provider.organizationId(), agendaId, id), ownerToken());

        // Assert
        assertEquals(204, response.getStatus());
        assertEquals(4, count(availability(day, day), "$[0].slots"), "the day is open again");
    }

    @Test
    @DisplayName("HU-007: removing an exception that does not exist answers 404")
    void removingAMissingExceptionIsNotFound() throws Exception {
        // Act
        MockHttpServletResponse response = call(delete("/api/v1/organizations/{org}/agendas/{agenda}/exceptions/{id}",
                provider.organizationId(), agendaId, UUID.randomUUID()), ownerToken());

        // Assert
        assertEquals(404, response.getStatus());
    }

    @Test
    @DisplayName("HU-007: a STAFF member cannot create exceptions (ADR-0008)")
    void staffCannotCreateExceptions() throws Exception {
        // Act
        MockHttpServletResponse response = close(day, staffToken());

        // Assert
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("HU-007: a STAFF member can read the exceptions")
    void staffCanListExceptions() throws Exception {
        // Act
        MockHttpServletResponse response = call(get("/api/v1/organizations/{org}/agendas/{agenda}/exceptions",
                provider.organizationId(), agendaId), staffToken());

        // Assert
        assertEquals(200, response.getStatus());
    }

    // ---- HU-008/HU-011: availability calendar ------------------------------------------------

    @Test
    @DisplayName("HU-008: a normal day offers every slot of its blocks")
    void normalDayOffersEverySlot() throws Exception {
        // Act
        MockHttpServletResponse response = availability(day, day.plusDays(1));

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(2, count(response, "$"));
        assertEquals(day.toString(), text(response, "$[0].date"));
        assertEquals(4, count(response, "$[0].slots"));
        assertEquals(local(day, 9, 0), Instant.parse(text(response, "$[0].slots[0].startsAt")));
    }

    @Test
    @DisplayName("HU-007/HU-008: a closed day offers no slots")
    void closedDayOffersNoSlots() throws Exception {
        // Arrange
        close(day, ownerToken());

        // Act
        MockHttpServletResponse response = availability(day, day);

        // Assert
        assertEquals(0, count(response, "$[0].slots"));
    }

    @Test
    @DisplayName("HU-007/HU-008: a day with special hours offers only the special window")
    void specialHoursReplaceTheWeeklyBlocks() throws Exception {
        // Arrange
        specialHours(day, "15:00", "16:00");

        // Act
        MockHttpServletResponse response = availability(day, day);

        // Assert
        assertEquals(2, count(response, "$[0].slots"));
        assertEquals(local(day, 15, 0), Instant.parse(text(response, "$[0].slots[0].startsAt")));
    }

    @Test
    @DisplayName("HU-011: a booked slot is no longer free")
    void bookedSlotIsNotFree() throws Exception {
        // Arrange
        bookAt(local(day, 9, 30));

        // Act
        MockHttpServletResponse response = availability(day, day);

        // Assert
        List<String> starts = JsonPath.read(response.getContentAsString(), "$[0].slots[*].startsAt");
        assertEquals(3, starts.size());
        assertFalse(starts.stream().map(Instant::parse).anyMatch(local(day, 9, 30)::equals));
    }

    @Test
    @DisplayName("HU-008: a calendar longer than 62 days is rejected")
    void calendarLongerThanTheLimitIsRejected() throws Exception {
        // Act
        MockHttpServletResponse response = availability(day, day.plusDays(63));

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", text(response, "$.errorCode"));
    }

    @Test
    @DisplayName("HU-008: an inverted calendar range is rejected")
    void invertedCalendarIsRejected() throws Exception {
        // Act
        MockHttpServletResponse response = availability(day, day.minusDays(1));

        // Assert
        assertEquals(400, response.getStatus());
    }

    // ---- HU-019: occupancy --------------------------------------------------------------------

    @Test
    @DisplayName("HU-019: occupancy counts booked slots over offered ones, leaving closed days out")
    void occupancyCountsBookedOverOfferedSlots() throws Exception {
        // Arrange
        bookAt(local(day, 10, 0));
        close(day.plusDays(1), ownerToken());

        // Act
        MockHttpServletResponse response = occupancy(day, day.plusDays(1), ownerToken());

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(1, count(response, "$"));
        assertEquals(agendaId, text(response, "$[0].agendaId"));
        assertEquals("4", text(response, "$[0].totalSlots"));
        assertEquals("1", text(response, "$[0].occupiedSlots"));
        assertEquals("0.25", text(response, "$[0].occupancyRate"));
    }

    @Test
    @DisplayName("HU-019: an inverted report period is rejected")
    void invertedOccupancyPeriodIsRejected() throws Exception {
        // Act
        MockHttpServletResponse response = occupancy(day, day.minusDays(1), ownerToken());

        // Assert
        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("HU-019: a STAFF member cannot read the occupancy report (ADR-0008)")
    void staffCannotReadOccupancy() throws Exception {
        // Act
        MockHttpServletResponse response = occupancy(day, day, staffToken());

        // Assert
        assertEquals(403, response.getStatus());
    }
}
