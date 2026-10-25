package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.AdminProviderDtos;
import com.codefactory.bookingplatform.organization.api.dto.AdminProviderDtos.ProviderPage;
import com.codefactory.bookingplatform.organization.api.dto.AdminProviderDtos.SuspensionRequest;
import com.codefactory.bookingplatform.organization.api.dto.OrganizationResponse;
import com.codefactory.bookingplatform.organization.api.dto.StatusChangeRequest;
import com.codefactory.bookingplatform.organization.application.ApproveProviderUseCase;
import com.codefactory.bookingplatform.organization.application.ChangeProviderStatusUseCase;
import com.codefactory.bookingplatform.organization.application.ListProvidersUseCase;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.model.ProviderFilter;
import com.codefactory.bookingplatform.shared.export.Csv;
import com.codefactory.bookingplatform.shared.listing.ListingParams;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.UUID;

/** Platform administration of providers (HU-002, HU-003). Everything under /api/v1/admin requires role ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Tag(name = "Provider Administration", description = "HU-002, HU-003 - listing, approval, suspension and reactivation of providers")
public class AdminOrganizationController {

    private final ApproveProviderUseCase approveProviderUseCase;
    private final ChangeProviderStatusUseCase changeProviderStatusUseCase;
    private final ListProvidersUseCase listProvidersUseCase;

    public AdminOrganizationController(ApproveProviderUseCase approveProviderUseCase,
                                       ChangeProviderStatusUseCase changeProviderStatusUseCase,
                                       ListProvidersUseCase listProvidersUseCase) {
        this.approveProviderUseCase = approveProviderUseCase;
        this.changeProviderStatusUseCase = changeProviderStatusUseCase;
        this.listProvidersUseCase = listProvidersUseCase;
    }

    private static UUID admin(JwtAuthenticationToken authentication) {
        return AuthenticatedUser.from(authentication).id();
    }

    @SuppressWarnings("java:S107") // one parameter per filter of the listing
    private static ProviderFilter filter(OrganizationStatus status, UUID categoryId, String city,
                                         LocalDate registeredFrom, LocalDate registeredTo, String sort,
                                         String direction, int page, int size) {
        return new ProviderFilter(status, categoryId, city, registeredFrom, registeredTo,
                ListingParams.sort(sort, ProviderFilter.Sort.class), ListingParams.descending(direction), page, size);
    }

    @GetMapping
    @Operation(summary = "List providers",
            description = "Paginated, with combinable filters. Without filters, every provider: that is "
                    + "\"Restablecer filtros\". Newest first by default.")
    @SuppressWarnings("java:S107")
    public ProviderPage list(
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "City name, without caring about case or accents") @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @Parameter(description = "name, status, category or registered") @RequestParam(defaultValue = "registered") String sort,
            @Parameter(description = "asc or desc") @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ProviderPage.from(listProvidersUseCase.page(
                filter(status, categoryId, city, registeredFrom, registeredTo, sort, direction, page, size)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export the filtered providers as CSV", description = "The same filters as the listing, every page.")
    @SuppressWarnings("java:S107")
    public ResponseEntity<String> export(
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @RequestParam(defaultValue = "registered") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        // The export takes every page: page and size only need to be valid
        String csv = Csv.of(AdminProviderDtos.CSV_HEADER, listProvidersUseCase.export(
                        filter(status, categoryId, city, registeredFrom, registeredTo, sort, direction, 0, 1)).stream()
                .map(AdminProviderDtos::csvRow)
                .toList());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Csv.MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"proveedores.csv\"")
                .body(csv);
    }

    @PostMapping("/{organizationId}/approval")
    @Operation(summary = "Approve a provider pending approval",
            description = "Moves it to ACTIVE, stores the reason with the change and emails the provider. "
                    + "409 if the provider is not pending approval.")
    public OrganizationResponse approve(@PathVariable UUID organizationId,
                                        @Valid @RequestBody StatusChangeRequest request,
                                        JwtAuthenticationToken authentication) {
        return OrganizationResponse.from(approveProviderUseCase.approve(organizationId, request.reason(),
                admin(authentication)));
    }

    @PostMapping("/{organizationId}/suspension")
    @Operation(summary = "Suspend an active provider",
            description = "Demands a reason. With future bookings, the first call answers 409 CONFIRMATION_REQUIRED "
                    + "with their number in details.affectedBookings; repeat it with \"confirm\": true. The bookings are kept.")
    public OrganizationResponse suspend(@PathVariable UUID organizationId, @Valid @RequestBody SuspensionRequest request,
                                        JwtAuthenticationToken authentication) {
        return OrganizationResponse.from(changeProviderStatusUseCase.suspend(organizationId, request.reason(),
                request.confirmed(), admin(authentication)));
    }

    @PostMapping("/{organizationId}/reactivation")
    @Operation(summary = "Reactivate a suspended provider", description = "Demands a reason.")
    public OrganizationResponse reactivate(@PathVariable UUID organizationId,
                                           @Valid @RequestBody StatusChangeRequest request,
                                           JwtAuthenticationToken authentication) {
        return OrganizationResponse.from(changeProviderStatusUseCase.reactivate(organizationId, request.reason(),
                admin(authentication)));
    }

    @PostMapping("/{organizationId}/rejection")
    @Operation(summary = "Reject a provider pending approval", description = "Demands a reason.")
    public OrganizationResponse reject(@PathVariable UUID organizationId,
                                       @Valid @RequestBody StatusChangeRequest request,
                                       JwtAuthenticationToken authentication) {
        return OrganizationResponse.from(changeProviderStatusUseCase.reject(organizationId, request.reason(),
                admin(authentication)));
    }
}
