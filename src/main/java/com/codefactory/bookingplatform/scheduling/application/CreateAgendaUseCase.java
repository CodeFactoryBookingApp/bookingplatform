package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.catalog.application.CatalogLookup;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaConflictException;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-006 "Crear una agenda válida", for the owner or a manager. The location, the optional
 * resource and every service travel as ids in the request; all must be the provider's own, and
 * each service must be provided in the agenda's location.
 */
@Service
public class CreateAgendaUseCase {

    private final AgendaRepository agendas;
    private final OrganizationAccess access;
    private final CatalogLookup catalog;

    public CreateAgendaUseCase(AgendaRepository agendas, OrganizationAccess access, CatalogLookup catalog) {
        this.agendas = agendas;
        this.access = access;
        this.catalog = catalog;
    }

    /** What an agenda is made of, as the request brings it. */
    public record Definition(String name, UUID locationId, UUID resourceId, int slotMinutes, LocalDate validFrom,
                             LocalDate validUntil, List<UUID> serviceIds, List<TimeBlock> blocks) {
    }

    @Transactional
    public Agenda create(UUID organizationId, UUID userId, Definition definition) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        Agenda agenda = Agenda.create(UUID.randomUUID(), organizationId, definition.locationId(),
                definition.resourceId(), definition.name().trim(), definition.slotMinutes(), definition.validFrom(),
                definition.validUntil(), definition.serviceIds(), definition.blocks());
        requireOwnReferences(organizationId, agenda);
        try {
            agendas.saveNew(agenda);
        } catch (AgendaConflictException ex) {
            throw ex.reason() == AgendaConflictException.Reason.DUPLICATE_NAME
                    ? new BusinessException(ErrorCode.DUPLICATE_NAME,
                            "The provider already has an agenda named '" + agenda.getName() + "'")
                    : new BusinessException(ErrorCode.OVERLAPPING_PERIOD,
                            "Another agenda of the same location or resource is valid in that period");
        }
        return agenda;
    }

    private void requireOwnReferences(UUID organizationId, Agenda agenda) {
        Map<String, String> errors = new LinkedHashMap<>();
        UUID locationId = agenda.getLocationId();
        if (!access.ownsLocation(organizationId, locationId)) {
            errors.put("locationId", "not a location of this provider");
        } else {
            if (agenda.getResourceId() != null
                    && !catalog.resourceAt(organizationId, agenda.getResourceId(), locationId)) {
                errors.put("resourceId", "not a resource of this provider in that location");
            }
            List<UUID> serviceIds = agenda.getServiceIds();
            for (int i = 0; i < serviceIds.size(); i++) {
                if (!catalog.serviceOfferedAt(organizationId, serviceIds.get(i), locationId)) {
                    errors.put("serviceIds[" + i + "]", "not a service of this provider offered in that location");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown location, resource or service", errors);
        }
    }
}
