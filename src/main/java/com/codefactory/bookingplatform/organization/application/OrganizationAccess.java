package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

/**
 * Organization scope of HU-022 (ADR-0008), and the facade other modules use to check it: catalog
 * and scheduling ask here before touching a provider's services, resources or agendas.
 *
 * <ul>
 *   <li>Someone who is not a member gets 404, the same answer as for an organization that does not
 *   exist: "no revela la existencia del recurso".</li>
 *   <li>A member whose role does not allow the operation gets 403 with a message naming the role.</li>
 * </ul>
 */
@Service
public class OrganizationAccess {

    private final OrganizationRepository repository;

    public OrganizationAccess(OrganizationRepository repository) {
        this.repository = repository;
    }

    public MemberRole requireMember(UUID organizationId, UUID userId) {
        return repository.findMemberRole(organizationId, userId).orElseThrow(OrganizationAccess::notFound);
    }

    public MemberRole requireRole(UUID organizationId, UUID userId, MemberRole... allowed) {
        MemberRole role = requireMember(organizationId, userId);
        if (Arrays.stream(allowed).noneMatch(role::equals)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Your role in this organization (" + role + ") does not allow this operation");
        }
        return role;
    }

    /**
     * Whether a location belongs to the organization. Other modules use it before attaching
     * anything to a location, since the id travels in the request body.
     */
    public boolean ownsLocation(UUID organizationId, UUID locationId) {
        return repository.hasLocation(organizationId, locationId);
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Organization not found");
    }
}
