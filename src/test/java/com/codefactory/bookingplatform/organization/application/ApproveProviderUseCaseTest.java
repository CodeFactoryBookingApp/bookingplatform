package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

/** HU-002 "Aprobación del proveedor", seen from the use case: what is stored and what is announced. */
class ApproveProviderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private OrganizationRepository organizations;
    private ApplicationEventPublisher events;
    private ApproveProviderUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        events = mock(ApplicationEventPublisher.class);
        useCase = new ApproveProviderUseCase(organizations, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Organization stored(OrganizationStatus status) {
        Organization organization = new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería",
                IdentificationType.NIT, "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota",
                List.of(new Location(UUID.randomUUID(), "Sede", "Calle 1", UUID.randomUUID(), null)),
                status, null, new OperationPolicy(60, 43200, 1440), 1);
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        return organization;
    }

    @Test
    @DisplayName("HU-002: the provider becomes ACTIVE, the trimmed reason is stored with the change, and the approval is announced")
    void approvesStoresTheReasonAndAnnounces() {
        // Arrange
        Organization organization = stored(OrganizationStatus.PENDING_APPROVAL);

        // Act
        Organization approved = useCase.approve(organization.getId(), "  RUT verificado  ", ADMIN);

        // Assert
        assertEquals(OrganizationStatus.ACTIVE, approved.getStatus());
        assertEquals(NOW, approved.getApprovedAt());
        verify(organizations).saveStatusChange(organization, OrganizationStatus.PENDING_APPROVAL, "RUT verificado", ADMIN, 0);
        verify(events).publishEvent(new ProviderApproved(organization.getId(), "Barbería", "contacto@barberia.com"));
    }

    @Test
    @DisplayName("Approving a provider that is not pending is a 409: nothing is stored and nothing is announced")
    void nonPendingProviderIsNotApproved() {
        // Arrange
        Organization organization = stored(OrganizationStatus.ACTIVE);
        UUID id = organization.getId();

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.approve(id, "otra vez", ADMIN));

        // Assert
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        verify(organizations, never()).saveStatusChange(any(), any(), any(), any(), anyInt());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Approving a provider that does not exist is a 404")
    void unknownProviderIsNotFound() {
        // Arrange
        UUID unknown = UUID.randomUUID();
        when(organizations.findById(unknown)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.approve(unknown, "ok", ADMIN));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }
}
