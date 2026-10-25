package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.MaintenanceWindow;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** HU-009 "Recurso en mantenimiento en un rango de fechas": the resource offers no slots meanwhile. */
@Service
public class ScheduleMaintenanceUseCase {

    private final ResourceRepository resources;
    private final OrganizationAccess access;

    public ScheduleMaintenanceUseCase(ResourceRepository resources, OrganizationAccess access) {
        this.resources = resources;
        this.access = access;
    }

    @Transactional
    public Resource schedule(UUID organizationId, UUID userId, UUID resourceId, Instant startsAt, Instant endsAt,
                             String reason) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        Resource resource = resources.findById(organizationId, resourceId)
                .orElseThrow(ResourceNotFound::exception);
        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        MaintenanceWindow window = resource.scheduleMaintenance(
                new MaintenanceWindow(UUID.randomUUID(), startsAt, endsAt, trimmedReason));
        resources.addMaintenance(resource.getId(), window);
        return resource;
    }
}
