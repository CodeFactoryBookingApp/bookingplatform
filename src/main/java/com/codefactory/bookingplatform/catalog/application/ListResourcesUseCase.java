package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The resource types and resources of a provider, for any of its members (HU-009, HU-022). */
@Service
public class ListResourcesUseCase {

    private final ResourceRepository resources;
    private final OrganizationAccess access;

    public ListResourcesUseCase(ResourceRepository resources, OrganizationAccess access) {
        this.resources = resources;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<ResourceType> types(UUID organizationId, UUID userId) {
        access.requireMember(organizationId, userId);
        return resources.findTypes(organizationId);
    }

    @Transactional(readOnly = true)
    public List<Resource> resources(UUID organizationId, UUID userId) {
        access.requireMember(organizationId, userId);
        return resources.findAll(organizationId);
    }
}
