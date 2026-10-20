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
            // Arrange
            doNothing().when(identityProvider).signOut(anyString());

            // Act
            useCase.logout("jwt-access-token");

            // Assert
            verify(identityProvider).signOut("jwt-access-token");
        }

        @Test
        @DisplayName("A revoked session returns normally without raising anything")
        void successReturnsQuietly() {
            // Arrange
            doNothing().when(identityProvider).signOut(anyString());

            // Act
            Executable act = () -> useCase.logout("jwt-access-token");

            // Assert
            assertDoesNotThrow(act);
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
            // Arrange
            doThrow(new UpstreamAuthException(error, "session gone")).when(identityProvider).signOut(anyString());

            // Act
            Executable act = () -> useCase.logout("stale-token");

            // Assert
            assertDoesNotThrow(act);
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
            // Arrange
            doThrow(new UpstreamAuthException(error, "gotrue down")).when(identityProvider).signOut(anyString());

            // Act
            Executable act = () -> useCase.logout("token");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.UPSTREAM_AUTH_ERROR, ex.errorCode());
        }

        @Test
        @DisplayName("An escalated upstream failure keeps the provider message for diagnosis")
        void escalatedFailureKeepsUpstreamMessage() {
            // Arrange
            doThrow(new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "gotrue timed out"))
                    .when(identityProvider).signOut(anyString());

            // Act
            Executable act = () -> useCase.logout("token");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals("gotrue timed out", ex.getMessage());
        }

        @Test
        @DisplayName("A non-auth runtime failure is not swallowed by the logout use case")
        void unrelatedRuntimeFailurePropagates() {
            // Arrange
            doThrow(new IllegalStateException("connection reset")).when(identityProvider).signOut(anyString());

            // Act
            Executable act = () -> useCase.logout("token");

            // Assert
            assertThrows(IllegalStateException.class, act);
        }
    }
}
