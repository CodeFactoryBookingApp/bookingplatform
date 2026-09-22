package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.infrastructure.supabase.GoTrueClient;
import com.codefactory.bookingplatform.shared.config.SecurityConfig;
import com.codefactory.bookingplatform.shared.config.SecurityProperties;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * The failure mode of starting without {@code SUPABASE_SECRET_KEY}, pinned end
 * to end: the application starts, the adapter is built, the request leaves with
 * an empty credential and only then does the call fail, in front of whoever is
 * watching.
 *
 * <p>The last test of the first group is the one that matters for an operator:
 * the resulting error reaching the caller is {@code INVALID_CREDENTIALS}, which
 * reads as "wrong password" and points the diagnosis away from the real cause.
 */
class MissingSupabaseCredentialTest {

    private static final String BASE = "https://demo.supabase.co";
    private static final String NO_KEY = "";

    private MockRestServiceServer server;
    private GoTrueClient clientWithoutCredential;

    @BeforeEach
    void setUp() {
        RestClient.Builder realBuilder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(realBuilder).build();
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(realBuilder.build());
        clientWithoutCredential = new GoTrueClient(new SupabaseProperties(BASE, NO_KEY), builder);
    }

    @Nested
    @DisplayName("The identity adapter accepts an empty secret key")
    class AdapterIsBuiltAnyway {

        @Test
        @DisplayName("RISK: building the adapter with an empty secret key does not fail, so nothing warns at startup")
        void adapterIsBuiltWithoutCredential() {
            assertNotNull(clientWithoutCredential);
        }

        @Test
        @DisplayName("RISK: the login call leaves with an empty apikey header instead of being refused locally")
        void loginRequestCarriesAnEmptyApiKey() {
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("apikey", ""))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"message\":\"Invalid API key\"}"));

            assertThrows(UpstreamAuthException.class,
                    () -> clientWithoutCredential.requestPasswordToken("ana.perez@example.com", "Str0ng!Pass"));
            server.verify();
        }

        @Test
        @DisplayName("RISK: Supabase answering 401 to an unauthenticated login is reported as INVALID_CREDENTIALS")
        void missingCredentialLooksLikeAWrongPassword() {
            server.expect(requestTo(BASE + "/token?grant_type=password"))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"message\":\"Invalid API key\"}"));

            UpstreamAuthException failure = assertThrows(UpstreamAuthException.class,
                    () -> clientWithoutCredential.requestPasswordToken("ana.perez@example.com", "Str0ng!Pass"));

            assertEquals(UpstreamAuthError.INVALID_CREDENTIALS, failure.error(),
                    "a missing deployment credential is reported to the user as a wrong password");
        }

        @Test
        @DisplayName("Registration fails with UNAVAILABLE instead, so the same root cause shows two different faces")
        void registrationReportsUnavailable() {
            server.expect(requestTo(BASE + "/admin/users"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("apikey", ""))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer "))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"message\":\"Invalid API key\"}"));

            UpstreamAuthException failure = assertThrows(UpstreamAuthException.class,
                    () -> clientWithoutCredential.createUser("ana.perez@example.com", "Str0ng!Pass", AppRole.CLIENT));

            assertEquals(UpstreamAuthError.UNAVAILABLE, failure.error());
            server.verify();
        }
    }

    @Nested
    @DisplayName("The JWT decoder accepts an unreachable JWKS URI")
    class JwtDecoderIsBuiltAnyway {

        private final SecurityConfig config = new SecurityConfig(
                new SecurityProperties("https://placeholder.supabase.co/auth/v1",
                        "https://placeholder.supabase.co/auth/v1/.well-known/jwks.json"),
                new ObjectMapper());

        @Test
        @DisplayName("RISK: the decoder is built without ever contacting the JWKS endpoint")
        void decoderIsBuiltWithoutContactingTheJwksEndpoint() {
            assertDoesNotThrow(config::jwtDecoder,
                    "a wrong SUPABASE_URL is invisible until the first token arrives");
        }

        @Test
        @DisplayName("The misconfiguration only surfaces when a token is decoded")
        void failureSurfacesOnFirstDecode() {
            JwtDecoder decoder = config.jwtDecoder();

            assertThrows(JwtException.class, () -> decoder.decode("not-a-real-token"));
        }
    }
}
