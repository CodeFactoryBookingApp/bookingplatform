package com.codefactory.bookingplatform.identity;

import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientJpaRepository;
import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationFlowIT extends PostgresIntegrationTestBase {

    private static final UUID FIXED_USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientJpaRepository clientJpaRepository;

    @MockitoBean
    private IdentityProviderPort identityProviderPort;

    @BeforeEach
    void cleanDatabase() {
        clientJpaRepository.deleteAll();
    }

    private String validPayload() {
        return """
                {
                  "fullName": "Ana Maria Perez",
                  "document": "CC-1020304050",
                  "birthDate": "1995-04-10",
                  "email": "ana.perez@example.com",
                  "phone": "+573001234567",
                  "city": "Bogota",
                  "notificationChannel": "EMAIL",
                  "password": "Str0ng!Pass"
                }
                """;
    }

    @Test
    @DisplayName("HU-001 AC: successful registration of an adult client leaves it PENDING_VERIFICATION")
    void registerAdultClientSuccessfully() throws Exception {
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(FIXED_USER_ID);

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(FIXED_USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        ClientEntity saved = clientJpaRepository.findById(FIXED_USER_ID).orElseThrow();
        assertEquals(ClientStatus.PENDING_VERIFICATION, saved.getStatus());
        assertEquals("ana.perez@example.com", saved.getEmail());
        verify(identityProviderPort).resendSignupVerification("ana.perez@example.com");
    }

    @Test
    @DisplayName("HU-001 AC: minors are rejected and no auth user is provisioned")
    void rejectMinorRegistration() throws Exception {
        String minorPayload = validPayload().replace("1995-04-10", LocalDate.now().minusYears(17).toString());

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minorPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MINOR_NOT_ALLOWED"));

        verify(identityProviderPort, never()).createUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("HU-001 AC: duplicated email is rejected with 409")
    void rejectDuplicateEmail() throws Exception {
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(FIXED_USER_ID);
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());

        UUID otherId = UUID.randomUUID();
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(otherId);
        String secondPayload = validPayload().replace("CC-1020304050", "CC-9999999999");

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("HU-001 AC: duplicated document is rejected with 409")
    void rejectDuplicateDocument() throws Exception {
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(FIXED_USER_ID);
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());

        UUID otherId = UUID.randomUUID();
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(otherId);
        String secondPayload = validPayload().replace("ana.perez@example.com", "otra@example.com");

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    @DisplayName("HU-001 AC: invalid email and phone formats are rejected with field details")
    void rejectInvalidFormats() throws Exception {
        String invalidPayload = validPayload()
                .replace("ana.perez@example.com", "not-an-email")
                .replace("+573001234567", "abc123");

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.phone").exists());

        verify(identityProviderPort, never()).createUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("HU-021 AC: password not meeting the policy is rejected")
    void rejectWeakPassword() throws Exception {
        String weakPayload = validPayload().replace("Str0ng!Pass", "weakpass");

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_TOO_WEAK"));

        verify(identityProviderPort, never()).createUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Upstream duplicated user maps to 409 DUPLICATE_EMAIL")
    void rejectWhenUpstreamReportsExistingUser() throws Exception {
        when(identityProviderPort.createUser(anyString(), anyString(), any()))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.USER_ALREADY_EXISTS, "exists"));

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));

        assertTrue(clientJpaRepository.count() == 0, "client profile must not be persisted");
    }

    @Test
    @DisplayName("HU-001 AC: resend verification is always 202 and only calls provider for known emails")
    void resendVerificationIsAcceptedWithoutLeakingExistence() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/verification-resends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(identityProviderPort, never()).resendSignupVerification(anyString());

        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(FIXED_USER_ID);
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/registrations/verification-resends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.perez@example.com\"}"))
                .andExpect(status().isAccepted());
        // once at registration, once at the explicit resend
        verify(identityProviderPort, times(2)).resendSignupVerification("ana.perez@example.com");
    }

    @Test
    @DisplayName("HU-001 AC: confirming with a valid one-time token activates the client")
    void confirmEmailActivatesClient() throws Exception {
        when(identityProviderPort.createUser(anyString(), anyString(), any())).thenReturn(FIXED_USER_ID);
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());

        when(identityProviderPort.verifyEmailToken("valid-token-hash"))
                .thenReturn(new ConfirmedUser(FIXED_USER_ID, "ana.perez@example.com"));

        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"valid-token-hash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertEquals(ClientStatus.ACTIVE, clientJpaRepository.findById(FIXED_USER_ID).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("HU-001 AC: invalid or expired verification token is rejected")
    void confirmEmailWithInvalidTokenIsRejected() throws Exception {
        when(identityProviderPort.verifyEmailToken("bad-token"))
                .thenThrow(new UpstreamAuthException(UpstreamAuthError.TOKEN_EXPIRED, "expired"));

        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"bad-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("All error responses carry a traceId for observability")
    void errorResponsesCarryTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload().replace("1995-04-10", LocalDate.now().minusYears(10).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(header().exists("X-Trace-Id"));
    }
}
