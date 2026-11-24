package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaExceptionJpaRepository extends JpaRepository<AgendaExceptionEntity, UUID> {

    List<AgendaExceptionEntity> findByAgendaIdOrderByExceptionDate(UUID agendaId);

    List<AgendaExceptionEntity> findByAgendaIdAndExceptionDateBetweenOrderByExceptionDate(
            UUID agendaId, LocalDate from, LocalDate to);

    Optional<AgendaExceptionEntity> findByIdAndAgendaId(UUID id, UUID agendaId);

    void deleteByIdAndAgendaId(UUID id, UUID agendaId);
}