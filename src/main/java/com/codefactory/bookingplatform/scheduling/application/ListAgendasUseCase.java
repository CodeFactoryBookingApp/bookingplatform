package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The agendas of a provider, for any of its members (HU-006, HU-022). */
@Service
public class ListAgendasUseCase {

    private final AgendaRepository agendas;
    private final OrganizationAccess access;

    public ListAgendasUseCase(AgendaRepository agendas, OrganizationAccess access) {
        this.agendas = agendas;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Agenda> all(UUID organizationId, UUID userId) {
        access.requireMember(organizationId, userId);
        return agendas.findAll(organizationId);
    }

    @Transactional(readOnly = true)
    public Agenda one(UUID organizationId, UUID userId, UUID agendaId) {
        access.requireMember(organizationId, userId);
        return agendas.findById(organizationId, agendaId).orElseThrow(ManageAgendaUseCase::notFound);
    }
}
