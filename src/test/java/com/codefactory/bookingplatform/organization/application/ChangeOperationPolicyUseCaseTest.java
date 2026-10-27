package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-002 "Edición de parámetros de operación": who may change them, and what gets stored.
 * Equivalence partitioning over the caller (OWNER, MANAGER allowed; STAFF denied; outsider hidden)
 * and over the provider status (ACTIVE accepts a new version, SUSPENDED does not).
 */
class ChangeOperationPolicyUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final OperationPolicy NEW_POLICY = new OperationPolicy(120, 20160, 720);

    private OrganizationRepository organizations;
    private ChangeOperationPolicyUseCase useCase;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        useCase = new ChangeOperationPolicyUseCase(organizations, new OrganizationAccess(organizations),
                Clock.fixed(NOW, ZoneOffset.UTC));
        organization = new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería", IdentificationType.NIT,
                "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota",
                List.of(new Location(UUID.randomUUID(), "Sede", "Calle 1", UUID.randomUUID(), null)),
                OrganizationStatus.ACTIVE, NOW, new OperationPolicy(60, 43200, 1440), 2);
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
    }

    @Test
    @DisplayName("HU-002: the owner changes the rules; the next version is stored from now on")
    void ownerChangesTheRules() {
        // Arrange
        when(organizations.findMemberRole(organization.getId(), USER)).thenReturn(Optional.of(MemberRole.OWNER));

        // Act
        Organization changed = useCase.change(organization.getId(), USER, NEW_POLICY);

        // Assert
        assertEquals(3, changed.getPolicyVersion());
        assertEquals(NEW_POLICY, changed.getPolicy());
        verify(organizations).savePolicy(organization, NOW);
    }

    @Test
    @DisplayName("HU-002 / ADR-0008: a manager may change the rules too; the next version is stored from now on")
    void managerChangesTheRules() {
        // Arrange
        when(organizations.findMemberRole(organization.getId(), USER)).thenReturn(Optional.of(MemberRole.MANAGER));

        // Act
        Organization changed = useCase.change(organization.getId(), USER, NEW_POLICY);

        // Assert
        assertEquals(3, changed.getPolicyVersion());
        assertEquals(NEW_POLICY, changed.getPolicy());
        verify(organizations).savePolicy(organization, NOW);
    }

    @Test
    @DisplayName("A suspended provider cannot change its rules even through its owner: 409 and no new version is stored")
    void suspendedProviderStoresNoNewVersion() {
        // Arrange
        Organization suspended = new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería",
                IdentificationType.NIT, "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota",
                organization.getLocations(), OrganizationStatus.SUSPENDED, NOW, new OperationPolicy(60, 43200, 1440), 2);
        UUID id = suspended.getId();
        when(organizations.findById(id)).thenReturn(Optional.of(suspended));
        when(organizations.findMemberRole(id, USER)).thenReturn(Optional.of(MemberRole.OWNER));

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.change(id, USER, NEW_POLICY));

        // Assert
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        verify(organizations, never()).savePolicy(any(), any());
    }

    @Test
    @DisplayName("HU-022: staff cannot change the rules (403) and nothing is stored")
    void staffCannotChangeTheRules() {
        // Arrange
        when(organizations.findMemberRole(organization.getId(), USER)).thenReturn(Optional.of(MemberRole.STAFF));
        UUID id = organization.getId();

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.change(id, USER, NEW_POLICY));

        // Assert
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
        verify(organizations, never()).savePolicy(any(), any());
    }

    @Test
    @DisplayName("HU-022: someone outside the provider gets 404 and nothing is stored")
    void outsiderGetsNotFound() {
        // Arrange
        when(organizations.findMemberRole(organization.getId(), USER)).thenReturn(Optional.empty());
        UUID id = organization.getId();

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.change(id, USER, NEW_POLICY));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verify(organizations, never()).savePolicy(any(), any());
    }
}
