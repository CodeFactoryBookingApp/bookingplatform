package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * HU-002 "Aprobación del proveedor": an administrator validates a pending provider, which becomes
 * ACTIVE, and the provider is told by email. The reason is stored with the change (HU-003).
 *
 * <p>The email is not sent here. {@link ProviderApproved} is published and
 * {@link ProviderApprovalMailer} sends it once the approval has committed: a mail server that is
 * down must not undo an approval, and an approval that rolls back must not announce itself.
 */
@Service
public class ApproveProviderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveProviderUseCase.class);

    private final OrganizationRepository organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApproveProviderUseCase(OrganizationRepository organizations, ApplicationEventPublisher events, Clock clock) {
        this.organizations = organizations;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Organization approve(UUID organizationId, String reason, UUID adminId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(OrganizationAccess::notFound);
        OrganizationStatus from = organization.getStatus();
        organization.approve(clock.instant());
        organizations.saveStatusChange(organization, from, reason.trim(), adminId, 0);
        events.publishEvent(new ProviderApproved(organization.getId(), organization.getName(),
                organization.getContactEmail()));
        log.info("Provider {} approved by {}", organization.getId(), adminId);
        return organization;
    }
}
