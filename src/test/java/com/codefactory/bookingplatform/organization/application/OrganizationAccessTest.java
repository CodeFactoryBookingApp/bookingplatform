package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-022 organization scope (ADR-0008): decision table over "is a member" x "role allowed".
 */
class OrganizationAccessTest {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private OrganizationRepository repository;
    private OrganizationAccess access;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        access = new OrganizationAccess(repository);
    }

    @Test
    @DisplayName("HU-022: someone outside the organization gets 404, the answer for one that does not exist")
    void nonMemberGetsNotFound() {
        // Arrange
        when(repository.findMemberRole(ORGANIZATION, USER)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> access.requireRole(ORGANIZATION, USER, MemberRole.OWNER));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        assertEquals("Organization not found", ex.getMessage());
    }

    @Test
    @DisplayName("HU-022: a member whose role is not allowed gets 403 with a message naming the role")
    void memberWithTheWrongRoleIsDenied() {
        // Arrange
        when(repository.findMemberRole(ORGANIZATION, USER)).thenReturn(Optional.of(MemberRole.STAFF));

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> access.requireRole(ORGANIZATION, USER, MemberRole.OWNER, MemberRole.MANAGER));

        // Assert
        assertEquals(ErrorCode.ACCESS_DENIED, ex.errorCode());
        assertTrue(ex.getMessage().contains("STAFF"), ex.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = MemberRole.class, names = {"OWNER", "MANAGER"})
    @DisplayName("HU-022: a member with an allowed role passes and gets its role back")
    void memberWithAnAllowedRolePasses(MemberRole role) {
        // Arrange
        when(repository.findMemberRole(ORGANIZATION, USER)).thenReturn(Optional.of(role));

        // Act
        MemberRole granted = access.requireRole(ORGANIZATION, USER, MemberRole.OWNER, MemberRole.MANAGER);

        // Assert
        assertEquals(role, granted);
    }

    @Test
    @DisplayName("Membership alone is enough when no particular role is required")
    void anyMemberPassesAMembershipCheck() {
        // Arrange
        when(repository.findMemberRole(ORGANIZATION, USER)).thenReturn(Optional.of(MemberRole.STAFF));

        // Act
        MemberRole role = access.requireMember(ORGANIZATION, USER);

        // Assert
        assertEquals(MemberRole.STAFF, role);
    }

    @Test
    @DisplayName("The zone of a location is the one the repository resolves (its own, or the organization's)")
    void locationZoneIsResolved() {
        // Arrange
        UUID location = UUID.randomUUID();
        when(repository.locationTimezone(ORGANIZATION, location)).thenReturn(Optional.of("America/Bogota"));

        // Act
        ZoneId zone = access.locationZone(ORGANIZATION, location);

        // Assert
        assertEquals(ZoneId.of("America/Bogota"), zone);
    }

    @Test
    @DisplayName("A location that is not the organization's has no zone: 404")
    void locationZoneOfAForeignLocationIsNotFound() {
        // Arrange
        UUID location = UUID.randomUUID();
        when(repository.locationTimezone(ORGANIZATION, location)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> access.locationZone(ORGANIZATION, location));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }

    @Test
    @DisplayName("Whether a location is the organization's is what the repository says")
    void ownsLocationAsksTheRepository() {
        // Arrange
        UUID location = UUID.randomUUID();
        when(repository.hasLocation(ORGANIZATION, location)).thenReturn(true);

        // Act
        boolean owns = access.ownsLocation(ORGANIZATION, location);

        // Assert
        assertTrue(owns);
    }
}
