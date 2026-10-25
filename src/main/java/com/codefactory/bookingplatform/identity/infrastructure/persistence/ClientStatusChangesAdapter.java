package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.port.ClientStatusChanges;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** account_status_changes rows of subject CLIENT (V1). The organization module writes its own. */
@Repository
public class ClientStatusChangesAdapter implements ClientStatusChanges {

    private final JdbcTemplate jdbc;

    public ClientStatusChangesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID clientId, ClientStatus from, ClientStatus to, String reason, UUID changedBy) {
        jdbc.update("""
                insert into account_status_changes (subject_type, client_id, from_status, to_status, reason, changed_by)
                values ('CLIENT', ?, ?, ?, ?, ?)
                """, clientId, from.name(), to.name(), reason, changedBy);
    }
}
