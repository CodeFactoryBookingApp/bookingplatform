package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        @ParameterizedTest(name = "{0} is propagated to the caller")
        @EnumSource(value = UpstreamAuthError.class, names = "USER_NOT_FOUND", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any upstream failure other than an unknown user is propagated")
        void otherUpstreamErrorsPropagate(UpstreamAuthError error) {
            doThrow(new UpstreamAuthException(error, "gotrue said " + error))
                    .when(identityProvider).sendPasswordRecovery(anyString());

            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertEquals(error, ex.error());
        }

        @Test
        @DisplayName("The propagated failure is the very exception raised by the provider")
        void propagatesTheOriginalException() {
            UpstreamAuthException raised =
                    new UpstreamAuthException(UpstreamAuthError.RATE_LIMITED, "too many emails");
            doThrow(raised).when(identityProvider).sendPasswordRecovery(anyString());

            UpstreamAuthException ex = assertThrows(UpstreamAuthException.class,
                    () -> useCase.requestRecovery("ana@example.com"));

            assertSame(raised, ex);
        }
    }
}
