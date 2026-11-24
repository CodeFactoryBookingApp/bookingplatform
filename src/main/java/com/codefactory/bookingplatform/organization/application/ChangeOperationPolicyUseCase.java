package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy.DefaultClosureStatus;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * HU-002 "Edición de parámetros de operación": the owner or a manager changes the operating rules.
 * The change applies to bookings made from now on; existing bookings keep the version they were
 * made under, because each one points at its policy row.
 */
@Service
public class ChangeOperationPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeOperationPolicyUseCase.class);

    private final OrganizationRepository organizations;
    private final OrganizationAccess access;
    private final Clock clock;

    public ChangeOperationPolicyUseCase(OrganizationRepository organizations, OrganizationAccess access, Clock clock) {
        this.organizations = organizations;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Organization change(UUID organizationId, UUID userId, int minNotice, int maxNotice,
                               int cancellationWindow, Boolean manualConfirmation) {
        return change(organizationId, userId, minNotice, maxNotice, cancellationWindow, manualConfirmation, null, null, null);
    }

    @Transactional
    public Organization change(UUID organizationId, UUID userId, int minNotice, int maxNotice,
                               int cancellationWindow, Boolean manualConfirmation, Integer maxReschedules) {
        return change(organizationId, userId, minNotice, maxNotice, cancellationWindow, manualConfirmation, maxReschedules, null, null);
    }

    @Transactional
    public Organization change(UUID organizationId, UUID userId, int minNotice, int maxNotice,
                               int cancellationWindow, Boolean manualConfirmation, Integer maxReschedules,
                               DefaultClosureStatus defaultClosureStatus, Integer autoCloseDelayHours) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(OrganizationAccess::notFound);
        OperationPolicy current = organization.getPolicy();
        boolean manual = manualConfirmation == null ? current.manualConfirmation() : manualConfirmation;
        int reschedules = maxReschedules == null ? current.maxReschedules() : maxReschedules;
        DefaultClosureStatus closure = defaultClosureStatus == null ? current.defaultClosureStatus() : defaultClosureStatus;
        int delayHours = autoCloseDelayHours == null ? current.autoCloseDelayHours() : autoCloseDelayHours;
        int version = organization.changePolicy(new OperationPolicy(minNotice, maxNotice, cancellationWindow, manual,
                reschedules, closure, delayHours));
        organizations.savePolicy(organization, clock.instant());
        log.info("Provider {} operating rules moved to version {}", organizationId, version);
        return organization;
    }
}
