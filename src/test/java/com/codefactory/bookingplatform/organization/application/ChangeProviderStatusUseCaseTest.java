package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationBookings;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-003 status changes of a provider: the suspension decision table (future bookings x
 * confirmed) and the state transitions of reactivation and rejection.
 */
class ChangeProviderStatusUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private OrganizationRepository organizations;
    private OrganizationBookings bookings;
    private ChangeProviderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        bookings = mock(OrganizationBookings.class);
        useCase = new ChangeProviderStatusUseCase(organizations, bookings, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Organization stored(OrganizationStatus status) {
        Organization organization = new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería",
                IdentificationType.NIT, "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota",
                List.of(new Location(UUID.randomUUID(), "Sede", "Calle 1", UUID.randomUUID(), null)), status,
                status == OrganizationStatus.PENDING_APPROVAL ? null : NOW, new OperationPolicy(0, 60, 0), 1);
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        return organization;
    }

    @Nested
    @DisplayName("Suspension")
    class Suspension {

        @Test
        @DisplayName("HU-003: with future bookings and no confirmation, the provider is not suspended and the answer counts them")
        void unconfirmedSuspensionWithBookingsAsksForConfirmation() {
            // Arrange
            Organization organization = stored(OrganizationStatus.ACTIVE);
            when(bookings.countFuture(organization.getId(), NOW)).thenReturn(3);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.suspend(organization.getId(), "Quejas", false, ADMIN));

            // Assert
            assertEquals(ErrorCode.CONFIRMATION_REQUIRED, ex.errorCode());
            assertEquals(Map.of("affectedBookings", "3"), ex.details());
            assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
            verify(organizations, never()).saveStatusChange(any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("HU-003: once confirmed, the provider is suspended and the change stores the reason and the bookings affected")
        void confirmedSuspensionStoresTheAffectedBookings() {
            // Arrange
            Organization organization = stored(OrganizationStatus.ACTIVE);
            when(bookings.countFuture(organization.getId(), NOW)).thenReturn(3);

            // Act
            useCase.suspend(organization.getId(), "  Quejas reiteradas ", true, ADMIN);

            // Assert
            assertEquals(OrganizationStatus.SUSPENDED, organization.getStatus());
            verify(organizations).saveStatusChange(organization, OrganizationStatus.ACTIVE, "Quejas reiteradas", ADMIN, 3);
        }

        @Test
        @DisplayName("Without future bookings there is nothing to confirm: the provider is suspended at once")
        void suspensionWithoutBookingsNeedsNoConfirmation() {
            // Arrange
            Organization organization = stored(OrganizationStatus.ACTIVE);

            // Act
            useCase.suspend(organization.getId(), "Documentos vencidos", false, ADMIN);

            // Assert
            assertEquals(OrganizationStatus.SUSPENDED, organization.getStatus());
            verify(organizations).saveStatusChange(organization, OrganizationStatus.ACTIVE, "Documentos vencidos", ADMIN, 0);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(value = OrganizationStatus.class, names = {"PENDING_APPROVAL", "SUSPENDED", "REJECTED"})
        @DisplayName("Only an active provider can be suspended: anything else is a 409 before asking for confirmation")
        void onlyActiveCanBeSuspended(OrganizationStatus status) {
            // Arrange
            Organization organization = stored(status);
            when(bookings.countFuture(organization.getId(), NOW)).thenReturn(2);

            // Act
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> useCase.suspend(organization.getId(), "Motivo", false, ADMIN));

            // Assert
            assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        }
    }

    @Test
    @DisplayName("HU-003: a suspended provider is reactivated with its reason")
    void reactivation() {
        // Arrange
        Organization organization = stored(OrganizationStatus.SUSPENDED);

        // Act
        useCase.reactivate(organization.getId(), "Corregido", ADMIN);

        // Assert
        assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
        verify(organizations).saveStatusChange(organization, OrganizationStatus.SUSPENDED, "Corregido", ADMIN, 0);
    }

    @Test
    @DisplayName("A provider pending approval is rejected with its reason; an active one cannot be")
    void rejection() {
        // Arrange
        Organization pending = stored(OrganizationStatus.PENDING_APPROVAL);
        Organization active = stored(OrganizationStatus.ACTIVE);
        UUID activeId = active.getId();

        // Act
        useCase.reject(pending.getId(), "RUT ilegible", ADMIN);

        // Assert
        assertEquals(OrganizationStatus.REJECTED, pending.getStatus());
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION,
                assertThrows(BusinessException.class, () -> useCase.reject(activeId, "No", ADMIN)).errorCode());
    }

    @Test
    @DisplayName("A provider that does not exist is a 404")
    void unknownProviderIsNotFound() {
        // Arrange
        UUID unknown = UUID.randomUUID();
        when(organizations.findById(unknown)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.reactivate(unknown, "x", ADMIN));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }
}
