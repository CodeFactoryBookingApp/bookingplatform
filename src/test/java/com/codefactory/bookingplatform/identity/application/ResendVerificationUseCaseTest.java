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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

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

    @ParameterizedTest(name = "\"{0}\" is looked up and resent as \"{1}\"")
    @DisplayName("The email is lowercased before the lookup and before the provider call")
    @CsvSource({
            "ANA@EXAMPLE.COM,  ana@example.com",
            "Ana.Perez@Example.Com, ana.perez@example.com",
            "ana@example.com,  ana@example.com"
    })
    void emailIsNormalisedBeforeLookupAndResend(String typedEmail, String normalisedEmail) {
        // Arrange
        when(clientRepository.findByEmail(normalisedEmail))
                .thenReturn(Optional.of(clientWithEmail(normalisedEmail, ClientStatus.PENDING_VERIFICATION)));

        // Act
        useCase.resend(typedEmail);

        // Assert
        verify(clientRepository).findByEmail(normalisedEmail);
        verify(userProvisioning).resendSignupVerification(normalisedEmail);
    }

    @ParameterizedTest
    @DisplayName("The resend is offered whatever the client status is, because the provider owns that decision")
    @EnumSource(value = ClientStatus.class, names = {"ACTIVE", "SUSPENDED"})
    void resendIsOfferedForEveryStatus(ClientStatus status) {
        // Arrange
        when(clientRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(clientWithEmail("ana@example.com", status)));

        // Act
        useCase.resend("ana@example.com");

        // Assert
        verify(userProvisioning).resendSignupVerification("ana@example.com");
    }

    // --- Unknown email: anti-enumeration -------------------------------------

    @Test
    @DisplayName("An unknown email never reaches the identity provider")
    void unknownEmailDoesNotReachTheProvider() {
        // Arrange
        when(clientRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act
        useCase.resend("DESCONOCIDO@example.com");

        // Assert
        verify(clientRepository).findByEmail("desconocido@example.com");
        verifyNoInteractions(userProvisioning);
    }

    // --- Exceptional paths ---------------------------------------------------

    @Test
    @DisplayName("A provider failure while resending is propagated instead of being swallowed")
    void providerFailureIsPropagated() {
        // Arrange
        when(clientRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(clientWithEmail("ana@example.com", ClientStatus.PENDING_VERIFICATION)));
        BusinessException upstream = new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR);
        doThrow(upstream).when(userProvisioning).resendSignupVerification("ana@example.com");

        // Act
        Executable act = () -> useCase.resend("ana@example.com");

        // Assert
        BusinessException thrown = assertThrows(BusinessException.class, act);
        assertSame(upstream, thrown);
    }
}
