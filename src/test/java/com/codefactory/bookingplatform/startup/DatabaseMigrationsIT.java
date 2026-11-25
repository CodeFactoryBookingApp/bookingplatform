package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one rule each new migration has to honour by hand: no table in {@code public} without row
 * level security, since Supabase exposes that schema through the Data API (ADR-0002).
 *
 * <p>Arrange-Act-Assert: the migrated container is the shared fixture; the Act is one catalog
 * query and the Assert reads its rows.
 */
class DatabaseMigrationsIT extends PostgresIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Every table in public has row level security, Flyway's history table included")
    void everyPublicTableHasRowLevelSecurity() {
        // Act
        List<String> exposed = jdbc.queryForList("""
                select relname from pg_class
                 where relnamespace = 'public'::regnamespace and relkind = 'r' and not relrowsecurity
                 order by relname
                """, String.class);

        // Assert
        assertEquals(List.of(), exposed);
    }
}
