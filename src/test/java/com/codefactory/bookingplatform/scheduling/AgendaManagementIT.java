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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HU-006 end to end: HTTP, the organization scope of ADR-0008 and PostgreSQL with V8.
 *
 * <p>Arrange-Act-Assert: the provider, its services, agendas and bookings are the Arrange; the
 * Act is the one HTTP call whose effect is measured.
 */
class AgendaManagementIT extends JwtIntegrationTestBase {

    /** Every day of the week, all day long: any booking the fixture creates falls inside. */
    private static final String ALL_WEEK = """
            [{"dayOfWeek": "MONDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "TUESDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "WEDNESDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "THURSDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "FRIDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "SATURDAY", "startTime": "00:00", "endTime": "23:59"},
             {"dayOfWeek": "SUNDAY", "startTime": "00:00", "endTime": "23:59"}]
            """;

    @Autowired
    private JdbcTemplate jdbc;

    private ProviderFixture fixture;
    private Provider provider;
    private UUID service;
    private final List<UUID> bookings = new ArrayList<>();

    @BeforeEach
    void createProvider() {
        fixture = new ProviderFixture(jdbc);
        provider = fixture.activeProvider();
        service = fixture.activeService(provider);
    }

    @AfterEach
    void deleteBookings() {
        bookings.forEach(fixture::deleteBooking);
    }

    private String token(UUID userId) {
        return "Bearer " + jwks().accessToken(userId, "user-" + userId + "@example.com", "PROVIDER");
    }

    private String ownerToken() {
        return token(provider.ownerId());
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, String body, String token)
            throws Exception {
        return call(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String agendaBody(String name, UUID serviceId, int slotMinutes, String blocks) {
        return """
                {"name": "%s", "locationId": "%s", "slotMinutes": %d, "validFrom": "%s",
                 "serviceIds": ["%s"], "blocks": %s}
                """.formatted(name, provider.locationId(), slotMinutes, LocalDate.now().minusDays(1), serviceId, blocks);
    }

    private MockHttpServletResponse createAgenda(String body, String token) throws Exception {
        return send(post("/api/v1/organizations/{org}/agendas", provider.organizationId()), body, token);
    }

    private String agendaId(String blocks) throws Exception {
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda principal", service, 30, blocks), ownerToken());
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private UUID booking() {
        UUID booking = fixture.futureBooking(provider, service, null);
        bookings.add(booking);
        return booking;
    }

    private static String field(MockHttpServletResponse response, String path) throws Exception {
        return String.valueOf((Object) JsonPath.read(response.getContentAsString(), path));
    }

    @Test
    @DisplayName("HU-006: an agenda with location, granularity, validity, a service and its blocks is created")
    void createsAValidAgenda() throws Exception {
        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda Poblado", service, 30, """
                [{"dayOfWeek": "TUESDAY", "startTime": "14:00", "endTime": "18:00"},
                 {"dayOfWeek": "MONDAY", "startTime": "08:00", "endTime": "12:00"}]
                """), ownerToken());

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("30", field(response, "$.slotMinutes"));
        assertEquals(List.of("MONDAY", "TUESDAY"), JsonPath.read(response.getContentAsString(), "$.blocks[*].dayOfWeek"));
        assertEquals(List.of(service.toString()), JsonPath.read(response.getContentAsString(), "$.serviceIds"));
    }

    @Test
    @DisplayName("HU-006: a block that ends before it starts cannot be saved")
    void blockEndingBeforeItStartsIsRejected() throws Exception {
        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda al revés", service, 30, """
                [{"dayOfWeek": "MONDAY", "startTime": "12:00", "endTime": "08:00"}]
                """), ownerToken());

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("MONDAY 12:00-08:00: the end must be after the start", field(response, "$.details.blocks"));
    }

    @Test
    @DisplayName("HU-006: two overlapping blocks on the same day are detected when saving")
    void overlappingBlocksAreRejected() throws Exception {
        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda solapada", service, 30, """
                [{"dayOfWeek": "MONDAY", "startTime": "08:00", "endTime": "12:00"},
                 {"dayOfWeek": "MONDAY", "startTime": "11:00", "endTime": "13:00"}]
                """), ownerToken());

        // Assert
        assertEquals(400, response.getStatus());
        assertTrue(field(response, "$.details.blocks").contains("overlaps"));
    }

    @Test
    @DisplayName("A second agenda of the same location valid at the same time is a 409")
    void overlappingValidityIsRejected() throws Exception {
        // Arrange
        agendaId(ALL_WEEK);

        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Otra agenda", service, 15, ALL_WEEK), ownerToken());

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("OVERLAPPING_PERIOD", field(response, "$.errorCode"));
    }

    @Test
    @DisplayName("A service that is not offered in the location of the agenda is a 400 naming it")
    void serviceOfAnotherLocationIsRejected() throws Exception {
        // Arrange
        UUID elsewhere = fixture.activeService(fixture.activeProvider());

        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda ajena", elsewhere, 30, ALL_WEEK), ownerToken());

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("not a service of this provider offered in that location", field(response, "$.details['serviceIds[0]']"));
    }

    @Test
    @DisplayName("HU-006: copying Monday to the rest of the week gives those days the same blocks")
    void copiesADay() throws Exception {
        // Arrange
        String agenda = agendaId("""
                [{"dayOfWeek": "MONDAY", "startTime": "08:00", "endTime": "12:00"},
                 {"dayOfWeek": "MONDAY", "startTime": "14:00", "endTime": "18:00"}]
                """);

        // Act
        MockHttpServletResponse response = send(post("/api/v1/organizations/{org}/agendas/{id}/day-copies",
                provider.organizationId(), agenda), """
                {"fromDay": "MONDAY", "toDays": ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]}
                """, ownerToken());

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(10, jdbc.queryForObject("select count(*) from schedules where agenda_id = ?::uuid",
                Integer.class, agenda));
        assertEquals(List.of("08:00:00", "14:00:00"), JsonPath.read(response.getContentAsString(),
                "$.blocks[?(@.dayOfWeek == 'FRIDAY')].startTime"));
    }

    @Test
    @DisplayName("HU-006: new hours that leave a confirmed booking outside are refused, listing the booking")
    void newHoursStrandingABookingAreRefused() throws Exception {
        // Arrange
        String agenda = agendaId(ALL_WEEK);
        UUID booking = booking();

        // Act
        MockHttpServletResponse response = send(put("/api/v1/organizations/{org}/agendas/{id}/blocks",
                provider.organizationId(), agenda), """
                {"blocks": [{"dayOfWeek": "MONDAY", "startTime": "00:00", "endTime": "00:15"}]}
                """, ownerToken());

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("HAS_FUTURE_BOOKINGS", field(response, "$.errorCode"));
        assertTrue(response.getContentAsString().contains(booking.toString()), response.getContentAsString());
        assertEquals(7, jdbc.queryForObject("select count(*) from schedules where agenda_id = ?::uuid",
                Integer.class, agenda), "the old blocks stay");
    }

    @Test
    @DisplayName("Without bookings in the way the new hours replace the old ones")
    void newHoursReplaceTheOldOnes() throws Exception {
        // Arrange
        String agenda = agendaId(ALL_WEEK);

        // Act
        MockHttpServletResponse response = send(put("/api/v1/organizations/{org}/agendas/{id}/blocks",
                provider.organizationId(), agenda), """
                {"blocks": [{"dayOfWeek": "SATURDAY", "startTime": "09:00", "endTime": "13:00"}]}
                """, ownerToken());

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals(List.of("SATURDAY"), JsonPath.read(response.getContentAsString(), "$.blocks[*].dayOfWeek"));
        assertEquals(1, jdbc.queryForObject("select count(*) from schedules where agenda_id = ?::uuid",
                Integer.class, agenda), "the old blocks are gone from the database");
    }

    @Test
    @DisplayName("HU-006: the granularity of an agenda with future bookings cannot change")
    void granularityWithBookingsIsRefused() throws Exception {
        // Arrange
        String agenda = agendaId(ALL_WEEK);
        booking();

        // Act
        MockHttpServletResponse response = send(put("/api/v1/organizations/{org}/agendas/{id}/granularity",
                provider.organizationId(), agenda), "{\"slotMinutes\": 15}", ownerToken());

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("HAS_FUTURE_BOOKINGS", field(response, "$.errorCode"));
    }

    @Test
    @DisplayName("Without future bookings the granularity changes")
    void granularityWithoutBookingsChanges() throws Exception {
        // Arrange
        String agenda = agendaId(ALL_WEEK);

        // Act
        MockHttpServletResponse response = send(put("/api/v1/organizations/{org}/agendas/{id}/granularity",
                provider.organizationId(), agenda), "{\"slotMinutes\": 60}", ownerToken());

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("60", field(response, "$.slotMinutes"));
        assertEquals(60, jdbc.queryForObject("select slot_minutes from agendas where id = ?::uuid",
                Integer.class, agenda), "the change is stored");
    }

    @Test
    @DisplayName("HU-022: staff may read the agendas but not create them; an outsider gets 404")
    void staffAndOutsider() throws Exception {
        // Arrange
        UUID staff = fixture.addMember(provider.organizationId(), "STAFF");
        UUID outsider = fixture.activeProvider().ownerId();

        // Act
        MockHttpServletResponse response = createAgenda(agendaBody("Agenda de staff", service, 30, ALL_WEEK), token(staff));

        // Assert
        assertEquals(403, response.getStatus());
        assertEquals(200, call(get("/api/v1/organizations/{org}/agendas", provider.organizationId())
                .header("Authorization", token(staff))).getStatus());
        assertEquals(404, call(get("/api/v1/organizations/{org}/agendas", provider.organizationId())
                .header("Authorization", token(outsider))).getStatus());
    }
}
