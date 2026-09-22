package com.codefactory.bookingplatform.identity.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.codefactory.bookingplatform.support.BeanValidationSupport.emailOfLength;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.invalidProperties;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.messagesFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation of the two small registration DTOs: email confirmation and
 * verification resend.
 */
class RegistrationSupportRequestValidationTest {

    @Nested
    @DisplayName("ConfirmEmailRequest")
    class ConfirmEmail {

        @Test
        @DisplayName("A non blank token hash raises no violation")
        void validPayload() {
            assertEquals(Set.of(), invalidProperties(new ConfirmEmailRequest("pkce_1a2b3c")));
        }

        @ParameterizedTest(name = "missing, empty or blank tokenHash is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   ", "\t"})
        void tokenHashIsMandatory(String value) {
            assertEquals(Set.of("tokenHash is required"),
                    messagesFor(new ConfirmEmailRequest(value), "tokenHash"));
        }

        @Test
        @DisplayName("The token hash has no length ceiling: a very long opaque token is accepted")
        void longTokenIsAccepted() {
            assertTrue(messagesFor(new ConfirmEmailRequest("a".repeat(512)), "tokenHash").isEmpty());
        }
    }

    @Nested
    @DisplayName("ResendVerificationRequest")
    class ResendVerification {

        @Test
        @DisplayName("A well formed email raises no violation")
        void validPayload() {
            assertEquals(Set.of(), invalidProperties(new ResendVerificationRequest("ana@example.com")));
        }

        @ParameterizedTest(name = "missing, empty or blank email is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void emailIsMandatory(String value) {
            assertTrue(messagesFor(new ResendVerificationRequest(value), "email").contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com", "ana perez@example.com"})
        void emailFormatIsChecked(String value) {
            assertTrue(messagesFor(new ResendVerificationRequest(value), "email")
                    .contains("email must be a valid address"));
        }

        @Test
        @DisplayName("The resend DTO has no length ceiling, unlike the registration DTO")
        void noMaxLengthConstraint() {
            String longButValid = emailOfLength(190);
            assertTrue(messagesFor(new ResendVerificationRequest(longButValid), "email").isEmpty());
        }
    }
}
