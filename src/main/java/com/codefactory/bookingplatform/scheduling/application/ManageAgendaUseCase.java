package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaBookings;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HU-006 changes to an existing agenda, for the owner or a manager. Every change is checked
 * against the future bookings of the agenda, read at the moment of the change.
 */
@Service
public class ManageAgendaUseCase {

    private final AgendaRepository agendas;
    private final AgendaBookings bookings;
    private final OrganizationAccess access;
    private final Clock clock;

    public ManageAgendaUseCase(AgendaRepository agendas, AgendaBookings bookings, OrganizationAccess access,
                               Clock clock) {
        this.agendas = agendas;
        this.bookings = bookings;
        this.access = access;
        this.clock = clock;
    }

    private Agenda find(UUID organizationId, UUID userId, UUID agendaId) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        return agendas.findById(organizationId, agendaId).orElseThrow(ManageAgendaUseCase::notFound);
    }

    static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Agenda not found");
    }

    /** HU-006 "Modificación que deja reservas fuera de horario". */
    @Transactional
    public Agenda replaceBlocks(UUID organizationId, UUID userId, UUID agendaId, List<TimeBlock> blocks) {
        Agenda agenda = find(organizationId, userId, agendaId);
        agenda.replaceBlocks(blocks, bookings.future(agenda, clock.instant()),
                access.locationZone(organizationId, agenda.getLocationId()));
        agendas.saveBlocks(agenda);
        return agenda;
    }

    /** HU-006 "Duplicar la configuración de un día". */
    @Transactional
    public Agenda copyDay(UUID organizationId, UUID userId, UUID agendaId, DayOfWeek from, Set<DayOfWeek> to) {
        Agenda agenda = find(organizationId, userId, agendaId);
        agenda.copyDay(from, to, bookings.future(agenda, clock.instant()),
                access.locationZone(organizationId, agenda.getLocationId()));
        agendas.saveBlocks(agenda);
        return agenda;
    }

    /** HU-006 "Cambio de granularidad con reservas futuras". */
    @Transactional
    public Agenda changeGranularity(UUID organizationId, UUID userId, UUID agendaId, int slotMinutes) {
        Agenda agenda = find(organizationId, userId, agendaId);
        agenda.changeGranularity(slotMinutes, !bookings.future(agenda, clock.instant()).isEmpty());
        agendas.saveGranularity(agenda);
        return agenda;
    }
}
