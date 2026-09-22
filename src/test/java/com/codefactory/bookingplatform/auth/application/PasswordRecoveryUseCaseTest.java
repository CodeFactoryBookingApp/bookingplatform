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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the password recovery request (HU-021).
 *
 * Techniques applied:
 *  - Equivalence partitioning on the outcome of the recovery request: known email,
 *    unknown email (anti-enumeration partition) and provider failure.
 *  - Decision table over UpstreamAuthError: only USER_NOT_FOUND is absorbed.
 *  - Branch coverage: normal flow, the catch branch, and both outcomes of the
 *    USER_NOT_FOUND guard inside it.
 *  - Interaction verification: the recovery request always reaches the provider.
 */
class PasswordRecoveryUseCaseTest {

    private IdentityProviderPort identityProvider;
    private PasswordRecoveryUseCase useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new PasswordRecoveryUseCase(identityProvider);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("A recovery request for a known email is delegated to the identity provider")
        void delegatesToProvider() {
            doNothing().when(identityProvider).sendPasswordRecovery(anyString());

            useCase.requestRecovery("ana@example.com");

            verify(identityProvider).sendPasswordRecovery("ana@example.com");
        }

        @ParameterizedTest(name = "[{0}] is forwarded unchanged")
        @ValueSource(strings = {"ana@example.com", "ANA@EXAMPLE.COM", "Ana.Perez@Example.Com"})
        @DisplayName("The email is forwarded to the provider exactly as received")
        void forwardsEmailAsReceived(String email) {
            doNothing().when(identityProvider).sendPasswordRecovery(anyString());

            useCase.requestRecovery(email);

            verify(identityProvider).sendPasswordRecovery(email);
        }
    }

    // ----------------------------------------------------------------- //
    // Security rule: no user enumeration through the recovery endpoint   //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Anti-enumeration")
    class AntiEnumeration {

        @Test
        @DisplayName("A recovery request for an unknown email succeeds so accounts cannot be enumerated")
        void unknownEmailIsAnsweredAsSuccess() {
            doThrow(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "user not found"))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            assertDoesNotThrow(() -> useCase.requestRecovery("ghost@example.com"));
        }

        @Test
        @DisplayName("An unknown email is still sent to the provider before being absorbed")
        void unknownEmailStillReachesProvider() {
            doThrow(new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "user not found"))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            useCase.requestRecovery("ghost@example.com");

            verify(identityProvider).sendPasswordRecovery("ghost@example.com");
        }
    }

    // ----------------------------------------------------------------- //
    // Exceptional cases                                                  //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Upstream failures")
    class UpstreamFailures {

        @ParameterizedTest(name = "{0} is translated into a business rejection")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"USER_NOT_FOUND", "RATE_LIMITED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any upstream failure other than an unknown user is reported as an upstream auth error")
        void otherUpstreamErrorsBecomeUpstreamAuthError(UpstreamAuthError error) {
            doThrow(new UpstreamAuthException(error, "gotrue said " + error))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertEquals(ErrorCode.UPSTREAM_AUTH_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("ANTI-ENUMERATION: a provider rate limit is answered 429, never as a generic 500")
        void rateLimitIsTranslatedInsteadOfEscaping() {
            doThrow(new UpstreamAuthException(UpstreamAuthError.RATE_LIMITED, "too many emails"))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertEquals(ErrorCode.RATE_LIMITED, ex.errorCode());
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.errorCode().status());
        }

        @ParameterizedTest(name = "{0} never escapes as a raw UpstreamAuthException")
        @EnumSource(value = UpstreamAuthError.class, names = "USER_NOT_FOUND", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("No upstream failure escapes unmapped, so none of them can surface as a 500")
        void noUpstreamErrorEscapesUnmapped(UpstreamAuthError error) {
            doThrow(new UpstreamAuthException(error, "gotrue said " + error))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertNotEquals(ErrorCode.INTERNAL_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("The upstream message is kept in the rejection so the trace stays useful")
        void keepsTheUpstreamMessage() {
            doThrow(new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "gotrue timed out"))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertEquals("gotrue timed out", ex.getMessage());
        }
    }
}
