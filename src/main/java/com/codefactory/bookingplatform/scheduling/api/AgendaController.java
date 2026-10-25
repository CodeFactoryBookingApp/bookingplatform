package com.codefactory.bookingplatform.scheduling.api;

import com.codefactory.bookingplatform.scheduling.api.dto.AgendaRequests.ChangeGranularity;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaRequests.CopyDay;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaRequests.CreateAgenda;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaRequests.ReplaceBlocks;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaResponse;
import com.codefactory.bookingplatform.scheduling.application.CreateAgendaUseCase;
import com.codefactory.bookingplatform.scheduling.application.ListAgendasUseCase;
import com.codefactory.bookingplatform.scheduling.application.ManageAgendaUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
 * HU-006 agendas of a provider. Any member reads; the owner or a manager changes (ADR-0008).
 * Outside the provider every path answers 404.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/agendas")
@Tag(name = "Agendas", description = "HU-006 - agendas, opening hours and granularity")
public class AgendaController {

    private final CreateAgendaUseCase createAgendaUseCase;
    private final ManageAgendaUseCase manageAgendaUseCase;
    private final ListAgendasUseCase listAgendasUseCase;

    public AgendaController(CreateAgendaUseCase createAgendaUseCase, ManageAgendaUseCase manageAgendaUseCase,
                            ListAgendasUseCase listAgendasUseCase) {
        this.createAgendaUseCase = createAgendaUseCase;
        this.manageAgendaUseCase = manageAgendaUseCase;
        this.listAgendasUseCase = listAgendasUseCase;
    }

    private static UUID user(JwtAuthenticationToken authentication) {
        return AuthenticatedUser.from(authentication).id();
    }

    @GetMapping
    @Operation(summary = "The agendas of the provider")
    public List<AgendaResponse> all(@PathVariable UUID organizationId, JwtAuthenticationToken authentication) {
        return listAgendasUseCase.all(organizationId, user(authentication)).stream()
                .map(AgendaResponse::from)
                .toList();
    }

    @GetMapping("/{agendaId}")
    @Operation(summary = "One agenda")
    public AgendaResponse one(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                              JwtAuthenticationToken authentication) {
        return AgendaResponse.from(listAgendasUseCase.one(organizationId, user(authentication), agendaId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an agenda",
            description = "Of a location, or of one resource in it, with granularity 15, 30 or 60, its validity, "
                    + "the services bookable in it and its weekly blocks. 400 if a block ends before it starts or "
                    + "two blocks overlap; 409 if another agenda of the same location or resource is valid in an "
                    + "overlapping period.")
    public AgendaResponse create(@PathVariable UUID organizationId, @Valid @RequestBody CreateAgenda request,
                                 JwtAuthenticationToken authentication) {
        return AgendaResponse.from(createAgendaUseCase.create(organizationId, user(authentication),
                new CreateAgendaUseCase.Definition(request.name(), request.locationId(), request.resourceId(),
                        request.slotMinutes(), request.validFrom(), request.validUntil(), request.serviceIds(),
                        request.blocksToDomain())));
    }

    @PutMapping("/{agendaId}/blocks")
    @Operation(summary = "Replace the weekly blocks",
            description = "409 HAS_FUTURE_BOOKINGS, listing them in details, if future bookings would fall outside "
                    + "the new opening hours; resolve them first.")
    public AgendaResponse replaceBlocks(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                        @Valid @RequestBody ReplaceBlocks request,
                                        JwtAuthenticationToken authentication) {
        return AgendaResponse.from(manageAgendaUseCase.replaceBlocks(organizationId, user(authentication), agendaId,
                request.blocksToDomain()));
    }

    @PostMapping("/{agendaId}/day-copies")
    @Operation(summary = "Copy the blocks of a day to other days",
            description = "The target days end up with exactly the blocks of the source day.")
    public AgendaResponse copyDay(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                  @Valid @RequestBody CopyDay request, JwtAuthenticationToken authentication) {
        return AgendaResponse.from(manageAgendaUseCase.copyDay(organizationId, user(authentication), agendaId,
                request.fromDay(), request.toDays()));
    }

    @PutMapping("/{agendaId}/granularity")
    @Operation(summary = "Change the granularity", description = "409 HAS_FUTURE_BOOKINGS while the agenda has future bookings.")
    public AgendaResponse changeGranularity(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                            @Valid @RequestBody ChangeGranularity request,
                                            JwtAuthenticationToken authentication) {
        return AgendaResponse.from(manageAgendaUseCase.changeGranularity(organizationId, user(authentication),
                agendaId, request.slotMinutes()));
    }
}
