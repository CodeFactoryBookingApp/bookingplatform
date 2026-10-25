package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HU-009 "instancias asociadas a una sede": the owner or a manager adds a concrete resource of
 * one of its types, in one of its locations. Both ids travel in the request, so both are checked
 * against the provider; the database checks them again (V7).
 */
@Service
public class RegisterResourceUseCase {

    private final ResourceRepository resources;
    private final OrganizationAccess access;

    public RegisterResourceUseCase(ResourceRepository resources, OrganizationAccess access) {
        this.resources = resources;
        this.access = access;
    }

    @Transactional
    public Resource register(UUID organizationId, UUID userId, UUID resourceTypeId, UUID locationId, String name,
                             int capacity) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        Map<String, String> errors = new LinkedHashMap<>();
        if (resources.findType(organizationId, resourceTypeId).isEmpty()) {
            errors.put("resourceTypeId", "not a resource type of this provider");
        }
        if (!access.ownsLocation(organizationId, locationId)) {
            errors.put("locationId", "not a location of this provider");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown resource type or location", errors);
        }
        Resource resource = Resource.create(UUID.randomUUID(), organizationId, resourceTypeId, locationId,
                name.trim(), capacity);
        try {
            resources.saveNew(resource);
        } catch (DuplicateNameException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME,
                    "The location already has a resource named '" + resource.getName() + "'");
        }
        return resource;
    }
}
