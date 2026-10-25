package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.OperationPolicyRequest;
import com.codefactory.bookingplatform.organization.api.dto.OrganizationResponse;
import com.codefactory.bookingplatform.organization.application.ChangeOperationPolicyUseCase;
import com.codefactory.bookingplatform.organization.application.FindOrganizationsUseCase;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A provider looking after its own business (HU-002). Platform role PROVIDER or ADMIN, checked by
 * SecurityConfig; membership and role inside the organization, checked by the use cases (ADR-0008).
 */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "HU-002 - the provider's own business and its operating rules")
public class OrganizationController {

    private final FindOrganizationsUseCase findOrganizationsUseCase;
    private final ChangeOperationPolicyUseCase changeOperationPolicyUseCase;

    public OrganizationController(FindOrganizationsUseCase findOrganizationsUseCase,
                                  ChangeOperationPolicyUseCase changeOperationPolicyUseCase) {
        this.findOrganizationsUseCase = findOrganizationsUseCase;
        this.changeOperationPolicyUseCase = changeOperationPolicyUseCase;
    }

    @GetMapping
    @Operation(summary = "The providers the signed-in user belongs to")
    public List<OrganizationResponse> mine(JwtAuthenticationToken authentication) {
        return findOrganizationsUseCase.findMine(AuthenticatedUser.from(authentication).id()).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "One provider",
            description = "Members and administrators only. Anyone else gets 404, as if it did not exist (HU-022).")
    public OrganizationResponse one(@PathVariable UUID organizationId, JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return OrganizationResponse.from(findOrganizationsUseCase.findOne(organizationId, user.id(), user.admin()));
    }

    @PutMapping("/{organizationId}/operation-policy")
    @Operation(summary = "Change the operating rules",
            description = "Owner or manager only. Creates a new policy version for the bookings made from now on; "
                    + "existing bookings keep theirs.")
    public OrganizationResponse changePolicy(@PathVariable UUID organizationId,
                                             @Valid @RequestBody OperationPolicyRequest request,
                                             JwtAuthenticationToken authentication) {
        OperationPolicy policy = new OperationPolicy(request.bookingNoticeMinutesMin(),
                request.bookingNoticeMinutesMax(), request.freeCancellationWindowMinutes());
        return OrganizationResponse.from(changeOperationPolicyUseCase.change(organizationId,
                AuthenticatedUser.from(authentication).id(), policy));
    }
}
