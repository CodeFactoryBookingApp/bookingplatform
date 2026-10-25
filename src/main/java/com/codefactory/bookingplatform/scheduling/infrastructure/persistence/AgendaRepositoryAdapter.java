package com.codefactory.bookingplatform.scheduling.infrastructure.persistence;

import com.codefactory.bookingplatform.scheduling.domain.model.Agenda;
import com.codefactory.bookingplatform.scheduling.domain.model.AgendaConflictException;
import com.codefactory.bookingplatform.scheduling.domain.model.TimeBlock;
import com.codefactory.bookingplatform.scheduling.domain.port.AgendaRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * agendas through JPA; agenda_services and schedules (the blocks), plain child tables, through
 * JDBC in the same transaction (V8).
 */
@Repository
public class AgendaRepositoryAdapter implements AgendaRepository {

    private static final String NAME_CONSTRAINT = "uk_agendas_org_name";
    private static final String PERIOD_CONSTRAINT = "ex_agendas_solape";

    private final AgendaJpaRepository agendas;
    private final JdbcTemplate jdbc;

    public AgendaRepositoryAdapter(AgendaJpaRepository agendas, JdbcTemplate jdbc) {
        this.agendas = agendas;
        this.jdbc = jdbc;
    }

    @Override
    public void saveNew(Agenda agenda) {
        AgendaEntity entity = new AgendaEntity();
        entity.setId(agenda.getId());
        entity.setOrganizationId(agenda.getOrganizationId());
        entity.setLocationId(agenda.getLocationId());
        entity.setResourceId(agenda.getResourceId());
        entity.setName(agenda.getName());
        entity.setSlotMinutes((short) agenda.getSlotMinutes());
        entity.setValidFrom(agenda.getValidFrom());
        entity.setValidUntil(agenda.getValidUntil());
        try {
            agendas.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
        for (UUID serviceId : agenda.getServiceIds()) {
            jdbc.update("insert into agenda_services (agenda_id, service_id, location_id) values (?, ?, ?)",
                    agenda.getId(), serviceId, agenda.getLocationId());
        }
        insertBlocks(agenda);
    }

    /** Delete, then insert: the old blocks would otherwise trip the overlap exclusion of the new ones. */
    @Override
    public void saveBlocks(Agenda agenda) {
        jdbc.update("delete from schedules where agenda_id = ?", agenda.getId());
        insertBlocks(agenda);
    }

    private void insertBlocks(Agenda agenda) {
        for (TimeBlock block : agenda.getBlocks()) {
            jdbc.update("insert into schedules (agenda_id, day_of_week, start_time, end_time) values (?, ?, ?, ?)",
                    agenda.getId(), block.day().getValue(), block.start(), block.end());
        }
    }

    @Override
    public void saveGranularity(Agenda agenda) {
        AgendaEntity entity = agendas.findById(agenda.getId()).orElseThrow();
        entity.setSlotMinutes((short) agenda.getSlotMinutes());
        agendas.saveAndFlush(entity);
    }

    @Override
    public List<Agenda> findAll(UUID organizationId) {
        return agendas.findByOrganizationIdOrderByName(organizationId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Agenda> findById(UUID organizationId, UUID agendaId) {
        return agendas.findByIdAndOrganizationId(agendaId, organizationId).map(this::toDomain);
    }

    private Agenda toDomain(AgendaEntity entity) {
        List<UUID> serviceIds = jdbc.queryForList(
                "select service_id from agenda_services where agenda_id = ? order by service_id",
                UUID.class, entity.getId());
        List<TimeBlock> blocks = jdbc.query("""
                select day_of_week, start_time, end_time from schedules
                 where agenda_id = ? and active order by day_of_week, start_time
                """, (row, n) -> new TimeBlock(DayOfWeek.of(row.getInt("day_of_week")),
                row.getObject("start_time", LocalTime.class), row.getObject("end_time", LocalTime.class)), entity.getId());
        return new Agenda(entity.getId(), entity.getOrganizationId(), entity.getLocationId(), entity.getResourceId(),
                entity.getName(), entity.getSlotMinutes(), entity.getValidFrom(), entity.getValidUntil(), serviceIds,
                blocks);
    }

    /**
     * PostgreSQL does not always hand the driver the name of a violated exclusion constraint, so
     * the message is checked as well.
     */
    private static RuntimeException translate(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
        String evidence = (constraint == null ? "" : constraint) + " " + ex.getMostSpecificCause().getMessage();
        if (evidence.contains(NAME_CONSTRAINT)) {
            return new AgendaConflictException(AgendaConflictException.Reason.DUPLICATE_NAME, ex);
        }
        if (evidence.contains(PERIOD_CONSTRAINT)) {
            return new AgendaConflictException(AgendaConflictException.Reason.OVERLAPPING_PERIOD, ex);
        }
        return ex;
    }
}
