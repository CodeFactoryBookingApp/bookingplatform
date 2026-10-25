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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HU-009 end to end: HTTP, the organization scope of ADR-0008 and PostgreSQL with V7.
 *
 * <p>Arrange-Act-Assert: the provider and whatever it already owns are the Arrange; the Act is the
 * one HTTP call whose effect is measured.
 */
class ResourceManagementIT extends JwtIntegrationTestBase {

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

    /** Bookings point at clients, which the base class deletes before every test. */
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

    private MockHttpServletResponse postJson(String path, String body, String token, Object... vars) throws Exception {
        return call(post(path, vars).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletResponse createType(String name, String token) throws Exception {
        return postJson("/api/v1/organizations/{org}/resource-types", "{\"name\": \"" + name + "\"}", token,
                provider.organizationId());
    }

    private String typeId(String name) throws Exception {
        MockHttpServletResponse response = createType(name, ownerToken());
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private MockHttpServletResponse createResource(String typeId, UUID locationId, String name) throws Exception {
        return postJson("/api/v1/organizations/{org}/resources",
                "{\"resourceTypeId\": \"%s\", \"locationId\": \"%s\", \"name\": \"%s\", \"capacity\": 1}"
                        .formatted(typeId, locationId, name), ownerToken(), provider.organizationId());
    }

    private String resourceId(String name) throws Exception {
        MockHttpServletResponse response = createResource(typeId("Tipo " + name), provider.locationId(), name);
        assertEquals(201, response.getStatus(), response.getContentAsString());
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private MockHttpServletResponse maintenance(String resourceId, Instant from, Instant to) throws Exception {
        return postJson("/api/v1/organizations/{org}/resources/{id}/maintenances",
                "{\"startsAt\": \"%s\", \"endsAt\": \"%s\", \"reason\": \"Revisión\"}".formatted(from, to),
                ownerToken(), provider.organizationId(), resourceId);
    }

    private MockHttpServletResponse retire(String resourceId) throws Exception {
        return call(post("/api/v1/organizations/{org}/resources/{id}/retirement", provider.organizationId(), resourceId)
                .header("Authorization", ownerToken()));
    }

    /** A CONFIRMED booking in two days that uses the resource. */
    private UUID futureBookingOn(String resourceId) {
        UUID booking = fixture.futureBooking(provider, fixture.activeService(provider), UUID.fromString(resourceId));
        bookings.add(booking);
        return booking;
    }

    private static String errorCodeOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.errorCode");
    }

    @Test
    @DisplayName("HU-009: a type with two instances in a location; both are listed AVAILABLE with their capacity")
    void createsTypeAndInstances() throws Exception {
        // Arrange
        String chair = typeId("Silla de barbero");
        createResource(chair, provider.locationId(), "Silla 1");
        createResource(chair, provider.locationId(), "Silla 2");

        // Act
        MockHttpServletResponse response = call(get("/api/v1/organizations/{org}/resources", provider.organizationId())
                .header("Authorization", ownerToken()));

        // Assert
        assertEquals(200, response.getStatus());
        assertEquals(List.of("Silla 1", "Silla 2"), JsonPath.read(response.getContentAsString(), "$[*].name"));
        assertEquals(List.of("AVAILABLE", "AVAILABLE"), JsonPath.read(response.getContentAsString(), "$[*].condition"));
        assertEquals(List.of(1, 1), JsonPath.read(response.getContentAsString(), "$[*].capacity"));
    }

    @Test
    @DisplayName("HU-009: a resource type name already used by the provider is a 409")
    void duplicatedTypeNameIsRejected() throws Exception {
        // Arrange
        typeId("Cancha");

        // Act
        MockHttpServletResponse response = createType("Cancha", ownerToken());

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("DUPLICATE_NAME", errorCodeOf(response));
    }

    @Test
    @DisplayName("A resource name already used in the same location is a 409")
    void duplicatedResourceNameInTheLocationIsRejected() throws Exception {
        // Arrange
        String room = typeId("Consultorio");
        createResource(room, provider.locationId(), "Consultorio 1");

        // Act
        MockHttpServletResponse response = createResource(room, provider.locationId(), "Consultorio 1");

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("DUPLICATE_NAME", errorCodeOf(response));
    }

    @Test
    @DisplayName("A location of another provider is a 400 naming the field, not a foreign key failure")
    void foreignLocationIsRejected() throws Exception {
        // Arrange
        String room = typeId("Sala");
        UUID foreignLocation = fixture.activeProvider().locationId();

        // Act
        MockHttpServletResponse response = createResource(room, foreignLocation, "Sala 1");

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("not a location of this provider",
                JsonPath.read(response.getContentAsString(), "$.details.locationId"));
    }

    @Test
    @DisplayName("HU-009: during a maintenance period the resource is IN_MAINTENANCE")
    void resourceInMaintenance() throws Exception {
        // Arrange
        String resource = resourceId("Silla 7");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Act
        MockHttpServletResponse response = maintenance(resource, now.minusSeconds(3600), now.plusSeconds(86400));

        // Assert
        assertEquals(201, response.getStatus(), response.getContentAsString());
        assertEquals("IN_MAINTENANCE", JsonPath.read(response.getContentAsString(), "$.condition"));
    }

    @Test
    @DisplayName("Two overlapping maintenance periods of a resource are a 409")
    void overlappingMaintenanceIsRejected() throws Exception {
        // Arrange
        String resource = resourceId("Silla 8");
        Instant start = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS);
        maintenance(resource, start, start.plusSeconds(7200));

        // Act
        MockHttpServletResponse response = maintenance(resource, start.plusSeconds(3600), start.plusSeconds(10800));

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("OVERLAPPING_PERIOD", errorCodeOf(response));
    }

    @Test
    @DisplayName("HU-009: a resource with a future confirmed booking cannot be retired; the error counts the bookings")
    void resourceWithFutureBookingIsNotRetired() throws Exception {
        // Arrange
        String resource = resourceId("Silla 9");
        futureBookingOn(resource);

        // Act
        MockHttpServletResponse response = retire(resource);

        // Assert
        assertEquals(409, response.getStatus());
        assertEquals("HAS_FUTURE_BOOKINGS", errorCodeOf(response));
        assertEquals("1", JsonPath.read(response.getContentAsString(), "$.details.futureBookings"));
    }

    @Test
    @DisplayName("HU-009: once its booking is cancelled, the resource can be retired")
    void resourceIsRetiredOnceItsBookingsAreResolved() throws Exception {
        // Arrange
        String resource = resourceId("Silla 10");
        UUID booking = futureBookingOn(resource);
        jdbc.update("update bookings set status = 'CANCELLED', cancelled_at = now() where id = ?", booking);

        // Act
        MockHttpServletResponse response = retire(resource);

        // Assert
        assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("RETIRED", JsonPath.read(response.getContentAsString(), "$.condition"));
    }

    @Test
    @DisplayName("HU-022: staff may list the resources but not create them")
    void staffReadsButDoesNotWrite() throws Exception {
        // Arrange
        UUID staff = fixture.addMember(provider.organizationId(), "STAFF");

        // Act
        MockHttpServletResponse response = createType("Equipo", token(staff));

        // Assert
        assertEquals(403, response.getStatus());
        assertEquals(200, call(get("/api/v1/organizations/{org}/resources", provider.organizationId())
                .header("Authorization", token(staff))).getStatus());
    }

    @Test
    @DisplayName("HU-022: a provider asking for the resources of another one gets 404")
    void outsiderGetsNotFound() throws Exception {
        // Arrange
        Provider other = fixture.activeProvider();

        // Act
        MockHttpServletResponse response = call(get("/api/v1/organizations/{org}/resources", provider.organizationId())
                .header("Authorization", token(other.ownerId())));

        // Assert
        assertEquals(404, response.getStatus());
        assertEquals("RESOURCE_NOT_FOUND", errorCodeOf(response));
    }
}
