package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.ProviderRegistrationResponse;
import com.codefactory.bookingplatform.organization.api.dto.RegisterProviderRequest;
import com.codefactory.bookingplatform.organization.application.RegisterProviderCommand;
import com.codefactory.bookingplatform.organization.application.RegisterProviderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/provider-registrations")
@Tag(name = "Provider Registration", description = "HU-002 - service provider self-registration")
public class ProviderRegistrationController {

    private final RegisterProviderUseCase registerProviderUseCase;

    public ProviderRegistrationController(RegisterProviderUseCase registerProviderUseCase) {
        this.registerProviderUseCase = registerProviderUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a service provider",
            description = "Creates the owner account (role PROVIDER) and the business with at least one location, "
                    + "in status PENDING_APPROVAL. Rejects a duplicated identification, an unknown category or city "
                    + "and a minimum booking notice greater than the maximum.")
    public ProviderRegistrationResponse register(@Valid @RequestBody RegisterProviderRequest request) {
        RegisterProviderCommand command = new RegisterProviderCommand(
                request.email(),
                request.password(),
                request.name(),
                request.categoryId(),
                request.identificationType(),
                request.identificationNumber(),
                request.contactEmail(),
                request.contactPhone(),
                request.timezone(),
                request.locations().stream()
                        .map(location -> new RegisterProviderCommand.LocationData(location.name(),
                                location.address(), location.cityId(), location.timezone()))
                        .toList(),
                request.bookingNoticeMinutesMin(),
                request.bookingNoticeMinutesMax(),
                request.freeCancellationWindowMinutes());
        return ProviderRegistrationResponse.from(registerProviderUseCase.register(command));
    }
}
