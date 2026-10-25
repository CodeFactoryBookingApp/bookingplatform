package com.codefactory.bookingplatform.organization.domain.port;

import com.codefactory.bookingplatform.organization.domain.model.DuplicateOrganizationException;
import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    boolean existsByIdentification(IdentificationType type, String number);

    /**
     * Stores a new provider with its locations, policy version 1 effective from {@code at}, and
     * {@code ownerUserId} as its OWNER. Writes before returning, so a caller that has to undo work
     * done elsewhere still sees the failure.
     *
     * @throws DuplicateOrganizationException if another provider already holds the identification
     */
    void register(Organization organization, UUID ownerUserId, Instant at);

    Optional<Organization> findById(UUID id);

    /** The providers the user belongs to, in any role. */
    List<Organization> findByMember(UUID userId);

    Optional<MemberRole> findMemberRole(UUID organizationId, UUID userId);

    boolean hasLocation(UUID organizationId, UUID locationId);

    /**
     * Stores the new status together with the reason, which the database demands for every
     * status change of an organization (HU-003).
     */
    void saveStatusChange(Organization organization, OrganizationStatus from, String reason, UUID changedBy);

    /** Closes the current policy version at {@code at} and stores the organization's new one from then on. */
    void savePolicy(Organization organization, Instant at);
}
