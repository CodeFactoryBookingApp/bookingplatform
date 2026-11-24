package com.codefactory.bookingplatform.auth.api;

import com.codefactory.bookingplatform.auth.application.ChangePlatformRoleUseCase;
import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Platform roles", description = "HU-022 - Platform role management and session invalidation")
public class AdminRoleController {

    private final ChangePlatformRoleUseCase useCase;

    public AdminRoleController(ChangePlatformRoleUseCase useCase) {
        this.useCase = useCase;
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a user's platform role and invalidate earlier sessions")
    public RoleResponse change(@PathVariable UUID id, @Valid @RequestBody RoleRequest request,
                               JwtAuthenticationToken authentication) {
        useCase.change(id, request.role(), authentication.getToken());
        return new RoleResponse(id, request.role());
    }

    public record RoleRequest(@NotNull AppRole role) {
    }

    public record RoleResponse(UUID id, AppRole role) {
    }
}
