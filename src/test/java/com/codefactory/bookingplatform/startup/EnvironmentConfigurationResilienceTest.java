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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

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
            runnerWith("SUPABASE_URL", "https://sentinel.example.com").run(context -> {
                StandardEnvironment environment = (StandardEnvironment) context.getEnvironment();
                SystemEnvironmentPropertySource source = (SystemEnvironmentPropertySource) environment
                        .getPropertySources().get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                assertNotNull(source);
                assertEquals("https://sentinel.example.com", source.getProperty("SUPABASE_URL"));
                assertNull(source.getProperty("PATH"), "the real OS environment must be gone");
            });
        }

        @Test
        @DisplayName("application.yml is still loaded, so the production defaults are what is under test")
        void applicationYamlIsLoaded() {
            runnerWithoutVariables().run(context -> assertEquals("bookingplatform",
                    context.getEnvironment().getProperty("spring.application.name")));
        }
    }

    @Nested
    @DisplayName("SUPABASE_SECRET_KEY")
    class SupabaseSecretKey {

        @Test
        @DisplayName("RISK: when it is missing the context still starts and the secret key binds to an empty string")
        void missingSecretKeyStartsWithAnEmptyCredential() {
            runnerWithoutVariables().run(context -> {
                assertNull(context.getStartupFailure(), "the application starts with no credential at all");
                assertEquals("", context.getBean(SupabaseProperties.class).secretKey());
            });
        }

        @Test
        @DisplayName("RISK: an empty value is indistinguishable from a missing one, also accepted")
        void emptySecretKeyIsAccepted() {
            runnerWith("SUPABASE_SECRET_KEY", "").run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals("", context.getBean(SupabaseProperties.class).secretKey());
            });
        }

        @ParameterizedTest(name = "a blank-but-not-empty value of [{0}] is accepted too")
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("RISK: whitespace passes as a credential because nothing validates the field")
        void blankSecretKeyIsAccepted(String blank) {
            runnerWith("SUPABASE_SECRET_KEY", blank).run(context -> {
                assertNull(context.getStartupFailure());
                assertTrue(context.getBean(SupabaseProperties.class).secretKey().isBlank());
            });
        }

        @Test
        @DisplayName("A real value is bound unchanged")
        void realSecretKeyIsBound() {
            runnerWith("SUPABASE_SECRET_KEY", "sb_secret_abc123").run(context ->
                    assertEquals("sb_secret_abc123", context.getBean(SupabaseProperties.class).secretKey()));
        }
    }

    @Nested
    @DisplayName("SUPABASE_URL")
    class SupabaseUrl {

        @Test
        @DisplayName("RISK: when it is missing the app starts pointed at a placeholder host that does not exist")
        void missingUrlFallsBackToPlaceholder() {
            runnerWithoutVariables().run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals("https://placeholder.supabase.co",
                        context.getBean(SupabaseProperties.class).url());
            });
        }

        @Test
        @DisplayName("RISK: the placeholder also propagates to the JWT issuer and the JWKS URI")
        void missingUrlPoisonsTheSecurityProperties() {
            runnerWithoutVariables().run(context -> {
                SecurityProperties security = context.getBean(SecurityProperties.class);
                assertEquals("https://placeholder.supabase.co/auth/v1", security.jwtIssuer());
                assertEquals("https://placeholder.supabase.co/auth/v1/.well-known/jwks.json", security.jwksUri());
            });
        }

        @Test
        @DisplayName("One variable drives three values: setting it fixes issuer and JWKS at once")
        void oneVariableDrivesTheWholeAuthConfiguration() {
            runnerWith("SUPABASE_URL", "https://abc.supabase.co").run(context -> {
                assertEquals("https://abc.supabase.co", context.getBean(SupabaseProperties.class).url());
                SecurityProperties security = context.getBean(SecurityProperties.class);
                assertEquals("https://abc.supabase.co/auth/v1", security.jwtIssuer());
                assertEquals("https://abc.supabase.co/auth/v1/.well-known/jwks.json", security.jwksUri());
            });
        }

        @Test
        @DisplayName("RISK: a trailing slash is not normalised and produces a double slash in the issuer")
        void trailingSlashIsNotNormalised() {
            runnerWith("SUPABASE_URL", "https://abc.supabase.co/").run(context ->
                    assertEquals("https://abc.supabase.co//auth/v1",
                            context.getBean(SecurityProperties.class).jwtIssuer()));
        }
    }

    @Nested
    @DisplayName("JWT_ISSUER and JWKS_URI")
    class JwtIssuerAndJwksUri {

        @Test
        @DisplayName("RISK: JWT_ISSUER and JWKS_URI are NOT read; the yaml only honours SUPABASE_URL")
        void dedicatedVariablesAreIgnored() {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("JWT_ISSUER", "https://tenant.example.com/auth/v1");
            variables.put("JWKS_URI", "https://tenant.example.com/auth/v1/.well-known/jwks.json");

            runnerWithEnvironment(variables).run(context -> {
                SecurityProperties security = context.getBean(SecurityProperties.class);
                assertEquals("https://placeholder.supabase.co/auth/v1", security.jwtIssuer());
                assertEquals("https://placeholder.supabase.co/auth/v1/.well-known/jwks.json", security.jwksUri());
            });
        }

        @Test
        @DisplayName("The relaxed-binding names APP_SECURITY_JWT_ISSUER and APP_SECURITY_JWKS_URI do override")
        void relaxedBindingNamesDoOverride() {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("APP_SECURITY_JWT_ISSUER", "https://tenant.example.com/auth/v1");
            variables.put("APP_SECURITY_JWKS_URI", "https://tenant.example.com/jwks.json");

            runnerWithEnvironment(variables).run(context -> {
                SecurityProperties security = context.getBean(SecurityProperties.class);
                assertEquals("https://tenant.example.com/auth/v1", security.jwtIssuer());
                assertEquals("https://tenant.example.com/jwks.json", security.jwksUri());
            });
        }
    }

    @Nested
    @DisplayName("DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD")
    class DatabaseVariables {

        @Test
        @DisplayName("RISK: a missing DATABASE_URL silently points the app at a local database")
        void missingDatabaseUrlFallsBackToLocalhost() {
            runnerWithoutVariables().run(context -> assertEquals(
                    "jdbc:postgresql://localhost:5432/bookingplatform",
                    context.getEnvironment().getProperty("spring.datasource.url")));
        }

        @Test
        @DisplayName("RISK: missing credentials fall back to postgres/postgres instead of failing")
        void missingCredentialsFallBackToPostgres() {
            runnerWithoutVariables().run(context -> {
                assertEquals("postgres", context.getEnvironment().getProperty("spring.datasource.username"));
                assertEquals("postgres", context.getEnvironment().getProperty("spring.datasource.password"));
            });
        }

        @Test
        @DisplayName("The three variables are honoured when present")
        void databaseVariablesAreHonoured() {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("DATABASE_URL", "jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require");
            variables.put("DATABASE_USER", "postgres.abc");
            variables.put("DATABASE_PASSWORD", "s3cr3t");

            runnerWithEnvironment(variables).run(context -> {
                assertEquals("jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require",
                        context.getEnvironment().getProperty("spring.datasource.url"));
                assertEquals("postgres.abc", context.getEnvironment().getProperty("spring.datasource.username"));
                assertEquals("s3cr3t", context.getEnvironment().getProperty("spring.datasource.password"));
            });
        }

        @Test
        @DisplayName("RISK: an empty DATABASE_URL is taken literally, it does not fall back to the default")
        void emptyDatabaseUrlIsTakenLiterally() {
            runnerWith("DATABASE_URL", "").run(context ->
                    assertEquals("", context.getEnvironment().getProperty("spring.datasource.url")));
        }

        @Test
        @DisplayName("A database that refuses connections fails at startup, so a broken deploy never serves traffic")
        void unreachableDatabaseFailsAtStartup() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/absent",
                            "spring.datasource.username=postgres",
                            "spring.datasource.password=postgres",
                            "spring.datasource.hikari.initialization-fail-timeout=1",
                            "spring.datasource.hikari.connection-timeout=250",
                            "spring.jpa.hibernate.ddl-auto=validate")
                    .run(context -> assertNotNull(context.getStartupFailure(),
                            "the database is the one dependency that is checked eagerly"));
        }
    }

    @Nested
    @DisplayName("PORT")
    class Port {

        @Test
        @DisplayName("A missing PORT falls back to 8080, the port the Dockerfile exposes")
        void missingPortFallsBackTo8080() {
            runnerWithoutVariables().run(context ->
                    assertEquals("8080", context.getEnvironment().getProperty("server.port")));
        }

        @Test
        @DisplayName("The PORT injected by the platform is honoured, which is what Render needs")
        void injectedPortIsHonoured() {
            runnerWith("PORT", "10000").run(context ->
                    assertEquals("10000", context.getEnvironment().getProperty("server.port")));
        }
    }

    @Nested
    @DisplayName("JPA_DDL_AUTO and SPRING_PROFILES_ACTIVE")
    class SchemaManagement {

        @Test
        @DisplayName("Without a profile the schema strategy is update, which lets Hibernate create tables")
        void defaultStrategyIsUpdate() {
            runnerWithoutVariables().run(context ->
                    assertEquals("update", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")));
        }

        @Test
        @DisplayName("JPA_DDL_AUTO is honoured when no profile pins the strategy")
        void variableIsHonouredByDefault() {
            runnerWith("JPA_DDL_AUTO", "none").run(context ->
                    assertEquals("none", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")));
        }

        @Test
        @DisplayName("RISK: under the cloud profile JPA_DDL_AUTO is ignored, the strategy is hardcoded to validate")
        void cloudProfileIgnoresTheVariable() {
            runnerWith("JPA_DDL_AUTO", "update")
                    .withPropertyValues("spring.profiles.active=cloud")
                    .run(context -> assertEquals("validate",
                            context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")));
        }

        @Test
        @DisplayName("The cloud profile shrinks the Hikari pool to 5, as the Supabase free pooler demands")
        void cloudProfileShrinksThePool() {
            runnerWithoutVariables()
                    .withPropertyValues("spring.profiles.active=cloud")
                    .run(context -> {
                        assertEquals("5", context.getEnvironment()
                                .getProperty("spring.datasource.hikari.maximum-pool-size"));
                        assertEquals("1", context.getEnvironment()
                                .getProperty("spring.datasource.hikari.minimum-idle"));
                    });
        }

        @Test
        @DisplayName("SPRING_PROFILES_ACTIVE=cloud, the value render.yaml sets, activates the cloud profile")
        void springProfilesActiveActivatesCloud() {
            runnerWith("SPRING_PROFILES_ACTIVE", "cloud").run(context -> {
                assertEquals(1, context.getEnvironment().getActiveProfiles().length);
                assertEquals("cloud", context.getEnvironment().getActiveProfiles()[0]);
                assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            });
        }

        @Test
        @DisplayName("RISK: without SPRING_PROFILES_ACTIVE no profile is active and ddl-auto stays at update")
        void withoutTheProfileTheSchemaIsMutable() {
            runnerWithoutVariables().run(context -> {
                assertEquals(0, context.getEnvironment().getActiveProfiles().length);
                assertEquals("update", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            });
        }
    }

    @Nested
    @DisplayName("ALLOWED_ORIGINS and APP_BASE_URL (declared in render.yaml)")
    class UnusedRenderVariables {

        @Test
        @DisplayName("RISK: ALLOWED_ORIGINS is declared in render.yaml but no property in the app consumes it")
        void allowedOriginsIsNotWiredToAnything() {
            runnerWith("ALLOWED_ORIGINS", "https://frontend.example.com").run(context -> {
                assertNull(context.getEnvironment().getProperty("spring.web.cors.allowed-origins"));
                assertNull(context.getEnvironment().getProperty("app.cors.allowed-origins"));
                assertNull(context.getEnvironment().getProperty("app.security.allowed-origins"));
            });
        }

        @Test
        @DisplayName("RISK: APP_BASE_URL becomes app.base-url by relaxed binding, but no @ConfigurationProperties reads it")
        void appBaseUrlIsVisibleButUnconsumed() {
            runnerWith("APP_BASE_URL", "https://bookingplatform.example.com").run(context -> {
                assertEquals("https://bookingplatform.example.com",
                        context.getEnvironment().getProperty("app.base-url"));

                assertTrue(recordComponentsOf(SupabaseProperties.class, SecurityProperties.class,
                        AuthPolicyProperties.class).stream().noneMatch("baseUrl"::equals),
                        "no configuration properties type binds app.base-url, so the value is dead configuration");
            });
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
            runnerWithoutVariables().run(context -> {
                AuthPolicyProperties policy = context.getBean(AuthPolicyProperties.class);
                assertNotNull(policy);
                assertEquals(5, policy.maxFailedAttempts());
                assertEquals(15, policy.lockWindowMinutes());
            });
        }

        @Test
        @DisplayName("The policy is overridable per environment through relaxed binding")
        void policyIsOverridable() {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("APP_AUTH_POLICY_MAX_FAILED_ATTEMPTS", "3");
            variables.put("APP_AUTH_POLICY_LOCK_WINDOW_MINUTES", "30");

            runnerWithEnvironment(variables).run(context -> {
                AuthPolicyProperties policy = context.getBean(AuthPolicyProperties.class);
                assertEquals(3, policy.maxFailedAttempts());
                assertEquals(30, policy.lockWindowMinutes());
            });
        }
    }

    @Nested
    @DisplayName("Actuator exposure")
    class ActuatorExposure {

        @Test
        @DisplayName("Only health and info are exposed over HTTP")
        void onlyHealthAndInfoAreExposed() {
            runnerWithoutVariables().run(context -> assertEquals("health,info",
                    context.getEnvironment().getProperty("management.endpoints.web.exposure.include")));
        }

        @Test
        @DisplayName("Health probes are enabled, which is what creates /actuator/health/readiness for Render")
        void probesAreEnabled() {
            runnerWithoutVariables().run(context -> assertEquals("true",
                    context.getEnvironment().getProperty("management.endpoint.health.probes.enabled")));
        }
    }
}
