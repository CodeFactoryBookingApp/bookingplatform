package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HU-022 / ADR-0009: reading a user's platform role from GoTrue ({@code app_metadata.role}).
 * Equivalence partitioning on the role value (known in any case / unknown / absent) and on the
 * provider answer (malformed body, 404, 429, 5xx).
 */
class GoTruePlatformUsersTest {

    private static final String BASE = "https://demo.supabase.co";
    private static final String SECRET = "secret";
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private MockRestServiceServer server;
    private GoTrueClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder realBuilder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(realBuilder).build();
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(realBuilder.build());
        client = new GoTrueClient(new SupabaseProperties(BASE, SECRET), builder);
    }

    private void userResponds(String json) {
        server.expect(requestTo(BASE + "/admin/users/" + USER)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("ADR-0009: the user is read with the admin key and its status dates are kept for the activity check")
    void readsTheUserWithItsStatusDates() {
        // Arrange
        userResponds("""
                {"id":"%s","app_metadata":{"role":"ADMIN"},"confirmed_at":"2026-11-12T09:30:00Z",
                 "banned_until":"2026-11-30T00:00:00Z","deleted_at":"2026-11-20T00:00:00Z"}
                """.formatted(USER));

        // Act
        PlatformUser user = client.getPlatformUser(USER);

        // Assert
        assertEquals(new PlatformUser(USER, AppRole.ADMIN, Instant.parse("2026-11-12T09:30:00Z"),
                Instant.parse("2026-11-30T00:00:00Z"), Instant.parse("2026-11-20T00:00:00Z")), user);
        server.verify();
    }

    @ParameterizedTest(name = "[{index}] app_metadata={0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "{\"role\":\"admin\"}      | ADMIN",
            "{\"role\":\"Provider\"}   | PROVIDER",
            "{\"role\":\"superuser\"}  | ",
            "{}                        | ",
            "null                      | "
    })
    @DisplayName("ADR-0009: the role is matched ignoring case; an unknown or missing role is no platform role (never ADMIN)")
    void roleIsMatchedIgnoringCase(String metadata, AppRole expected) {
        // Arrange
        userResponds("{\"id\":\"%s\",\"app_metadata\":%s}".formatted(USER, metadata));

        // Act
        PlatformUser user = client.getPlatformUser(USER);

        // Assert
        assertEquals(expected, user.role());
    }

    @Test
    @DisplayName("ADR-0009: a user without id in the answer is a malformed response (UNAVAILABLE), not a user")
    void userWithoutIdIsUnavailable() {
        // Arrange
        userResponds("{\"app_metadata\":{\"role\":\"ADMIN\"}}");

        // Act
        UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, () -> client.getPlatformUser(USER));

        // Assert
        assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
    }

    @Test
    @DisplayName("ADR-0009: a user list without \"users\" is a malformed response (UNAVAILABLE), never an empty list that would hide administrators")
    void listWithoutUsersIsUnavailable() {
        // Arrange
        server.expect(requestTo(BASE + "/admin/users?page=1&per_page=50"))
                .andRespond(withSuccess("{\"aud\":\"authenticated\"}", MediaType.APPLICATION_JSON));

        // Act
        UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, () -> client.listPlatformUsers(1, 50));

        // Assert
        assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
    }

    @ParameterizedTest(name = "[{index}] HTTP {0} -> {1}")
    @CsvSource({"404, USER_NOT_FOUND", "429, RATE_LIMITED", "500, UNAVAILABLE"})
    @DisplayName("HU-022: provider failures when reading a user keep the error catalogue (404 is a missing user here)")
    void readFailuresAreClassified(int status, UpstreamAuthError expected) {
        // Arrange
        server.expect(requestTo(BASE + "/admin/users/" + USER)).andRespond(withStatus(HttpStatusCode.valueOf(status)));

        // Act
        UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, () -> client.getPlatformUser(USER));

        // Assert
        assertEquals(expected, ex.error());
    }
}
