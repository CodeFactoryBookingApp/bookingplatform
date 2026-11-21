package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.application.ServiceDemandUseCase;
import com.codefactory.bookingplatform.booking.domain.model.ServiceDemand.Report;
import com.codefactory.bookingplatform.shared.export.Csv;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reports/service-demand")
@Tag(name = "Service demand", description = "HU-020 - Usage, demand and comparison by period")
public class ServiceDemandController {

    private final ServiceDemandUseCase useCase;

    public ServiceDemandController(ServiceDemandUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Report service demand in the organization's timezone", description = "Includes from and excludes to.")
    public Report report(@PathVariable UUID organizationId, @RequestParam LocalDate from, @RequestParam LocalDate to,
                         JwtAuthenticationToken auth) {
        return useCase.report(organizationId, AuthenticatedUser.from(auth).id(), from, to);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export service metrics, period comparison and hourly demand")
    public ResponseEntity<String> export(@PathVariable UUID organizationId, @RequestParam LocalDate from,
                                         @RequestParam LocalDate to, JwtAuthenticationToken auth) {
        Report report = useCase.report(organizationId, AuthenticatedUser.from(auth).id(), from, to);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Csv.MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demanda-servicios.csv\"")
                .body(ServiceDemandCsv.format(report));
    }
}
