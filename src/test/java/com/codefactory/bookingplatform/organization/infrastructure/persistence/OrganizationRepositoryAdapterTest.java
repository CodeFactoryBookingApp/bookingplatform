package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.DuplicateOrganizationException;
import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the adapter decides on its own: which integrity violation is a duplicate provider, and the
 * order of the writes the database rules depend on. The writes themselves run against PostgreSQL
 * in ProviderOnboardingIT.
 */
class OrganizationRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    private OrganizationJpaRepository organizations;
    private OperationPolicyJpaRepository policies;
    private AccountStatusChangeJpaRepository statusChanges;
    private OrganizationRepositoryAdapter adapter;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationJpaRepository.class);
        policies = mock(OperationPolicyJpaRepository.class);
        statusChanges = mock(AccountStatusChangeJpaRepository.class);
        adapter = new OrganizationRepositoryAdapter(organizations, mock(LocationJpaRepository.class), policies,
                mock(OrganizationMemberJpaRepository.class), statusChanges);
        organization = new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería", IdentificationType.NIT,
                "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota",
                List.of(new Location(UUID.randomUUID(), "Sede", "Calle 1", UUID.randomUUID(), null)),
                OrganizationStatus.ACTIVE, NOW, new OperationPolicy(60, 43200, 1440), 2);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException("23505"), constraint));
    }

    @Test
    @DisplayName("The unique identification constraint of V4 becomes a DuplicateOrganizationException")
    void identificationViolationIsADuplicate() {
        // Arrange
        when(organizations.saveAndFlush(any(OrganizationEntity.class)))
                .thenThrow(violation("uk_organizations_identification"));

        // Act
        DuplicateOrganizationException ex = assertThrows(DuplicateOrganizationException.class,
                () -> adapter.register(organization, UUID.randomUUID(), NOW));

        // Assert
        assertSame(DataIntegrityViolationException.class, ex.getCause().getClass());
    }

    @Test
    @DisplayName("Any other integrity violation is rethrown untouched: it is not a duplicate")
    void otherViolationIsRethrown() {
        // Arrange
        DataIntegrityViolationException other = violation("fk_organizations_category");
        when(organizations.saveAndFlush(any(OrganizationEntity.class))).thenThrow(other);

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.register(organization, UUID.randomUUID(), NOW));

        // Assert
        assertSame(other, ex);
    }

    @Test
    @DisplayName("A violation that does not name its constraint is rethrown untouched")
    void unnamedViolationIsRethrown() {
        // Arrange
        DataIntegrityViolationException unnamed = new DataIntegrityViolationException("unknown");
        when(organizations.saveAndFlush(any(OrganizationEntity.class))).thenThrow(unnamed);

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.register(organization, UUID.randomUUID(), NOW));

        // Assert
        assertSame(unnamed, ex);
    }

    @Test
    @DisplayName("HU-003: the reason is written before the status, which the trigger on organizations demands")
    void reasonIsWrittenBeforeTheStatus() {
        // Arrange
        when(organizations.findById(organization.getId())).thenReturn(Optional.of(new OrganizationEntity()));

        // Act
        adapter.saveStatusChange(organization, OrganizationStatus.PENDING_APPROVAL, "RUT verificado", UUID.randomUUID(), 0);

        // Assert
        InOrder order = inOrder(statusChanges, organizations);
        order.verify(statusChanges).saveAndFlush(any(AccountStatusChangeEntity.class));
        order.verify(organizations).saveAndFlush(any(OrganizationEntity.class));
    }

    @Test
    @DisplayName("HU-002: the current version is closed before the new one is inserted, one current version at a time")
    void currentPolicyIsClosedBeforeTheNewOneIsInserted() {
        // Act
        adapter.savePolicy(organization, NOW);

        // Assert
        InOrder order = inOrder(policies);
        order.verify(policies).closeCurrent(organization.getId(), NOW);
        // Entities have no equals: the inserted row is matched by what matters
        order.verify(policies).saveAndFlush(argThat(entity -> entity.getVersion() == 2
                && entity.getOrganizationId().equals(organization.getId())
                && NOW.equals(entity.getEffectiveFrom())
                && entity.getReplacedAt() == null));
    }
}
