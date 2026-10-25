package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceBookings;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** HU-009 "No se puede retirar un recurso con reservas futuras": retired for good, once nothing is pending on it. */
@Service
public class RetireResourceUseCase {

    private final ResourceRepository resources;
    private final ResourceBookings bookings;
    private final OrganizationAccess access;
    private final Clock clock;

    public RetireResourceUseCase(ResourceRepository resources, ResourceBookings bookings, OrganizationAccess access,
                                 Clock clock) {
        this.resources = resources;
        this.bookings = bookings;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Resource retire(UUID organizationId, UUID userId, UUID resourceId) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        Resource resource = resources.findById(organizationId, resourceId)
                .orElseThrow(ResourceNotFound::exception);
        resource.retire(bookings.countFuture(resourceId, clock.instant()));
        resources.saveStatus(resource);
        return resource;
    }
}
