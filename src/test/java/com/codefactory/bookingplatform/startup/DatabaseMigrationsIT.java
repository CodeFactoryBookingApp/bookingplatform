package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema every integration test runs on is the one Flyway builds from
 * {@code db/migration}, not one Hibernate derives from the entities. These checks pin that down,
 * plus the one rule each new migration has to honour by hand: no table in {@code public} without
 * row level security, since Supabase exposes that schema through the Data API (ADR-0002).
 *
 * <p>Arrange-Act-Assert: the migrated container is the shared fixture; the Act is one catalog
 * query and the Assert reads its rows.
 */
class DatabaseMigrationsIT extends PostgresIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("An empty database is migrated from V1 with no baseline row")
    void emptyDatabaseIsMigratedFromTheFirstVersion() {
        // Act
        List<String> history = jdbc.queryForList(
                "select version || ':' || type || ':' || success from flyway_schema_history order by installed_rank",
                String.class);

        // Assert
        assertEquals("1:SQL:true", history.get(0));
        assertTrue(history.stream().allMatch(row -> row.endsWith(":SQL:true")), history.toString());
    }

    @Test
    @DisplayName("The rules that live only in the database exist, which ddl-auto would never have created")
    void databaseOnlyRulesExist() {
        // Act
        Integer rules = jdbc.queryForObject("""
                select (select count(*) from pg_trigger where tgname = 'tg_bookings_reglas')
                     + (select count(*) from pg_constraint where conname = 'ex_booking_resources_solape')
                """, Integer.class);

        // Assert
        assertEquals(2, rules);
    }

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
