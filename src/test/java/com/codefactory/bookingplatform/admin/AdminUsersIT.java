package com.codefactory.bookingplatform.admin;

import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


/**
 * HU-003 end to end: the administrator's listings of clients and providers, their export, and
 * the status changes with reason. The base class wipes clients before every test; providers are
 * shared with other test classes, so each test puts its providers in a city of its own.
 *
 * <p>Arrange-Act-Assert: the users created are the Arrange; the Act is the one HTTP call measured.
 */
class AdminUsersIT extends JwtIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private ProviderFixture fixture;
    private String word;
    private final List<UUID> bookings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixture = new ProviderFixture(jdbc);
        word = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void deleteBookings() {
        bookings.forEach(fixture::deleteBooking);
    }

    private String adminToken() {
        return "Bearer " + jwks().accessToken(UUID.randomUUID(), "admin@plataforma.com", "ADMIN");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", adminToken())).andReturn().getResponse();
    }

    private MockHttpServletResponse post(String path, String body, Object... vars) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, vars)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID client(String name, String city, String status) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                insert into clients values (?, ?, ?, '1990-01-01', ?, '3001112233', ?, 'EMAIL', ?, localtimestamp,
                                            localtimestamp)
                """, id, name, "DOC-" + suffix, "c-" + suffix + "@example.com", city, status);
        return id;
    }

    /** A city of the catalog only this test uses, so that filtering by it isolates its providers. */
    private String ownCity() {
        String city = "Ciudad " + word;
        jdbc.update("insert into cities (name, region) values (?, 'Pruebas')", city);
        return city;
    }

    private static List<String> names(MockHttpServletResponse response, String field) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.items[*]." + field);
    }

    @Nested
    @DisplayName("Client listing")
    class Clients {

        @Test
        @DisplayName("HU-003: status and city combine, the city matched without case or accents, with the total")
        void filtersCombine() throws Exception {
            // Arrange
            client("Ana", "Bogotá", "ACTIVE");
            client("Bea", "Bogotá", "SUSPENDED");
            client("Carla", "Medellín", "ACTIVE");

            // Act
            MockHttpServletResponse response = call(get("/api/v1/admin/clients")
                    .param("status", "ACTIVE").param("city", "BOGOTA"));

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals(List.of("Ana"), names(response, "fullName"));
            assertEquals(1, (int) JsonPath.read(response.getContentAsString(), "$.total"));
        }

        @Test
        @DisplayName("HU-003: sorting by a column, ascending or descending; without filters every client is listed")
        void sortsByColumn() throws Exception {
            // Arrange
            client("Bea", "Cali", "ACTIVE");
            client("Ana", "Cali", "ACTIVE");
            client("Carla", "Cali", "PENDING_VERIFICATION");

            // Act
            MockHttpServletResponse response = call(get("/api/v1/admin/clients").param("sort", "name")
                    .param("direction", "desc"));

            // Assert
            assertEquals(List.of("Carla", "Bea", "Ana"), names(response, "fullName"));
        }

        @Test
        @DisplayName("HU-003: the export is a CSV of the filtered clients only, every page")
        void exportsTheFilteredListing() throws Exception {
            // Arrange
            client("Ana", "Pasto", "ACTIVE");
            client("Bea", "Pasto", "SUSPENDED");

            // Act
            MockHttpServletResponse response = call(get("/api/v1/admin/clients/export").param("status", "SUSPENDED"));

            // Assert
            assertEquals(200, response.getStatus());
            assertTrue(response.getContentType().startsWith("text/csv"), response.getContentType());
            String csv = response.getContentAsString(StandardCharsets.UTF_8);
            assertTrue(csv.startsWith("﻿id,nombre,documento"), csv);
            assertTrue(csv.contains("Bea"), csv);
            assertFalse(csv.contains("Ana"), csv);
        }

        @Test
        @DisplayName("HU-003: suspending a client demands a reason, which is stored with the change")
        void suspensionStoresTheReason() throws Exception {
            // Arrange
            UUID client = client("Ana", "Cali", "ACTIVE");

            // Act
            MockHttpServletResponse response = post("/api/v1/admin/clients/{id}/suspension",
                    "{\"reason\": \"Uso indebido\"}", client);

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("SUSPENDED", jdbc.queryForObject("select status from clients where id = ?", String.class, client));
            assertEquals("Uso indebido", jdbc.queryForObject(
                    "select reason from account_status_changes where client_id = ?", String.class, client));
        }

        @Test
        @DisplayName("A suspension without a reason is a 400")
        void suspensionWithoutReasonIsRejected() throws Exception {
            // Arrange
            UUID client = client("Ana", "Cali", "ACTIVE");

            // Act
            MockHttpServletResponse response = post("/api/v1/admin/clients/{id}/suspension", "{\"reason\": \" \"}", client);

            // Assert
            assertEquals(400, response.getStatus());
        }
    }

    @Nested
    @DisplayName("Provider listing and status")
    class Providers {

        @Test
        @DisplayName("HU-003: status, category and city combine for providers")
        void filtersCombine() throws Exception {
            // Arrange
            String city = ownCity();
            fixture.provider("Clínica activa", "ACTIVE", "Salud", city);
            fixture.provider("Clínica pendiente", "PENDING_APPROVAL", "Salud", city);
            fixture.provider("Salón activo", "ACTIVE", "Belleza", city);
            String salud = jdbc.queryForObject("select id::text from organization_categories where name = 'Salud'", String.class);

            // Act
            MockHttpServletResponse response = call(get("/api/v1/admin/organizations").param("status", "ACTIVE")
                    .param("categoryId", salud).param("city", city.toUpperCase()));

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals(List.of("Clínica activa"), names(response, "name"));
        }

        @Test
        @DisplayName("HU-003: the provider export is a CSV of the filtered providers")
        void exportsTheFilteredProviders() throws Exception {
            // Arrange
            String city = ownCity();
            fixture.provider("Gimnasio " + word, "ACTIVE", "Deporte", city);

            // Act
            MockHttpServletResponse response = call(get("/api/v1/admin/organizations/export").param("city", city));

            // Assert
            String csv = response.getContentAsString(StandardCharsets.UTF_8);
            assertEquals(2, csv.split("\r\n").length, csv);
            assertTrue(csv.contains("Gimnasio " + word + ",NIT,"), csv);
        }

        @Test
        @DisplayName("HU-003: suspending a provider with a future booking asks for confirmation and reports it; nothing changes yet")
        void suspensionWithBookingsAsksForConfirmation() throws Exception {
            // Arrange
            Provider provider = fixture.activeProvider();
            bookings.add(fixture.futureBooking(provider, fixture.activeService(provider), null));

            // Act
            MockHttpServletResponse response = post("/api/v1/admin/organizations/{id}/suspension",
                    "{\"reason\": \"Quejas reiteradas\"}", provider.organizationId());

            // Assert
            assertEquals(409, response.getStatus());
            assertEquals("CONFIRMATION_REQUIRED", JsonPath.read(response.getContentAsString(), "$.errorCode"));
            assertEquals("1", JsonPath.read(response.getContentAsString(), "$.details.affectedBookings"));
            assertEquals("ACTIVE", jdbc.queryForObject("select status from organizations where id = ?",
                    String.class, provider.organizationId()));
        }

        @Test
        @DisplayName("HU-003: once confirmed, it is suspended, the reason and affected bookings are stored, the booking is kept")
        void confirmedSuspension() throws Exception {
            // Arrange
            Provider provider = fixture.activeProvider();
            UUID booking = fixture.futureBooking(provider, fixture.activeService(provider), null);
            bookings.add(booking);

            // Act
            MockHttpServletResponse response = post("/api/v1/admin/organizations/{id}/suspension",
                    "{\"reason\": \"Quejas reiteradas\", \"confirm\": true}", provider.organizationId());

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("SUSPENDED", JsonPath.read(response.getContentAsString(), "$.status"));
            assertEquals(1, jdbc.queryForObject("""
                    select affected_bookings from account_status_changes
                     where organization_id = ? and to_status = 'SUSPENDED' and reason = 'Quejas reiteradas'
                    """, Integer.class, provider.organizationId()));
            assertEquals("CONFIRMED", jdbc.queryForObject("select status from bookings where id = ?", String.class, booking));
        }

        @Test
        @DisplayName("A suspended provider is reactivated, and one pending approval is rejected, each with its reason")
        void reactivationAndRejection() throws Exception {
            // Arrange
            Provider suspended = fixture.provider("Suspendido " + word, "SUSPENDED", null, null);
            Provider pending = fixture.provider("Pendiente " + word, "PENDING_APPROVAL", null, null);

            // Act
            MockHttpServletResponse response = post("/api/v1/admin/organizations/{id}/reactivation",
                    "{\"reason\": \"Corregido\"}", suspended.organizationId());

            // Assert
            assertEquals("ACTIVE", JsonPath.read(response.getContentAsString(), "$.status"));
            assertEquals("REJECTED", JsonPath.read(post("/api/v1/admin/organizations/{id}/rejection",
                    "{\"reason\": \"RUT ilegible\"}", pending.organizationId()).getContentAsString(), "$.status"));
        }
    }

    @Test
    @DisplayName("HU-022: a provider cannot use the administration: 403")
    void providerIsDenied() throws Exception {
        // Act
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/admin/clients").header("Authorization",
                "Bearer " + jwks().accessToken(UUID.randomUUID(), "p@example.com", "PROVIDER"))).andReturn().getResponse();

        // Assert
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("A filter value of the wrong type is a 400 that names the parameter, not a 500")
    void wrongParameterTypeIsA400() throws Exception {
        // Act
        MockHttpServletResponse response = call(get("/api/v1/admin/organizations").param("status", "FOO"));

        // Assert
        assertEquals(400, response.getStatus());
        assertEquals("has an invalid value", JsonPath.read(response.getContentAsString(), "$.details.status"));
    }
}
