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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        @ParameterizedTest(name = "[{0}] is rejected because it has no {1}")
        @CsvSource({
                "abcdef1!, uppercase",
                "ABCDEF1!, lowercase",
                "Abcdefg!, digit",
                "Abcdefg1, special character",
                "Abc1!,    minimum length"
        })
        @DisplayName("A password that breaks the policy is rejected as too weak")
        void weakPasswordIsRejected(String password, String brokenRule) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", password));

            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }

        @Test
        @DisplayName("The weak password rejection carries the policy message, not an empty detail")
        void weakPasswordCarriesTheDefaultMessage() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", "abcdef1!"));

            assertEquals(ErrorCode.PASSWORD_TOO_WEAK.defaultMessage(), ex.getMessage());
        }

        @ParameterizedTest(name = "[{0}] never reaches the identity provider")
        @ValueSource(strings = {"abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdefg1", "Abc1!"})
        @DisplayName("A weak password is rejected before any user is created upstream")
        void weakPasswordDoesNotReachProvider(String password) {
            assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", password));

            verifyNoInteractions(identityProvider);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("A null password is rejected as too weak instead of blowing up")
        void nullPasswordIsRejected(String password) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", password));

            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }

        @Test
        @DisplayName("The rejection lists every broken rule in the violations detail")
        void rejectionListsViolations() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", "abc"));

            String violations = ex.details().get("violations");
            assertTrue(violations.contains("at least 8 characters")
                            && violations.contains("uppercase")
                            && violations.contains("digit")
                            && violations.contains("special"),
                    "violations detail should list every broken rule but was: " + violations);
        }

        @Test
        @DisplayName("A password at the 8-character minimum is accepted")
        void minimumLengthPasswordIsAccepted() {
            UUID id = UUID.randomUUID();
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class))).thenReturn(id);

            assertEquals(id, service.provisionClientUser("ana@example.com", VALID_PASSWORD));
        }

        @Test
        @DisplayName("A password at the 72-character maximum is accepted")
        void maximumLengthPasswordIsAccepted() {
            UUID id = UUID.randomUUID();
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class))).thenReturn(id);

            String atMax = VALID_PASSWORD + "x".repeat(72 - VALID_PASSWORD.length());

            assertEquals(id, service.provisionClientUser("ana@example.com", atMax));
        }

        @Test
        @DisplayName("A password one character above the 72-character maximum is rejected")
        void aboveMaximumLengthPasswordIsRejected() {
            String aboveMax = VALID_PASSWORD + "x".repeat(73 - VALID_PASSWORD.length());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", aboveMax));

            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("Provisioning a client user: happy path")
    class ProvisioningHappyPath {

        @Test
        @DisplayName("A valid signup returns the identifier issued by the identity provider")
        void returnsProviderUserId() {
            UUID id = UUID.randomUUID();
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class))).thenReturn(id);

            assertEquals(id, service.provisionClientUser("ana@example.com", VALID_PASSWORD));
        }

        @Test
        @DisplayName("Self-provisioned users are always created with the CLIENT role")
        void alwaysCreatesClientRole() {
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenReturn(UUID.randomUUID());

            service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            ArgumentCaptor<AppRole> captor = ArgumentCaptor.forClass(AppRole.class);
            verify(identityProvider).createUser(anyString(), anyString(), captor.capture());
            assertEquals(AppRole.CLIENT, captor.getValue());
        }

        @Test
        @DisplayName("The credentials are forwarded to the provider exactly as received")
        void forwardsCredentials() {
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenReturn(UUID.randomUUID());

            service.provisionClientUser("ana@example.com", VALID_PASSWORD);

            verify(identityProvider).createUser("ana@example.com", VALID_PASSWORD, AppRole.CLIENT);
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
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenThrow(upstream(error));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", VALID_PASSWORD));

            assertEquals(expected, ex.errorCode());
        }

        @Test
        @DisplayName("An unavailable provider keeps the upstream message for diagnosis")
        void unavailableKeepsUpstreamMessage() {
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenThrow(upstream(UpstreamAuthError.UNAVAILABLE));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", VALID_PASSWORD));

            assertEquals("gotrue said UNAVAILABLE", ex.getMessage());
        }

        @Test
        @DisplayName("A duplicate email is reported with the DUPLICATE_EMAIL default message")
        void duplicateEmailUsesDefaultMessage() {
            when(identityProvider.createUser(anyString(), anyString(), any(AppRole.class)))
                    .thenThrow(upstream(UpstreamAuthError.USER_ALREADY_EXISTS));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.provisionClientUser("ana@example.com", VALID_PASSWORD));

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
            UUID id = UUID.randomUUID();
            doNothing().when(identityProvider).deleteUser(any(UUID.class));

            service.deprovisionUser(id);

            verify(identityProvider).deleteUser(id);
        }

        @ParameterizedTest(name = "{0} while compensating is swallowed")
        @EnumSource(UpstreamAuthError.class)
        @DisplayName("An upstream failure while compensating is swallowed instead of propagated")
        void upstreamFailureIsSwallowed(UpstreamAuthError error) {
            doThrow(upstream(error)).when(identityProvider).deleteUser(any(UUID.class));

            assertDoesNotThrow(() -> service.deprovisionUser(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Any runtime failure while compensating is swallowed instead of propagated")
        void runtimeFailureIsSwallowed() {
            doThrow(new IllegalStateException("connection reset"))
                    .when(identityProvider).deleteUser(any(UUID.class));

            assertDoesNotThrow(() -> service.deprovisionUser(UUID.randomUUID()));
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
            doNothing().when(identityProvider).resendSignupVerification(anyString());

            service.resendSignupVerification("ana@example.com");

            verify(identityProvider).resendSignupVerification("ana@example.com");
        }

        @Test
        @DisplayName("A resend for an unknown email succeeds so accounts cannot be enumerated")
        void unknownEmailIsAnsweredAsSuccess() {
            doThrow(upstream(UpstreamAuthError.USER_NOT_FOUND))
                    .when(identityProvider).resendSignupVerification(anyString());

            assertDoesNotThrow(() -> service.resendSignupVerification("ghost@example.com"));
        }

        @ParameterizedTest(name = "{0} is escalated as a business error")
        @EnumSource(value = UpstreamAuthError.class, names = "USER_NOT_FOUND", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any upstream failure other than an unknown user is escalated")
        void otherUpstreamErrorsAreEscalated(UpstreamAuthError error) {
            doThrow(upstream(error)).when(identityProvider).resendSignupVerification(anyString());

            assertThrows(BusinessException.class, () -> service.resendSignupVerification("ana@example.com"));
        }

        @Test
        @DisplayName("A rate-limited resend is escalated as RATE_LIMITED")
        void rateLimitedResendIsMapped() {
            doThrow(upstream(UpstreamAuthError.RATE_LIMITED))
                    .when(identityProvider).resendSignupVerification(anyString());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.resendSignupVerification("ana@example.com"));

            assertEquals(ErrorCode.RATE_LIMITED, ex.errorCode());
        }
    }

    // ================================================================= //
    // confirmEmail                                                      //
    // ================================================================= //

    @Nested
    @DisplayName("Confirming the email with a one-time token")
    class ConfirmEmail {

        @Test
        @DisplayName("A valid token returns the confirmed user reported by the provider")
        void validTokenReturnsConfirmedUser() {
            ConfirmedUser confirmed = new ConfirmedUser(UUID.randomUUID(), "ana@example.com");
            when(identityProvider.verifyEmailToken(anyString())).thenReturn(confirmed);

            assertSame(confirmed, service.confirmEmail("token-hash"));
        }

        @Test
        @DisplayName("The token hash is forwarded to the provider exactly as received")
        void forwardsTokenHash() {
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenReturn(new ConfirmedUser(UUID.randomUUID(), "ana@example.com"));

            service.confirmEmail("token-hash");

            verify(identityProvider).verifyEmailToken("token-hash");
        }

        @ParameterizedTest(name = "{0} is reported as VERIFICATION_TOKEN_INVALID")
        @EnumSource(value = UpstreamAuthError.class, names = {"TOKEN_INVALID", "TOKEN_EXPIRED"})
        @DisplayName("An invalid or expired confirmation token is reported as an invalid token")
        void badTokenIsTranslated(UpstreamAuthError error) {
            when(identityProvider.verifyEmailToken(anyString())).thenThrow(upstream(error));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.confirmEmail("token-hash"));

            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode());
        }

        @Test
        @DisplayName("An invalid confirmation token does not leak the provider message")
        void badTokenUsesDefaultMessage() {
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenThrow(upstream(UpstreamAuthError.TOKEN_EXPIRED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.confirmEmail("token-hash"));

            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID.defaultMessage(), ex.getMessage());
        }

        @ParameterizedTest(name = "{0} is escalated as a business error")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"TOKEN_INVALID", "TOKEN_EXPIRED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Any other upstream failure during confirmation is escalated")
        void otherUpstreamErrorsAreEscalated(UpstreamAuthError error) {
            when(identityProvider.verifyEmailToken(anyString())).thenThrow(upstream(error));

            assertThrows(BusinessException.class, () -> service.confirmEmail("token-hash"));
        }

        @ParameterizedTest(name = "{0} during confirmation keeps its own error code")
        @EnumSource(value = UpstreamAuthError.class,
                names = {"RATE_LIMITED", "USER_ALREADY_EXISTS", "EMAIL_NOT_CONFIRMED"})
        @DisplayName("A confirmation failure that is not about the token keeps its own meaning")
        void nonTokenFailuresKeepTheirOwnCode(UpstreamAuthError error) {
            when(identityProvider.verifyEmailToken(anyString())).thenThrow(upstream(error));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.confirmEmail("token-hash"));

            assertNotEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, ex.errorCode());
        }

        @Test
        @DisplayName("Confirming an email for an unknown user is reported as RESOURCE_NOT_FOUND")
        void unknownUserIsMapped() {
            when(identityProvider.verifyEmailToken(anyString()))
                    .thenThrow(upstream(UpstreamAuthError.USER_NOT_FOUND));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.confirmEmail("token-hash"));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        }
    }
}
