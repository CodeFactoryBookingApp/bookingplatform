package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-001 email confirmation use case.
 *
 * Techniques applied:
 * - Decision table over the two inputs that decide the outcome: token accepted
 *   by the provider (yes/no) and client profile present in our database
 *   (yes/no), crossed with the status the profile is in.
 * - White-box branch coverage: the orElseThrow branch, the idempotent branch of
 *   Client.verifyEmail and the branch where the aggregate rejects the change.
 * - Interaction testing: the profile must be persisted after the status change,
 *   and nothing must be persisted when a rule fails.
 */
class ConfirmEmailUseCaseTest {

    private static final UUID CLIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String TOKEN = "token-hash";

    private ClientRepository clientRepository;
    private UserProvisioning userProvisioning;
    private ConfirmEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        userProvisioning = mock(UserProvisioning.class);
        useCase = new ConfirmEmailUseCase(clientRepository, userProvisioning);
    }

    private Client clientInStatus(ClientStatus status) {
        return new Client(CLIENT_ID, "Ana Perez", "CC12345678", LocalDate.of(1995, 4, 10),
                "ana@example.com", "+573001234567", "Bogota", NotificationChannel.EMAIL, status);
    }

    private void tokenResolvesTo(Client client) {
        when(userProvisioning.confirmEmail(TOKEN)).thenReturn(new ConfirmedUser(CLIENT_ID, "ana@example.com"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // --- Happy path ----------------------------------------------------------

    @Test
    @DisplayName("A valid token activates the pending client and reports the new status")
    void validTokenActivatesPendingClient() {
        // Arrange
        Client pending = clientInStatus(ClientStatus.PENDING_VERIFICATION);
        tokenResolvesTo(pending);

        // Act
        RegistrationOutcome outcome = useCase.confirm(TOKEN);

        // Assert
        assertEquals(CLIENT_ID, outcome.clientId());
        assertEquals("ana@example.com", outcome.email());
        assertEquals(ClientStatus.ACTIVE, outcome.status());
        assertTrue(pending.canConfirmBooking());
    }

    @Test
    @DisplayName("The activated client is persisted after the status change, not before")
    void activatedClientIsPersisted() {
        // Arrange
        Client pending = clientInStatus(ClientStatus.PENDING_VERIFICATION);
        tokenResolvesTo(pending);

        // Act
        useCase.confirm(TOKEN);

        // Assert
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertSame(pending, captor.getValue());
        assertEquals(ClientStatus.ACTIVE, captor.getValue().getStatus());

        InOrder order = inOrder(userProvisioning, clientRepository);
        order.verify(userProvisioning).confirmEmail(TOKEN);
        order.verify(clientRepository).findById(CLIENT_ID);
        order.verify(clientRepository).save(pending);
    }

    // --- Idempotency ---------------------------------------------------------

    @Test
    @DisplayName("Confirming twice is idempotent: the second call succeeds and keeps the client ACTIVE")
    void confirmingTwiceIsIdempotent() {
        // Arrange
        Client pending = clientInStatus(ClientStatus.PENDING_VERIFICATION);
        tokenResolvesTo(pending);
        RegistrationOutcome first = useCase.confirm(TOKEN);

        // Act
        RegistrationOutcome second = useCase.confirm(TOKEN);

        // Assert
        assertEquals(ClientStatus.ACTIVE, first.status());
        assertEquals(ClientStatus.ACTIVE, second.status());
        assertEquals(first, second);
        verify(clientRepository, times(2)).save(pending);
    }

    // --- Exceptional paths ---------------------------------------------------

    @Test
    @DisplayName("A token accepted by the provider without a client profile raises RESOURCE_NOT_FOUND")
    void missingClientProfileRaisesResourceNotFound() {
        // Arrange
        when(userProvisioning.confirmEmail(TOKEN)).thenReturn(new ConfirmedUser(CLIENT_ID, "ana@example.com"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        // Act
        Executable act = () -> useCase.confirm(TOKEN);

        // Assert
        BusinessException ex = assertThrows(BusinessException.class, act);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        assertEquals("Client profile not found for the confirmed user", ex.getMessage());
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("An invalid or already used token fails at the provider and never touches the client profile")
    void invalidTokenIsPropagatedWithoutTouchingTheRepository() {
        // Arrange
        BusinessException invalid = new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        when(userProvisioning.confirmEmail(anyString())).thenThrow(invalid);

        // Act
        Executable act = () -> useCase.confirm("used-token");

        // Assert
        BusinessException thrown = assertThrows(BusinessException.class, act);
        assertSame(invalid, thrown);
        verifyNoInteractions(clientRepository);
    }

    @Test
    @DisplayName("A suspended client cannot be reactivated through email confirmation and is not persisted")
    void suspendedClientCannotBeConfirmed() {
        // Arrange
        Client suspended = clientInStatus(ClientStatus.SUSPENDED);
        when(userProvisioning.confirmEmail(TOKEN)).thenReturn(new ConfirmedUser(CLIENT_ID, "ana@example.com"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(suspended));

        // Act
        Executable act = () -> useCase.confirm(TOKEN);

        // Assert
        BusinessException ex = assertThrows(BusinessException.class, act);
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(ClientStatus.SUSPENDED, suspended.getStatus());
        assertFalse(suspended.canConfirmBooking());
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("A failure while persisting the activation is propagated to the caller")
    void persistenceFailureIsPropagated() {
        // Arrange
        when(userProvisioning.confirmEmail(TOKEN)).thenReturn(new ConfirmedUser(CLIENT_ID, "ana@example.com"));
        when(clientRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(clientInStatus(ClientStatus.PENDING_VERIFICATION)));
        RuntimeException boom = new IllegalStateException("database down");
        when(clientRepository.save(any(Client.class))).thenThrow(boom);

        // Act
        Executable act = () -> useCase.confirm(TOKEN);

        // Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, act);
        assertSame(boom, thrown);
    }
}
