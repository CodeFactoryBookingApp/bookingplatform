package com.codefactory.bookingplatform.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile that actually ships, started on the database it will find on its first deploy with
 * Flyway. Render runs the image with {@code SPRING_PROFILES_ACTIVE=cloud}; Supabase already holds
 * the Sprint 1 schema, applied by hand from the SQL Editor, and no Flyway history table.
 *
 * <p>So the container is prepared the same way, by running V1 and V2 directly, and the context is
 * started on top of it. Flyway has to baseline that database and re-apply both migrations without
 * failing, and Hibernate's {@code validate} has to accept the result. A red run here is a
 * deployment that dies on boot with no way to recover from the outside.
 */
@SpringBootTest
@ActiveProfiles("cloud")
class CloudProfileContextIT {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
        applyByHand("V1__esquema_inicial.sql");
        applyByHand("V2__rls.sql");
    }

    /** What the Sprint 1 runbook did: run the script as-is, outside Flyway. */
    private static void applyByHand(String migration) {
        Path script = MIGRATIONS.resolve(migration);
        try (Connection connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(script));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not apply " + script.toAbsolutePath(), ex);
        }
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("ddl-auto is validate, so Hibernate verified every mapping against the migrated schema")
    void schemaIsValidatedNotCreated() {
        // Act
        String ddlAuto = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");

        // Assert
        assertEquals("validate", ddlAuto);
    }

    @Test
    @DisplayName("A database that predates Flyway is baselined at 0 and V1 and V2 are re-applied over it")
    void preFlywayDatabaseIsBaselinedAndMigrated() {
        // Act
        List<String> history = jdbc.queryForList(
                "select version || ':' || type || ':' || success from flyway_schema_history order by installed_rank",
                String.class);

        // Assert: then every later migration, whatever their number, on top
        assertEquals(List.of("0:BASELINE:true", "1:SQL:true", "2:SQL:true"), history.subList(0, 3));
        assertTrue(history.stream().skip(3).allMatch(row -> row.endsWith(":SQL:true")), history.toString());
    }
}
