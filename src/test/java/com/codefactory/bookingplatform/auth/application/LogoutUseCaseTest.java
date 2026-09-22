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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the logout business rules (HU-021).
 *
 * Techniques applied:
 *  - Decision table over UpstreamAuthError: the two "session already dead" values
 *    are absorbed (logout is idempotent), every other value is escalated.
 *  - Branch and condition coverage: both operands of the compound OR in the catch
 *    block, the catch branch itself and the normal flow.
 *  - Interaction verification: the access token reaches the provider untouched.
 */
class LogoutUseCaseTest {

    private IdentityProviderPort identityProvider;
    private LogoutUseCase useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new LogoutUseCase(identityProvider);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Logout revokes the session at the identity provider with the given access token")
        void revokesSessionAtProvider() {
            doNothing().when(identityProvider).signOut(anyString());

            useCase.logout("jwt-access-token");

            verify(identityProvider).signOut("jwt-access-token");
        }

        @Test
        @DisplayName("A revoked session returns normally without raising anything")
        void successReturnsQuietly() {
            doNothing().when(identityProvider).signOut(anyString());

            assertDoesNotThrow(() -> useCase.logout("jwt-access-token"));
        }
    }

    // ----------------------------------------------------------------- //
    // Decision table / state transition: session already invalid         //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Idempotency (state transition: the session is already dead)")
    class AlreadyInvalidSession {

        @ParameterizedTest(name = "{0} is treated as a successful logout")
        @EnumSource(value = UpstreamAuthError.class, names = {"TOKEN_INVALID", "TOKEN_EXPIRED"})
        @DisplayName("Logging out an already invalid or expired session succeeds")
        void alreadyInvalidSessionIsTreatedAsSuccess(UpstreamAuthError error) {
            doThrow(new UpstreamAuthException(error, "session gone")).when(identityProvider).signOut(anyString());

            assertDoesNotThrow(() -> useCase.logout("stale-token"));
        }
    }

    // ----------------------------------------------------------------- //
    // Exceptional cases: everything the use case must escalate           //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Upstream failures")
    class UpstreamFailures {

        @ParameterizedTest(name = "{0} is escalated as UPSTREAM_AUTH_ERROR")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"TOKEN_INVALID", "TOKEN_EXPIRED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any other upstream failure is escalated as an upstream auth error")
        void otherUpstreamErrorsAreEscalated(UpstreamAuthError error) {
            doThrow(new UpstreamAuthException(error, "gotrue down")).when(identityProvider).signOut(anyString());

            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.logout("token"));

            assertEquals(ErrorCode.UPSTREAM_AUTH_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("An escalated upstream failure keeps the provider message for diagnosis")
        void escalatedFailureKeepsUpstreamMessage() {
            doThrow(new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "gotrue timed out"))
                    .when(identityProvider).signOut(anyString());

            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.logout("token"));

            assertEquals("gotrue timed out", ex.getMessage());
        }

        @Test
        @DisplayName("A non-auth runtime failure is not swallowed by the logout use case")
        void unrelatedRuntimeFailurePropagates() {
            doThrow(new IllegalStateException("connection reset")).when(identityProvider).signOut(anyString());

            assertThrows(IllegalStateException.class, () -> useCase.logout("token"));
        }
    }
}
