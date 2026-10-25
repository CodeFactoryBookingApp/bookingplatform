package com.codefactory.bookingplatform.catalog.api;

import com.codefactory.bookingplatform.catalog.api.dto.ResourceRequests.CreateResource;
import com.codefactory.bookingplatform.catalog.api.dto.ResourceRequests.CreateResourceType;
import com.codefactory.bookingplatform.catalog.api.dto.ResourceRequests.ScheduleMaintenance;
import com.codefactory.bookingplatform.catalog.api.dto.ResourceResponses.ResourceResponse;
import com.codefactory.bookingplatform.catalog.api.dto.ResourceResponses.ResourceTypeResponse;
import com.codefactory.bookingplatform.catalog.application.CreateResourceTypeUseCase;
import com.codefactory.bookingplatform.catalog.application.ListResourcesUseCase;
import com.codefactory.bookingplatform.catalog.application.RegisterResourceUseCase;
import com.codefactory.bookingplatform.catalog.application.RetireResourceUseCase;
import com.codefactory.bookingplatform.catalog.application.ScheduleMaintenanceUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * HU-009 resources of a provider. Any member reads; the owner or a manager changes (ADR-0008).
 * Outside the provider every path answers 404.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
@Tag(name = "Resources", description = "HU-009 - resource types, resources, maintenance and retirement")
public class ResourceController {

    private final CreateResourceTypeUseCase createResourceTypeUseCase;
    private final RegisterResourceUseCase registerResourceUseCase;
    private final ScheduleMaintenanceUseCase scheduleMaintenanceUseCase;
    private final RetireResourceUseCase retireResourceUseCase;
    private final ListResourcesUseCase listResourcesUseCase;
    private final Clock clock;

    @SuppressWarnings("java:S107") // one use case per operation, as in the rest of the API
    public ResourceController(CreateResourceTypeUseCase createResourceTypeUseCase,
                              RegisterResourceUseCase registerResourceUseCase,
                              ScheduleMaintenanceUseCase scheduleMaintenanceUseCase,
                              RetireResourceUseCase retireResourceUseCase,
                              ListResourcesUseCase listResourcesUseCase,
                              Clock clock) {
        this.createResourceTypeUseCase = createResourceTypeUseCase;
        this.registerResourceUseCase = registerResourceUseCase;
        this.scheduleMaintenanceUseCase = scheduleMaintenanceUseCase;
        this.retireResourceUseCase = retireResourceUseCase;
        this.listResourcesUseCase = listResourcesUseCase;
        this.clock = clock;
    }

    private static UUID user(JwtAuthenticationToken authentication) {
        return AuthenticatedUser.from(authentication).id();
    }

    @GetMapping("/resource-types")
    @Operation(summary = "The resource types of the provider")
    public List<ResourceTypeResponse> types(@PathVariable UUID organizationId, JwtAuthenticationToken authentication) {
        return listResourcesUseCase.types(organizationId, user(authentication)).stream()
                .map(ResourceTypeResponse::from)
                .toList();
    }

    @PostMapping("/resource-types")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a resource type", description = "409 if the provider already has one with that name.")
    public ResourceTypeResponse createType(@PathVariable UUID organizationId,
                                           @Valid @RequestBody CreateResourceType request,
                                           JwtAuthenticationToken authentication) {
        return ResourceTypeResponse.from(createResourceTypeUseCase.create(organizationId, user(authentication),
                request.name(), request.description()));
    }

    @GetMapping("/resources")
    @Operation(summary = "The resources of the provider, with their condition right now and their maintenance periods")
    public List<ResourceResponse> resources(@PathVariable UUID organizationId, JwtAuthenticationToken authentication) {
        return listResourcesUseCase.resources(organizationId, user(authentication)).stream()
                .map(resource -> ResourceResponse.from(resource, clock.instant()))
                .toList();
    }

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a resource",
            description = "Of one of the provider's types, in one of its locations. 409 if the location already "
                    + "has a resource with that name.")
    public ResourceResponse register(@PathVariable UUID organizationId, @Valid @RequestBody CreateResource request,
                                     JwtAuthenticationToken authentication) {
        return ResourceResponse.from(registerResourceUseCase.register(organizationId, user(authentication),
                request.resourceTypeId(), request.locationId(), request.name(), request.capacity()), clock.instant());
    }

    @PostMapping("/resources/{resourceId}/maintenances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a maintenance period",
            description = "The resource offers no slots during it. 409 if it overlaps another one or the resource is retired.")
    public ResourceResponse scheduleMaintenance(@PathVariable UUID organizationId, @PathVariable UUID resourceId,
                                                @Valid @RequestBody ScheduleMaintenance request,
                                                JwtAuthenticationToken authentication) {
        return ResourceResponse.from(scheduleMaintenanceUseCase.schedule(organizationId, user(authentication),
                resourceId, request.startsAt(), request.endsAt(), request.reason()), clock.instant());
    }

    @PostMapping("/resources/{resourceId}/retirement")
    @Operation(summary = "Retire a resource",
            description = "409 HAS_FUTURE_BOOKINGS while bookings are pending on it, with their number in details.")
    public ResourceResponse retire(@PathVariable UUID organizationId, @PathVariable UUID resourceId,
                                   JwtAuthenticationToken authentication) {
        return ResourceResponse.from(retireResourceUseCase.retire(organizationId, user(authentication), resourceId),
                clock.instant());
    }
}
