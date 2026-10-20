package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.shared.config.AuthPolicyProperties;
import com.codefactory.bookingplatform.shared.config.SecurityProperties;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the application does when a deployment variable is missing or empty.
 *
 * <p>Every case runs through {@link ApplicationContextRunner} with the real
 * {@code application.yml} loaded by {@link ConfigDataApplicationContextInitializer},
 * so the placeholders and their defaults are the production ones. The operating
 * system environment of the machine running the suite is swapped for a
 * controlled {@link SystemEnvironmentPropertySource}: that makes a variable
 * genuinely absent (or genuinely present, with relaxed binding intact) no
 * matter where the tests run.
 *
 * <p>These tests pin the behaviour as it is today, defects included. Where the
 * recorded behaviour is a deployment risk it is spelled out in the test name,
 * so a change in {@code src/main} that fixes it fails here loudly and on
 * purpose.
 *
 * <p>Arrange-Act-Assert: the runner with its simulated environment is the Arrange; the Act starts
 * the context once and reads back what the scenario is about (a property, a bound bean, the
 * start-up failure) through {@link #startAndRead}; the Assert checks those values once the context
 * is closed, so no assertion lives inside the runner callback.
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

    /** A runner that sees no deployment variable at all: every default applies. */
    private static ApplicationContextRunner runnerWithoutVariables() {
        return runnerWithEnvironment(Map.of());
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

    /** One bound bean of the started context. */
    private static <T> T readBean(ApplicationContextRunner runner, Class<T> type) {
        return startAndRead(runner, context -> context.getBean(type));
    }

    /** What a start leaves behind: the start-up failure, or (when it started) the requested bean. */
    private record Startup<T>(Throwable failure, T bean) {
    }

    private static <T> Startup<T> start(ApplicationContextRunner runner, Class<T> beanType) {
        return startAndRead(runner, context -> context.getStartupFailure() != null
                ? new Startup<>(context.getStartupFailure(), null)
                : new Startup<>(null, context.getBean(beanType)));
    }

    /** The active profiles and the resulting schema strategy, read from a single start. */
    private record ProfileOutcome(String[] activeProfiles, String ddlAuto) {
    }

    private static ProfileOutcome readProfileOutcome(ApplicationContextRunner runner) {
        return startAndRead(runner, context -> new ProfileOutcome(
                context.getEnvironment().getActiveProfiles(),
                context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SupabaseProperties.class, SecurityProperties.class, AuthPolicyProperties.class})
    static class BoundProperties {
    }

    @Nested
    @DisplayName("The harness itself: absence and presence are simulated faithfully")
    class RunnerContract {

        @Test
        @DisplayName("The process environment is replaced, so the host machine cannot leak into the scenarios")
        void processEnvironmentIsReplaced() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SUPABASE_URL", "https://sentinel.example.com");

            // Act
            SystemEnvironmentPropertySource source = startAndRead(runner, context ->
                    (SystemEnvironmentPropertySource) ((StandardEnvironment) context.getEnvironment())
                            .getPropertySources().get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME));

            // Assert
            assertNotNull(source);
            assertEquals("https://sentinel.example.com", source.getProperty("SUPABASE_URL"));
            assertNull(source.getProperty("PATH"), "the real OS environment must be gone");
        }

        @Test
        @DisplayName("application.yml is still loaded, so the production defaults are what is under test")
        void applicationYamlIsLoaded() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String applicationName = readProperty(runner, "spring.application.name");

            // Assert
            assertEquals("bookingplatform", applicationName);
        }
    }

    @Nested
    @DisplayName("SUPABASE_SECRET_KEY")
    class SupabaseSecretKey {

        @Test
        @DisplayName("RISK: when it is missing the context still starts and the secret key binds to an empty string")
        void missingSecretKeyStartsWithAnEmptyCredential() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            Startup<SupabaseProperties> startup = start(runner, SupabaseProperties.class);

            // Assert
            assertNull(startup.failure(), "the application starts with no credential at all");
            assertEquals("", startup.bean().secretKey());
        }

        @Test
        @DisplayName("RISK: an empty value is indistinguishable from a missing one, also accepted")
        void emptySecretKeyIsAccepted() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SUPABASE_SECRET_KEY", "");

            // Act
            Startup<SupabaseProperties> startup = start(runner, SupabaseProperties.class);

            // Assert
            assertNull(startup.failure());
            assertEquals("", startup.bean().secretKey());
        }

        @ParameterizedTest(name = "a blank-but-not-empty value of [{0}] is accepted too")
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("RISK: whitespace passes as a credential because nothing validates the field")
        void blankSecretKeyIsAccepted(String blank) {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SUPABASE_SECRET_KEY", blank);

            // Act
            Startup<SupabaseProperties> startup = start(runner, SupabaseProperties.class);

            // Assert
            assertNull(startup.failure());
            assertTrue(startup.bean().secretKey().isBlank());
        }

        @Test
        @DisplayName("A real value is bound unchanged")
        void realSecretKeyIsBound() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SUPABASE_SECRET_KEY", "sb_secret_abc123");

            // Act
            SupabaseProperties properties = readBean(runner, SupabaseProperties.class);

            // Assert
            assertEquals("sb_secret_abc123", properties.secretKey());
        }
    }

    @Nested
    @DisplayName("SUPABASE_URL")
    class SupabaseUrl {

        @Test
        @DisplayName("RISK: when it is missing the app starts pointed at a placeholder host that does not exist")
        void missingUrlFallsBackToPlaceholder() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            Startup<SupabaseProperties> startup = start(runner, SupabaseProperties.class);

            // Assert
            assertNull(startup.failure());
            assertEquals("https://placeholder.supabase.co", startup.bean().url());
        }

        @Test
        @DisplayName("RISK: the placeholder also propagates to the JWT issuer and the JWKS URI")
        void missingUrlPoisonsTheSecurityProperties() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            SecurityProperties security = readBean(runner, SecurityProperties.class);

            // Assert
            assertEquals("https://placeholder.supabase.co/auth/v1", security.jwtIssuer());
            assertEquals("https://placeholder.supabase.co/auth/v1/.well-known/jwks.json", security.jwksUri());
        }

        @Test
        @DisplayName("One variable drives three values: setting it fixes issuer and JWKS at once")
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
        @DisplayName("RISK: a trailing slash is not normalised and produces a double slash in the issuer")
        void trailingSlashIsNotNormalised() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SUPABASE_URL", "https://abc.supabase.co/");

            // Act
            SecurityProperties security = readBean(runner, SecurityProperties.class);

            // Assert
            assertEquals("https://abc.supabase.co//auth/v1", security.jwtIssuer());
        }
    }

    @Nested
    @DisplayName("JWT_ISSUER and JWKS_URI")
    class JwtIssuerAndJwksUri {

        @Test
        @DisplayName("RISK: JWT_ISSUER and JWKS_URI are NOT read; the yaml only honours SUPABASE_URL")
        void dedicatedVariablesAreIgnored() {
            // Arrange
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("JWT_ISSUER", "https://tenant.example.com/auth/v1");
            variables.put("JWKS_URI", "https://tenant.example.com/auth/v1/.well-known/jwks.json");
            ApplicationContextRunner runner = runnerWithEnvironment(variables);

            // Act
            SecurityProperties security = readBean(runner, SecurityProperties.class);

            // Assert
            assertEquals("https://placeholder.supabase.co/auth/v1", security.jwtIssuer());
            assertEquals("https://placeholder.supabase.co/auth/v1/.well-known/jwks.json", security.jwksUri());
        }

        @Test
        @DisplayName("The relaxed-binding names APP_SECURITY_JWT_ISSUER and APP_SECURITY_JWKS_URI do override")
        void relaxedBindingNamesDoOverride() {
            // Arrange
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("APP_SECURITY_JWT_ISSUER", "https://tenant.example.com/auth/v1");
            variables.put("APP_SECURITY_JWKS_URI", "https://tenant.example.com/jwks.json");
            ApplicationContextRunner runner = runnerWithEnvironment(variables);

            // Act
            SecurityProperties security = readBean(runner, SecurityProperties.class);

            // Assert
            assertEquals("https://tenant.example.com/auth/v1", security.jwtIssuer());
            assertEquals("https://tenant.example.com/jwks.json", security.jwksUri());
        }
    }

    @Nested
    @DisplayName("DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD")
    class DatabaseVariables {

        @Test
        @DisplayName("RISK: a missing DATABASE_URL silently points the app at a local database")
        void missingDatabaseUrlFallsBackToLocalhost() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String datasourceUrl = readProperty(runner, "spring.datasource.url");

            // Assert
            assertEquals("jdbc:postgresql://localhost:5432/bookingplatform", datasourceUrl);
        }

        @Test
        @DisplayName("RISK: missing credentials fall back to postgres/postgres instead of failing")
        void missingCredentialsFallBackToPostgres() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            Map<String, String> credentials = readProperties(runner,
                    "spring.datasource.username", "spring.datasource.password");

            // Assert
            assertEquals("postgres", credentials.get("spring.datasource.username"));
            assertEquals("postgres", credentials.get("spring.datasource.password"));
        }

        @Test
        @DisplayName("The three variables are honoured when present")
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
        @DisplayName("RISK: an empty DATABASE_URL is taken literally, it does not fall back to the default")
        void emptyDatabaseUrlIsTakenLiterally() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("DATABASE_URL", "");

            // Act
            String datasourceUrl = readProperty(runner, "spring.datasource.url");

            // Assert
            assertEquals("", datasourceUrl);
        }

        @Test
        @DisplayName("A database that refuses connections fails at startup, so a broken deploy never serves traffic")
        void unreachableDatabaseFailsAtStartup() {
            // Arrange
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/absent",
                            "spring.datasource.username=postgres",
                            "spring.datasource.password=postgres",
                            "spring.datasource.hikari.initialization-fail-timeout=1",
                            "spring.datasource.hikari.connection-timeout=250",
                            "spring.jpa.hibernate.ddl-auto=validate");

            // Act
            Throwable startupFailure = startAndRead(runner, AssertableApplicationContext::getStartupFailure);

            // Assert
            assertNotNull(startupFailure, "the database is the one dependency that is checked eagerly");
        }
    }

    @Nested
    @DisplayName("PORT")
    class Port {

        @Test
        @DisplayName("A missing PORT falls back to 8080, the port the Dockerfile exposes")
        void missingPortFallsBackTo8080() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String serverPort = readProperty(runner, "server.port");

            // Assert
            assertEquals("8080", serverPort);
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
    }

    @Nested
    @DisplayName("JPA_DDL_AUTO and SPRING_PROFILES_ACTIVE")
    class SchemaManagement {

        @Test
        @DisplayName("Without a profile the schema strategy is update, which lets Hibernate create tables")
        void defaultStrategyIsUpdate() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String ddlAuto = readProperty(runner, "spring.jpa.hibernate.ddl-auto");

            // Assert
            assertEquals("update", ddlAuto);
        }

        @Test
        @DisplayName("JPA_DDL_AUTO is honoured when no profile pins the strategy")
        void variableIsHonouredByDefault() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("JPA_DDL_AUTO", "none");

            // Act
            String ddlAuto = readProperty(runner, "spring.jpa.hibernate.ddl-auto");

            // Assert
            assertEquals("none", ddlAuto);
        }

        @Test
        @DisplayName("RISK: under the cloud profile JPA_DDL_AUTO is ignored, the strategy is hardcoded to validate")
        void cloudProfileIgnoresTheVariable() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("JPA_DDL_AUTO", "update")
                    .withPropertyValues("spring.profiles.active=cloud");

            // Act
            String ddlAuto = readProperty(runner, "spring.jpa.hibernate.ddl-auto");

            // Assert
            assertEquals("validate", ddlAuto);
        }

        @Test
        @DisplayName("The cloud profile shrinks the Hikari pool to 5, as the Supabase free pooler demands")
        void cloudProfileShrinksThePool() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables()
                    .withPropertyValues("spring.profiles.active=cloud");

            // Act
            Map<String, String> hikari = readProperties(runner,
                    "spring.datasource.hikari.maximum-pool-size", "spring.datasource.hikari.minimum-idle");

            // Assert
            assertEquals("5", hikari.get("spring.datasource.hikari.maximum-pool-size"));
            assertEquals("1", hikari.get("spring.datasource.hikari.minimum-idle"));
        }

        @Test
        @DisplayName("SPRING_PROFILES_ACTIVE=cloud, the value render.yaml sets, activates the cloud profile")
        void springProfilesActiveActivatesCloud() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("SPRING_PROFILES_ACTIVE", "cloud");

            // Act
            ProfileOutcome outcome = readProfileOutcome(runner);

            // Assert
            assertEquals(1, outcome.activeProfiles().length);
            assertEquals("cloud", outcome.activeProfiles()[0]);
            assertEquals("validate", outcome.ddlAuto());
        }

        @Test
        @DisplayName("RISK: without SPRING_PROFILES_ACTIVE no profile is active and ddl-auto stays at update")
        void withoutTheProfileTheSchemaIsMutable() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            ProfileOutcome outcome = readProfileOutcome(runner);

            // Assert
            assertEquals(0, outcome.activeProfiles().length);
            assertEquals("update", outcome.ddlAuto());
        }
    }

    @Nested
    @DisplayName("ALLOWED_ORIGINS and APP_BASE_URL (declared in render.yaml)")
    class UnusedRenderVariables {

        @Test
        @DisplayName("RISK: ALLOWED_ORIGINS is declared in render.yaml but no property in the app consumes it")
        void allowedOriginsIsNotWiredToAnything() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("ALLOWED_ORIGINS", "https://frontend.example.com");

            // Act
            Map<String, String> corsProperties = readProperties(runner,
                    "spring.web.cors.allowed-origins", "app.cors.allowed-origins", "app.security.allowed-origins");

            // Assert
            assertNull(corsProperties.get("spring.web.cors.allowed-origins"));
            assertNull(corsProperties.get("app.cors.allowed-origins"));
            assertNull(corsProperties.get("app.security.allowed-origins"));
        }

        @Test
        @DisplayName("RISK: APP_BASE_URL becomes app.base-url by relaxed binding, but no @ConfigurationProperties reads it")
        void appBaseUrlIsVisibleButUnconsumed() {
            // Arrange
            ApplicationContextRunner runner = runnerWith("APP_BASE_URL", "https://bookingplatform.example.com");

            // Act
            String baseUrl = readProperty(runner, "app.base-url");

            // Assert
            assertEquals("https://bookingplatform.example.com", baseUrl);
            assertTrue(recordComponentsOf(SupabaseProperties.class, SecurityProperties.class,
                    AuthPolicyProperties.class).stream().noneMatch("baseUrl"::equals),
                    "no configuration properties type binds app.base-url, so the value is dead configuration");
        }

        private java.util.List<String> recordComponentsOf(Class<?>... types) {
            return java.util.Arrays.stream(types)
                    .flatMap(type -> java.util.Arrays.stream(type.getRecordComponents()))
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();
        }
    }

    @Nested
    @DisplayName("app.auth-policy")
    class AuthPolicy {

        @Test
        @DisplayName("The lock policy defaults are fixed in the yaml: 5 attempts in a 15 minute window")
        void policyDefaults() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            AuthPolicyProperties policy = readBean(runner, AuthPolicyProperties.class);

            // Assert
            assertNotNull(policy);
            assertEquals(5, policy.maxFailedAttempts());
            assertEquals(15, policy.lockWindowMinutes());
        }

        @Test
        @DisplayName("The policy is overridable per environment through relaxed binding")
        void policyIsOverridable() {
            // Arrange
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("APP_AUTH_POLICY_MAX_FAILED_ATTEMPTS", "3");
            variables.put("APP_AUTH_POLICY_LOCK_WINDOW_MINUTES", "30");
            ApplicationContextRunner runner = runnerWithEnvironment(variables);

            // Act
            AuthPolicyProperties policy = readBean(runner, AuthPolicyProperties.class);

            // Assert
            assertEquals(3, policy.maxFailedAttempts());
            assertEquals(30, policy.lockWindowMinutes());
        }
    }

    @Nested
    @DisplayName("Actuator exposure")
    class ActuatorExposure {

        @Test
        @DisplayName("Only health and info are exposed over HTTP")
        void onlyHealthAndInfoAreExposed() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String exposed = readProperty(runner, "management.endpoints.web.exposure.include");

            // Assert
            assertEquals("health,info", exposed);
        }

        @Test
        @DisplayName("Health probes are enabled, which is what creates /actuator/health/readiness for Render")
        void probesAreEnabled() {
            // Arrange
            ApplicationContextRunner runner = runnerWithoutVariables();

            // Act
            String probesEnabled = readProperty(runner, "management.endpoint.health.probes.enabled");

            // Assert
            assertEquals("true", probesEnabled);
        }
    }
}
