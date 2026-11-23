package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaExceptionConflictException;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaExceptionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** agenda_exceptions (V14). */
@Repository
public class AgendaExceptionRepositoryAdapter implements AgendaExceptionRepository {

    private static final String DATE_CONSTRAINT = "uk_agenda_exceptions";

    private final AgendaExceptionJpaRepository exceptions;

    public AgendaExceptionRepositoryAdapter(AgendaExceptionJpaRepository exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public void save(AgendaException exception) {
        AgendaExceptionEntity entity = new AgendaExceptionEntity();
        entity.setId(exception.id());
        entity.setAgendaId(exception.agendaId());
        entity.setExceptionDate(exception.date());
        entity.setType(exception.type());
        entity.setSpecialStart(exception.specialStart());
        entity.setSpecialEnd(exception.specialEnd());
        entity.setReason(exception.reason());
        try {
            exceptions.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            String constraint = ex.getCause() instanceof ConstraintViolationException violation
                    ? violation.getConstraintName() : null;
            String evidence = (constraint == null ? "" : constraint) + " " + ex.getMostSpecificCause().getMessage();
            if (evidence.contains(DATE_CONSTRAINT)) {
                throw new AgendaExceptionConflictException(ex);
            }
            throw ex;
        }
    }

    @Override
    public void delete(UUID agendaId, UUID exceptionId) {
        exceptions.deleteByIdAndAgendaId(exceptionId, agendaId);
    }

    @Override
    public List<AgendaException> findByAgenda(UUID agendaId) {
        return exceptions.findByAgendaIdOrderByExceptionDate(agendaId).stream()
                .map(AgendaExceptionRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<AgendaException> findByAgendaAndDateRange(UUID agendaId, LocalDate from, LocalDate to) {
        return exceptions.findByAgendaIdAndExceptionDateBetweenOrderByExceptionDate(agendaId, from, to).stream()
                .map(AgendaExceptionRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<AgendaException> findOne(UUID agendaId, UUID exceptionId) {
        return exceptions.findByIdAndAgendaId(exceptionId, agendaId).map(AgendaExceptionRepositoryAdapter::toDomain);
    }

    private static AgendaException toDomain(AgendaExceptionEntity entity) {
        return new AgendaException(entity.getId(), entity.getAgendaId(), entity.getExceptionDate(), entity.getType(),
                entity.getSpecialStart(), entity.getSpecialEnd(), entity.getReason());
    }
}