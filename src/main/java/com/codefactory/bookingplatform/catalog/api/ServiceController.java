package com.codefactory.bookingplatform.catalog.api;

import com.codefactory.bookingplatform.catalog.api.dto.ServiceRequests.CreateService;
import com.codefactory.bookingplatform.catalog.api.dto.ServiceRequests.ServiceDetails;
import com.codefactory.bookingplatform.catalog.api.dto.ServiceResponse;
import com.codefactory.bookingplatform.catalog.application.CreateServiceUseCase;
import com.codefactory.bookingplatform.catalog.application.ListServicesUseCase;
import com.codefactory.bookingplatform.catalog.application.ManageServiceUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HU-004 services of a provider. Any member reads; the owner or a manager changes (ADR-0008).
 * Outside the provider every path answers 404.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/services")
@Tag(name = "Services", description = "HU-004 - services offered, their activation and deactivation")
public class ServiceController {

    private final CreateServiceUseCase createServiceUseCase;
    private final ManageServiceUseCase manageServiceUseCase;
    private final ListServicesUseCase listServicesUseCase;

    public ServiceController(CreateServiceUseCase createServiceUseCase, ManageServiceUseCase manageServiceUseCase,
                             ListServicesUseCase listServicesUseCase) {
        this.createServiceUseCase = createServiceUseCase;
        this.manageServiceUseCase = manageServiceUseCase;
        this.listServicesUseCase = listServicesUseCase;
    }

    private static UUID user(JwtAuthenticationToken authentication) {
        return AuthenticatedUser.from(authentication).id();
    }

    @GetMapping
    @Operation(summary = "The services of the provider, active or not")
    public List<ServiceResponse> all(@PathVariable UUID organizationId, JwtAuthenticationToken authentication) {
        return listServicesUseCase.all(organizationId, user(authentication)).stream()
                .map(ServiceResponse::from)
                .toList();
    }

    @GetMapping("/{serviceId}")
    @Operation(summary = "One service")
    public ServiceResponse one(@PathVariable UUID organizationId, @PathVariable UUID serviceId,
                               JwtAuthenticationToken authentication) {
        return ServiceResponse.from(listServicesUseCase.one(organizationId, user(authentication), serviceId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a service",
            description = "In the provider's locations, with at least one required resource type. It is created "
                    + "INACTIVE. 409 if the provider already has a service with that name.")
    public ServiceResponse create(@PathVariable UUID organizationId, @Valid @RequestBody CreateService request,
                                  JwtAuthenticationToken authentication) {
        return ServiceResponse.from(createServiceUseCase.create(organizationId, user(authentication),
                request.details().toDomain(), request.locationIds(), request.requirementsToDomain()));
    }

    @PutMapping("/{serviceId}")
    @Operation(summary = "Update the details of a service",
            description = "Name, description, duration, preparation, cleanup, price and capacity. Existing bookings "
                    + "keep their times. Locations and required resources are set when the service is created.")
    public ServiceResponse update(@PathVariable UUID organizationId, @PathVariable UUID serviceId,
                                  @Valid @RequestBody ServiceDetails request, JwtAuthenticationToken authentication) {
        return ServiceResponse.from(manageServiceUseCase.update(organizationId, user(authentication), serviceId,
                request.toDomain()));
    }

    @PostMapping("/{serviceId}/activation")
    @Operation(summary = "Activate a service",
            description = "409 INSUFFICIENT_RESOURCES if a location lacks the active resources it requires (HU-009).")
    public ServiceResponse activate(@PathVariable UUID organizationId, @PathVariable UUID serviceId,
                                    JwtAuthenticationToken authentication) {
        return ServiceResponse.from(manageServiceUseCase.activate(organizationId, user(authentication), serviceId));
    }

    @PostMapping("/{serviceId}/deactivation")
    @Operation(summary = "Deactivate a service",
            description = "It is kept with its bookings but no longer offered in the catalog.")
    public ServiceResponse deactivate(@PathVariable UUID organizationId, @PathVariable UUID serviceId,
                                      JwtAuthenticationToken authentication) {
        return ServiceResponse.from(manageServiceUseCase.deactivate(organizationId, user(authentication), serviceId));
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a service that was never booked",
            description = "409 HAS_BOOKINGS if it has any booking: deactivate it instead.")
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID serviceId,
                       JwtAuthenticationToken authentication) {
        manageServiceUseCase.delete(organizationId, user(authentication), serviceId);
    }
}
