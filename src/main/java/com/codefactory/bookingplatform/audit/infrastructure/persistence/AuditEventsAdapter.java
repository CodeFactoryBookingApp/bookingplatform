package com.codefactory.bookingplatform.audit.infrastructure.persistence;

import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.audit.domain.port.AuditEvents;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditEventsAdapter implements AuditEvents {

    private static final String FILTERS = """
             from audit_events
            where (cast(:scope as uuid) is null or organization_id = :scope)
              and (cast(:fromTime as timestamptz) is null or occurred_at >= :fromTime)
              and (cast(:toTime as timestamptz) is null or occurred_at < :toTime)
              and (cast(:actor as uuid) is null or actor_id = :actor)
              and (cast(:action as varchar) is null or action = :action)
            """;
    private static final String ORDER = " order by occurred_at desc, id desc";

    private final JdbcClient jdbc;

    public AuditEventsAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.sql("""
                insert into audit_events (occurred_at, actor_id, actor_role, action, resource_type,
                                          resource_id, organization_id, result, related_resource_id)
                values (:at, :actor, :role, :action, :resourceType, :resourceId, :scope, :result, :related)
                """).param("at", offset(event.occurredAt())).param("actor", event.actorId())
                .param("role", event.actorRole()).param("action", event.action())
                .param("resourceType", event.resourceType()).param("resourceId", event.resourceId())
                .param("scope", event.organizationId()).param("result", event.result())
                .param("related", event.relatedResourceId()).update();
    }

    @Override
    public long count(UUID organizationId, AuditFilter filter) {
        return filtered("select count(*)" + FILTERS, organizationId, filter).query(Long.class).single();
    }

    @Override
    public List<AuditEntry> page(UUID organizationId, AuditFilter filter, int size, long offset) {
        return filtered("select *" + FILTERS + ORDER + " limit :limit offset :offset", organizationId, filter)
                .param("limit", size).param("offset", offset).query(AuditEventsAdapter::map).list();
    }

    @Override
    public List<AuditEntry> findAll(UUID organizationId, AuditFilter filter) {
        return filtered("select *" + FILTERS + ORDER, organizationId, filter)
                .query(AuditEventsAdapter::map).list();
    }

    private JdbcClient.StatementSpec filtered(String sql, UUID organizationId, AuditFilter filter) {
        return jdbc.sql(sql).param("scope", organizationId)
                .param("fromTime", offset(filter.from())).param("toTime", offset(filter.to()))
                .param("actor", filter.actorId()).param("action", filter.action());
    }

    private static OffsetDateTime offset(Instant time) {
        return time == null ? null : time.atOffset(ZoneOffset.UTC);
    }

    private static AuditEntry map(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditEntry(rs.getObject("id", UUID.class),
                new AuditEvent(rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("actor_id", UUID.class), rs.getString("actor_role"), rs.getString("action"),
                        rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                        rs.getObject("organization_id", UUID.class), rs.getString("result"),
                        rs.getObject("related_resource_id", UUID.class)));
    }
}
