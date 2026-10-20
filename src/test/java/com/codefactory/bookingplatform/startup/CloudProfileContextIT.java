package com.codefactory.bookingplatform.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The profile that actually ships. Render runs the image with
 * {@code SPRING_PROFILES_ACTIVE=cloud}, and that profile pins
 * {@code ddl-auto=validate}: Hibernate refuses to start if the database does
 * not already match the entity mapping.
 *
 * <p>So the container is created from {@code docs/database/schema.sql}, the
 * physical model committed for Sprint 1, and the context is started on top of
 * it. A green run here is the evidence that the documented DDL and the code
 * agree; a red one is a deployment that dies on boot with no way to recover
 * from the outside.
 */
@SpringBootTest
@ActiveProfiles("cloud")
class CloudProfileContextIT {

    private static final Path SCHEMA = Path.of("docs", "database", "schema.sql");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
        applyCommittedSchema();
    }

    private static void applyCommittedSchema() {
        try (Connection connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(SCHEMA));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not apply " + SCHEMA.toAbsolutePath(), ex);
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

    @Test
    @DisplayName("ddl-auto is validate, so Hibernate verified every mapping against the committed DDL")
    void schemaIsValidatedNotCreated() {
        // Act
        String ddlAuto = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");

        // Assert
        assertEquals("validate", ddlAuto);
    }
}
