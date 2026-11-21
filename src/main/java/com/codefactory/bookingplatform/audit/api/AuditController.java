package com.codefactory.bookingplatform.audit.api;

import com.codefactory.bookingplatform.audit.application.ListAuditEventsUseCase;
import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import com.codefactory.bookingplatform.shared.export.Csv;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "Audit", description = "HU-023 - Platform and organization audit events")
public class AuditController {

    private static final String ADMIN_PATH = "/api/v1/admin/audit-events";
    private static final String SCOPED_PATH = "/api/v1/organizations/{organizationId}/audit-events";
    private static final List<String> CSV_HEADER = List.of("id", "fecha", "usuario", "rol", "accion",
            "tipo_recurso", "recurso", "organizacion", "resultado", "recurso_relacionado");

    private final ListAuditEventsUseCase useCase;

    public AuditController(ListAuditEventsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping({ADMIN_PATH, SCOPED_PATH})
    @Operation(summary = "List audit events", description = "The time range includes from and excludes to.")
    public ResultPage<AuditResponse> list(@PathVariable(required = false) UUID organizationId,
                                         @ParameterObject AuditFilterRequest filter,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         JwtAuthenticationToken authentication) {
        return useCase.page(AuthenticatedUser.from(authentication), organizationId, filter.toFilter(), page, size)
                .map(AuditResponse::from);
    }

    @GetMapping(value = {ADMIN_PATH + "/export", SCOPED_PATH + "/export"}, produces = "text/csv")
    @Operation(summary = "Export filtered audit events as CSV with personal identifiers masked")
    public ResponseEntity<String> export(@PathVariable(required = false) UUID organizationId,
                                         @ParameterObject AuditFilterRequest filter,
                                         JwtAuthenticationToken authentication) {
        List<List<String>> rows = useCase.export(AuthenticatedUser.from(authentication), organizationId,
                filter.toFilter()).stream().map(AuditController::csvRow).toList();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Csv.MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria.csv\"")
                .body(Csv.of(CSV_HEADER, rows));
    }

    private static List<String> csvRow(AuditEntry entry) {
        AuditEvent event = entry.event();
        String resource = "USER".equals(event.resourceType()) ? masked(event.resourceId())
                : event.resourceId().toString();
        return List.of(entry.id().toString(), event.occurredAt().toString(), masked(event.actorId()),
                event.actorRole(), event.action(), event.resourceType(), resource,
                Objects.toString(event.organizationId(), ""), event.result(),
                Objects.toString(event.relatedResourceId(), ""));
    }

    private static String masked(UUID id) {
        return id == null ? "" : "****" + id.toString().substring(32);
    }

    public record AuditFilterRequest(Instant from, Instant to, UUID actorId, String action) {
        AuditFilter toFilter() {
            return new AuditFilter(from, to, actorId, action);
        }
    }

    public record AuditResponse(UUID id, Instant occurredAt, UUID actorId, String actorRole, String action,
                                String resourceType, UUID resourceId, UUID organizationId, String result,
                                UUID relatedResourceId) {
        static AuditResponse from(AuditEntry entry) {
            AuditEvent event = entry.event();
            return new AuditResponse(entry.id(), event.occurredAt(), event.actorId(), event.actorRole(),
                    event.action(), event.resourceType(), event.resourceId(), event.organizationId(), event.result(),
                    event.relatedResourceId());
        }
    }
}
