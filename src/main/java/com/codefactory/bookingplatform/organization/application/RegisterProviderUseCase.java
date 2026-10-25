package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.organization.domain.model.DuplicateOrganizationException;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.organization.domain.port.ReferenceDataRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * HU-002: a service provider registers its business with its own new account (ADR-0008).
 *
 * <p>Everything that can be checked locally is checked before the account is created at the
 * identity provider, so a rejected registration leaves nothing behind. After that point any
 * failure deletes the account again, the same compensation as the client registration (D7).
 */
@Service
public class RegisterProviderUseCase {

    static final String DEFAULT_TIMEZONE = "America/Bogota";

    private static final Logger log = LoggerFactory.getLogger(RegisterProviderUseCase.class);

    private final OrganizationRepository organizations;
    private final ReferenceDataRepository referenceData;
    private final UserProvisioning userProvisioning;
    private final Clock clock;

    public RegisterProviderUseCase(OrganizationRepository organizations, ReferenceDataRepository referenceData,
                                   UserProvisioning userProvisioning, Clock clock) {
        this.organizations = organizations;
        this.referenceData = referenceData;
        this.userProvisioning = userProvisioning;
        this.clock = clock;
    }

    @Transactional
    public ProviderRegistration register(RegisterProviderCommand command) {
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        Organization organization = toOrganization(command, email);
        requireKnownReferences(organization);
        if (organizations.existsByIdentification(organization.getIdentificationType(),
                organization.getIdentificationNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_IDENTIFICATION);
        }

        UUID ownerId = userProvisioning.provisionProviderUser(email, command.password());
        try {
            organizations.register(organization, ownerId, clock.instant());
            // The identity provider sends nothing on account creation; the owner has to verify
            // the email before signing in, exactly like a client.
            userProvisioning.resendSignupVerification(email);
        } catch (DuplicateOrganizationException ex) {
            // A concurrent registration took the identification after the check above
            userProvisioning.deprovisionUser(ownerId);
            throw new BusinessException(ErrorCode.DUPLICATE_IDENTIFICATION);
        } catch (RuntimeException ex) {
            userProvisioning.deprovisionUser(ownerId);
            throw ex;
        }
        log.info("Provider {} registered in status {}", organization.getId(), organization.getStatus());
        return new ProviderRegistration(organization.getId(), ownerId, email, organization.getStatus());
    }

    private static Organization toOrganization(RegisterProviderCommand command, String email) {
        List<Location> locations = command.locations() == null ? List.of() : command.locations().stream()
                .map(location -> new Location(UUID.randomUUID(), location.name().trim(), location.address().trim(),
                        location.cityId(), blankToNull(location.timezone())))
                .toList();
        OperationPolicy policy = new OperationPolicy(command.bookingNoticeMinutesMin(),
                command.bookingNoticeMinutesMax(), command.freeCancellationWindowMinutes());
        String contactEmail = blankToNull(command.contactEmail());
        String timezone = blankToNull(command.timezone());
        return Organization.register(
                UUID.randomUUID(),
                command.categoryId(),
                command.name().trim(),
                command.identificationType(),
                command.identificationNumber().trim().toUpperCase(Locale.ROOT),
                contactEmail == null ? email : contactEmail.toLowerCase(Locale.ROOT),
                command.contactPhone().trim(),
                timezone == null ? DEFAULT_TIMEZONE : timezone,
                locations,
                policy);
    }

    /** Without this, an unknown id would surface as a foreign key violation, that is, a 500. */
    private void requireKnownReferences(Organization organization) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!referenceData.categoryExists(organization.getCategoryId())) {
            errors.put("categoryId", "unknown category");
        }
        List<Location> locations = organization.getLocations();
        for (int i = 0; i < locations.size(); i++) {
            if (!referenceData.cityExists(locations.get(i).cityId())) {
                errors.put("locations[" + i + "].cityId", "unknown city");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown category or city", errors);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
