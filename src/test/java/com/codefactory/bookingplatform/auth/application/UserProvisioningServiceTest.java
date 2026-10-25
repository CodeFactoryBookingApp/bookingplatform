package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the auth module facade used by the other business modules.
 *
 * Techniques applied:
 *  - Boundary value analysis and equivalence partitioning on the password policy
 *    applied at provisioning time.
 *  - Decision table over UpstreamAuthError for mapUpstream: every one of the eight
 *    enum values is exercised through provisionClientUser, which reaches the switch
 *    unconditionally.
 *  - Branch coverage: the weak-password guard, every catch block, the two
 *    anti-enumeration and token guards, and the compensation catch.
 *  - Interaction verification: the provider must not be contacted with a weak
 *    password, and the role must always be CLIENT.
 */
class UserProvisioningServiceTest {

    private static final String VALID_PASSWORD = "Abcdef1!";

    private IdentityProviderPort identityProvider;
    private UserProvisioningService service;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        service = new UserProvisioningService(identityProvider);
    }

    private UpstreamAuthException upstream(UpstreamAuthError error) {
        return new UpstreamAuthException(error, "gotrue said " + error);
    }

    // ================================================================= //
    // provisionClientUser                                               //
    // ================================================================= //

    @Nested
    @DisplayName("Provisioning a client user: password policy (partitions and boundaries)")
    class ProvisioningPasswordPolicy {

        @ParameterizedTest(name = "[{0}] is rejected and the detail names: {1}")
        @CsvSource({
                "abcdef1!, uppercase",
                "ABCDEF1!, lowercase",
                "Abcdefg!, digit",
                "Abcdefg1, special character",
                "Abc1!,    at least 8 characters"
        })
        @DisplayName("A password that breaks the policy is rejected as too weak")
        void weakPasswordIsRejected(String password, String brokenRule) {
            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", password);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
            String violations = ex.details().get("violations");
            assertTrue(violations.contains(brokenRule),
                    () -> "the violations detail should name the broken rule " + brokenRule + " but was: " + violations);
        }

        @ParameterizedTest(name = "[{0}] never reaches the identity provider")
        @ValueSource(strings = {"abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdefg1", "Abc1!"})
        @DisplayName("A weak password is rejected before any user is created upstream")
        void weakPasswordDoesNotReachProvider(String password) {
            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", password);

            // Assert
            assertThrows(BusinessException.class, act);
            verifyNoInteractions(identityProvider);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("A null password is rejected as too weak instead of blowing up")
        void nullPasswordIsRejected(String password) {
            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", password);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }

        @Test
        @DisplayName("The rejection lists every broken rule in the violations detail")
        void rejectionListsViolations() {
            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", "abc");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals("Password must be at least 8 characters long; "
                            + "Password must contain at least one uppercase letter; "
                            + "Password must contain at least one digit; "
                            + "Password must contain at least one special character",
                    ex.details().get("violations"),
                    "the violations detail lists every broken rule, in policy order, and nothing else");
        }

        @Test
        @DisplayName("A password one character above the 72-character maximum is rejected")
        void aboveMaximumLengthPasswordIsRejected() {
            // Arrange
            String aboveMax = VALID_PASSWORD + "x".repeat(73 - VALID_PASSWORD.length());

            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", aboveMax);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Provisioning a client user: happy path")
    class ProvisioningHappyPath {

        @Test
        @DisplayName("The credentials are forwarded to the provider exactly as received")
        void forwardsCredentials() {
            // Arrange
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenReturn(UUID.randomUUID());

            // Act
            service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            // Assert
            verify(identityProvider).createUser("ana@example.com", VALID_PASSWORD, AppRole.CLIENT);
        }

        /** The client row is keyed by this id; losing it would orphan the provider account. */
        @Test
        @DisplayName("A valid signup returns the identifier issued by the identity provider")
        void returnsProviderUserId() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class))).thenReturn(id);

            // Act
            UUID result = service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            // Assert
            assertEquals(id, result);
        }

        @Test
        @DisplayName("HU-002: a provider account is created with the PROVIDER role, under the same password policy")
        void providerAccountGetsTheProviderRole() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class))).thenReturn(id);

            // Act
            UUID result = service.provisionProviderUser("barberia@example.com", VALID_PASSWORD);

            // Assert
            assertEquals(id, result);
            verify(identityProvider).createUser("barberia@example.com", VALID_PASSWORD, AppRole.PROVIDER);
        }

        @Test
        @DisplayName("HU-002: a weak password is rejected for a provider account too, before anything is created")
        void providerAccountHonoursThePasswordPolicy() {
            // Act
            Executable act = () -> service.provisionProviderUser("barberia@example.com", "abc");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
            verifyNoInteractions(identityProvider);
        }
    }

    // ----------------------------------------------------------------- //
    // Decision table: the complete mapUpstream switch (8 of 8 arms)      //
    // ----------------------------------------------------------------- //

    @Nested
    @DisplayName("Upstream error translation (every arm of mapUpstream)")
    class UpstreamMapping {

        @ParameterizedTest(name = "{0} is reported to the caller as {1}")
        @CsvSource({
                "USER_ALREADY_EXISTS, DUPLICATE_EMAIL",
                "RATE_LIMITED,        RATE_LIMITED",
                "USER_NOT_FOUND,      RESOURCE_NOT_FOUND",
                "TOKEN_INVALID,       VERIFICATION_TOKEN_INVALID",
                "TOKEN_EXPIRED,       VERIFICATION_TOKEN_INVALID",
                "INVALID_CREDENTIALS, INVALID_CREDENTIALS",
                "EMAIL_NOT_CONFIRMED, EMAIL_NOT_CONFIRMED",
                "UNAVAILABLE,         UPSTREAM_AUTH_ERROR"
        })
        @DisplayName("Each upstream error maps to its business error code during provisioning")
        void mapsEachUpstreamError(UpstreamAuthError error, ErrorCode expected) {
            // Arrange
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenThrow(upstream(error));

            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(expected, ex.errorCode());
        }

        @Test
        @DisplayName("A duplicate email is reported with the DUPLICATE_EMAIL default message")
        void duplicateEmailUsesDefaultMessage() {
            // Arrange
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenThrow(upstream(UpstreamAuthError.USER_ALREADY_EXISTS));

            // Act
            Executable act = () -> service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.DUPLICATE_EMAIL.defaultMessage(), ex.getMessage());
        }
    }

    // ================================================================= //
    // deprovisionUser: compensation, must never fail                    //
    // ================================================================= //

    @Nested
    @DisplayName("Deprovisioning (compensating action, never propagates)")
    class Deprovisioning {

        @Test
        @DisplayName("Deprovisioning deletes the auth user at the identity provider")
        void deletesUserAtProvider() {
            // Arrange
            UUID id = UUID.randomUUID();
            doNothing().when(identityProvider).deleteUser(any(UUID.class));

            // Act
            service.deprovisionUser(id);

            // Assert
            verify(identityProvider).deleteUser(id);
        }

        @ParameterizedTest(name = "{0} while compensating is swallowed")
        @EnumSource(value = UpstreamAuthError.class, names = "UNAVAILABLE")
        @DisplayName("An upstream failure while compensating is swallowed instead of propagated")
        void upstreamFailureIsSwallowed(UpstreamAuthError error) {
            // Arrange
            doThrow(upstream(error)).when(identityProvider).deleteUser(any(UUID.class));

            // Act
            Executable act = () -> service.deprovisionUser(UUID.randomUUID());

            // Assert
            assertDoesNotThrow(act);
        }
    }

    // ================================================================= //
    // resendSignupVerification: anti-enumeration                        //
    // ================================================================= //

    @Nested
    @DisplayName("Resending the signup verification")
    class ResendSignupVerification {

        @Test
        @DisplayName("The resend request is delegated to the identity provider")
        void delegatesToProvider() {
            // Arrange
            doNothing().when(identityProvider).resendSignupVerification(anyString());

            // Act
            service.resendSignupVerification("ana@example.com");

            // Assert
            verify(identityProvider).resendSignupVerification("ana@example.com");
        }

        @Test
        @DisplayName("A resend for an unknown email succeeds so accounts cannot be enumerated")
        void unknownEmailIsAnsweredAsSuccess() {
            // Arrange
            doThrow(upstream(UpstreamAuthError.USER_NOT_FOUND))
                    .when(identityProvider).resendSignupVerification(anyString());

            // Act
            Executable act = () -> service.resendSignupVerification("ghost@example.com");

            // Assert
            assertDoesNotThrow(act);
            verify(identityProvider).resendSignupVerification("ghost@example.com");
        }
    }

    // ================================================================= //
    // confirmEmail                                                      //
    // ================================================================= //

    @Nested
    @DisplayName("Confirming the email with a one-time token")
    class ConfirmEmail {

        /** ConfirmEmailUseCase activates the client whose id comes back here. */
        @Test
        @DisplayName("A valid token returns the confirmed user reported by the provider")
        void validTokenReturnsConfirmedUser() {
            // Arrange
            ConfirmedUser confirmed = new ConfirmedUser(UUID.randomUUID(), "ana@example.com");
            when(identityProvider.verifyEmailToken(anyString())).thenReturn(confirmed);

            // Act
            ConfirmedUser result = service.confirmEmail("token-hash");

            // Assert
            assertEquals(confirmed, result);
        }

        @Test
        @DisplayName("The token hash is forwarded to the provider exactly as received")
        void forwardsTokenHash() {
            // Arrange
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenReturn(new ConfirmedUser(UUID.randomUUID(), "ana@example.com"));

            // Act
            service.confirmEmail("token-hash");

            // Assert
            verify(identityProvider).verifyEmailToken("token-hash");
        }

        @ParameterizedTest(name = "{0} is reported as VERIFICATION_TOKEN_INVALID")
        @EnumSource(value = UpstreamAuthError.class, names = {"TOKEN_INVALID", "TOKEN_EXPIRED"})
        @DisplayName("An invalid or expired confirmation token is reported as an invalid token")
        void badTokenIsTranslated(UpstreamAuthError error) {
            // Arrange
            when(identityProvider.verifyEmailToken(anyString())).thenThrow(upstream(error));

            // Act
            Executable act = () -> service.confirmEmail("token-hash");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode());
        }

        @Test
        @DisplayName("An invalid confirmation token does not leak the provider message")
        void badTokenUsesDefaultMessage() {
            // Arrange
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenThrow(upstream(UpstreamAuthError.TOKEN_EXPIRED));

            // Act
            Executable act = () -> service.confirmEmail("token-hash");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID.defaultMessage(), ex.getMessage());
        }

        @ParameterizedTest(name = "{0} during confirmation is reported as {1}")
        @CsvSource({
                "RATE_LIMITED,        RATE_LIMITED",
                "USER_ALREADY_EXISTS, DUPLICATE_EMAIL",
                "EMAIL_NOT_CONFIRMED, EMAIL_NOT_CONFIRMED"
        })
        @DisplayName("A confirmation failure that is not about the token keeps its own meaning")
        void nonTokenFailuresKeepTheirOwnCode(UpstreamAuthError error, ErrorCode expected) {
            // Arrange
            when(identityProvider.verifyEmailToken(anyString())).thenThrow(upstream(error));

            // Act
            Executable act = () -> service.confirmEmail("token-hash");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(expected, ex.errorCode());
        }

        @Test
        @DisplayName("Confirming an email for an unknown user is reported as RESOURCE_NOT_FOUND")
        void unknownUserIsMapped() {
            // Arrange
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenThrow(upstream(UpstreamAuthError.USER_NOT_FOUND));

            // Act
            Executable act = () -> service.confirmEmail("token-hash");

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }
}
