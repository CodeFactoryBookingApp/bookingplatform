package com.codefactory.bookingplatform.catalog;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HU-004 end to end, with the HU-009 activation rule: HTTP, the organization scope of ADR-0008
 * and PostgreSQL.
 *
 * <p>Arrange-Act-Assert: the provider, its resources and services are the Arrange; the Act is the
 * one HTTP call whose effect is measured.
 */
class ServiceManagementIT extends JwtIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private ProviderFixture fixture;
    private Provider provider;
    private final List<UUID> bookings = new ArrayList<>();

    @BeforeEach
    void createProvider() {
        fixture = new ProviderFixture(jdbc);
        provider = fixture.activeProvider();
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

    private String resourceType(String name) throws Exception {
        MockHttpServletResponse response = send(post("/api/v1/organizations/{org}/resource-types",
                provider.organizationId()), "{\"name\": \"" + name + "\"}", ownerToken());
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private void resource(String typeId, String name) throws Exception {
        MockHttpServletResponse response = send(post("/api/v1/organizations/{org}/resources", provider.organizationId()),
                "{\"resourceTypeId\": \"%s\", \"locationId\": \"%s\", \"name\": \"%s\", \"capacity\": 1}"
                        .formatted(typeId, provider.locationId(), name), ownerToken());
        assertEquals(201, response.getStatus(), response.getContentAsString());
    }

    private static String serviceBody(String name, int capacity, String typeId) {
        return """
                {
                  "details": {"name": "%s", "durationMinutes": 30, "preparationMinutes": 5, "cleanupMinutes": 5,
                              "price": 35000, "customerCapacity": %d},
                  "locationIds": ["%%s"],
                  "requirements": [{"resourceTypeId": "%s", "quantity": 1}]
                }
                """.formatted(name, capacity, typeId);
    }

    private MockHttpServletResponse createService(String name, int capacity, String typeId) throws Exception {
        return send(post("/api/v1/organizations/{org}/services", provider.organizationId()),
                serviceBody(name, capacity, typeId).formatted(provider.locationId()), ownerToken());
    }

    private String serviceId(String name, String typeId) throws Exception {
        MockHttpServletResponse response = createService(name, 1, typeId);
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private MockHttpServletResponse action(String serviceId, String action) throws Exception {
        return call(post("/api/v1/organizations/{org}/services/{id}/" + action, provider.organizationId(), serviceId)
                .header("Authorization", ownerToken()));
    }

    /** An ACTIVE service with the chair it needs, as a booking requires. */
    private String activeService(String name) throws Exception {
        String chair = resourceType("Silla " + name);
        resource(chair, "Silla de " + name);
        String service = serviceId(name, chair);
        assertEquals(200, action(service, "activation").getStatus());
        return service;
    }

    private UUID bookingOf(String serviceId) {
        UUID booking = fixture.futureBooking(provider, UUID.fromString(serviceId), null);
        bookings.add(booking);
        return booking;
    }

    private static String field(MockHttpServletResponse response, String path) throws Exception {
        return String.valueOf((Object) JsonPath.read(response.getContentAsString(), path));
    }

    @Test
    @DisplayName("HU-004: a service with a unique name, a duration and a required resource type is created INACTIVE")
    void createsAnInactiveService() throws Exception {
        // Arrange
        String chair = resourceType("Silla de barbero");

        // Act
        MockHttpServletResponse response = createService("Corte clásico", 1, chair);

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("INACTIVE", field(response, "$.status"));
        assertEquals("false", field(response, "$.group"));
        assertEquals("COP", field(response, "$.currency"));
        String id = JsonPath.read(response.getContentAsString(), "$.id");
        assertEquals(1, jdbc.queryForObject("select count(*) from service_locations where service_id = ?::uuid",
                Integer.class, id));
        assertEquals(1, jdbc.queryForObject(
                "select quantity_required from service_resource_requirements where service_id = ?::uuid", Integer.class, id));
    }

    @Test
    @DisplayName("HU-004: a service name already used by the provider is a 409")
    void duplicatedNameIsRejected() throws Exception {
        // Arrange
        String chair = resourceType("Silla");
        serviceId("Barba", chair);

        // Act
        MockHttpServletResponse response = createService("Barba", 1, chair);

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("DUPLICATE_NAME", field(response, "$.errorCode"));
    }

    @Test
    @DisplayName("HU-004: a group service with more than one place is accepted")
    void groupServiceIsAccepted() throws Exception {
        // Arrange
        String room = resourceType("Salón");

        // Act
        MockHttpServletResponse response = createService("Taller de barbería", 8, room);

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("true", field(response, "$.group"));
        assertEquals("8", field(response, "$.customerCapacity"));
    }

    @Test
    @DisplayName("A resource type of another provider is a 400 naming the requirement")
    void foreignResourceTypeIsRejected() throws Exception {
        // Arrange
        UUID foreignType = UUID.randomUUID();
        jdbc.update("insert into resource_types (id, organization_id, name) values (?, ?, 'Ajeno')",
                foreignType, fixture.activeProvider().organizationId());

        // Act
        MockHttpServletResponse response = createService("Corte", 1, foreignType.toString());

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("not a resource type of this provider",
                field(response, "$.details['requirements[0].resourceTypeId']"));
    }

    @Test
    @DisplayName("HU-009: without any active instance of the required type the service cannot be activated")
    void activationWithoutInstancesIsRefused() throws Exception {
        // Arrange
        String chair = resourceType("Silla vacía");
        String service = serviceId("Corte sin silla", chair);

        // Act
        MockHttpServletResponse response = action(service, "activation");

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("INSUFFICIENT_RESOURCES", field(response, "$.errorCode"));
        assertEquals("requires 1, has 0",
                field(response, "$.details['" + provider.locationId() + "/" + chair + "']"));
    }

    @Test
    @DisplayName("HU-009: with an active instance in the location the service becomes ACTIVE")
    void activationWithAnInstanceSucceeds() throws Exception {
        // Arrange
        String chair = resourceType("Silla con instancia");
        resource(chair, "Silla 1");
        String service = serviceId("Corte con silla", chair);

        // Act
        MockHttpServletResponse response = action(service, "activation");

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("ACTIVE", field(response, "$.status"));
    }

    @Test
    @DisplayName("HU-004: a service with bookings is not deleted; the answer says to deactivate it")
    void serviceWithBookingsIsNotDeleted() throws Exception {
        // Arrange
        String service = activeService("Corte reservado");
        bookingOf(service);

        // Act
        MockHttpServletResponse response = call(delete("/api/v1/organizations/{org}/services/{id}",
                provider.organizationId(), service).header("Authorization", ownerToken()));

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("HAS_BOOKINGS", field(response, "$.errorCode"));
        assertTrue(field(response, "$.detail").contains("deactivate"), response.getContentAsString());
        assertEquals(200, call(get("/api/v1/organizations/{org}/services/{id}", provider.organizationId(), service)
                .header("Authorization", ownerToken())).getStatus());
    }

    @Test
    @DisplayName("HU-004: deactivating a service with bookings keeps the service and its bookings")
    void deactivationKeepsTheBookings() throws Exception {
        // Arrange
        String service = activeService("Corte a desactivar");
        UUID booking = bookingOf(service);

        // Act
        MockHttpServletResponse response = action(service, "deactivation");

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("INACTIVE", field(response, "$.status"));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from bookings where id = ?", String.class, booking));
    }

    @Test
    @DisplayName("A service never booked is deleted, and is gone afterwards")
    void serviceWithoutBookingsIsDeleted() throws Exception {
        // Arrange
        String service = serviceId("Corte efímero", resourceType("Silla efímera"));

        // Act
        MockHttpServletResponse response = call(delete("/api/v1/organizations/{org}/services/{id}",
                provider.organizationId(), service).header("Authorization", ownerToken()));

        // Assert
        assertEquals(204, response.getStatus());
        assertEquals(404, call(get("/api/v1/organizations/{org}/services/{id}", provider.organizationId(), service)
                .header("Authorization", ownerToken())).getStatus());
    }

    @Test
    @DisplayName("HU-004: changing the duration and preparation leaves the times of existing bookings untouched")
    void durationChangeKeepsBookingTimes() throws Exception {
        // Arrange
        String service = activeService("Corte largo");
        UUID booking = bookingOf(service);
        Map<String, Object> before = jdbc.queryForMap("select starts_at, ends_at from bookings where id = ?", booking);

        // Act
        MockHttpServletResponse response = send(put("/api/v1/organizations/{org}/services/{id}",
                provider.organizationId(), service), """
                {"name": "Corte largo", "durationMinutes": 60, "preparationMinutes": 15, "cleanupMinutes": 10,
                 "price": 50000}
                """, ownerToken());

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("60", field(response, "$.durationMinutes"));
        assertEquals(before, jdbc.queryForMap("select starts_at, ends_at from bookings where id = ?", booking));
    }

    @Test
    @DisplayName("HU-022: staff may list the services but not create them; an outsider gets 404")
    void staffAndOutsider() throws Exception {
        // Arrange
        UUID staff = fixture.addMember(provider.organizationId(), "STAFF");
        UUID outsider = fixture.activeProvider().ownerId();
        String chair = resourceType("Silla de staff");

        // Act
        MockHttpServletResponse response = send(post("/api/v1/organizations/{org}/services", provider.organizationId()),
                serviceBody("Corte de staff", 1, chair).formatted(provider.locationId()), token(staff));

        // Assert
        assertEquals(403, response.getStatus());
        assertEquals(200, call(get("/api/v1/organizations/{org}/services", provider.organizationId())
                .header("Authorization", token(staff))).getStatus());
        MockHttpServletResponse outside = call(get("/api/v1/organizations/{org}/services", provider.organizationId())
                .header("Authorization", token(outsider)));
        assertEquals(404, outside.getStatus());
        assertTrue(outside.getContentAsString().contains("RESOURCE_NOT_FOUND"));
    }
}
