package com.codefactory.bookingplatform.organization.api.dto;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Location;
import com.codefactory.bookingplatform.organization.domain.model.OperationPolicy;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, UUID categoryId, IdentificationType identificationType,
                                   String identificationNumber, String contactEmail, String contactPhone,
                                   String timezone, OrganizationStatus status, Instant approvedAt,
                                   List<LocationResponse> locations, PolicyResponse operationPolicy) {

    public record LocationResponse(UUID id, String name, String address, UUID cityId, String timezone) {

        static LocationResponse from(Location location) {
            return new LocationResponse(location.id(), location.name(), location.address(), location.cityId(),
                    location.timezone());
        }
    }

    public record PolicyResponse(int version, int bookingNoticeMinutesMin, int bookingNoticeMinutesMax,
                                 int freeCancellationWindowMinutes) {
    }

    public static OrganizationResponse from(Organization organization) {
        OperationPolicy policy = organization.getPolicy();
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getCategoryId(),
                organization.getIdentificationType(), organization.getIdentificationNumber(),
                organization.getContactEmail(), organization.getContactPhone(), organization.getTimezone(),
                organization.getStatus(), organization.getApprovedAt(),
                organization.getLocations().stream().map(LocationResponse::from).toList(),
                new PolicyResponse(organization.getPolicyVersion(), policy.bookingNoticeMinutesMin(),
                        policy.bookingNoticeMinutesMax(), policy.freeCancellationWindowMinutes()));
    }
}
