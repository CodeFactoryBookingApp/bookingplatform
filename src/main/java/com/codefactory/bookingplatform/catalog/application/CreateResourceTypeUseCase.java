package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** HU-009 "Crear un tipo de recurso": the owner or a manager defines a kind of resource. */
@Service
public class CreateResourceTypeUseCase {

    private final ResourceRepository resources;
    private final OrganizationAccess access;

    public CreateResourceTypeUseCase(ResourceRepository resources, OrganizationAccess access) {
        this.resources = resources;
        this.access = access;
    }

    @Transactional
    public ResourceType create(UUID organizationId, UUID userId, String name, String description) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        String trimmedDescription = description == null || description.isBlank() ? null : description.trim();
        ResourceType type = new ResourceType(UUID.randomUUID(), organizationId, name.trim(), trimmedDescription);
        try {
            resources.saveType(type);
        } catch (DuplicateNameException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME,
                    "The provider already has a resource type named '" + type.name() + "'");
        }
        return type;
    }
}
