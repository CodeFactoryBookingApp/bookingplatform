package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.OrganizationResponse;
import com.codefactory.bookingplatform.organization.api.dto.StatusChangeRequest;
import com.codefactory.bookingplatform.organization.application.ApproveProviderUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Platform administration of providers. Everything under /api/v1/admin requires role ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Tag(name = "Provider Administration", description = "HU-002, HU-003 - approval of providers by an administrator")
public class AdminOrganizationController {

    private final ApproveProviderUseCase approveProviderUseCase;

    public AdminOrganizationController(ApproveProviderUseCase approveProviderUseCase) {
        this.approveProviderUseCase = approveProviderUseCase;
    }

    @PostMapping("/{organizationId}/approval")
    @Operation(summary = "Approve a provider pending approval",
            description = "Moves it to ACTIVE, stores the reason with the change and emails the provider. "
                    + "409 if the provider is not pending approval.")
    public OrganizationResponse approve(@PathVariable UUID organizationId,
                                        @Valid @RequestBody StatusChangeRequest request,
                                        JwtAuthenticationToken authentication) {
        return OrganizationResponse.from(approveProviderUseCase.approve(organizationId, request.reason(),
                AuthenticatedUser.from(authentication).id()));
    }
}
