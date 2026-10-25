package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceBookings;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-004 life of an existing service, for the owner or a manager: update its details, activate
 * it (HU-009 checks its resources), deactivate it, or delete it while nothing was ever booked.
 */
@Service
public class ManageServiceUseCase {

    private final ServiceRepository services;
    private final ResourceRepository resources;
    private final ServiceBookings bookings;
    private final OrganizationAccess access;

    public ManageServiceUseCase(ServiceRepository services, ResourceRepository resources, ServiceBookings bookings,
                                OrganizationAccess access) {
        this.services = services;
        this.resources = resources;
        this.bookings = bookings;
        this.access = access;
    }

    private OfferedService find(UUID organizationId, UUID userId, UUID serviceId) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        return services.findById(organizationId, serviceId).orElseThrow(ManageServiceUseCase::notFound);
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Service not found");
    }

    /** HU-004 "Cambio de duración no afecta reservas existentes": they store their own times. */
    @Transactional
    public OfferedService update(UUID organizationId, UUID userId, UUID serviceId, OfferedService.Details details) {
        OfferedService service = find(organizationId, userId, serviceId);
        service.update(CreateServiceUseCase.Details.trimmed(details));
        try {
            services.save(service);
        } catch (DuplicateNameException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME,
                    "The provider already has a service named '" + service.getName() + "'");
        }
        return service;
    }

    /** HU-009 "Activación de servicio sin recursos suficientes". */
    @Transactional
    public OfferedService activate(UUID organizationId, UUID userId, UUID serviceId) {
        OfferedService service = find(organizationId, userId, serviceId);
        service.activate(resources::countActive);
        services.save(service);
        return service;
    }

    @Transactional
    public OfferedService deactivate(UUID organizationId, UUID userId, UUID serviceId) {
        OfferedService service = find(organizationId, userId, serviceId);
        service.deactivate();
        services.save(service);
        return service;
    }

    /**
     * HU-004 "Desactivar un servicio con reservas": a service with bookings, past or future, is
     * not deleted, because the bookings keep pointing at it; it can be deactivated instead.
     */
    @Transactional
    public void delete(UUID organizationId, UUID userId, UUID serviceId) {
        OfferedService service = find(organizationId, userId, serviceId);
        if (bookings.exist(service.getId())) {
            throw new BusinessException(ErrorCode.HAS_BOOKINGS,
                    "The service has bookings; deactivate it so it no longer appears in the catalog");
        }
        services.delete(service.getId());
    }
}
