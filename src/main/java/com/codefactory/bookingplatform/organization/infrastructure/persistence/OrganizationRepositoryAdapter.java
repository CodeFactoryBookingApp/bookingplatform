package com.codefactory.bookingplatform.organization.infrastructure.persistence;

import com.codefactory.bookingplatform.organization.domain.model.DuplicateOrganizationException;
import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps the provider aggregate onto its five tables: organizations, locations,
 * organization_policies, organization_members and account_status_changes.
 */
@Repository
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    /** uk_organizations_identification, from V4. */
    private static final String IDENTIFICATION_CONSTRAINT = "identification";

    private final OrganizationJpaRepository organizations;
    private final LocationJpaRepository locations;
    private final OperationPolicyJpaRepository policies;
    private final OrganizationMemberJpaRepository members;
    private final AccountStatusChangeJpaRepository statusChanges;

    public OrganizationRepositoryAdapter(OrganizationJpaRepository organizations, LocationJpaRepository locations,
                                         OperationPolicyJpaRepository policies, OrganizationMemberJpaRepository members,
                                         AccountStatusChangeJpaRepository statusChanges) {
        this.organizations = organizations;
        this.locations = locations;
        this.policies = policies;
        this.members = members;
        this.statusChanges = statusChanges;
    }

    @Override
    public boolean existsByIdentification(IdentificationType type, String number) {
        return organizations.existsByIdentificationTypeAndIdentificationNumber(type, number);
    }

    @Override
    public void register(Organization organization, UUID ownerUserId, Instant at) {
        try {
            organizations.saveAndFlush(toEntity(organization));
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        locations.saveAll(organization.getLocations().stream()
                .map(location -> toEntity(organization.getId(), location))
                .toList());
        policies.save(toEntity(organization, at));
        OrganizationMemberEntity owner = new OrganizationMemberEntity();
        owner.setId(UUID.randomUUID());
        owner.setOrganizationId(organization.getId());
        owner.setUserId(ownerUserId);
        owner.setRole(MemberRole.OWNER);
        members.saveAndFlush(owner);
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return organizations.findById(id).map(this::toDomain);
    }

    @Override
    public List<Organization> findByMember(UUID userId) {
        return members.findByUserId(userId).stream()
                .map(member -> findById(member.getOrganizationId()))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<MemberRole> findMemberRole(UUID organizationId, UUID userId) {
        return members.findByOrganizationIdAndUserId(organizationId, userId)
                .map(OrganizationMemberEntity::getRole);
    }

    @Override
    public boolean hasLocation(UUID organizationId, UUID locationId) {
        return locationId != null && locations.existsByIdAndOrganizationId(locationId, organizationId);
    }

    @Override
    public Optional<String> locationTimezone(UUID organizationId, UUID locationId) {
        return locations.findById(locationId)
                .filter(location -> location.getOrganizationId().equals(organizationId))
                .flatMap(location -> location.getTimezone() != null
                        ? Optional.of(location.getTimezone())
                        : organizations.findById(organizationId).map(OrganizationEntity::getTimezone));
    }

    @Override
    public void saveStatusChange(Organization organization, OrganizationStatus from, String reason, UUID changedBy) {
        // The reason goes first: the trigger on organizations rejects a status change that has
        // no matching row in account_status_changes (HU-003).
        AccountStatusChangeEntity change = new AccountStatusChangeEntity();
        change.setId(UUID.randomUUID());
        change.setSubjectType(AccountStatusChangeEntity.ORGANIZATION);
        change.setOrganizationId(organization.getId());
        change.setFromStatus(from.name());
        change.setToStatus(organization.getStatus().name());
        change.setReason(reason);
        change.setChangedBy(changedBy);
        statusChanges.saveAndFlush(change);

        OrganizationEntity entity = organizations.findById(organization.getId()).orElseThrow();
        entity.setStatus(organization.getStatus());
        entity.setApprovedAt(organization.getApprovedAt());
        organizations.saveAndFlush(entity);
    }

    @Override
    public void savePolicy(Organization organization, Instant at) {
        policies.closeCurrent(organization.getId(), at);
        policies.saveAndFlush(toEntity(organization, at));
    }

    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
        if (constraint != null && constraint.contains(IDENTIFICATION_CONSTRAINT)) {
            return new DuplicateOrganizationException(ex);
        }
        return ex;
    }

    private static OrganizationEntity toEntity(Organization organization) {
        OrganizationEntity entity = new OrganizationEntity();
        entity.setId(organization.getId());
        entity.setCategoryId(organization.getCategoryId());
        entity.setName(organization.getName());
        entity.setIdentificationType(organization.getIdentificationType());
        entity.setIdentificationNumber(organization.getIdentificationNumber());
        entity.setContactEmail(organization.getContactEmail());
        entity.setContactPhone(organization.getContactPhone());
        entity.setTimezone(organization.getTimezone());
        entity.setStatus(organization.getStatus());
        entity.setApprovedAt(organization.getApprovedAt());
        return entity;
    }

    private static LocationEntity toEntity(UUID organizationId, Location location) {
        LocationEntity entity = new LocationEntity();
        entity.setId(location.id());
        entity.setOrganizationId(organizationId);
        entity.setName(location.name());
        entity.setAddress(location.address());
        entity.setCityId(location.cityId());
        entity.setTimezone(location.timezone());
        entity.setStatus(LocationEntity.ACTIVE);
        return entity;
    }

    /** The current policy of the organization as a new version starting at {@code at}. */
    private static OperationPolicyEntity toEntity(Organization organization, Instant at) {
        OperationPolicy policy = organization.getPolicy();
        OperationPolicyEntity entity = new OperationPolicyEntity();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(organization.getId());
        entity.setVersion(organization.getPolicyVersion());
        entity.setBookingNoticeMinutesMin(policy.bookingNoticeMinutesMin());
        entity.setBookingNoticeMinutesMax(policy.bookingNoticeMinutesMax());
        entity.setFreeCancellationWindowMinutes(policy.freeCancellationWindowMinutes());
        entity.setEffectiveFrom(at);
        return entity;
    }

    private Organization toDomain(OrganizationEntity entity) {
        List<Location> branches = locations.findByOrganizationId(entity.getId(), Sort.by("name")).stream()
                .map(location -> new Location(location.getId(), location.getName(), location.getAddress(),
                        location.getCityId(), location.getTimezone()))
                .toList();
        OperationPolicyEntity current = policies.findByOrganizationIdAndReplacedAtIsNull(entity.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Organization " + entity.getId() + " has no current operating policy"));
        return new Organization(entity.getId(), entity.getCategoryId(), entity.getName(),
                entity.getIdentificationType(), entity.getIdentificationNumber(), entity.getContactEmail(),
                entity.getContactPhone(), entity.getTimezone(), branches, entity.getStatus(), entity.getApprovedAt(),
                new OperationPolicy(current.getBookingNoticeMinutesMin(), current.getBookingNoticeMinutesMax(),
                        current.getFreeCancellationWindowMinutes()),
                current.getVersion());
    }
}
