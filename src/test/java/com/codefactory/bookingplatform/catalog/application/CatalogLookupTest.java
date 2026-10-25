package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.catalog.domain.model.OfferedService;
import com.codefactory.bookingplatform.catalog.domain.model.Resource;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceRequirement;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceStatus;
import com.codefactory.bookingplatform.catalog.domain.model.ServiceStatus;
import com.codefactory.bookingplatform.catalog.domain.port.ResourceRepository;
import com.codefactory.bookingplatform.catalog.domain.port.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The questions the scheduling module asks the catalog before building an agenda (HU-006). */
class CatalogLookupTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID HERE = UUID.randomUUID();
    private static final UUID ELSEWHERE = UUID.randomUUID();

    private ServiceRepository services;
    private ResourceRepository resources;
    private CatalogLookup lookup;

    @BeforeEach
    void setUp() {
        services = mock(ServiceRepository.class);
        resources = mock(ResourceRepository.class);
        lookup = new CatalogLookup(services, resources);
    }

    @Test
    @DisplayName("A resource is at a location only if it is the provider's and sits there")
    void resourceAtItsLocation() {
        // Arrange
        Resource resource = new Resource(UUID.randomUUID(), ORG, UUID.randomUUID(), HERE, "Silla", 1,
                ResourceStatus.ACTIVE, List.of());
        when(resources.findById(ORG, resource.getId())).thenReturn(Optional.of(resource));

        // Act
        boolean here = lookup.resourceAt(ORG, resource.getId(), HERE);

        // Assert
        assertTrue(here);
        assertFalse(lookup.resourceAt(ORG, resource.getId(), ELSEWHERE));
        assertFalse(lookup.resourceAt(ORG, UUID.randomUUID(), HERE), "a resource that is not the provider's");
    }

    @Test
    @DisplayName("A service is offered at a location only if it is the provider's and lists that location")
    void serviceOfferedAtItsLocations() {
        // Arrange
        OfferedService service = new OfferedService(UUID.randomUUID(), ORG,
                new OfferedService.Details("Corte", null, 30, 0, 0, BigDecimal.TEN, 1), ServiceStatus.ACTIVE,
                List.of(HERE), List.of(new ResourceRequirement(UUID.randomUUID(), 1)));
        when(services.findById(ORG, service.getId())).thenReturn(Optional.of(service));

        // Act
        boolean here = lookup.serviceOfferedAt(ORG, service.getId(), HERE);

        // Assert
        assertTrue(here);
        assertFalse(lookup.serviceOfferedAt(ORG, service.getId(), ELSEWHERE));
        assertFalse(lookup.serviceOfferedAt(ORG, UUID.randomUUID(), HERE), "a service that is not the provider's");
    }
}
