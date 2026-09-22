package com.codefactory.bookingplatform.support;

import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.infrastructure.persistence.LoginAttemptJpaRepository;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration base for the tests that need the <em>real</em> JWT pipeline.
 *
 * <p>{@link PostgresIntegrationTestBase} gives us PostgreSQL; on top of that this base starts a
 * local JWKS endpoint and repoints {@code app.security.jwt-issuer} / {@code app.security.jwks-uri}
 * at it, so the {@code JwtDecoder} bean built by {@code SecurityConfig} resolves keys, checks
 * signatures, {@code exp} and {@code iss} for real instead of being short-circuited by the
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} helper.
 *
 * <p>It also installs {@link SimulatedIdentityProvider} over the mocked {@link IdentityProviderPort}
 * so the external provider behaves like a provider (one-time links, sessions) rather than like a
 * per-call stub.
 */
public abstract class JwtIntegrationTestBase extends PostgresIntegrationTestBase {

    protected static final LocalJwksServer JWKS_SERVER = LocalJwksServer.start();

    @DynamicPropertySource
    static void registerJwksProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt-issuer", JWKS_SERVER::issuer);
        registry.add("app.security.jwks-uri", JWKS_SERVER::jwksUri);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ClientJpaRepository clientJpaRepository;

    @Autowired
    protected LoginAttemptJpaRepository loginAttemptJpaRepository;

    @MockitoBean
    protected IdentityProviderPort identityProviderPort;

    protected SimulatedIdentityProvider identityProvider;

    @BeforeEach
    void resetStateAndInstallProvider() {
        clientJpaRepository.deleteAll();
        loginAttemptJpaRepository.deleteAll();
        identityProvider = new SimulatedIdentityProvider(JWKS_SERVER.jwks());
        identityProvider.install(identityProviderPort);
    }

    protected static TestJwks jwks() {
        return JWKS_SERVER.jwks();
    }

    /** Registration payload for the reference client used across the acceptance tests. */
    protected static String registrationPayload(String email, String document) {
        return """
                {
                  "fullName": "Ana Maria Perez",
                  "document": "%s",
                  "birthDate": "1995-04-10",
                  "email": "%s",
                  "phone": "+573001234567",
                  "city": "Bogota",
                  "notificationChannel": "EMAIL",
                  "password": "Str0ng!Pass"
                }
                """.formatted(document, email);
    }
}
