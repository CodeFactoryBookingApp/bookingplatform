package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.shared.config.AuthPolicyProperties;
import com.codefactory.bookingplatform.shared.config.SecurityProperties;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The contract between the deployment variables that {@code render.yaml} sets and the
 * properties the application reads from them.
 *
 * <p>Every case runs through {@link ApplicationContextRunner} with the real
 * {@code application.yml} loaded by {@link ConfigDataApplicationContextInitializer},
 * so the placeholders are the production ones. The operating system environment of
 * the machine running the suite is swapped for a controlled
 * {@link SystemEnvironmentPropertySource}, so the result does not depend on where the
 * tests run.
 *
 * <p>Behaviours that are known defects (no fail-fast without {@code SUPABASE_SECRET_KEY},
 * BUG-013; a trailing slash in {@code SUPABASE_URL}, BUG-014; {@code ALLOWED_ORIGINS}
 * ignored, BUG-011) are reported in Mantis and deliberately not pinned here.
 *
 * <p>Arrange-Act-Assert: the runner with its simulated environment is the Arrange; the Act starts
 * the context once and reads back what the scenario is about through {@link #startAndRead}; the
 * Assert checks those values once the context is closed.
 */
class EnvironmentConfigurationResilienceTest {

    /** A runner whose process environment contains exactly {@code variables}. */
    private static ApplicationContextRunner runnerWithEnvironment(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer((ConfigurableApplicationContext context) ->
                        context.getEnvironment().getPropertySources().replace(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                new SystemEnvironmentPropertySource(
                                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                        variables)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(BoundProperties.class);
    }

    private static ApplicationContextRunner runnerWith(String name, Object value) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(name, value);
        return runnerWithEnvironment(variables);
    }

    /** Starts the context described by {@code runner}, reads {@code probe} from it and closes it again. */
    private static <T> T startAndRead(ApplicationContextRunner runner,
                                      Function<AssertableApplicationContext, T> probe) {
        AtomicReference<T> value = new AtomicReference<>();
        runner.run(context -> value.set(probe.apply(context)));
        return value.get();
    }

    /** The resolved value of one property in the started context ({@code null} when unresolved). */
    private static String readProperty(ApplicationContextRunner runner, String name) {
        return startAndRead(runner, context -> context.getEnvironment().getProperty(name));
    }

    /** The resolved values of several properties, read from a single start of the context. */
    private static Map<String, String> readProperties(ApplicationContextRunner runner, String... names) {
        return startAndRead(runner, context -> {
            Map<String, String> values = new HashMap<>();
            for (String name : names) {
                values.put(name, context.getEnvironment().getProperty(name));
            }
            return values;
        });
    }

    /** The active profiles and the resulting schema strategy, read from a single start. */
    private record ProfileOutcome(String[] activeProfiles, String ddlAuto) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SupabaseProperties.class, SecurityProperties.class, AuthPolicyProperties.class})
    static class BoundProperties {
    }

    @Test
    @DisplayName("SUPABASE_URL drives the whole auth configuration: Supabase URL, JWT issuer and JWKS URI")
    void oneVariableDrivesTheWholeAuthConfiguration() {
        // Arrange
        ApplicationContextRunner runner = runnerWith("SUPABASE_URL", "https://abc.supabase.co");
        record AuthConfiguration(SupabaseProperties supabase, SecurityProperties security) {
        }

        // Act
        AuthConfiguration bound = startAndRead(runner, context -> new AuthConfiguration(
                context.getBean(SupabaseProperties.class), context.getBean(SecurityProperties.class)));

        // Assert
        assertEquals("https://abc.supabase.co", bound.supabase().url());
        assertEquals("https://abc.supabase.co/auth/v1", bound.security().jwtIssuer());
        assertEquals("https://abc.supabase.co/auth/v1/.well-known/jwks.json", bound.security().jwksUri());
    }

    @Test
    @DisplayName("DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD are honoured when present")
    void databaseVariablesAreHonoured() {
        // Arrange
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("DATABASE_URL", "jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require");
        variables.put("DATABASE_USER", "postgres.abc");
        variables.put("DATABASE_PASSWORD", "s3cr3t");
        ApplicationContextRunner runner = runnerWithEnvironment(variables);

        // Act
        Map<String, String> datasource = readProperties(runner,
                "spring.datasource.url", "spring.datasource.username", "spring.datasource.password");

        // Assert
        assertEquals("jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require",
                datasource.get("spring.datasource.url"));
        assertEquals("postgres.abc", datasource.get("spring.datasource.username"));
        assertEquals("s3cr3t", datasource.get("spring.datasource.password"));
    }

    @Test
    @DisplayName("The PORT injected by the platform is honoured, which is what Render needs")
    void injectedPortIsHonoured() {
        // Arrange
        ApplicationContextRunner runner = runnerWith("PORT", "10000");

        // Act
        String serverPort = readProperty(runner, "server.port");

        // Assert
        assertEquals("10000", serverPort);
    }

    @Test
    @DisplayName("SPRING_PROFILES_ACTIVE=cloud, the value render.yaml sets, activates the cloud profile and validate")
    void springProfilesActiveActivatesCloud() {
        // Arrange
        ApplicationContextRunner runner = runnerWith("SPRING_PROFILES_ACTIVE", "cloud");

        // Act
        ProfileOutcome outcome = startAndRead(runner, context -> new ProfileOutcome(
                context.getEnvironment().getActiveProfiles(),
                context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")));

        // Assert
        assertEquals(1, outcome.activeProfiles().length);
        assertEquals("cloud", outcome.activeProfiles()[0]);
        assertEquals("validate", outcome.ddlAuto());
    }
}
