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
            assertEquals(Set.of(), invalidProperties(new LoginRequest("ana@example.com", "Str0ng!Pass")));
        }

        @ParameterizedTest(name = "missing, empty or blank email is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void emailIsMandatory(String value) {
            assertTrue(messagesFor(new LoginRequest(value, "Str0ng!Pass"), "email")
                    .contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com", "ana example.com"})
        void emailFormatIsChecked(String value) {
            assertTrue(messagesFor(new LoginRequest(value, "Str0ng!Pass"), "email")
                    .contains("email must be a valid address"));
        }

        @ParameterizedTest(name = "missing, empty or blank password is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void passwordIsMandatory(String value) {
            assertEquals(Set.of("password is required"),
                    messagesFor(new LoginRequest("ana@example.com", value), "password"));
        }

        @Test
        @DisplayName("Login does not constrain the password length: a single character is accepted")
        void passwordHasNoLengthConstraint() {
            assertTrue(messagesFor(new LoginRequest("ana@example.com", "x"), "password").isEmpty());
        }
    }

    @Nested
    @DisplayName("PasswordRecoveryRequest")
    class Recovery {

        @Test
        @DisplayName("A well formed recovery payload raises no violation")
        void validPayload() {
            assertEquals(Set.of(), invalidProperties(new PasswordRecoveryRequest("ana@example.com")));
        }

        @ParameterizedTest(name = "missing, empty or blank email is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void emailIsMandatory(String value) {
            assertTrue(messagesFor(new PasswordRecoveryRequest(value), "email").contains("email is required"));
        }

        @ParameterizedTest(name = "[{0}] is not a valid address")
        @ValueSource(strings = {"not-an-email", "a@", "@example.com"})
        void emailFormatIsChecked(String value) {
            assertTrue(messagesFor(new PasswordRecoveryRequest(value), "email")
                    .contains("email must be a valid address"));
        }
    }

    @Nested
    @DisplayName("PasswordResetRequest")
    class Reset {

        @Test
        @DisplayName("A well formed reset payload raises no violation")
        void validPayload() {
            assertEquals(Set.of(), invalidProperties(new PasswordResetRequest("token-hash", "Str0ng!Pass")));
        }

        @ParameterizedTest(name = "missing, empty or blank tokenHash is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void tokenHashIsMandatory(String value) {
            assertEquals(Set.of("tokenHash is required"),
                    messagesFor(new PasswordResetRequest(value, "Str0ng!Pass"), "tokenHash"));
        }

        @ParameterizedTest(name = "newPassword of length {0} is accepted")
        @ValueSource(ints = {8, 9, 71, 72})
        void acceptedPasswordLengths(int length) {
            assertTrue(messagesFor(new PasswordResetRequest("token-hash", repeat('a', length)), "newPassword")
                    .isEmpty());
        }

        @ParameterizedTest(name = "newPassword of length {0} is outside [8, 72] and is rejected")
        @ValueSource(ints = {1, 7, 73, 100})
        void rejectedPasswordLengths(int length) {
            assertTrue(messagesFor(new PasswordResetRequest("token-hash", repeat('a', length)), "newPassword")
                    .contains("newPassword must be between 8 and 72 characters"));
        }

        @ParameterizedTest(name = "missing or empty newPassword is rejected: [{0}]")
        @NullSource
        @ValueSource(strings = {""})
        void newPasswordIsMandatory(String value) {
            assertTrue(messagesFor(new PasswordResetRequest("token-hash", value), "newPassword")
                    .contains("newPassword is required"));
        }

        @Test
        @DisplayName("A newPassword of eight spaces is long enough but still blank")
        void blankNewPasswordIsRejected() {
            assertEquals(Set.of("newPassword is required"),
                    messagesFor(new PasswordResetRequest("token-hash", "        "), "newPassword"));
        }
    }
}
