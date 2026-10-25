package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reading providers: a member sees its own, an administrator sees any (HU-022). */
@Service
public class FindOrganizationsUseCase {

    private final OrganizationRepository organizations;
    private final OrganizationAccess access;

    public FindOrganizationsUseCase(OrganizationRepository organizations, OrganizationAccess access) {
        this.organizations = organizations;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Organization findOne(UUID organizationId, UUID userId, boolean admin) {
        if (!admin) {
            access.requireMember(organizationId, userId);
        }
        return organizations.findById(organizationId).orElseThrow(OrganizationAccess::notFound);
    }

    /** The providers of the signed-in user, so a provider can find its own after logging in. */
    @Transactional(readOnly = true)
    public List<Organization> findMine(UUID userId) {
        return organizations.findByMember(userId);
    }
}
