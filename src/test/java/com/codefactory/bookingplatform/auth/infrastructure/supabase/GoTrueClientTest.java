package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contract tests for the Supabase Auth (GoTrue) adapter.
 *
 * <p>GoTrue answers OTP verification with a session carrying the user nested
 * ({"access_token": ..., "user": {...}}), not with the bare user object.
 * These tests lock that parsing contract, the header/security contract
 * (the secret key must only travel to admin endpoints) and the full decision
 * table that translates provider failures into {@link UpstreamAuthError}.
 */
class GoTrueClientTest {

    private static final String BASE = "https://demo.supabase.co";
    private static final String SECRET = "secret";

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

    // ---------------------------------------------------------------------
    // Endpoint catalogue: lets the error matrices drive every operation
    // through the same table without duplicating the request plumbing.
    // ---------------------------------------------------------------------

    enum Endpoint {
        CREATE_USER,
        DELETE_USER,
        TOKEN,
        VERIFY,
        RESET,
        RESEND,
        RECOVER,
        LOGOUT
    }

    private void invoke(Endpoint endpoint) {
        switch (endpoint) {
            case CREATE_USER -> client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);
            case DELETE_USER -> client.deleteUser(UUID.randomUUID());
            case TOKEN -> client.requestPasswordToken("ana.perez@example.com", "Secret123!");
            case VERIFY -> client.verifyEmailToken("token-hash");
            case RESET -> client.resetPasswordWithToken("token-hash", "NewSecret123!");
            case RESEND -> client.resendSignupVerification("ana.perez@example.com");
            case RECOVER -> client.sendPasswordRecovery("ana.perez@example.com");
            case LOGOUT -> client.signOut("user-access-token");
        }
    }

    /** The path each endpoint must call. Anchoring it here makes every row of the tables below
     *  verify the operation as well as the classification: without it the matcher accepts any
     *  request and an endpoint calling the wrong path would go unnoticed. */
    private static String pathOf(Endpoint endpoint) {
        return switch (endpoint) {
            case CREATE_USER, DELETE_USER -> "/admin/users";
            case TOKEN -> "/token";
            case VERIFY, RESET -> "/verify";
            case RESEND -> "/resend";
            case RECOVER -> "/recover";
            case LOGOUT -> "/logout";
        };
    }

    /** Arrange: the provider answers the endpoint's request with the given status and body.
     *  The request is only matched when it targets {@link #pathOf(Endpoint)}, so a call to the
     *  wrong path fails the test instead of being classified. */
    private void providerFailsWith(Endpoint endpoint, int status, String body) {
        String esperado = pathOf(endpoint);
        server.expect(request -> assertTrue(request.getURI().getPath().startsWith(esperado),
                        () -> endpoint + " called " + request.getURI().getPath() + " instead of " + esperado))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body(body));
    }

    /** Assert: the call failed with an upstream error and the expected request was really sent. */
    private UpstreamAuthException assertUpstreamFailure(Executable act) {
        UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
        server.verify();
        return ex;
    }

    static Stream<Arguments> endpointContexts() {
        return Stream.of(
                Arguments.of(Endpoint.CREATE_USER, "createUser"),
                Arguments.of(Endpoint.DELETE_USER, "deleteUser"),
                Arguments.of(Endpoint.TOKEN, "token"),
                Arguments.of(Endpoint.VERIFY, "verify"),
                // El reseteo comparte el endpoint /verify, y por tanto su contexto.
                Arguments.of(Endpoint.RESET, "verify"),
                Arguments.of(Endpoint.RESEND, "resend"),
                Arguments.of(Endpoint.RECOVER, "recover"),
                Arguments.of(Endpoint.LOGOUT, "logout"));
    }

    @ParameterizedTest(name = "{0} classifies its failures under the \"{1}\" context")
    @MethodSource("endpointContexts")
    @DisplayName("Each endpoint hands the mapper its own context, which is what decides the classification")
    void everyEndpointCarriesItsOwnErrorContext(Endpoint endpoint, String context) {
        // El contexto es un literal que decide cómo se traduce el fallo: con "token" un 400 es
        // INVALID_CREDENTIALS y con "verify" es TOKEN_INVALID. Las tablas de abajo aseveran el
        // error resultante, pero hay combinaciones donde dos contextos coinciden, así que un
        // literal intercambiado puede pasar inadvertido. Un 502 no lo mapea ninguna regla y cae
        // en la rama por defecto, que es la única que nombra el contexto: eso lo ancla.
        // Arrange
        providerFailsWith(endpoint, 502, "");

        // Act
        Executable act = () -> invoke(endpoint);

        // Assert
        UpstreamAuthException ex = assertUpstreamFailure(act);
        assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
        assertTrue(ex.getMessage().contains("in " + context + ": HTTP 502"),
                () -> "expected the " + context + " context and the status in: " + ex.getMessage());
    }

    // ---------------------------------------------------------------------
    // mapError: decision table (black box)
    // ---------------------------------------------------------------------

    static Stream<Arguments> errorDecisionTable() {
        return Stream.of(
                // --- rule 1: HTTP 429 wins over every body and every context ---
                Arguments.of(Endpoint.TOKEN, 429, "", UpstreamAuthError.RATE_LIMITED),
                Arguments.of(Endpoint.CREATE_USER, 429, "{\"msg\":\"user not found\"}", UpstreamAuthError.RATE_LIMITED),
                Arguments.of(Endpoint.VERIFY, 429, "{\"msg\":\"token has expired\"}", UpstreamAuthError.RATE_LIMITED),
                Arguments.of(Endpoint.RECOVER, 429, "{\"msg\":\"email not confirmed\"}", UpstreamAuthError.RATE_LIMITED),

                // --- rule 2: body says the email is not confirmed ---
                Arguments.of(Endpoint.TOKEN, 400, "{\"msg\":\"Email not confirmed\"}", UpstreamAuthError.EMAIL_NOT_CONFIRMED),
                Arguments.of(Endpoint.TOKEN, 400, "{\"error_code\":\"email_not_confirmed\"}", UpstreamAuthError.EMAIL_NOT_CONFIRMED),
                // body matching is case insensitive (toLowerCase(Locale.ROOT))
                Arguments.of(Endpoint.TOKEN, 401, "{\"msg\":\"EMAIL NOT CONFIRMED\"}", UpstreamAuthError.EMAIL_NOT_CONFIRMED),

                // --- rule 3: body says the user already exists ---
                Arguments.of(Endpoint.CREATE_USER, 422, "{\"msg\":\"User already exists\"}", UpstreamAuthError.USER_ALREADY_EXISTS),
                Arguments.of(Endpoint.CREATE_USER, 422, "{\"error_code\":\"user_exists\"}", UpstreamAuthError.USER_ALREADY_EXISTS),
                Arguments.of(Endpoint.CREATE_USER, 400, "{\"error_code\":\"email_exists\"}", UpstreamAuthError.USER_ALREADY_EXISTS),

                // --- rule 4: body says not found, OR the status is 404 ---
                Arguments.of(Endpoint.DELETE_USER, 400, "{\"msg\":\"User not found\"}", UpstreamAuthError.USER_NOT_FOUND),
                Arguments.of(Endpoint.DELETE_USER, 404, "", UpstreamAuthError.USER_NOT_FOUND),
                Arguments.of(Endpoint.RECOVER, 404, "", UpstreamAuthError.USER_NOT_FOUND),

                // --- rule 5: body says expired ---
                Arguments.of(Endpoint.VERIFY, 400, "{\"msg\":\"Token has expired\"}", UpstreamAuthError.TOKEN_EXPIRED),
                // the body beats the context: a 401 on /token that mentions an expired
                // session is reported as TOKEN_EXPIRED, not INVALID_CREDENTIALS
                Arguments.of(Endpoint.TOKEN, 401, "{\"msg\":\"session expired\"}", UpstreamAuthError.TOKEN_EXPIRED),

                // --- rule 6: context "token" ---
                Arguments.of(Endpoint.TOKEN, 400, "{\"msg\":\"Invalid login credentials\"}", UpstreamAuthError.INVALID_CREDENTIALS),
                Arguments.of(Endpoint.TOKEN, 401, "", UpstreamAuthError.INVALID_CREDENTIALS),
                Arguments.of(Endpoint.TOKEN, 403, "", UpstreamAuthError.UNAVAILABLE),

                // --- rule 7: context "verify" (shared by verifyEmailToken and resetPasswordWithToken) ---
                Arguments.of(Endpoint.VERIFY, 400, "", UpstreamAuthError.TOKEN_INVALID),
                Arguments.of(Endpoint.VERIFY, 403, "", UpstreamAuthError.TOKEN_INVALID),
                Arguments.of(Endpoint.RESET, 400, "", UpstreamAuthError.TOKEN_INVALID),
                Arguments.of(Endpoint.RESET, 403, "{\"msg\":\"otp_disabled\"}", UpstreamAuthError.TOKEN_INVALID),
                Arguments.of(Endpoint.VERIFY, 401, "", UpstreamAuthError.UNAVAILABLE),

                // --- rule 8: any other context falls through to UNAVAILABLE ---
                // (5xx answers of every context are anchored by everyEndpointCarriesItsOwnErrorContext)
                Arguments.of(Endpoint.CREATE_USER, 400, "", UpstreamAuthError.UNAVAILABLE),
                Arguments.of(Endpoint.CREATE_USER, 401, "", UpstreamAuthError.UNAVAILABLE),
                Arguments.of(Endpoint.RESEND, 400, "", UpstreamAuthError.UNAVAILABLE)
        );
    }

    @ParameterizedTest(name = "[{index}] {0} HTTP {1} body={2} -> {3}")
    @MethodSource("errorDecisionTable")
    @DisplayName("Provider failures are translated into the agreed UpstreamAuthError catalogue")
    void mapsProviderFailuresToTheErrorCatalogue(Endpoint endpoint, int status, String body, UpstreamAuthError expected) {
        // Arrange
        providerFailsWith(endpoint, status, body);

        // Act
        Executable act = () -> invoke(endpoint);

        // Assert
        UpstreamAuthException ex = assertUpstreamFailure(act);
        assertEquals(expected, ex.error());
    }

    @ParameterizedTest(name = "[{index}] HTTP {1} + body \"{2}\" -> {3}")
    @MethodSource("precedenceTable")
    @DisplayName("Rule precedence in the error table is first-match-wins, not most-specific-wins")
    void errorTableIsFirstMatchWins(Endpoint endpoint, int status, String body, UpstreamAuthError expected) {
        // Arrange
        providerFailsWith(endpoint, status, body);

        // Act
        Executable act = () -> invoke(endpoint);

        // Assert
        UpstreamAuthException ex = assertUpstreamFailure(act);
        assertEquals(expected, ex.error());
    }

    static Stream<Arguments> precedenceTable() {
        return Stream.of(
                // 429 beats an "already exists" body.
                Arguments.of(Endpoint.CREATE_USER, 429, "{\"msg\":\"User already exists\"}", UpstreamAuthError.RATE_LIMITED),
                // "email not confirmed" beats "user not found" when both appear.
                Arguments.of(Endpoint.TOKEN, 400, "{\"msg\":\"email not confirmed\",\"hint\":\"user not found\"}",
                        UpstreamAuthError.EMAIL_NOT_CONFIRMED),
                // P4: GoTrue answers 404 to an expired OTP. The "expired" rule is checked
                // before the 404 one, so a stale verification link is TOKEN_EXPIRED and the
                // user is told the link expired, not that the account does not exist.
                Arguments.of(Endpoint.VERIFY, 404, "{\"msg\":\"Token has expired\"}", UpstreamAuthError.TOKEN_EXPIRED),
                // P5: a bare 404 on /verify is about the one-time token, not about a user,
                // so it reaches the "verify" branch and is reported as TOKEN_INVALID.
                Arguments.of(Endpoint.VERIFY, 404, "", UpstreamAuthError.TOKEN_INVALID),
                // "not found" in the body beats the token context: a 400 on /token whose body
                // mentions a missing user is USER_NOT_FOUND, not INVALID_CREDENTIALS.
                Arguments.of(Endpoint.TOKEN, 400, "{\"msg\":\"User not found\"}", UpstreamAuthError.USER_NOT_FOUND),
                // The password reset shares the /verify context, so its 404 is classified the same way.
                Arguments.of(Endpoint.RESET, 404, "", UpstreamAuthError.TOKEN_INVALID),
                // The 404 exemption is about the status alone: an explicit "not found" body on
                // /verify is still USER_NOT_FOUND.
                Arguments.of(Endpoint.VERIFY, 404, "{\"msg\":\"User not found\"}", UpstreamAuthError.USER_NOT_FOUND)
                // That the exemption is scoped to /verify (an admin 404 keeps reporting a missing user)
                // is the DELETE_USER 404 row of the decision table.
        );
    }

    @ParameterizedTest(name = "[{index}] {0} with the provider down -> UNAVAILABLE")
    @EnumSource(Endpoint.class)
    @DisplayName("Every operation degrades to UNAVAILABLE when the provider is unreachable")
    void networkFailureDegradesToUnavailable(Endpoint endpoint) {
        // Arrange
        String esperado = pathOf(endpoint);
        server.expect(request -> assertTrue(request.getURI().getPath().startsWith(esperado),
                        () -> endpoint + " called " + request.getURI().getPath() + " instead of " + esperado))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        // Act
        Executable act = () -> invoke(endpoint);

        // Assert
        UpstreamAuthException ex = assertUpstreamFailure(act);
        assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
        assertEquals("Identity provider is unreachable", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause().getCause());
    }

    // ---------------------------------------------------------------------
    // Header / security contract
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Secret key exposure")
    class HeaderContract {

        @Test
        @DisplayName("Admin endpoints authenticate with apikey AND a bearer secret key")
        void adminEndpointsSendApiKeyAndBearerSecret() {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/admin/users"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("apikey", SECRET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET))
                    .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(id), MediaType.APPLICATION_JSON));

            // Act
            client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("deleteUser targets the user id on the admin endpoint with the admin headers")
        void deleteUserUsesAdminHeaders() {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/admin/users/" + id))
                    .andExpect(method(HttpMethod.DELETE))
                    .andExpect(header("apikey", SECRET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET))
                    .andRespond(withSuccess());

            // Act
            client.deleteUser(id);

            // Assert
            server.verify();
        }

        @ParameterizedTest(name = "[{index}] {0} must not leak the secret key in Authorization")
        @CsvSource({
                "TOKEN,   /token?grant_type=password",
                "VERIFY,  /verify",
                "RESET,   /verify",
                "RESEND,  /resend",
                "RECOVER, /recover"
        })
        @DisplayName("Public endpoints send only the apikey: the secret key must never be bearer-exposed")
        void publicEndpointsSendOnlyTheApiKey(Endpoint endpoint, String path) {
            // Arrange
            server.expect(requestTo(BASE + path))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("apikey", SECRET))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess("""
                            {"access_token":"jwt","refresh_token":"r","user":{"id":"%s","email":"ana.perez@example.com"}}
                            """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

            // Act
            invoke(endpoint);

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("signOut authenticates as the user: bearer is the session token, never the secret key")
        void signOutSendsTheUserAccessTokenAsBearer() {
            // Arrange
            server.expect(requestTo(BASE + "/logout"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("apikey", SECRET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-access-token"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(204)));

            // Act
            client.signOut("user-access-token");

            // Assert
            server.verify();
        }
    }

    // ---------------------------------------------------------------------
    // createUser
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("Users are provisioned unconfirmed so the email verification flow stays mandatory")
        void requestsAnUnconfirmedUserWithTheRequestedRole() {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/admin/users"))
                    .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                    .andExpect(jsonPath("$.password").value("Secret123!"))
                    .andExpect(jsonPath("$.email_confirm").value(false))
                    .andExpect(jsonPath("$.app_metadata.role").value("PROVIDER"))
                    .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(id), MediaType.APPLICATION_JSON));

            // Act
            UUID created = client.createUser("ana.perez@example.com", "Secret123!", AppRole.PROVIDER);

            // Assert
            assertEquals(id, created);
            server.verify();
        }

        @Test
        @DisplayName("A response without an id is an unusable provider answer: UNAVAILABLE")
        void missingIdFieldIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/admin/users"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("missing field: id"), ex.getMessage());
        }

        @Test
        @DisplayName("An explicit null id is treated exactly like a missing id")
        void nullIdFieldIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/admin/users"))
                    .andRespond(withSuccess("{\"id\":null}", MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("missing field: id"), ex.getMessage());
        }

        @Test
        @DisplayName("A malformed id is reported as UNAVAILABLE, not as a raw IllegalArgumentException")
        void malformedIdIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/admin/users"))
                    .andRespond(withSuccess("{\"id\":\"not-a-uuid\"}", MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("not a valid UUID"), ex.getMessage());
            assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        }

        @Test
        @DisplayName("An empty body (no payload at all) is reported as UNAVAILABLE, not as a crash")
        void emptyBodyIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/admin/users")).andRespond(withSuccess());

            // Act
            Executable act = () -> client.createUser("ana.perez@example.com", "Secret123!", AppRole.CLIENT);

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("missing field: id"), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // requestPasswordToken
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("requestPasswordToken")
    class RequestPasswordToken {

        @Test
        @DisplayName("A successful login returns the provider session verbatim")
        void returnsTheProviderSession() {
            // Arrange
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                    .andExpect(jsonPath("$.password").value("Secret123!"))
                    .andRespond(withSuccess("""
                            {"access_token":"jwt","refresh_token":"refresh","token_type":"Bearer","expires_in":7200}
                            """, MediaType.APPLICATION_JSON));

            // Act
            AuthTokens tokens = client.requestPasswordToken("ana.perez@example.com", "Secret123!");

            // Assert
            assertEquals("jwt", tokens.accessToken());
            assertEquals("refresh", tokens.refreshToken());
            assertEquals("Bearer", tokens.tokenType());
            assertEquals(7200L, tokens.expiresIn());
            server.verify();
        }

        @Test
        @DisplayName("A session without token_type/expires_in falls back to bearer and one hour")
        void appliesDefaultsWhenTheProviderOmitsTokenTypeAndExpiry() {
            // Arrange
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andRespond(withSuccess("""
                            {"access_token":"jwt","refresh_token":"refresh"}
                            """, MediaType.APPLICATION_JSON));

            // Act
            AuthTokens tokens = client.requestPasswordToken("ana.perez@example.com", "Secret123!");

            // Assert
            assertEquals("bearer", tokens.tokenType());
            assertEquals(3600L, tokens.expiresIn());
        }

        @ParameterizedTest(name = "[{index}] missing {0} -> UNAVAILABLE")
        @CsvSource({
                "access_token,  {\"refresh_token\":\"refresh\"}",
                "refresh_token, {\"access_token\":\"jwt\"}"
        })
        @DisplayName("A session missing either token is an unusable provider answer: UNAVAILABLE")
        void missingTokenFieldsAreReportedAsUnavailable(String missingField, String body) {
            // Arrange
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.requestPasswordToken("ana.perez@example.com", "Secret123!");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("missing field: " + missingField), ex.getMessage());
        }

        @Test
        @DisplayName("A non numeric expires_in is an unusable provider answer: UNAVAILABLE")
        void nonNumericExpiresInIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andRespond(withSuccess("""
                            {"access_token":"jwt","refresh_token":"refresh","expires_in":"never"}
                            """, MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.requestPasswordToken("ana.perez@example.com", "Secret123!");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("expires_in"), ex.getMessage());
            assertInstanceOf(NumberFormatException.class, ex.getCause());
        }

        @Test
        @DisplayName("A decimal expires_in (valid JSON number) is reported as UNAVAILABLE too")
        void decimalExpiresInIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andRespond(withSuccess("""
                            {"access_token":"jwt","refresh_token":"refresh","expires_in":3600.0}
                            """, MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.requestPasswordToken("ana.perez@example.com", "Secret123!");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("expires_in"), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // verify: verifyEmailToken / resetPasswordWithToken
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("verifyEmailToken reads id/email from the nested session user")
    void verifyEmailTokenReadsNestedUser() {
        // Arrange
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://demo.supabase.co/verify"))
                .andRespond(withSuccess("""
                        {"access_token":"jwt","user":{"id":"%s","email":"ana.perez@example.com"}}
                        """.formatted(id), MediaType.APPLICATION_JSON));

        // Act
        ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

        // Assert
        assertEquals(id, confirmed.userId());
        assertEquals("ana.perez@example.com", confirmed.email());
        server.verify();
    }

    @Test
    @DisplayName("verifyEmailToken still supports a bare user object response")
    void verifyEmailTokenSupportsBareUser() {
        // Arrange
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://demo.supabase.co/verify"))
                .andRespond(withSuccess("""
                        {"id":"%s","email":"ana.perez@example.com"}
                        """.formatted(id), MediaType.APPLICATION_JSON));

        // Act
        ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

        // Assert
        assertEquals(id, confirmed.userId());
        assertEquals("ana.perez@example.com", confirmed.email());
        server.verify();
    }

    @Nested
    @DisplayName("verify endpoint")
    class Verify {

        @Test
        @DisplayName("Email verification posts the OTP hash with type=email and omits the password key entirely")
        void emailVerificationSendsTypeEmailWithoutPassword() {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/verify"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.token_hash").value("token-hash"))
                    .andExpect(jsonPath("$.type").value("email"))
                    // jsonPath(...).doesNotExist() also accepts "password": null; the raw body check does not
                    .andExpect(content().string(not(containsString("password"))))
                    .andRespond(withSuccess("{\"id\":\"%s\",\"email\":\"ana.perez@example.com\"}".formatted(id),
                            MediaType.APPLICATION_JSON));

            // Act
            client.verifyEmailToken("token-hash");

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("A password reset posts the OTP hash with type=recovery and the new password")
        void passwordResetSendsTypeRecoveryWithThePassword() {
            // Arrange
            server.expect(requestTo(BASE + "/verify"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.token_hash").value("token-hash"))
                    .andExpect(jsonPath("$.type").value("recovery"))
                    .andExpect(jsonPath("$.password").value("NewSecret123!"))
                    .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(UUID.randomUUID()),
                            MediaType.APPLICATION_JSON));

            // Act
            client.resetPasswordWithToken("token-hash", "NewSecret123!");

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("A 200 with no payload is handled as an empty session, not as a null pointer")
        void emptyVerifyResponseIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/verify")).andRespond(withSuccess());

            // Act
            Executable act = () -> client.verifyEmailToken("token-hash");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("missing field: id"), ex.getMessage());
        }

        @Test
        @DisplayName("A confirmed user without email still resolves: the id is what the caller consumes")
        void missingEmailStillResolvesTheUser() {
            // El correo no lo lee nadie: ConfirmEmailUseCase busca al cliente por userId.
            // Exigirlo convertiria una respuesta sin ese campo en un 502 por un valor que
            // se descarta, asi que se tolera su ausencia y se devuelve null, nunca "null".
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/verify"))
                    .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(id), MediaType.APPLICATION_JSON));

            // Act
            ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

            // Assert
            assertEquals(id, confirmed.userId());
            assertNull(confirmed.email(), "a missing email must never become the string \"null\"");
        }

        @Test
        @DisplayName("An explicit null email is treated exactly like a missing one, never as the string \"null\"")
        void nullEmailNeverBecomesTheStringNull() {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + "/verify"))
                    .andRespond(withSuccess("{\"id\":\"%s\",\"email\":null}".formatted(id),
                            MediaType.APPLICATION_JSON));

            // Act
            ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

            // Assert
            assertEquals(id, confirmed.userId());
            assertNull(confirmed.email(), "String.valueOf(null) would have produced the 4-char string \"null\"");
        }

        @Test
        @DisplayName("A malformed user id is reported as UNAVAILABLE, not as a raw IllegalArgumentException")
        void malformedUserIdIsReportedAsUnavailable() {
            // Arrange
            server.expect(requestTo(BASE + "/verify"))
                    .andRespond(withSuccess("{\"id\":\"not-a-uuid\",\"email\":\"ana.perez@example.com\"}",
                            MediaType.APPLICATION_JSON));

            // Act
            Executable act = () -> client.verifyEmailToken("token-hash");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.UNAVAILABLE, ex.error());
            assertTrue(ex.getMessage().contains("not a valid UUID"), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // resend / recover
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("resend and recover")
    class ResendAndRecover {

        @Test
        @DisplayName("Resending a verification asks GoTrue for a signup type email")
        void resendSendsTheSignupType() {
            // Arrange
            server.expect(requestTo(BASE + "/resend"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                    .andExpect(jsonPath("$.type").value("signup"))
                    .andRespond(withSuccess());

            // Act
            client.resendSignupVerification("ana.perez@example.com");

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("Password recovery only sends the email, never the current password")
        void recoverSendsOnlyTheEmail() {
            // Arrange
            server.expect(requestTo(BASE + "/recover"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andRespond(withSuccess());

            // Act
            client.sendPasswordRecovery("ana.perez@example.com");

            // Assert
            server.verify();
        }
    }

    // ---------------------------------------------------------------------
    // signOut
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("signOut")
    class SignOut {

        @ParameterizedTest(name = "[{index}] HTTP {0} -> TOKEN_INVALID")
        @CsvSource({"401", "403", "404"})
        @DisplayName("Logging out with a dead session is reported as TOKEN_INVALID, never as USER_NOT_FOUND")
        void deadSessionIsTokenInvalid(int status) {
            // Arrange
            server.expect(requestTo(BASE + "/logout"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("{\"msg\":\"user not found\"}"));

            // Act
            Executable act = () -> client.signOut("stale-token");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertEquals(UpstreamAuthError.TOKEN_INVALID, ex.error());
            assertTrue(ex.getMessage().startsWith("Session is no longer valid"), ex.getMessage());
        }

        @ParameterizedTest(name = "[{index}] HTTP {0} -> {1}")
        @CsvSource({
                "429, RATE_LIMITED",
                "400, UNAVAILABLE"
        })
        @DisplayName("Any other logout failure falls back to the general error table")
        void otherLogoutFailuresUseTheGeneralTable(int status, UpstreamAuthError expected) {
            // Arrange
            providerFailsWith(Endpoint.LOGOUT, status, "");

            // Act
            Executable act = () -> invoke(Endpoint.LOGOUT);

            // Assert
            UpstreamAuthException ex = assertUpstreamFailure(act);
            assertEquals(expected, ex.error());
        }
    }

    // ---------------------------------------------------------------------
    // Request envelope: base url, content type and the exact shape of the
    // body. These are the parts a "did it call the endpoint" assertion never
    // looks at, and the parts a wrong value breaks silently in production.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Request envelope")
    class RequestEnvelope {

        @Test
        @DisplayName("Every call is rooted at the GoTrue path of the configured Supabase project")
        void clientIsRootedAtTheProjectAuthPath() {
            // Arrange
            RestClient.Builder builder = mock(RestClient.Builder.class);
            when(builder.baseUrl(anyString())).thenReturn(builder);
            when(builder.build()).thenReturn(RestClient.builder().build());

            // Act
            new GoTrueClient(new SupabaseProperties("https://other.supabase.co", SECRET), builder);

            // Assert
            verify(builder).baseUrl("https://other.supabase.co/auth/v1");
        }

        @ParameterizedTest(name = "[{index}] {0} declares application/json")
        @CsvSource({
                "CREATE_USER, /admin/users",
                "TOKEN,       /token?grant_type=password",
                "VERIFY,      /verify",
                "RESET,       /verify",
                "RESEND,      /resend",
                "RECOVER,     /recover"
        })
        @DisplayName("Every operation with a JSON body announces it with an explicit Content-Type")
        void jsonBodiesDeclareTheContentType(Endpoint endpoint, String path) {
            // Arrange
            UUID id = UUID.randomUUID();
            server.expect(requestTo(BASE + path))
                    .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                    .andRespond(withSuccess("""
                            {"id":"%s","access_token":"jwt","refresh_token":"r","user":{"id":"%s","email":"ana.perez@example.com"}}
                            """.formatted(id, id), MediaType.APPLICATION_JSON));

            // Act
            invoke(endpoint);

            // Assert
            server.verify();
        }

        @Test
        @DisplayName("A dead session reports what the provider answered, so the cause is not lost")
        void deadSessionKeepsTheUpstreamDetail() {
            // Arrange
            server.expect(requestTo(BASE + "/logout"))
                    .andRespond(withStatus(HttpStatusCode.valueOf(401)).body("{\"msg\":\"token already revoked\"}"));

            // Act
            Executable act = () -> client.signOut("stale-token");

            // Assert
            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class, act);
            assertTrue(ex.getMessage().contains("token already revoked"), ex.getMessage());
        }
    }
}
