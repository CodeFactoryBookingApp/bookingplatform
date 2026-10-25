package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Facade of the catalog for the other modules: questions about services and resources that the
 * scheduling module asks before building an agenda on them (HU-006).
 */
@Service
public class CatalogLookup {

    private final ServiceRepository services;
    private final ResourceRepository resources;

    public CatalogLookup(ServiceRepository services, ResourceRepository resources) {
        this.services = services;
        this.resources = resources;
    }

    /** Whether the resource belongs to the organization and sits in that location. */
    @Transactional(readOnly = true)
    public boolean resourceAt(UUID organizationId, UUID resourceId, UUID locationId) {
        return resources.findById(organizationId, resourceId)
                .filter(resource -> resource.getLocationId().equals(locationId))
                .isPresent();
    }

    /** Whether the service belongs to the organization and is provided in that location. */
    @Transactional(readOnly = true)
    public boolean serviceOfferedAt(UUID organizationId, UUID serviceId, UUID locationId) {
        return services.findById(organizationId, serviceId)
                .filter(service -> service.getLocationIds().contains(locationId))
                .isPresent();
    }
}
