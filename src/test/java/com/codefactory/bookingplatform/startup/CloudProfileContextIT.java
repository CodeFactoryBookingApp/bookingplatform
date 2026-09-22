package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
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

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("The cloud profile starts against the committed schema.sql, which is what Render does on boot")
    void cloudProfileStarts() {
        assertNotNull(context);
        assertEquals(Set.of("cloud"), Set.of(context.getEnvironment().getActiveProfiles()));
    }

    @Test
    @DisplayName("ddl-auto is validate, so Hibernate verified every mapping against the committed DDL")
    void schemaIsValidatedNotCreated() {
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    @DisplayName("The Hikari pool is capped at 5 connections, the limit the Supabase free pooler imposes")
    void hikariPoolIsCapped() throws Exception {
        HikariDataSource dataSource = context.getBean(DataSource.class).unwrap(HikariDataSource.class);

        assertEquals(5, dataSource.getMaximumPoolSize());
        assertEquals(1, dataSource.getMinimumIdle());
    }

    @Test
    @DisplayName("The critical beans exist under the cloud profile too, not only under test")
    void criticalBeansExistInCloud() {
        LoginLockPolicy policy = context.getBean(LoginLockPolicy.class);

        assertEquals(5, policy.maxFailedAttempts());
        assertEquals(Duration.ofMinutes(15), policy.lockWindow());
        assertNotNull(context.getBean(JwtDecoder.class));
        assertNotNull(context.getBean(SecurityFilterChain.class));
        assertNotNull(context.getBean(java.time.Clock.class));
    }

    @Test
    @DisplayName("RISK: with no SUPABASE_SECRET_KEY the cloud profile still starts, holding an empty credential")
    void cloudProfileStartsWithoutACredential() {
        SupabaseProperties properties = context.getBean(SupabaseProperties.class);

        assertTrue(properties.secretKey().isBlank(),
                "no variable was provided, so the deployment would be running with no Supabase credential");
        assertEquals("https://placeholder.supabase.co", properties.url());
    }

    @Test
    @DisplayName("The health endpoint answers on the cloud profile, which is what Render polls")
    void healthAnswersInCloud() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("The readiness probe Render is configured to use answers on the cloud profile")
    void readinessAnswersInCloud() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
