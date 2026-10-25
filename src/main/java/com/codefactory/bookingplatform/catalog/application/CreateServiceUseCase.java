package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-004 "Crear un servicio con recurso requerido": the owner or a manager defines a service,
 * where it is provided and what it needs. It is stored INACTIVE.
 */
@Service
public class CreateServiceUseCase {

    private final ServiceRepository services;
    private final ResourceRepository resources;
    private final OrganizationAccess access;

    public CreateServiceUseCase(ServiceRepository services, ResourceRepository resources, OrganizationAccess access) {
        this.services = services;
        this.resources = resources;
        this.access = access;
    }

    @Transactional
    public OfferedService create(UUID organizationId, UUID userId, OfferedService.Details details,
                                 List<UUID> locationIds, List<ResourceRequirement> requirements) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        OfferedService service = OfferedService.create(UUID.randomUUID(), organizationId, Details.trimmed(details),
                locationIds, requirements);
        requireOwnReferences(organizationId, service);
        try {
            services.saveNew(service);
        } catch (DuplicateNameException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME,
                    "The provider already has a service named '" + service.getName() + "'");
        }
        return service;
    }

    /** Locations and resource types travel as ids in the request; both must be the provider's own. */
    private void requireOwnReferences(UUID organizationId, OfferedService service) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<UUID> locationIds = service.getLocationIds();
        for (int i = 0; i < locationIds.size(); i++) {
            if (!access.ownsLocation(organizationId, locationIds.get(i))) {
                errors.put("locationIds[" + i + "]", "not a location of this provider");
            }
        }
        List<ResourceRequirement> requirements = service.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            if (resources.findType(organizationId, requirements.get(i).resourceTypeId()).isEmpty()) {
                errors.put("requirements[" + i + "].resourceTypeId", "not a resource type of this provider");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown location or resource type", errors);
        }
    }

    /** Text normalisation shared by creation and update. */
    static final class Details {

        private Details() {
        }

        static OfferedService.Details trimmed(OfferedService.Details details) {
            String description = details.description() == null || details.description().isBlank()
                    ? null : details.description().trim();
            return new OfferedService.Details(details.name().trim(), description, details.durationMinutes(),
                    details.preparationMinutes(), details.cleanupMinutes(), details.price(),
                    details.customerCapacity());
        }
    }
}
