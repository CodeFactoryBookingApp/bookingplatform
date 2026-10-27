package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.BookedSlot;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Java side of the future bookings of an agenda (HU-006): which question is asked for a
 * location agenda and which for a resource agenda, with which values, and how a row becomes a
 * {@link BookedSlot}. The SQL itself (statuses, services, the resource join) runs against
 * PostgreSQL in AgendaManagementIT.
 *
 * <p>Technique: equivalence partitioning on the kind of agenda (without resource / with resource).
 */
class AgendaBookingsAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID LOCATION = UUID.randomUUID();
    private static final List<TimeBlock> BLOCKS =
            List.of(new TimeBlock(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

    private JdbcTemplate jdbc;
    private AgendaBookingsAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        adapter = new AgendaBookingsAdapter(jdbc);
    }

    private static Agenda agenda(UUID resourceId) {
        return new Agenda(UUID.randomUUID(), UUID.randomUUID(), LOCATION, resourceId, "Agenda", 30,
                LocalDate.of(2026, 11, 1), null, List.of(UUID.randomUUID()), BLOCKS);
    }

    @Test
    @DisplayName("A location agenda asks for the bookings of its location and services after now, without a resource filter")
    @SuppressWarnings("unchecked")
    void locationAgendaIsNotFilteredByResource() {
        // Arrange
        Agenda agenda = agenda(null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        // Act
        adapter.future(agenda, NOW);

        // Assert
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<BookedSlot>>any(), eq(LOCATION),
                eq(Timestamp.from(NOW)), eq(agenda.getId()));
        assertFalse(sql.getValue().contains("booking_resources"));
    }

    @Test
    @DisplayName("HU-006: a resource agenda only answers for the bookings that use its resource")
    @SuppressWarnings("unchecked")
    void resourceAgendaIsFilteredByItsResource() {
        // Arrange
        UUID resource = UUID.randomUUID();
        Agenda agenda = agenda(resource);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        BookedSlot slot = new BookedSlot(UUID.randomUUID(), NOW, NOW.plusSeconds(1800));
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<BookedSlot>>any(), eq(LOCATION), eq(Timestamp.from(NOW)),
                eq(agenda.getId()), eq(resource))).thenReturn(List.of(slot));

        // Act
        List<BookedSlot> found = adapter.future(agenda, NOW);

        // Assert
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<BookedSlot>>any(), eq(LOCATION),
                eq(Timestamp.from(NOW)), eq(agenda.getId()), eq(resource));
        assertTrue(sql.getValue().contains("br.resource_id = ?"));
        assertEquals(List.of(slot), found);
    }

    @Test
    @DisplayName("A booking row becomes a slot with its id, its start and its end, not swapped")
    @SuppressWarnings("unchecked")
    void rowBecomesABookedSlot() throws SQLException {
        // Arrange
        adapter.future(agenda(null), NOW);
        ArgumentCaptor<RowMapper<BookedSlot>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(org.mockito.ArgumentMatchers.anyString(), mapper.capture(),
                org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any(),
                org.mockito.ArgumentMatchers.<Object>any());
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-02T15:00:00Z");
        Instant end = Instant.parse("2026-11-02T15:30:00Z");
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(id);
        when(row.getTimestamp("starts_at")).thenReturn(Timestamp.from(start));
        when(row.getTimestamp("ends_at")).thenReturn(Timestamp.from(end));

        // Act
        BookedSlot slot = mapper.getValue().mapRow(row, 0);

        // Assert
        assertEquals(new BookedSlot(id, start, end), slot);
    }
}
