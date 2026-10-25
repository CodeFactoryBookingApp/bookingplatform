package com.codefactory.bookingplatform.identity.api;

import com.codefactory.bookingplatform.identity.application.ChangeClientStatusUseCase;
import com.codefactory.bookingplatform.identity.application.ListClientsUseCase;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.ClientSummary;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.shared.export.Csv;
import com.codefactory.bookingplatform.shared.listing.ListingParams;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HU-003 client administration. Everything under /api/v1/admin requires role ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/clients")
@Tag(name = "Client Administration", description = "HU-003 - listing, export, suspension and reactivation of clients")
public class AdminClientController {

    private final ListClientsUseCase listClientsUseCase;
    private final ChangeClientStatusUseCase changeClientStatusUseCase;

    public AdminClientController(ListClientsUseCase listClientsUseCase,
                                 ChangeClientStatusUseCase changeClientStatusUseCase) {
        this.listClientsUseCase = listClientsUseCase;
        this.changeClientStatusUseCase = changeClientStatusUseCase;
    }

    public record ClientRow(UUID id, String fullName, String document, String email, String phone, String city,
                            NotificationChannel notificationChannel, ClientStatus status, Instant registeredAt) {

        static ClientRow from(ClientSummary summary) {
            return new ClientRow(summary.id(), summary.fullName(), summary.document(), summary.email(),
                    summary.phone(), summary.city(), summary.notificationChannel(), summary.status(),
                    summary.registeredAt());
        }
    }

    public record ClientPage(List<ClientRow> items, long total, int page, int size) {

        static ClientPage from(ResultPage<ClientSummary> page) {
            ResultPage<ClientRow> rows = page.map(ClientRow::from);
            return new ClientPage(rows.items(), rows.total(), rows.page(), rows.size());
        }
    }

    public record StatusResponse(UUID id, ClientStatus status) {

        static StatusResponse from(Client client) {
            return new StatusResponse(client.getId(), client.getStatus());
        }
    }

    public record StatusChangeRequest(

            @NotBlank(message = "reason is required")
            @Size(max = 500, message = "reason must be at most 500 characters")
            String reason) {
    }

    static final List<String> CSV_HEADER = List.of("id", "nombre", "documento", "correo", "telefono", "ciudad",
            "canal_notificacion", "estado", "registrado");

    static List<String> csvRow(ClientSummary summary) {
        return List.of(summary.id().toString(), summary.fullName(), summary.document(), summary.email(),
                summary.phone(), summary.city(), summary.notificationChannel().name(), summary.status().name(),
                summary.registeredAt().toString());
    }

    private static ClientFilter filter(ClientStatus status, String city, LocalDate registeredFrom,
                                       LocalDate registeredTo, String sort, String direction, int page, int size) {
        return new ClientFilter(status, city, registeredFrom, registeredTo,
                ListingParams.sort(sort, ClientFilter.Sort.class), ListingParams.descending(direction), page, size);
    }

    @GetMapping
    @Operation(summary = "List clients",
            description = "Paginated, with combinable filters. Without filters, every client: that is "
                    + "\"Restablecer filtros\". Newest first by default.")
    @SuppressWarnings("java:S107") // one parameter per filter of the listing
    public ClientPage list(
            @RequestParam(required = false) ClientStatus status,
            @Parameter(description = "City, without caring about case or accents") @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @Parameter(description = "name, email, city, status or registered") @RequestParam(defaultValue = "registered") String sort,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ClientPage.from(listClientsUseCase.page(
                filter(status, city, registeredFrom, registeredTo, sort, direction, page, size)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export the filtered clients as CSV", description = "The same filters as the listing, every page.")
    public ResponseEntity<String> export(
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @RequestParam(defaultValue = "registered") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        // The export takes every page: page and size only need to be valid
        String csv = Csv.of(CSV_HEADER, listClientsUseCase.export(
                        filter(status, city, registeredFrom, registeredTo, sort, direction, 0, 1)).stream()
                .map(AdminClientController::csvRow)
                .toList());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Csv.MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clientes.csv\"")
                .body(csv);
    }

    @PostMapping("/{clientId}/suspension")
    @Operation(summary = "Suspend a client", description = "Demands a reason. A suspended client cannot confirm bookings.")
    public StatusResponse suspend(@PathVariable UUID clientId, @Valid @RequestBody StatusChangeRequest request,
                                  JwtAuthenticationToken authentication) {
        return StatusResponse.from(changeClientStatusUseCase.suspend(clientId, request.reason(),
                AuthenticatedUser.from(authentication).id()));
    }

    @PostMapping("/{clientId}/reactivation")
    @Operation(summary = "Reactivate a suspended client", description = "Demands a reason.")
    public StatusResponse reactivate(@PathVariable UUID clientId, @Valid @RequestBody StatusChangeRequest request,
                                     JwtAuthenticationToken authentication) {
        return StatusResponse.from(changeClientStatusUseCase.reactivate(clientId, request.reason(),
                AuthenticatedUser.from(authentication).id()));
    }
}
