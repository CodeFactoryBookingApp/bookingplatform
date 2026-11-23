package com.codefactory.bookingplatform.scheduling.api.dto;

import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AgendaExceptionResponse(UUID id, UUID agendaId, LocalDate date, AgendaException.Type type,
                                      LocalTime specialStart, LocalTime specialEnd, String reason) {

    public static AgendaExceptionResponse from(AgendaException exception) {
        return new AgendaExceptionResponse(exception.id(), exception.agendaId(), exception.date(), exception.type(),
                exception.specialStart(), exception.specialEnd(), exception.reason());
    }
}