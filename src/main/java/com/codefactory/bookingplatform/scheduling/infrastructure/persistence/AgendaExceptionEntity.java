package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "agenda_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class AgendaExceptionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "agenda_id", nullable = false, updatable = false)
    private UUID agendaId;

    @Column(name = "exception_date", nullable = false, updatable = false)
    private LocalDate exceptionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private Type type;

    @Column(name = "special_start", updatable = false)
    private LocalTime specialStart;

    @Column(name = "special_end", updatable = false)
    private LocalTime specialEnd;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;
}