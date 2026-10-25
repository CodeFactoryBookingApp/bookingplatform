package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaConflictException;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which integrity violation is an agenda conflict. The writes themselves run against PostgreSQL in AgendaManagementIT. */
class AgendaRepositoryAdapterTest {

    private AgendaJpaRepository agendas;
    private AgendaRepositoryAdapter adapter;
    private final Agenda agenda = new Agenda(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Agenda",
            30, LocalDate.of(2026, 11, 1), null, List.of(UUID.randomUUID()),
            List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

    @BeforeEach
    void setUp() {
        agendas = mock(AgendaJpaRepository.class);
        adapter = new AgendaRepositoryAdapter(agendas, mock(JdbcTemplate.class));
    }

    @Test
    @DisplayName("The unique name of V8 is a DUPLICATE_NAME conflict")
    void nameViolationIsADuplicate() {
        // Arrange
        when(agendas.saveAndFlush(any(AgendaEntity.class))).thenThrow(new DataIntegrityViolationException("dup",
                new ConstraintViolationException("dup", new SQLException("23505"), "uk_agendas_org_name")));

        // Act
        AgendaConflictException ex = assertThrows(AgendaConflictException.class, () -> adapter.saveNew(agenda));

        // Assert
        assertEquals(AgendaConflictException.Reason.DUPLICATE_NAME, ex.reason());
    }

    @Test
    @DisplayName("The exclusion of V8 is an OVERLAPPING_PERIOD conflict, even when only the message names it")
    void exclusionViolationIsAnOverlap() {
        // Arrange
        SQLException root = new SQLException("conflicting key value violates exclusion constraint \"ex_agendas_solape\"",
                "23P01");
        when(agendas.saveAndFlush(any(AgendaEntity.class))).thenThrow(new DataIntegrityViolationException("overlap",
                new ConstraintViolationException("overlap", root, null)));

        // Act
        AgendaConflictException ex = assertThrows(AgendaConflictException.class, () -> adapter.saveNew(agenda));

        // Assert
        assertEquals(AgendaConflictException.Reason.OVERLAPPING_PERIOD, ex.reason());
    }

    @Test
    @DisplayName("Any other integrity violation is rethrown untouched")
    void otherViolationIsRethrown() {
        // Arrange
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk",
                new ConstraintViolationException("fk", new SQLException("fk_agendas_location_org", "23503"),
                        "fk_agendas_location_org"));
        when(agendas.saveAndFlush(any(AgendaEntity.class))).thenThrow(other);

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.saveNew(agenda));

        // Assert
        assertSame(other, ex);
    }
}
