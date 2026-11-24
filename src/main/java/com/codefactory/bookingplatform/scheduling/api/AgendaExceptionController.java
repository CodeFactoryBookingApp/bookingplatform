package com.codefactory.bookingplatform.scheduling.api;

import com.codefactory.bookingplatform.scheduling.api.dto.AgendaExceptionRequests.CloseDate;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaExceptionRequests.SpecialHours;
import com.codefactory.bookingplatform.scheduling.api.dto.AgendaExceptionResponse;
import com.codefactory.bookingplatform.scheduling.application.ManageAgendaExceptionsUseCase;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HU-007 excepciones y bloqueos de una agenda: festivos, cierres puntuales y horarios especiales.
 * Mismo control de acceso que HU-006: cualquier miembro lee, el owner o un manager escribe.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/agendas/{agendaId}/exceptions")
@Tag(name = "Agenda exceptions", description = "HU-007 - festivos, cierres y horarios especiales")
public class AgendaExceptionController {

    private final ManageAgendaExceptionsUseCase useCase;

    public AgendaExceptionController(ManageAgendaExceptionsUseCase useCase) {
        this.useCase = useCase;
    }

    private static UUID user(JwtAuthenticationToken authentication) {
        return AuthenticatedUser.from(authentication).id();
    }

    @GetMapping
    @Operation(summary = "Exceptions of the agenda")
    public List<AgendaExceptionResponse> list(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                              JwtAuthenticationToken authentication) {
        return useCase.list(organizationId, user(authentication), agendaId).stream()
                .map(AgendaExceptionResponse::from).toList();
    }

    @PostMapping("/closures")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Close the agenda on a date",
            description = "409 if the agenda already has an exception for that date.")
    public AgendaExceptionResponse close(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                         @Valid @RequestBody CloseDate request, JwtAuthenticationToken authentication) {
        return AgendaExceptionResponse.from(useCase.close(organizationId, user(authentication), agendaId,
                request.date(), request.reason()));
    }

    @PostMapping("/special-hours")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Override the hours of the agenda on a date",
            description = "409 if the agenda already has an exception for that date.")
    public AgendaExceptionResponse specialHours(@PathVariable UUID organizationId, @PathVariable UUID agendaId,
                                                @Valid @RequestBody SpecialHours request,
                                                JwtAuthenticationToken authentication) {
        return AgendaExceptionResponse.from(useCase.specialHours(organizationId, user(authentication), agendaId,
                request.date(), request.startTime(), request.endTime(), request.reason()));
    }

    @DeleteMapping("/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an exception")
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID agendaId, @PathVariable UUID exceptionId,
                       JwtAuthenticationToken authentication) {
        useCase.delete(organizationId, user(authentication), agendaId, exceptionId);
    }
}