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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 *
 * <p>Arrange-Act-Assert: the context itself is the shared fixture (started once by Spring), so most
 * tests have nothing to arrange; the Act is the lookup or query against the running context and the
 * Assert checks what came back.
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
            // Act
            String contextId = context.getId();

            // Assert
            assertNotNull(context);
            assertNotNull(contextId);
        }

        @Test
        @DisplayName("The test profile is the active one, so the test datasource and schema apply")
        void testProfileIsActive() {
            // Act
            String[] activeProfiles = context.getEnvironment().getActiveProfiles();

            // Assert
            assertEquals(Set.of("test"), Set.of(activeProfiles));
        }

        @Test
        @DisplayName("The schema strategy under test is create-drop, so every run starts from an empty database")
        void schemaIsRecreatedForTheSuite() {
            // Act
            String ddlAuto = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");

            // Assert
            assertEquals("create-drop", ddlAuto);
        }
    }

    @Nested
    @DisplayName("Domain policy beans carry the configured values")
    class PolicyBeans {

        @Test
        @DisplayName("LoginLockPolicy is built from app.auth-policy: 5 attempts, 15 minute window")
        void loginLockPolicyMatchesConfiguration() {
            // Act
            LoginLockPolicy policy = context.getBean(LoginLockPolicy.class);

            // Assert
            assertEquals(5, policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(15), policy.lockWindow());
        }

        @Test
        @DisplayName("LoginLockPolicy agrees with the AuthPolicyProperties bean it was built from")
        void loginLockPolicyAgreesWithItsProperties() {
            // Arrange
            AuthPolicyProperties properties = context.getBean(AuthPolicyProperties.class);

            // Act
            LoginLockPolicy policy = context.getBean(LoginLockPolicy.class);

            // Assert
            assertEquals(properties.maxFailedAttempts(), policy.maxFailedAttempts());
            assertEquals(Duration.ofMinutes(properties.lockWindowMinutes()), policy.lockWindow());
        }

        @Test
        @DisplayName("The Clock bean runs on UTC, so audit timestamps do not drift with the host zone")
        void clockIsUtc() {
            // Act
            Clock clock = context.getBean(Clock.class);

            // Assert
            assertEquals(ZoneOffset.UTC, clock.getZone());
        }

        @Test
        @DisplayName("There is exactly one Clock bean, so nothing can inject a different notion of now")
        void clockIsUnique() {
            // Act
            String[] clockBeans = context.getBeanNamesForType(Clock.class);

            // Assert
            assertEquals(1, clockBeans.length);
        }
    }

    @Nested
    @DisplayName("Security beans")
    class SecurityBeans {

        @Test
        @DisplayName("The JwtDecoder is a Nimbus decoder, which is the JWKS-backed one")
        void jwtDecoderIsNimbus() {
            // Act
            JwtDecoder decoder = context.getBean(JwtDecoder.class);

            // Assert
            assertInstanceOf(NimbusJwtDecoder.class, decoder);
        }

        @Test
        @DisplayName("There is exactly one SecurityFilterChain, so no second chain can shadow the rules")
        void singleSecurityFilterChain() {
            // Act
            String[] chainNames = context.getBeanNamesForType(SecurityFilterChain.class);

            // Assert
            assertEquals(1, chainNames.length);
            assertNotNull(context.getBean(SecurityFilterChain.class));
        }

        @Test
        @DisplayName("The security properties point at the Supabase auth endpoints of the configured project")
        void securityPropertiesArePresent() {
            // Act
            SecurityProperties properties = context.getBean(SecurityProperties.class);

            // Assert
            assertTrue(properties.jwtIssuer().endsWith("/auth/v1"));
            assertTrue(properties.jwksUri().endsWith("/.well-known/jwks.json"));
        }

        @Test
        @DisplayName("The Supabase JWT converter bean is registered, which is what maps app_metadata.role")
        void jwtConverterIsRegistered() {
            // Act
            SupabaseJwtAuthConverter converter = context.getBean(SupabaseJwtAuthConverter.class);

            // Assert
            assertNotNull(converter);
        }

        @Test
        @DisplayName("RISK: the only CorsConfigurationSource is Spring MVC's introspector, no application CORS policy exists")
        void thereIsNoApplicationDefinedCorsPolicy() {
            // Act
            String[] names = context.getBeanNamesForType(CorsConfigurationSource.class);

            // Assert
            assertEquals(1, names.length);
            assertEquals("mvcHandlerMappingIntrospector", names[0],
                    "http.cors(withDefaults()) falls back to the MVC introspector; "
                            + "render.yaml declares ALLOWED_ORIGINS but nothing turns it into a policy");
        }

        @Test
        @DisplayName("RISK: a cross origin preflight gets no Access-Control-Allow-Origin, so a browser frontend is blocked")
        void preflightGetsNoCorsHeaders() throws Exception {
            // Act
            ResultActions result = mockMvc.perform(options("/api/v1/auth/login")
                    .header(HttpHeaders.ORIGIN, "https://frontend.example.com")
                    .header("Access-Control-Request-Method", "POST"));

            // Assert
            result.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("Persistence beans")
    class PersistenceBeans {

        @Test
        @DisplayName("Both Spring Data repositories are created")
        void jpaRepositoriesExist() {
            // Act
            ClientJpaRepository clients = context.getBean(ClientJpaRepository.class);
            LoginAttemptJpaRepository loginAttempts = context.getBean(LoginAttemptJpaRepository.class);

            // Assert
            assertNotNull(clients);
            assertNotNull(loginAttempts);
        }

        @Test
        @DisplayName("Both domain ports are satisfied by their persistence adapters")
        void domainPortsAreAdapted() {
            // Act
            ClientRepository clients = context.getBean(ClientRepository.class);
            LoginAttemptRepository loginAttempts = context.getBean(LoginAttemptRepository.class);

            // Assert
            assertNotNull(clients);
            assertNotNull(loginAttempts);
        }

        @Test
        @DisplayName("The MapStruct mapper is a Spring bean, which is what the adapters inject")
        void mapperIsABean() {
            // Act
            ClientMapper mapper = context.getBean(ClientMapper.class);

            // Assert
            assertNotNull(mapper);
        }

        @Test
        @DisplayName("The DataSource hands out a working connection to the real database")
        void dataSourceConnects() throws Exception {
            // Arrange
            DataSource dataSource = context.getBean(DataSource.class);

            // Act
            try (Connection connection = dataSource.getConnection()) {
                // Assert
                assertTrue(connection.isValid(5));
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            }
        }

        @Test
        @DisplayName("Hibernate created the two Sprint 1 tables")
        void schemaWasCreated() throws Exception {
            // Arrange
            DataSource dataSource = context.getBean(DataSource.class);
            String countSprint1Tables = "select count(*) from information_schema.tables "
                    + "where table_schema = 'public' and table_name in ('clients','login_attempts')";

            // Act
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(countSprint1Tables)) {
                // Assert
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
            // Act
            LoginUseCase login = context.getBean(LoginUseCase.class);
            LogoutUseCase logout = context.getBean(LogoutUseCase.class);
            PasswordRecoveryUseCase passwordRecovery = context.getBean(PasswordRecoveryUseCase.class);
            PasswordResetUseCase passwordReset = context.getBean(PasswordResetUseCase.class);
            UserProvisioning userProvisioning = context.getBean(UserProvisioning.class);

            // Assert
            assertNotNull(login);
            assertNotNull(logout);
            assertNotNull(passwordRecovery);
            assertNotNull(passwordReset);
            assertNotNull(userProvisioning);
        }

        @Test
        @DisplayName("Every registration use case is wired")
        void identityUseCasesExist() {
            // Act
            RegisterClientUseCase register = context.getBean(RegisterClientUseCase.class);
            ConfirmEmailUseCase confirmEmail = context.getBean(ConfirmEmailUseCase.class);
            ResendVerificationUseCase resendVerification = context.getBean(ResendVerificationUseCase.class);

            // Assert
            assertNotNull(register);
            assertNotNull(confirmEmail);
            assertNotNull(resendVerification);
        }

        @Test
        @DisplayName("The identity provider port is satisfied by the Supabase GoTrue adapter")
        void identityProviderIsGoTrue() {
            // Act
            IdentityProviderPort identityProvider = context.getBean(IdentityProviderPort.class);

            // Assert
            assertInstanceOf(GoTrueClient.class, identityProvider);
        }

        @Test
        @DisplayName("The RestClient builder used to reach Supabase is available from the container")
        void restClientBuilderIsAvailable() {
            // Act
            RestClient.Builder builder = context.getBean(RestClient.Builder.class);

            // Assert
            assertNotNull(builder);
        }

        @Test
        @DisplayName("The Supabase properties are bound and carry a non blank URL")
        void supabasePropertiesAreBound() {
            // Act
            SupabaseProperties properties = context.getBean(SupabaseProperties.class);

            // Assert
            assertNotNull(properties);
            assertFalse(properties.url().isBlank());
        }

        @Test
        @DisplayName("The cross cutting beans are registered: trace id filter and the error handler")
        void crossCuttingBeansExist() {
            // Act
            TraceIdFilter traceIdFilter = context.getBean(TraceIdFilter.class);
            GlobalExceptionHandler exceptionHandler = context.getBean(GlobalExceptionHandler.class);

            // Assert
            assertNotNull(traceIdFilter);
            assertNotNull(exceptionHandler);
        }

        @Test
        @DisplayName("The OpenAPI contract bean is built, so Swagger UI has something to render")
        void openApiBeanExists() {
            // Act
            OpenAPI openApi = context.getBean(OpenAPI.class);

            // Assert
            assertEquals("Booking Platform API", openApi.getInfo().getTitle());
            assertEquals("v1", openApi.getInfo().getVersion());
        }
    }
}
