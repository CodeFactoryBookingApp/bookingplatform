package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationBookings;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * HU-003 status changes of a provider by an administrator: suspend, reactivate, reject. Each one
 * demands a reason, stored with the change; approving is {@link ApproveProviderUseCase}.
 */
@Service
public class ChangeProviderStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeProviderStatusUseCase.class);

    private final OrganizationRepository organizations;
    private final OrganizationBookings bookings;
    private final Clock clock;

    public ChangeProviderStatusUseCase(OrganizationRepository organizations, OrganizationBookings bookings,
                                       Clock clock) {
        this.organizations = organizations;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * HU-003 "Suspender un proveedor con reservas futuras": without {@code confirmed}, a provider
     * with future bookings is not suspended and the answer says how many there are. The number is
     * stored with the change.
     */
    @Transactional
    public Organization suspend(UUID organizationId, String reason, boolean confirmed, UUID adminId) {
        int affected = bookings.countFuture(organizationId, clock.instant());
        return change(organizationId, reason, adminId, affected,
                organization -> organization.suspend(affected, confirmed));
    }

    @Transactional
    public Organization reactivate(UUID organizationId, String reason, UUID adminId) {
        return change(organizationId, reason, adminId, 0, Organization::reactivate);
    }

    @Transactional
    public Organization reject(UUID organizationId, String reason, UUID adminId) {
        return change(organizationId, reason, adminId, 0, Organization::reject);
    }

    private Organization change(UUID organizationId, String reason, UUID adminId, int affectedBookings,
                                Consumer<Organization> transition) {
        Organization organization = organizations.findById(organizationId).orElseThrow(OrganizationAccess::notFound);
        OrganizationStatus from = organization.getStatus();
        transition.accept(organization);
        organizations.saveStatusChange(organization, from, reason.trim(), adminId, affectedBookings);
        log.info("Provider {} moved from {} to {} by {}", organizationId, from, organization.getStatus(), adminId);
        return organization;
    }
}
