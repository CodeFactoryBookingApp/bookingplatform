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
            // Arrange
            ConfirmEmailRequest request = new ConfirmEmailRequest("pkce_1a2b3c");

            // Act
            Set<String> invalid = invalidProperties(request);

            // Assert
            assertEquals(Set.of(), invalid);
        }

        @ParameterizedTest(name = "missing, empty or blank tokenHash is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   ", "\t"})
        void tokenHashIsMandatory(String value) {
            // Arrange
            ConfirmEmailRequest request = new ConfirmEmailRequest(value);

            // Act
            Set<String> messages = messagesFor(request, "tokenHash");

            // Assert
            assertEquals(Set.of("tokenHash is required"), messages);
        }

        @Test
        @DisplayName("The token hash has no length ceiling: a very long opaque token is accepted")
        void longTokenIsAccepted() {
            // Arrange
            ConfirmEmailRequest request = new ConfirmEmailRequest("a".repeat(512));

            // Act
            Set<String> messages = messagesFor(request, "tokenHash");

            // Assert
            assertTrue(messages.isEmpty());
        }
    }

    @Nested
    @DisplayName("ResendVerificationRequest")
    class ResendVerification {

        @Test
        @DisplayName("A well formed email raises no violation")
        void validPayload() {
            // Arrange
            ResendVerificationRequest request = new ResendVerificationRequest("ana@example.com");

            // Act
            Set<String> invalid = invalidProperties(request);

            // Assert
            assertEquals(Set.of(), invalid);
        }

        @ParameterizedTest(name = "missing, empty or blank email is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void emailIsMandatory(String value) {
            // Arrange
            ResendVerificationRequest request = new ResendVerificationRequest(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com", "ana perez@example.com"})
        void emailFormatIsChecked(String value) {
            // Arrange
            ResendVerificationRequest request = new ResendVerificationRequest(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email must be a valid address"));
        }

        @Test
        @DisplayName("The resend DTO has no length ceiling, unlike the registration DTO")
        void noMaxLengthConstraint() {
            // Arrange
            String longButValid = emailOfLength(190);
            ResendVerificationRequest request = new ResendVerificationRequest(longButValid);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.isEmpty());
        }
    }
}
