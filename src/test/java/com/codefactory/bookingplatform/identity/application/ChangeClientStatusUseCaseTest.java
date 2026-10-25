package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.domain.port.ClientStatusChanges;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-003 suspension and reactivation of a client, and the filters of the client listing. */
class ChangeClientStatusUseCaseTest {

    private static final UUID ADMIN = UUID.randomUUID();

    private ClientRepository clients;
    private ClientStatusChanges statusChanges;
    private ChangeClientStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        statusChanges = mock(ClientStatusChanges.class);
        useCase = new ChangeClientStatusUseCase(clients, statusChanges);
    }

    private Client stored(ClientStatus status) {
        Client client = new Client(UUID.randomUUID(), "Ana Pérez", "1017", LocalDate.of(1995, 4, 10),
                "ana@example.com", "3001112233", "Medellín", NotificationChannel.EMAIL, status);
        when(clients.findById(client.getId())).thenReturn(Optional.of(client));
        return client;
    }

    @Test
    @DisplayName("HU-003: an active client is suspended; the change is stored with its trimmed reason and the admin")
    void suspendsAndRecordsTheReason() {
        // Arrange
        Client client = stored(ClientStatus.ACTIVE);

        // Act
        Client suspended = useCase.suspend(client.getId(), "  Uso indebido ", ADMIN);

        // Assert
        assertEquals(ClientStatus.SUSPENDED, suspended.getStatus());
        verify(clients).save(client);
        verify(statusChanges).record(client.getId(), ClientStatus.ACTIVE, ClientStatus.SUSPENDED, "Uso indebido", ADMIN);
    }

    @Test
    @DisplayName("Suspending a client that already is suspended is a 409, and records nothing")
    void suspendingTwiceIsAConflict() {
        // Arrange
        Client client = stored(ClientStatus.SUSPENDED);

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.suspend(client.getId(), "Otra vez", ADMIN));

        // Assert
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        verify(statusChanges, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("HU-003: a suspended client is reactivated with its reason")
    void reactivates() {
        // Arrange
        Client client = stored(ClientStatus.SUSPENDED);

        // Act
        Client active = useCase.reactivate(client.getId(), "Aclarado", ADMIN);

        // Assert
        assertEquals(ClientStatus.ACTIVE, active.getStatus());
        verify(statusChanges).record(client.getId(), ClientStatus.SUSPENDED, ClientStatus.ACTIVE, "Aclarado", ADMIN);
    }

    @Test
    @DisplayName("A client that does not exist is a 404")
    void unknownClientIsNotFound() {
        // Arrange
        UUID unknown = UUID.randomUUID();
        when(clients.findById(unknown)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.suspend(unknown, "x", ADMIN));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }

    @Test
    @DisplayName("The client listing is newest first by default, a blank city is no filter, and an inverted range is a 400")
    void filterDefaultsAndRange() {
        // Act
        ClientFilter filter = new ClientFilter(null, "  ", null, null, null, true, 0, 20);

        // Assert
        assertEquals(ClientFilter.Sort.REGISTERED, filter.sort());
        assertNull(filter.city());
        BusinessException ex = assertThrows(BusinessException.class, () -> new ClientFilter(null, null,
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 24), null, false, 0, 20));
        assertEquals("must not be before the start of the range", ex.details().get("registeredTo"));
    }
}
