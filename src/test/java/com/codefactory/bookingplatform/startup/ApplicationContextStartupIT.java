package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.auth.application.LoginUseCase;
import com.codefactory.bookingplatform.auth.application.LogoutUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordRecoveryUseCase;
import com.codefactory.bookingplatform.auth.application.PasswordResetUseCase;
import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.LoginAttemptRepository;
import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.auth.infrastructure.persistence.LoginAttemptJpaRepository;
import com.codefactory.bookingplatform.auth.infrastructure.supabase.GoTrueClient;
import com.codefactory.bookingplatform.identity.application.ConfirmEmailUseCase;
import com.codefactory.bookingplatform.identity.application.RegisterClientUseCase;
import com.codefactory.bookingplatform.identity.application.ResendVerificationUseCase;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientJpaRepository;
import com.codefactory.bookingplatform.shared.config.AuthPolicyProperties;
import com.codefactory.bookingplatform.shared.config.SecurityProperties;
import com.codefactory.bookingplatform.shared.config.SupabaseJwtAuthConverter;
import com.codefactory.bookingplatform.shared.config.SupabaseProperties;
import com.codefactory.bookingplatform.shared.error.GlobalExceptionHandler;
import com.codefactory.bookingplatform.shared.observability.TraceIdFilter;
import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * The application context under the {@code test} profile: it must start, and it
 * must start complete. A context that loads but is missing a bean, or that
 * builds a policy from the wrong numbers, is a deployment that fails later and
 * in production, which is exactly what this class exists to prevent.
 */
class ApplicationContextStartupIT extends PostgresIntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("The context starts")
    class ContextStarts {

        @Test
        @DisplayName("The application context is available and running")
        void contextIsUp() {
            assertNotNull(context);
            assertNotNull(context.getId());
        }

        @Test
        @DisplayName("The test profile is the active one, so the test datasource and schema apply")
        void testProfileIsActive() {
            assertEquals(Set.of("test"), Set.of(context.getEnvironment().getActiveProfiles()));
        }

        @Test
        @DisplayName("The schema strategy under test is create-drop, so every run starts from an empty database")
        void schemaIsRecreatedForTheSuite() {
            assertEquals("create-drop", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        }
    }

    @Nested
    @DisplayName("Domain policy beans carry the configured values")
    class PolicyBeans {

        @Test
        @DisplayName("LoginLockPolicy is built from app.auth-policy: 5 attempts, 15 minute window")
        void loginLockPolicyMatchesConfiguration() {
            LoginLockPolicy policy = context.getBean(LoginLockPolicy.class);

            assertEquals(5, policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(15), policy.lockWindow());
        }

        @Test
        @DisplayName("LoginLockPolicy agrees with the AuthPolicyProperties bean it was built from")
        void loginLockPolicyAgreesWithItsProperties() {
            AuthPolicyProperties properties = context.getBean(AuthPolicyProperties.class);
            LoginLockPolicy policy = context.getBean(LoginLockPolicy.class);

            assertEquals(properties.maxFailedAttempts(), policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(properties.lockWindowMinutes()), policy.lockWindow());
        }

        @Test
        @DisplayName("The Clock bean runs on UTC, so audit timestamps do not drift with the host zone")
        void clockIsUtc() {
            assertEquals(ZoneOffset.UTC, context.getBean(Clock.class).getZone());
        }

        @Test
        @DisplayName("There is exactly one Clock bean, so nothing can inject a different notion of now")
        void clockIsUnique() {
            assertEquals(1, context.getBeanNamesForType(Clock.class).length);
        }
    }

    @Nested
    @DisplayName("Security beans")
    class SecurityBeans {

        @Test
        @DisplayName("The JwtDecoder is a Nimbus decoder, which is the JWKS-backed one")
        void jwtDecoderIsNimbus() {
            assertInstanceOf(NimbusJwtDecoder.class, context.getBean(JwtDecoder.class));
        }

        @Test
        @DisplayName("There is exactly one SecurityFilterChain, so no second chain can shadow the rules")
        void singleSecurityFilterChain() {
            assertEquals(1, context.getBeanNamesForType(SecurityFilterChain.class).length);
            assertNotNull(context.getBean(SecurityFilterChain.class));
        }

        @Test
        @DisplayName("The security properties point at the Supabase auth endpoints of the configured project")
        void securityPropertiesArePresent() {
            SecurityProperties properties = context.getBean(SecurityProperties.class);

            assertTrue(properties.jwtIssuer().endsWith("/auth/v1"));
            assertTrue(properties.jwksUri().endsWith("/.well-known/jwks.json"));
        }

        @Test
        @DisplayName("The Supabase JWT converter bean is registered, which is what maps app_metadata.role")
        void jwtConverterIsRegistered() {
            assertNotNull(context.getBean(SupabaseJwtAuthConverter.class));
        }

        @Test
        @DisplayName("RISK: the only CorsConfigurationSource is Spring MVC's introspector, no application CORS policy exists")
        void thereIsNoApplicationDefinedCorsPolicy() {
            String[] names = context.getBeanNamesForType(CorsConfigurationSource.class);

            assertEquals(1, names.length);
            assertEquals("mvcHandlerMappingIntrospector", names[0],
                    "http.cors(withDefaults()) falls back to the MVC introspector; "
                            + "render.yaml declares ALLOWED_ORIGINS but nothing turns it into a policy");
        }

        @Test
        @DisplayName("RISK: a cross origin preflight gets no Access-Control-Allow-Origin, so a browser frontend is blocked")
        void preflightGetsNoCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "https://frontend.example.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("Persistence beans")
    class PersistenceBeans {

        @Test
        @DisplayName("Both Spring Data repositories are created")
        void jpaRepositoriesExist() {
            assertNotNull(context.getBean(ClientJpaRepository.class));
            assertNotNull(context.getBean(LoginAttemptJpaRepository.class));
        }

        @Test
        @DisplayName("Both domain ports are satisfied by their persistence adapters")
        void domainPortsAreAdapted() {
            assertNotNull(context.getBean(ClientRepository.class));
            assertNotNull(context.getBean(LoginAttemptRepository.class));
        }

        @Test
        @DisplayName("The MapStruct mapper is a Spring bean, which is what the adapters inject")
        void mapperIsABean() {
            assertNotNull(context.getBean(ClientMapper.class));
        }

        @Test
        @DisplayName("The DataSource hands out a working connection to the real database")
        void dataSourceConnects() throws Exception {
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                assertTrue(connection.isValid(5));
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            }
        }

        @Test
        @DisplayName("Hibernate created the two Sprint 1 tables")
        void schemaWasCreated() throws Exception {
            try (Connection connection = context.getBean(DataSource.class).getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "select count(*) from information_schema.tables "
                                 + "where table_schema = 'public' and table_name in ('clients','login_attempts')")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Nested
    @DisplayName("Application and adapter beans")
    class ApplicationBeans {

        @Test
        @DisplayName("Every authentication use case is wired")
        void authUseCasesExist() {
            assertNotNull(context.getBean(LoginUseCase.class));
            assertNotNull(context.getBean(LogoutUseCase.class));
            assertNotNull(context.getBean(PasswordRecoveryUseCase.class));
            assertNotNull(context.getBean(PasswordResetUseCase.class));
            assertNotNull(context.getBean(UserProvisioning.class));
        }

        @Test
        @DisplayName("Every registration use case is wired")
        void identityUseCasesExist() {
            assertNotNull(context.getBean(RegisterClientUseCase.class));
            assertNotNull(context.getBean(ConfirmEmailUseCase.class));
            assertNotNull(context.getBean(ResendVerificationUseCase.class));
        }

        @Test
        @DisplayName("The identity provider port is satisfied by the Supabase GoTrue adapter")
        void identityProviderIsGoTrue() {
            assertInstanceOf(GoTrueClient.class, context.getBean(IdentityProviderPort.class));
        }

        @Test
        @DisplayName("The RestClient builder used to reach Supabase is available from the container")
        void restClientBuilderIsAvailable() {
            assertNotNull(context.getBean(RestClient.Builder.class));
        }

        @Test
        @DisplayName("The Supabase properties are bound and carry a non blank URL")
        void supabasePropertiesAreBound() {
            SupabaseProperties properties = context.getBean(SupabaseProperties.class);

            assertNotNull(properties);
            assertFalse(properties.url().isBlank());
        }

        @Test
        @DisplayName("The cross cutting beans are registered: trace id filter and the error handler")
        void crossCuttingBeansExist() {
            assertNotNull(context.getBean(TraceIdFilter.class));
            assertNotNull(context.getBean(GlobalExceptionHandler.class));
        }

        @Test
        @DisplayName("The OpenAPI contract bean is built, so Swagger UI has something to render")
        void openApiBeanExists() {
            OpenAPI openApi = context.getBean(OpenAPI.class);

            assertEquals("Booking Platform API", openApi.getInfo().getTitle());
            assertEquals("v1", openApi.getInfo().getVersion());
        }
    }
}
