package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-002 provider aggregate: what a registration must contain, and the transitions of its status
 * and of its operating rules (state transition testing over OrganizationStatus).
 */
class OrganizationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final OperationPolicy POLICY = new OperationPolicy(60, 43200, 1440);

    private static Location location(String timezone) {
        return new Location(UUID.randomUUID(), "Sede Poblado", "Cra 43A # 5-15", UUID.randomUUID(), timezone);
    }

    private static Organization register(String timezone, List<Location> locations) {
        return Organization.register(UUID.randomUUID(), UUID.randomUUID(), "Barbería El Poblado",
                IdentificationType.NIT, "900123456-1", "contacto@barberia.com", "6041112233", timezone,
                locations, POLICY);
    }

    private static Organization withStatus(OrganizationStatus status) {
        return new Organization(UUID.randomUUID(), UUID.randomUUID(), "Barbería", IdentificationType.NIT,
                "900123456-1", "contacto@barberia.com", "6041112233", "America/Bogota", List.of(location(null)),
                status, null, POLICY, 3);
    }

    @Test
    @DisplayName("HU-002: a registration with one location is PENDING_APPROVAL, not approved, on policy version 1")
    void registrationStartsPendingOnVersionOne() {
        // Act
        Organization organization = register("America/Bogota", List.of(location(null)));

        // Assert
        assertEquals(OrganizationStatus.PENDING_APPROVAL, organization.getStatus());
        assertNull(organization.getApprovedAt());
        assertEquals(1, organization.getPolicyVersion());
        assertEquals(POLICY, organization.getPolicy());
    }

    @Test
    @DisplayName("HU-002: a provider without any location is rejected")
    void registrationWithoutLocationsIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> register("America/Bogota", List.of()));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertTrue(ex.details().containsKey("locations"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"Bogota", "-05:00", ""})
    @DisplayName("A provider time zone that is not an IANA region is rejected, fixed offsets included")
    void unknownProviderTimezoneIsRejected(String timezone) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> register(timezone, List.of(location(null))));

        // Assert
        assertTrue(ex.details().containsKey("timezone"), ex.details()::toString);
    }

    @Test
    @DisplayName("A location time zone is checked too, and the error names the location by position")
    void unknownLocationTimezoneIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> register("America/Bogota", List.of(location(null), location("Medellin"))));

        // Assert
        assertTrue(ex.details().containsKey("locations[1].timezone"), ex.details()::toString);
    }

    @Test
    @DisplayName("A location without its own time zone uses the provider one and is accepted")
    void locationWithoutTimezoneIsAccepted() {
        // Act
        Organization organization = register("America/Bogota", List.of(location(null), location("America/Lima")));

        // Assert
        assertEquals(2, organization.getLocations().size());
    }

    @Test
    @DisplayName("HU-002: approving a pending provider makes it ACTIVE and records when")
    void approvingPendingActivates() {
        // Arrange
        Organization organization = withStatus(OrganizationStatus.PENDING_APPROVAL);

        // Act
        organization.approve(NOW);

        // Assert
        assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
        assertEquals(NOW, organization.getApprovedAt());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = OrganizationStatus.class, names = {"ACTIVE", "SUSPENDED", "REJECTED"})
    @DisplayName("Only a pending provider can be approved: any other status is a 409 and nothing changes")
    void approvingANonPendingProviderIsRejected(OrganizationStatus status) {
        // Arrange
        Organization organization = withStatus(status);

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> organization.approve(NOW));

        // Assert
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        assertEquals(status, organization.getStatus());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = OrganizationStatus.class, names = {"ACTIVE", "PENDING_APPROVAL"})
    @DisplayName("HU-002: changing the operating rules moves to the next version")
    void changingThePolicyBumpsTheVersion(OrganizationStatus status) {
        // Arrange
        Organization organization = withStatus(status);
        OperationPolicy newPolicy = new OperationPolicy(120, 20160, 720);

        // Act
        int version = organization.changePolicy(newPolicy);

        // Assert
        assertEquals(4, version);
        assertEquals(4, organization.getPolicyVersion());
        assertEquals(newPolicy, organization.getPolicy());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = OrganizationStatus.class, names = {"SUSPENDED", "REJECTED"})
    @DisplayName("A suspended or rejected provider cannot change its operating rules")
    void suspendedOrRejectedCannotChangeThePolicy(OrganizationStatus status) {
        // Arrange
        Organization organization = withStatus(status);

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> organization.changePolicy(new OperationPolicy(0, 10, 0)));

        // Assert
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.errorCode());
        assertEquals(3, organization.getPolicyVersion());
    }
}
