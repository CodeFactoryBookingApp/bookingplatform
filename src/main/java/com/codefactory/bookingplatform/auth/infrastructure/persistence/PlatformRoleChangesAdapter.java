package com.codefactory.bookingplatform.auth.infrastructure.persistence;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PlatformRoleChangesAdapter implements PlatformRoleChanges {

    private final JdbcClient jdbc;

    public PlatformRoleChangesAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock() {
        jdbc.sql("select 1 from pg_advisory_xact_lock(202611, 22)").query(Integer.class).single();
    }

    @Override
    public Optional<RoleChange> find(UUID userId) {
        return jdbc.sql("select * from platform_role_changes where user_id = :id")
                .param("id", userId).query(PlatformRoleChangesAdapter::map).optional();
    }

    @Override
    public void reserve(RoleChange change) {
        jdbc.sql("""
                insert into platform_role_changes (user_id, revision, target_role)
                values (:id, :revision, :role)
                on conflict (user_id) do update set revision = excluded.revision,
                    target_role = excluded.target_role, completed_at = null
                """).param("id", change.userId()).param("revision", change.revision())
                .param("role", change.role().name()).update();
    }

    @Override
    public void complete(RoleChange change, Instant at) {
        jdbc.sql("update platform_role_changes set completed_at = :at where user_id = :id and revision = :revision")
                .param("at", at.atOffset(ZoneOffset.UTC)).param("id", change.userId())
                .param("revision", change.revision()).update();
    }

    private static RoleChange map(ResultSet rs, int rowNumber) throws SQLException {
        OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
        return new RoleChange(rs.getObject("user_id", UUID.class), rs.getObject("revision", UUID.class),
                AppRole.valueOf(rs.getString("target_role")), completedAt == null ? null : completedAt.toInstant());
    }
}
