package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-001 verification email resend.
 *
 * Techniques applied:
 * - Decision table with a single condition: does the normalised email belong to
 *   a known client? Both outcomes are covered, including the anti-enumeration
 *   rule that an unknown email must look exactly like a known one to the caller.
 * - Black-box partitioning of the normalisation rule (upper, mixed and lower
 *   case addresses).
 * - White-box branch coverage: both arms of ifPresentOrElse.
 * - Interaction testing: the provider must not be called for unknown emails.
 */
class ResendVerificationUseCaseTest {

    private ClientRepository clientRepository;
    private UserProvisioning userProvisioning;
    private ResendVerificationUseCase useCase;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        userProvisioning = mock(UserProvisioning.class);
        useCase = new ResendVerificationUseCase(clientRepository, userProvisioning);
    }

    private Client clientWithEmail(String email, ClientStatus status) {
        return new Client(UUID.randomUUID(), "Ana Perez", "CC12345678", LocalDate.of(1995, 4, 10),
                email, "+573001234567", "Bogota", NotificationChannel.EMAIL, status);
    }

    // --- Known email ---------------------------------------------------------

    @Test
    @DisplayName("A known email gets a new verification message from the identity provider")
    void knownEmailTriggersTheProvider() {
        when(clientRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(clientWithEmail("ana@example.com", ClientStatus.PENDING_VERIFICATION)));

        useCase.resend("ana@example.com");

        verify(userProvisioning).resendSignupVerification("ana@example.com");
    }

    @ParameterizedTest(name = "\"{0}\" is looked up and resent as \"{1}\"")
    @DisplayName("The email is lowercased before the lookup and before the provider call")
    @CsvSource({
            "ANA@EXAMPLE.COM,  ana@example.com",
            "Ana.Perez@Example.Com, ana.perez@example.com",
            "ana@example.com,  ana@example.com"
    })
    void emailIsNormalisedBeforeLookupAndResend(String typedEmail, String normalisedEmail) {
        when(clientRepository.findByEmail(normalisedEmail))
                .thenReturn(Optional.of(clientWithEmail(normalisedEmail, ClientStatus.PENDING_VERIFICATION)));

        useCase.resend(typedEmail);

        verify(clientRepository).findByEmail(normalisedEmail);
        verify(userProvisioning).resendSignupVerification(normalisedEmail);
    }

    @ParameterizedTest
    @DisplayName("The resend is offered whatever the client status is, because the provider owns that decision")
    @EnumSource(ClientStatus.class)
    void resendIsOfferedForEveryStatus(ClientStatus status) {
        when(clientRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(clientWithEmail("ana@example.com", status)));

        useCase.resend("ana@example.com");

        verify(userProvisioning).resendSignupVerification("ana@example.com");
    }

    // --- Unknown email: anti-enumeration -------------------------------------

    @Test
    @DisplayName("An unknown email is silently accepted so the endpoint cannot be used to enumerate users")
    void unknownEmailIsSilentlyAccepted() {
        when(clientRepository.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> useCase.resend("desconocido@example.com"));
    }

    @Test
    @DisplayName("An unknown email never reaches the identity provider")
    void unknownEmailDoesNotReachTheProvider() {
        when(clientRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        useCase.resend("DESCONOCIDO@example.com");

        verify(clientRepository).findByEmail("desconocido@example.com");
        verifyNoInteractions(userProvisioning);
    }

    // --- Exceptional paths ---------------------------------------------------

    @Test
    @DisplayName("A provider failure while resending is propagated instead of being swallowed")
    void providerFailureIsPropagated() {
        when(clientRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(clientWithEmail("ana@example.com", ClientStatus.PENDING_VERIFICATION)));
        BusinessException upstream = new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR);
        doThrow(upstream).when(userProvisioning).resendSignupVerification("ana@example.com");

        assertSame(upstream, assertThrows(BusinessException.class, () -> useCase.resend("ana@example.com")));
    }

    @Test
    @DisplayName("DEFECT PIN: a null email breaks the resend with NullPointerException instead of a validation error")
    void nullEmailFailsWithNullPointerException() {
        // Documented gap, see the QA report: ResendVerificationUseCase line 29 calls
        // toLowerCase on the raw argument with no null guard of its own.
        assertThrows(NullPointerException.class, () -> useCase.resend(null));
        verifyNoInteractions(clientRepository);
        verifyNoInteractions(userProvisioning);
    }
}
