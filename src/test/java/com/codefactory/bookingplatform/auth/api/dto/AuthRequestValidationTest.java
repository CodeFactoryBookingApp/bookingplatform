package com.codefactory.bookingplatform.auth.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.codefactory.bookingplatform.support.BeanValidationSupport.invalidProperties;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.messagesFor;
import static com.codefactory.bookingplatform.support.BeanValidationSupport.repeat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and equivalence-class coverage for the auth module request DTOs,
 * checked directly with a Jakarta Validator.
 */
class AuthRequestValidationTest {

    @Nested
    @DisplayName("LoginRequest")
    class Login {

        @Test
        @DisplayName("A well formed login payload raises no violation")
        void validPayload() {
            // Arrange
            LoginRequest request = new LoginRequest("ana@example.com", "Str0ng!Pass");

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
            LoginRequest request = new LoginRequest(value, "Str0ng!Pass");

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com", "ana example.com"})
        void emailFormatIsChecked(String value) {
            // Arrange
            LoginRequest request = new LoginRequest(value, "Str0ng!Pass");

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email must be a valid address"));
        }

        @ParameterizedTest(name = "missing, empty or blank password is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void passwordIsMandatory(String value) {
            // Arrange
            LoginRequest request = new LoginRequest("ana@example.com", value);

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertEquals(Set.of("password is required"), messages);
        }

        @Test
        @DisplayName("Login does not constrain the password length: a single character is accepted")
        void passwordHasNoLengthConstraint() {
            // Arrange
            LoginRequest request = new LoginRequest("ana@example.com", "x");

            // Act
            Set<String> messages = messagesFor(request, "password");

            // Assert
            assertTrue(messages.isEmpty());
        }
    }

    @Nested
    @DisplayName("PasswordRecoveryRequest")
    class Recovery {

        @Test
        @DisplayName("A well formed recovery payload raises no violation")
        void validPayload() {
            // Arrange
            PasswordRecoveryRequest request = new PasswordRecoveryRequest("ana@example.com");

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
            PasswordRecoveryRequest request = new PasswordRecoveryRequest(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com"})
        void emailFormatIsChecked(String value) {
            // Arrange
            PasswordRecoveryRequest request = new PasswordRecoveryRequest(value);

            // Act
            Set<String> messages = messagesFor(request, "email");

            // Assert
            assertTrue(messages.contains("email must be a valid address"));
        }
    }

    @Nested
    @DisplayName("PasswordResetRequest")
    class Reset {

        @Test
        @DisplayName("A well formed reset payload raises no violation")
        void validPayload() {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest("token-hash", "Str0ng!Pass");

            // Act
            Set<String> invalid = invalidProperties(request);

            // Assert
            assertEquals(Set.of(), invalid);
        }

        @ParameterizedTest(name = "missing, empty or blank tokenHash is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void tokenHashIsMandatory(String value) {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest(value, "Str0ng!Pass");

            // Act
            Set<String> messages = messagesFor(request, "tokenHash");

            // Assert
            assertEquals(Set.of("tokenHash is required"), messages);
        }

        @ParameterizedTest(name = "newPassword of length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        void acceptedPasswordLengths(int length) {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest("token-hash", repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "newPassword");

            // Assert
            assertTrue(messages.isEmpty());
        }

        @ParameterizedTest(name = "newPassword of length {0} is outside [8, 72] and is rejected")
        @ValueSource(ints = {1, 7, 73, 100})
        void rejectedPasswordLengths(int length) {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest("token-hash", repeat('a', length));

            // Act
            Set<String> messages = messagesFor(request, "newPassword");

            // Assert
            assertTrue(messages.contains("newPassword must be between 8 and 72 characters"));
        }

        @ParameterizedTest(name = "missing or empty newPassword is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {""})
        void newPasswordIsMandatory(String value) {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest("token-hash", value);

            // Act
            Set<String> messages = messagesFor(request, "newPassword");

            // Assert
            assertTrue(messages.contains("newPassword is required"));
        }

        @Test
        @DisplayName("A newPassword of eight spaces is long enough but still blank")
        void blankNewPasswordIsRejected() {
            // Arrange
            PasswordResetRequest request = new PasswordResetRequest("token-hash", "        ");

            // Act
            Set<String> messages = messagesFor(request, "newPassword");

            // Assert
            assertEquals(Set.of("newPassword is required"), messages);
        }
    }
}
