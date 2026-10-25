package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-022 when reading a provider: members see their own, administrators any, everyone else 404. */
class FindOrganizationsUseCaseTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private OrganizationRepository organizations;
    private FindOrganizationsUseCase useCase;
    private final Organization organization = mock(Organization.class);

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        useCase = new FindOrganizationsUseCase(organizations, new OrganizationAccess(organizations));
        when(organizations.findById(ORGANIZATION)).thenReturn(Optional.of(organization));
    }

    @Test
    @DisplayName("An administrator reads any provider without being a member of it")
    void adminReadsWithoutMembership() {
        // Act
        Organization found = useCase.findOne(ORGANIZATION, USER, true);

        // Assert
        assertSame(organization, found);
        verify(organizations, never()).findMemberRole(any(), any());
    }

    @Test
    @DisplayName("HU-022: a non-member who is not an administrator gets 404")
    void nonMemberGetsNotFound() {
        // Arrange
        when(organizations.findMemberRole(ORGANIZATION, USER)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.findOne(ORGANIZATION, USER, false));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }
}
