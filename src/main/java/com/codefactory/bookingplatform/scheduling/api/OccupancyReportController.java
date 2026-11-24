package com.codefactory.bookingplatform.scheduling.api;

import com.codefactory.bookingplatform.scheduling.api.dto.OccupancyResponse;
import com.codefactory.bookingplatform.scheduling.application.OccupancyReportUseCase;
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reports/occupancy")
@Tag(name = "Occupancy", description = "HU-019 - reporte de ocupación por agenda")
public class OccupancyReportController {

    private final OccupancyReportUseCase useCase;

    public OccupancyReportController(OccupancyReportUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Occupancy of every agenda between two dates", description = "Includes from and to.")
    public List<OccupancyResponse> report(@PathVariable UUID organizationId, @RequestParam LocalDate from,
                                          @RequestParam LocalDate to, JwtAuthenticationToken auth) {
        return useCase.report(organizationId, AuthenticatedUser.from(auth).id(), from, to).stream()
                .map(OccupancyResponse::from).toList();
    }
}