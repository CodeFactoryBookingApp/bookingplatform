package com.codefactory.bookingplatform.organization;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HU-002 end to end through HTTP, PostgreSQL migrated by Flyway and the simulated identity
 * provider, together with the authorization rules of ADR-0008 that it introduces.
 *
 * <p>Organizations are not wiped between tests, so each test registers its own provider with a
 * fresh identification and email.
 *
 * <p>Arrange-Act-Assert: the registrations a test needs are its Arrange; the Act is the one HTTP
 * call whose effect is measured.
 */
class ProviderOnboardingIT extends JwtIntegrationTestBase {

    private static final String PASSWORD = "Str0ng!Pass";

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private String categoryId;
    private String cityId;

    @BeforeEach
    void loadReferenceData() {
        categoryId = jdbc.queryForObject("select id::text from organization_categories where name = 'Belleza'", String.class);
        cityId = jdbc.queryForObject("select id::text from cities where name = 'Medellín'", String.class);
    }

    private record Provider(String organizationId, UUID ownerId, String email, String identification) {
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String registrationBody(String email, String identification, int min, int max, String city) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "name": "Barbería El Poblado %s",
                  "categoryId": "%s",
                  "identificationType": "NIT",
                  "identificationNumber": "%s",
                  "contactPhone": "6041112233",
                  "locations": [{"name": "Sede Poblado", "address": "Cra 43A # 5-15", "cityId": "%s"}],
                  "bookingNoticeMinutesMin": %d,
                  "bookingNoticeMinutesMax": %d,
                  "freeCancellationWindowMinutes": 1440
                }
                """.formatted(email, PASSWORD, identification, categoryId, identification, city, min, max);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse register(String email, String identification, int min, int max, String city)
            throws Exception {
        return call(post("/api/v1/provider-registrations").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(email, identification, min, max, city)));
    }

    private Provider registered() throws Exception {
        String suffix = unique();
        String email = "barberia-" + suffix + "@example.com";
        String identification = "900" + suffix.replaceAll("[^0-9]", "1") + "-1";
        MockHttpServletResponse response = register(email, identification, 60, 43200, cityId);
        assertEquals(201, response.getStatus(), response.getContentAsString());
        String body = response.getContentAsString();
        return new Provider(JsonPath.read(body, "$.organizationId"),
                UUID.fromString(JsonPath.read(body, "$.ownerUserId")), email, identification);
    }

    private String bearer(UUID userId, String email, String role) {
        return "Bearer " + jwks().accessToken(userId, email, role);
    }

    private String ownerToken(Provider provider) {
        return bearer(provider.ownerId(), provider.email(), "PROVIDER");
    }

    private String adminToken() {
        return bearer(UUID.randomUUID(), "admin@plataforma.com", "ADMIN");
    }

    private MockHttpServletResponse approve(Provider provider, String token) throws Exception {
        return call(post("/api/v1/admin/organizations/{id}/approval", provider.organizationId())
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"RUT y cámara de comercio verificados\"}"));
    }

    private MockHttpServletResponse changePolicy(Provider provider, String token, int min, int max) throws Exception {
        return call(put("/api/v1/organizations/{id}/operation-policy", provider.organizationId())
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingNoticeMinutesMin\": %d, \"bookingNoticeMinutesMax\": %d, \"freeCancellationWindowMinutes\": 720}"
                        .formatted(min, max)));
    }

    private static String errorCodeOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.errorCode");
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("The categories and cities of V5 are public, so the form can be filled without an account")
        void referenceDataIsPublic() throws Exception {
            // Act
            MockHttpServletResponse cities = call(get("/api/v1/cities"));

            // Assert
            assertEquals(200, cities.getStatus());
            assertTrue(cities.getContentAsString().contains("Medellín"));
            assertTrue(call(get("/api/v1/organization-categories")).getContentAsString().contains("Belleza"));
        }

        @Test
        @DisplayName("HU-002: a provider with one location is stored PENDING_APPROVAL with policy v1 and its owner, under a PROVIDER account")
        void registrationStoresAPendingProvider() throws Exception {
            // Act
            Provider provider = registered();

            // Assert
            Map<String, Object> row = jdbc.queryForMap(
                    "select status, identification_type, identification_number, approved_at from organizations where id = ?::uuid",
                    provider.organizationId());
            assertEquals("PENDING_APPROVAL", row.get("status"));
            assertEquals("NIT", row.get("identification_type"));
            assertEquals(provider.identification(), row.get("identification_number"));
            assertNull(row.get("approved_at"));
            assertEquals(1, count("select count(*) from locations where organization_id = ?::uuid", provider.organizationId()));
            assertEquals(1, count("select count(*) from organization_policies where organization_id = ?::uuid "
                    + "and version = 1 and replaced_at is null", provider.organizationId()));
            assertEquals("OWNER", jdbc.queryForObject("select role from organization_members where organization_id = ?::uuid "
                    + "and user_id = ?", String.class, provider.organizationId(), provider.ownerId()));
            verify(identityProviderPort).createUser(provider.email(), PASSWORD, AppRole.PROVIDER);
        }

        @Test
        @DisplayName("HU-002: an identification already registered is a 409 and no second account is created")
        void duplicatedIdentificationIsRejected() throws Exception {
            // Arrange
            Provider first = registered();

            // Act
            MockHttpServletResponse response = register("otro-" + unique() + "@example.com", first.identification(),
                    60, 43200, cityId);

            // Assert
            assertEquals(409, response.getStatus());
            assertEquals("DUPLICATE_IDENTIFICATION", errorCodeOf(response));
            verify(identityProviderPort).createUser(anyString(), anyString(), any(AppRole.class));
        }

        @Test
        @DisplayName("HU-002: a minimum notice greater than the maximum cannot be saved, and the error names the field")
        void inconsistentNoticeIsRejected() throws Exception {
            // Act
            MockHttpServletResponse response = register("b-" + unique() + "@example.com", "901" + unique().replaceAll("[^0-9]", "2"),
                    5000, 100, cityId);

            // Assert
            assertEquals(400, response.getStatus());
            assertEquals("must not be greater than bookingNoticeMinutesMax",
                    JsonPath.read(response.getContentAsString(), "$.details.bookingNoticeMinutesMin"));
            verify(identityProviderPort, never()).createUser(anyString(), anyString(), any(AppRole.class));
        }

        @Test
        @DisplayName("An unknown city is a 400 naming the location, not a foreign key failure")
        void unknownCityIsRejected() throws Exception {
            // Act
            MockHttpServletResponse response = register("c-" + unique() + "@example.com", "902" + unique().replaceAll("[^0-9]", "3"),
                    60, 43200, UUID.randomUUID().toString());

            // Assert
            assertEquals(400, response.getStatus());
            assertEquals("unknown city", JsonPath.read(response.getContentAsString(), "$.details['locations[0].cityId']"));
        }

        @Test
        @DisplayName("The owner verifies the email through the same link as a client, and the provider account is confirmed")
        void ownerConfirmsTheEmail() throws Exception {
            // Arrange
            Provider provider = registered();
            String token = identityProvider.currentEmailToken(provider.email());

            // Act
            MockHttpServletResponse response = call(post("/api/v1/registrations/email-verifications")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tokenHash\": \"" + token + "\"}"));

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertTrue(identityProvider.isEmailConfirmed(provider.email()));
        }
    }

    @Nested
    @DisplayName("Approval")
    class Approval {

        @Test
        @DisplayName("HU-002: an administrator approves; the provider is ACTIVE, the reason is stored and the email goes out")
        void adminApprovesAndTheProviderIsTold() throws Exception {
            // Arrange
            Provider provider = registered();

            // Act
            MockHttpServletResponse response = approve(provider, adminToken());

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("ACTIVE", JsonPath.read(response.getContentAsString(), "$.status"));
            assertNotNull(JsonPath.read(response.getContentAsString(), "$.approvedAt"));
            assertEquals("RUT y cámara de comercio verificados", jdbc.queryForObject(
                    "select reason from account_status_changes where organization_id = ?::uuid and to_status = 'ACTIVE'",
                    String.class, provider.organizationId()));
            verify(emailSender, timeout(2000)).send(org.mockito.ArgumentMatchers.argThat(
                    (EmailMessage email) -> email.to().equals(provider.email())));
        }

        @Test
        @DisplayName("HU-022: a provider cannot approve itself: 403")
        void providerCannotApprove() throws Exception {
            // Arrange
            Provider provider = registered();

            // Act
            MockHttpServletResponse response = approve(provider, ownerToken(provider));

            // Assert
            assertEquals(403, response.getStatus());
            assertEquals("PENDING_APPROVAL", jdbc.queryForObject("select status from organizations where id = ?::uuid",
                    String.class, provider.organizationId()));
        }

        @Test
        @DisplayName("Approving twice is a 409 and sends no second email")
        void approvingTwiceIsAConflict() throws Exception {
            // Arrange
            Provider provider = registered();
            approve(provider, adminToken());

            // Act
            MockHttpServletResponse response = approve(provider, adminToken());

            // Assert
            assertEquals(409, response.getStatus());
            assertEquals("INVALID_STATUS_TRANSITION", errorCodeOf(response));
            verify(emailSender, timeout(2000).times(1)).send(any(EmailMessage.class));
        }
    }

    @Nested
    @DisplayName("Operating rules and organization scope")
    class OperatingRules {

        @Test
        @DisplayName("HU-002: the owner changes the rules; v2 is current and v1 is closed, so existing bookings keep theirs")
        void ownerChangesTheRules() throws Exception {
            // Arrange
            Provider provider = registered();

            // Act
            MockHttpServletResponse response = changePolicy(provider, ownerToken(provider), 120, 20160);

            // Assert
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals(2, (int) JsonPath.read(response.getContentAsString(), "$.operationPolicy.version"));
            List<Map<String, Object>> versions = jdbc.queryForList("select version, replaced_at from organization_policies "
                    + "where organization_id = ?::uuid order by version", provider.organizationId());
            assertEquals(2, versions.size());
            assertNotNull(versions.get(0).get("replaced_at"));
            assertNull(versions.get(1).get("replaced_at"));
        }

        @Test
        @DisplayName("HU-022: a provider asking for another provider gets 404, as if it did not exist")
        void otherProviderGetsNotFound() throws Exception {
            // Arrange
            Provider mine = registered();
            Provider theirs = registered();

            // Act
            MockHttpServletResponse response = call(get("/api/v1/organizations/{id}", theirs.organizationId())
                    .header("Authorization", ownerToken(mine)));

            // Assert
            assertEquals(404, response.getStatus());
            assertEquals("RESOURCE_NOT_FOUND", errorCodeOf(response));
        }

        @Test
        @DisplayName("HU-022: staff of the provider cannot change its rules: 403 naming the role")
        void staffCannotChangeTheRules() throws Exception {
            // Arrange
            Provider provider = registered();
            UUID staff = UUID.randomUUID();
            jdbc.update("insert into organization_members (organization_id, user_id, role) values (?::uuid, ?, 'STAFF')",
                    provider.organizationId(), staff);

            // Act
            MockHttpServletResponse response = changePolicy(provider, bearer(staff, "recepcion@example.com", "PROVIDER"),
                    120, 20160);

            // Assert
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("STAFF"), response.getContentAsString());
        }

        @Test
        @DisplayName("HU-022: a client cannot use the provider endpoints at all: 403")
        void clientIsDenied() throws Exception {
            // Act
            MockHttpServletResponse response = call(get("/api/v1/organizations")
                    .header("Authorization", bearer(UUID.randomUUID(), "ana@example.com", "CLIENT")));

            // Assert
            assertEquals(403, response.getStatus());
        }

        @Test
        @DisplayName("After signing in, a provider finds its own business and only that one")
        void providerListsItsOwnBusiness() throws Exception {
            // Arrange
            Provider provider = registered();
            registered();

            // Act
            MockHttpServletResponse response = call(get("/api/v1/organizations")
                    .header("Authorization", ownerToken(provider)));

            // Assert
            assertEquals(200, response.getStatus());
            List<String> ids = JsonPath.read(response.getContentAsString(), "$[*].id");
            assertEquals(List.of(provider.organizationId()), ids);
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
