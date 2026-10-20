package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for the password reset with a one-time recovery token (HU-021).
 *
 * Techniques applied:
 *  - Boundary value analysis on the password length (7/8 and 72/73 characters).
 *  - Equivalence partitioning on each password policy rule (uppercase, lowercase,
 *    digit, special character) plus the null partition.
 *  - Decision table over UpstreamAuthError: the two token values are translated,
 *    everything else is propagated.
 *  - Branch coverage: the weak-password guard, the normal flow, the catch branch
 *    and both operands of the compound OR inside it.
 *  - Interaction verification: a weak password must never reach the provider.
 */
class PasswordResetUseCaseTest {

    /** Shortest password that satisfies every rule: upper, lower, digit, special. */
    private static final String VALID_PASSWORD = "Abcdef1!";

    private IdentityProviderPort identityProvider;
    private PasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new PasswordResetUseCase(identityProvider);
    }

    private static String padded(int totalLength) {
        return VALID_PASSWORD + "x".repeat(totalLength - VALID_PASSWORD.length());
    }

    /**
     * Boundary values inside the valid range [8, 72]: the minimum, just inside each
     * end, and the maximum.
     */
    static Stream<String> acceptedLengthBoundaries() {
        return Stream.of(
                VALID_PASSWORD, // 8: minimum
                padded(9),      // 9: just inside
                padded(71),     // 71: just inside
                padded(72));    // 72: maximum
    }

    /** Boundary values just outside the valid range [8, 72]. */
    static Stream<String> rejectedLengthBoundaries() {
        return Stream.of(
                "Abcde1!",      // 7: below minimum
                padded(73));    // 73: above maximum
    }

    // ----------------------------------------------------------------- //
    // Black box: boundary value analysis on the password length          //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Password length (boundary value analysis at 8 and 72 characters)")
    class LengthBoundaries {

        @ParameterizedTest(name = "[{0}] is accepted")
        @MethodSource("com.codefactory.bookingplatform.auth.application.PasswordResetUseCaseTest#acceptedLengthBoundaries")
        @DisplayName("Passwords on or just inside the 8 and 72 character boundaries are accepted for the reset")
        void lengthsOnOrInsideBoundariesAreAccepted(String password) {
            // Arrange
            doNothing().when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token", password);

            // Assert
            assertDoesNotThrow(act);
        }

        @ParameterizedTest(name = "[{0}] is rejected")
        @MethodSource("com.codefactory.bookingplatform.auth.application.PasswordResetUseCaseTest#rejectedLengthBoundaries")
        @DisplayName("Passwords just outside the 8 and 72 character boundaries are rejected as too weak")
        void lengthsOutsideBoundariesAreRejected(String password) {
            // Arrange
            doNothing().when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token", password);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }
    }

    // ----------------------------------------------------------------- //
    // Black box: one equivalence partition per password policy rule      //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Password strength (one equivalence partition per policy rule)")
    class PasswordStrength {

        @ParameterizedTest(name = "[{0}] is rejected: {1}")
        @CsvSource({
                "abcdef1!, uppercase",
                "ABCDEF1!, lowercase",
                "Abcdefg!, digit",
                "Abcdefg1, special character"
        })
        @DisplayName("A password missing any required character class is rejected as too weak")
        void weakPasswordIsRejected(String password, String missingRule) {
            // Act
            Executable act = () -> useCase.resetPassword("token", password);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }

        @ParameterizedTest(name = "[{0}] never reaches the identity provider")
        @ValueSource(strings = {"abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdefg1", "short1!"})
        @DisplayName("A weak password is rejected before the identity provider is contacted")
        void weakPasswordDoesNotReachProvider(String password) {
            // Act
            Executable act = () -> useCase.resetPassword("token", password);

            // Assert
            assertThrows(BusinessException.class, act);
            verifyNoInteractions(identityProvider);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("A null password is rejected as too weak instead of blowing up")
        void nullPasswordIsRejected(String password) {
            // Act
            Executable act = () -> useCase.resetPassword("token", password);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }

        @Test
        @DisplayName("The rejection lists the violated rules in the violations detail")
        void rejectionListsViolations() {
            // Act
            Executable act = () -> useCase.resetPassword("token", "abc");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            String violations = ex.details().get("violations");
            assertTrue(violations.contains("at least 8 characters")
                            && violations.contains("uppercase")
                            && violations.contains("digit")
                            && violations.contains("special"),
                    "violations detail should list every broken rule but was: " + violations);
        }

        @Test
        @DisplayName("The rejection uses the PASSWORD_TOO_WEAK default message")
        void rejectionUsesDefaultMessage() {
            // Act
            Executable act = () -> useCase.resetPassword("token", "abc");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK.defaultMessage(), ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("A strong password is sent to the provider together with the recovery token")
        void strongPasswordIsDelegated() {
            // Arrange
            doNothing().when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            verify(identityProvider).resetPasswordWithToken("token-hash", VALID_PASSWORD);
        }
    }

    // ----------------------------------------------------------------- //
    // Exceptional cases: the recovery token is no longer usable          //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Recovery token failures")
    class TokenFailures {

        @ParameterizedTest(name = "{0} is reported as VERIFICATION_TOKEN_INVALID")
        @EnumSource(value = UpstreamAuthError.class, names = {"TOKEN_INVALID", "TOKEN_EXPIRED"})
        @DisplayName("An invalid or expired recovery token is reported as an invalid verification token")
        void badTokenIsTranslated(UpstreamAuthError error) {
            // Arrange
            doThrow(new UpstreamAuthException(error, "token rejected"))
                    .when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode());
        }

        @ParameterizedTest(name = "{0} is translated into a business rejection")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"TOKEN_INVALID", "TOKEN_EXPIRED", "RATE_LIMITED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any other upstream failure during the reset is reported as an upstream auth error")
        void otherUpstreamErrorsBecomeUpstreamAuthError(UpstreamAuthError error) {
            // Arrange
            doThrow(new UpstreamAuthException(error, "gotrue said " + error))
                    .when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.UPSTREAM_AUTH_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("A provider rate limit during the reset is answered 429, never as a generic 500")
        void rateLimitIsTranslatedInsteadOfEscaping() {
            // Arrange
            doThrow(new UpstreamAuthException(UpstreamAuthError.RATE_LIMITED, "too many resets"))
                    .when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.RATE_LIMITED, ex.errorCode());
        }

        @ParameterizedTest(name = "{0} never escapes as a raw UpstreamAuthException")
        @EnumSource(UpstreamAuthError.class)
        @DisplayName("No upstream failure escapes unmapped, so none of them can surface as a 500")
        void noUpstreamErrorEscapesUnmapped(UpstreamAuthError error) {
            // Arrange
            doThrow(new UpstreamAuthException(error, "gotrue said " + error))
                    .when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertNotEquals(ErrorCode.INTERNAL_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("The upstream message is kept in the rejection so the trace stays useful")
        void keepsTheUpstreamMessage() {
            // Arrange
            doThrow(new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "gotrue timed out"))
                    .when(identityProvider).resetPasswordWithToken(anyString(), anyString());

            // Act
            Executable act = () -> useCase.resetPassword("token-hash", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals("gotrue timed out", ex.getMessage());
        }
    }
}
