package com.codefactory.bookingplatform.scheduling.domain.port;

import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaExceptionRepository {

    /**
     * @throws com.codefactory.bookingplatform.scheduling.domain.model.AgendaExceptionConflictException
     *         if the agenda already has an exception for that date
     */
    void save(AgendaException exception);

    void delete(UUID agendaId, UUID exceptionId);

    List<AgendaException> findByAgenda(UUID agendaId);

    /** Exceptions of the agenda whose date falls in [from, to], both included. */
    List<AgendaException> findByAgendaAndDateRange(UUID agendaId, LocalDate from, LocalDate to);

    Optional<AgendaException> findOne(UUID agendaId, UUID exceptionId);
}