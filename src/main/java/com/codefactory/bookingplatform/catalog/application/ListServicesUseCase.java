package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The services of a provider, active or not, for any of its members (HU-004, HU-022). */
@Service
public class ListServicesUseCase {

    private final ServiceRepository services;
    private final OrganizationAccess access;

    public ListServicesUseCase(ServiceRepository services, OrganizationAccess access) {
        this.services = services;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<OfferedService> all(UUID organizationId, UUID userId) {
        access.requireMember(organizationId, userId);
        return services.findAll(organizationId);
    }

    @Transactional(readOnly = true)
    public OfferedService one(UUID organizationId, UUID userId, UUID serviceId) {
        access.requireMember(organizationId, userId);
        return services.findById(organizationId, serviceId).orElseThrow(ManageServiceUseCase::notFound);
    }
}
