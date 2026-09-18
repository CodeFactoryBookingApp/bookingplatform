package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GoTrue answers OTP verification with a session carrying the user nested
 * ({"access_token": ..., "user": {...}}), not with the bare user object.
 * These tests lock that parsing contract.
 */
class GoTrueClientTest {

    private MockRestServiceServer server;
    private GoTrueClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder realBuilder = RestClient.builder().baseUrl("https://demo.supabase.co");
        server = MockRestServiceServer.bindTo(realBuilder).build();
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(realBuilder.build());
        client = new GoTrueClient(new SupabaseProperties("https://demo.supabase.co", "secret"), builder);
    }

    @Test
    @DisplayName("verifyEmailToken reads id/email from the nested session user")
    void verifyEmailTokenReadsNestedUser() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://demo.supabase.co/verify"))
                .andRespond(withSuccess("""
                        {"access_token":"jwt","user":{"id":"%s","email":"ana.perez@example.com"}}
                        """.formatted(id), MediaType.APPLICATION_JSON));

        ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

        assertEquals(id, confirmed.userId());
        assertEquals("ana.perez@example.com", confirmed.email());
        server.verify();
    }

    @Test
    @DisplayName("verifyEmailToken still supports a bare user object response")
    void verifyEmailTokenSupportsBareUser() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://demo.supabase.co/verify"))
                .andRespond(withSuccess("""
                        {"id":"%s","email":"ana.perez@example.com"}
                        """.formatted(id), MediaType.APPLICATION_JSON));

        ConfirmedUser confirmed = client.verifyEmailToken("token-hash");

        assertEquals(id, confirmed.userId());
        assertEquals("ana.perez@example.com", confirmed.email());
        server.verify();
    }
}
