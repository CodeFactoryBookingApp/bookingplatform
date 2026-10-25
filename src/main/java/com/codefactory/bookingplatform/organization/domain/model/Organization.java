package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service provider aggregate root (HU-002). Pure domain model: no Spring, no JPA.
 *
 * <p>A provider is born PENDING_APPROVAL and only an administrator moves it to ACTIVE. Its
 * operating rules are versioned: every change produces a new version, and existing bookings keep
 * the one that applied when they were made.
 */
@Getter
public class Organization {

    private final UUID id;
    private final UUID categoryId;
    private final String name;
    private final IdentificationType identificationType;
    private final String identificationNumber;
    private final String contactEmail;
    private final String contactPhone;
    private final String timezone;
    private final List<Location> locations;
    private OrganizationStatus status;
    private Instant approvedAt;
    private OperationPolicy policy;
    private int policyVersion;

    @SuppressWarnings("java:S107") // reconstitution from storage needs every field
    public Organization(UUID id, UUID categoryId, String name, IdentificationType identificationType,
                        String identificationNumber, String contactEmail, String contactPhone, String timezone,
                        List<Location> locations, OrganizationStatus status, Instant approvedAt,
                        OperationPolicy policy, int policyVersion) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.identificationType = identificationType;
        this.identificationNumber = identificationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.timezone = timezone;
        this.locations = List.copyOf(locations);
        this.status = status;
        this.approvedAt = approvedAt;
        this.policy = policy;
        this.policyVersion = policyVersion;
    }

    /** HU-002: a new provider with at least one branch, pending approval, on policy version 1. */
    @SuppressWarnings("java:S107")
    public static Organization register(UUID id, UUID categoryId, String name, IdentificationType identificationType,
                                        String identificationNumber, String contactEmail, String contactPhone,
                                        String timezone, List<Location> locations, OperationPolicy policy) {
        if (locations == null || locations.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A provider needs at least one location",
                    Map.of("locations", "at least one location is required"));
        }
        requireZone(timezone, "timezone");
        for (int i = 0; i < locations.size(); i++) {
            String locationZone = locations.get(i).timezone();
            if (locationZone != null) {
                requireZone(locationZone, "locations[" + i + "].timezone");
            }
        }
        return new Organization(id, categoryId, name, identificationType, identificationNumber, contactEmail,
                contactPhone, timezone, locations, OrganizationStatus.PENDING_APPROVAL, null, policy, 1);
    }

    /**
     * The schema does not check that a time zone is a real IANA zone; this does. Fixed offsets
     * such as "-05:00" are refused too: they ignore daylight saving rules.
     */
    private static void requireZone(String zone, String field) {
        if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown time zone",
                    Map.of(field, "must be an IANA time zone such as America/Bogota"));
        }
    }

    /** HU-002: an administrator validates the provider, which becomes visible and reservable. */
    public void approve(Instant at) {
        if (status != OrganizationStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a provider pending approval can be approved; this one is " + status);
        }
        this.status = OrganizationStatus.ACTIVE;
        this.approvedAt = at;
    }

    /**
     * HU-003 "Suspender un proveedor con reservas futuras": an administrator takes an active
     * provider out of the catalog. With future bookings the suspension has to be confirmed first,
     * and the refusal says how many bookings it affects. The bookings themselves are kept.
     */
    public void suspend(int futureBookings, boolean confirmed) {
        requireStatus(OrganizationStatus.ACTIVE, "suspended");
        if (futureBookings > 0 && !confirmed) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED,
                    "The provider has " + futureBookings + " future bookings; confirm the suspension to go ahead",
                    Map.of("affectedBookings", String.valueOf(futureBookings)));
        }
        this.status = OrganizationStatus.SUSPENDED;
    }

    /** HU-003: a suspended provider is visible and reservable again. Its approval date stands. */
    public void reactivate() {
        requireStatus(OrganizationStatus.SUSPENDED, "reactivated");
        this.status = OrganizationStatus.ACTIVE;
    }

    /** HU-003: an administrator turns down a provider pending approval. */
    public void reject() {
        requireStatus(OrganizationStatus.PENDING_APPROVAL, "rejected");
        this.status = OrganizationStatus.REJECTED;
    }

    private void requireStatus(OrganizationStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a " + expected + " provider can be " + action + "; this one is " + status);
        }
    }

    /**
     * HU-002: new operating rules for the bookings made from now on. Returns the new version;
     * existing bookings keep pointing at theirs.
     */
    public int changePolicy(OperationPolicy newPolicy) {
        if (status != OrganizationStatus.ACTIVE && status != OrganizationStatus.PENDING_APPROVAL) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A " + status + " provider cannot change its operating rules");
        }
        this.policy = newPolicy;
        this.policyVersion++;
        return policyVersion;
    }
}
