package com.codefactory.bookingplatform.scheduling.application;

import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaExceptionConflictException;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaExceptionRepository;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * HU-007 "Excepciones y bloqueos de agenda": festivos, cierres puntuales y horarios especiales
 * que anulan los bloques semanales de una agenda (HU-006) en una fecha concreta. Para el owner o
 * un manager, igual que el resto de cambios de agenda.
 */
@Service
public class ManageAgendaExceptionsUseCase {

    private final AgendaRepository agendas;
    private final AgendaExceptionRepository exceptions;
    private final OrganizationAccess access;

    public ManageAgendaExceptionsUseCase(AgendaRepository agendas, AgendaExceptionRepository exceptions,
                                         OrganizationAccess access) {
        this.agendas = agendas;
        this.exceptions = exceptions;
        this.access = access;
    }

    private void requireAgenda(UUID organizationId, UUID userId, UUID agendaId) {
        access.requireRole(organizationId, userId, MemberRole.OWNER, MemberRole.MANAGER);
        agendas.findById(organizationId, agendaId).orElseThrow(ManageAgendaUseCase::notFound);
    }

    @Transactional
    public AgendaException close(UUID organizationId, UUID userId, UUID agendaId, LocalDate date, String reason) {
        requireAgenda(organizationId, userId, agendaId);
        return saveOrConflict(AgendaException.closed(agendaId, date, reason));
    }

    @Transactional
    public AgendaException specialHours(UUID organizationId, UUID userId, UUID agendaId, LocalDate date,
                                        LocalTime start, LocalTime end, String reason) {
        requireAgenda(organizationId, userId, agendaId);
        return saveOrConflict(AgendaException.specialHours(agendaId, date, start, end, reason));
    }

    private AgendaException saveOrConflict(AgendaException exception) {
        try {
            exceptions.save(exception);
        } catch (AgendaExceptionConflictException ex) {
            throw new BusinessException(ErrorCode.OVERLAPPING_PERIOD,
                    "This agenda already has an exception for " + exception.date());
        }
        return exception;
    }

    @Transactional(readOnly = true)
    public List<AgendaException> list(UUID organizationId, UUID userId, UUID agendaId) {
        access.requireMember(organizationId, userId);
        agendas.findById(organizationId, agendaId).orElseThrow(ManageAgendaUseCase::notFound);
        return exceptions.findByAgenda(agendaId);
    }

    @Transactional
    public void delete(UUID organizationId, UUID userId, UUID agendaId, UUID exceptionId) {
        requireAgenda(organizationId, userId, agendaId);
        exceptions.findOne(agendaId, exceptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Exception not found"));
        exceptions.delete(agendaId, exceptionId);
    }
}