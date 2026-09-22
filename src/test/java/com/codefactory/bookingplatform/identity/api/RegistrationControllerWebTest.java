package com.codefactory.bookingplatform.identity.api;

import com.codefactory.bookingplatform.identity.application.ConfirmEmailUseCase;
import com.codefactory.bookingplatform.identity.application.RegisterClientCommand;
import com.codefactory.bookingplatform.identity.application.RegisterClientUseCase;
import com.codefactory.bookingplatform.identity.application.RegistrationOutcome;
import com.codefactory.bookingplatform.identity.application.ResendVerificationUseCase;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice for {@link RegistrationController}: HTTP contract (status codes,
 * response body, ProblemDetail mapping) with the use cases mocked away.
 * Security filters are off so that only the web adapter is under test.
 */
@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationControllerWebTest {

    private static final UUID CLIENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String REGISTRATIONS = "/api/v1/registrations";
    private static final String RESENDS = "/api/v1/registrations/verification-resends";
    private static final String CONFIRMATIONS = "/api/v1/registrations/email-verifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterClientUseCase registerClientUseCase;

    @MockitoBean
    private ResendVerificationUseCase resendVerificationUseCase;

    @MockitoBean
    private ConfirmEmailUseCase confirmEmailUseCase;

    /** Field name to raw JSON value; the insertion order is the declaration order of the DTO. */
    private static Map<String, String> validFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("fullName", "\"Ana Maria Perez\"");
        fields.put("document", "\"CC-1020304050\"");
        fields.put("birthDate", "\"1995-04-10\"");
        fields.put("email", "\"ana.perez@example.com\"");
        fields.put("phone", "\"+573001234567\"");
        fields.put("city", "\"Bogota\"");
        fields.put("notificationChannel", "\"EMAIL\"");
        fields.put("password", "\"Str0ng!Pass\"");
        return fields;
    }

    private static String json(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                .collect(Collectors.joining(",\n", "{\n", "\n}"));
    }

    private static String validRegistrationPayload() {
        return json(validFields());
    }

    private static String payloadWithout(String field) {
        Map<String, String> fields = validFields();
        fields.remove(field);
        return json(fields);
    }

    private static String payloadWith(String field, String rawJsonValue) {
        Map<String, String> fields = validFields();
        fields.put(field, rawJsonValue);
        return json(fields);
    }

    // ---------------------------------------------------------------- register

    @Test
    @DisplayName("POST /registrations answers 201 Created")
    void registerAnswers201() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com",
                        ClientStatus.PENDING_VERIFICATION));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /registrations returns clientId, email, status and message")
    void registerReturnsTheExpectedBody() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com",
                        ClientStatus.PENDING_VERIFICATION));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("SECURITY: the registration response never echoes the password back")
    void registerResponseDoesNotLeakThePassword() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com",
                        ClientStatus.PENDING_VERIFICATION));

        String body = mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("Str0ng!Pass"), "the plain password leaked into the response body");
        assertFalse(body.toLowerCase().contains("password"), "a password field leaked into the response body");
    }

    @Test
    @DisplayName("POST /registrations maps every payload field onto the command, untouched")
    void registerMapsTheWholePayloadOntoTheCommand() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com",
                        ClientStatus.PENDING_VERIFICATION));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterClientCommand> captor = ArgumentCaptor.forClass(RegisterClientCommand.class);
        verify(registerClientUseCase).register(captor.capture());
        RegisterClientCommand command = captor.getValue();
        assertEquals(new RegisterClientCommand("Ana Maria Perez", "CC-1020304050", LocalDate.of(1995, 4, 10),
                "ana.perez@example.com", "+573001234567", "Bogota", NotificationChannel.EMAIL, "Str0ng!Pass"),
                command);
    }

    @ParameterizedTest(name = "{0} from the use case becomes HTTP {1}")
    @CsvSource({
            "MINOR_NOT_ALLOWED,      400",
            "DUPLICATE_EMAIL,        409",
            "DUPLICATE_DOCUMENT,     409",
            "PASSWORD_TOO_WEAK,      400",
            "UPSTREAM_AUTH_ERROR,    502",
            "RATE_LIMITED,           429",
            "INTERNAL_ERROR,         500"})
    @DisplayName("A BusinessException is mapped to its declared status")
    void businessExceptionsAreMappedToTheirStatus(String errorCode, int expectedStatus) throws Exception {
        when(registerClientUseCase.register(any()))
                .thenThrow(new BusinessException(ErrorCode.valueOf(errorCode)));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    @Test
    @DisplayName("A BusinessException carrying details exposes them under $.details")
    void businessExceptionDetailsAreExposed() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_TOO_WEAK, "weak",
                        Map.of("violations", "at least one digit")));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.violations").value("at least one digit"));
    }

    @Test
    @DisplayName("The ProblemDetail carries type, title and instance alongside the errorCode")
    void problemDetailIsFullyPopulated() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenThrow(new BusinessException(ErrorCode.MINOR_NOT_ALLOWED));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://bookingplatform.codefactory.com/errors/minor_not_allowed"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value(REGISTRATIONS))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("An unexpected failure is hidden behind a generic 500 INTERNAL_ERROR")
    void unexpectedFailureIsMaskedAsInternalError() throws Exception {
        when(registerClientUseCase.register(any()))
                .thenThrow(new IllegalStateException("connection pool exhausted at 10.0.0.7"));

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationPayload()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Unexpected internal error"));
    }

    @ParameterizedTest(name = "a missing {0} is rejected with 400 and named in $.details")
    @ValueSource(strings = {"fullName", "document", "birthDate", "email", "phone", "city",
            "notificationChannel", "password"})
    @DisplayName("Every mandatory field missing is reported as VALIDATION_ERROR")
    void missingMandatoryFieldsAreReported(String field) throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithout(field)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details." + field).isNotEmpty());

        verify(registerClientUseCase, never()).register(any());
    }

    @Test
    @DisplayName("A blank fullName is rejected before the use case is reached")
    void blankFullNameIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWith("fullName", "\"   \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").value("fullName is required"));

        verify(registerClientUseCase, never()).register(any());
    }

    @Test
    @DisplayName("An out of range document is rejected with its pattern message")
    void outOfRangeDocumentIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWith("document", "\"ABCD\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.document")
                        .value("document must be 5-20 alphanumeric characters or hyphens"));
    }

    @Test
    @DisplayName("Several invalid fields are all reported in one 400 answer")
    void severalInvalidFieldsAreReportedTogether() throws Exception {
        Map<String, String> fields = validFields();
        fields.put("document", "\"A\"");
        fields.put("phone", "\"12\"");
        fields.put("email", "\"not-an-email\"");

        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(fields)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.document").isNotEmpty())
                .andExpect(jsonPath("$.details.phone").isNotEmpty())
                .andExpect(jsonPath("$.details.email").isNotEmpty());
    }

    @Test
    @DisplayName("A birthDate in the future is rejected by the Past constraint, not by the use case")
    void futureBirthDateIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWith("birthDate", "\"" + LocalDate.now().plusDays(1) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.birthDate").value("birthDate must be in the past"));

        verify(registerClientUseCase, never()).register(any());
    }

    @Test
    @DisplayName("A syntactically broken JSON body is answered 400 VALIDATION_ERROR")
    void brokenJsonIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Ana\",,,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    @DisplayName("An unknown notificationChannel is answered 400 VALIDATION_ERROR")
    void unknownNotificationChannelIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWith("notificationChannel", "\"PIGEON\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(registerClientUseCase, never()).register(any());
    }

    @Test
    @DisplayName("A birthDate in the wrong format is answered 400 VALIDATION_ERROR")
    void malformedBirthDateIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWith("birthDate", "\"10/04/1995\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(registerClientUseCase, never()).register(any());
    }

    @Test
    @DisplayName("An empty request body is answered 400 VALIDATION_ERROR")
    void emptyBodyIsRejected() throws Exception {
        mockMvc.perform(post(REGISTRATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------- resend

    @Test
    @DisplayName("POST /verification-resends answers 202 Accepted with an empty body")
    void resendAnswers202() throws Exception {
        mockMvc.perform(post(RESENDS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana.perez@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(resendVerificationUseCase).resend("ana.perez@example.com");
    }

    @Test
    @DisplayName("An unknown email still gets 202, so the endpoint does not enumerate users")
    void resendDoesNotEnumerateUsers() throws Exception {
        mockMvc.perform(post(RESENDS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @ParameterizedTest(name = "resend with email [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   ", "not-an-email", "@example.com"})
    void resendRejectsInvalidEmails(String email) throws Exception {
        mockMvc.perform(post(RESENDS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").isNotEmpty());

        verify(resendVerificationUseCase, never()).resend(anyString());
    }

    @Test
    @DisplayName("A resend failure from the provider surfaces as 502")
    void resendUpstreamFailureIsMappedTo502() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR))
                .when(resendVerificationUseCase).resend(anyString());

        mockMvc.perform(post(RESENDS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana.perez@example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("UPSTREAM_AUTH_ERROR"));
    }

    // ------------------------------------------------------------- confirm

    @Test
    @DisplayName("POST /email-verifications answers 200 OK")
    void confirmAnswers200() throws Exception {
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.ACTIVE));

        mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"pkce_1a2b3c\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /email-verifications returns the activated client and never the token hash")
    void confirmReturnsTheActivatedClientWithoutTheToken() throws Exception {
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.ACTIVE));

        String body = mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"pkce_1a2b3c\"}"))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana.perez@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("pkce_1a2b3c"), "the one-time token hash leaked into the response body");
    }

    @Test
    @DisplayName("The confirmation token reaches the use case untouched")
    void confirmForwardsTheTokenHash() throws Exception {
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.ACTIVE));

        mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"pkce_1a2b3c\"}"))
                .andExpect(status().isOk());

        verify(confirmEmailUseCase).confirm("pkce_1a2b3c");
    }

    @ParameterizedTest(name = "confirm with tokenHash [{0}] is rejected with 400")
    @ValueSource(strings = {"", "   "})
    void confirmRejectsBlankTokens(String tokenHash) throws Exception {
        mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"" + tokenHash + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.tokenHash").value("tokenHash is required"));

        verify(confirmEmailUseCase, never()).confirm(anyString());
    }

    @Test
    @DisplayName("A consumed or invalid token is answered 400 VERIFICATION_TOKEN_INVALID")
    void confirmWithInvalidTokenIsRejected() throws Exception {
        when(confirmEmailUseCase.confirm(anyString()))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"already-used\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("A confirmed user with no client profile is answered 404 RESOURCE_NOT_FOUND")
    void confirmWithoutProfileIsAnswered404() throws Exception {
        when(confirmEmailUseCase.confirm(anyString()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Client profile not found"));

        mockMvc.perform(post(CONFIRMATIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\": \"orphan-token\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
