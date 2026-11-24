package com.codefactory.bookingplatform.scheduling.api;

import com.codefactory.bookingplatform.scheduling.api.dto.AvailabilityResponse;
import com.codefactory.bookingplatform.scheduling.application.GetAvailabilityUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HU-008/HU-011: calendario de disponibilidad de una agenda. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/agendas/{agendaId}/availability")
@Tag(name = "Availability", description = "HU-008/HU-011 - franjas libres de una agenda")
public class AgendaAvailabilityController {

    private final GetAvailabilityUseCase useCase;

    public AgendaAvailabilityController(GetAvailabilityUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Free slots of the agenda between two dates",
            description = "Includes from and to; at most 62 days apart.")
    public List<AvailabilityResponse> calendar(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                               @RequestParam LocalDate from, @RequestParam LocalDate to,
                                               JwtAuthenticationToken authentication) {
        return useCase.calendar(organizationId, AuthenticatedUser.from(authentication).id(), agendaId, from, to)
                .stream().map(AvailabilityResponse::from).toList();
    }
}