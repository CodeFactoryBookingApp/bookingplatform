package com.codefactory.bookingplatform.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security rule under test: the application role is taken from the
 * server-managed app_metadata claim only. Anything else, including the
 * client-editable user_metadata claim, must grant nothing.
 *
 * White box: the converter has three decision points (claim is a Map, role is a
 * String, role is not blank); each one is exercised on both outcomes.
 */
class SupabaseJwtAuthConverterTest {

    private final SupabaseJwtAuthConverter converter = new SupabaseJwtAuthConverter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject("11111111-2222-3333-4444-555555555555")
                .issuedAt(Instant.parse("2026-09-22T10:00:00Z"))
                .expiresAt(Instant.parse("2026-09-22T11:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static Jwt jwtWithAppMetadata(Object appMetadata) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("app_metadata", appMetadata);
        return jwtWithClaims(claims);
    }

    private static List<String> authorityNames(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @ParameterizedTest(name = "app_metadata.role = [{0}] grants [{1}]")
    @CsvSource({
            "client,   ROLE_CLIENT",
            "CLIENT,   ROLE_CLIENT",
            "Provider, ROLE_PROVIDER",
            "aDmIn,    ROLE_ADMIN",
            "a,        ROLE_A"})
    @DisplayName("A role in app_metadata becomes ROLE_ plus its upper case name")
    void roleFromAppMetadataIsUpperCasedAndPrefixed(String role, String expectedAuthority) {
        // Arrange
        Jwt jwt = jwtWithAppMetadata(Map.of("role", role));

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertEquals(List.of(expectedAuthority), authorityNames(authorities));
    }

    @ParameterizedTest(name = "under the Turkish locale [{0}] still grants [{1}]")
    @CsvSource({
            "admin,  ROLE_ADMIN",
            "client, ROLE_CLIENT",
            "i,      ROLE_I"})
    @DisplayName("The authority name does not depend on the JVM default locale (Turkish dotted-I trap)")
    void roleUpperCasingIsLocaleIndependent(String role, String expectedAuthority) {
        // Arrange
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        Jwt jwt = jwtWithAppMetadata(Map.of("role", role));
        try {
            // Act
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Assert
            assertEquals(List.of(expectedAuthority), authorityNames(authorities));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("Exactly one authority is granted, never a duplicate")
    void grantsASingleAuthority() {
        // Arrange
        Jwt jwt = jwtWithAppMetadata(Map.of("role", "client"));

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertEquals(1, authorities.size());
    }

    @Test
    @DisplayName("PRIVILEGE ESCALATION GUARD: a role planted in user_metadata is ignored")
    void roleInUserMetadataIsIgnored() {
        // Arrange
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_metadata", Map.of("role", "admin"));
        Jwt jwt = jwtWithClaims(claims);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertEquals(List.of(), authorityNames(authorities));
    }

    @Test
    @DisplayName("PRIVILEGE ESCALATION GUARD: user_metadata never overrides the app_metadata role")
    void userMetadataDoesNotOverrideAppMetadata() {
        // Arrange
        Map<String, Object> claims = new HashMap<>();
        claims.put("app_metadata", Map.of("role", "client"));
        claims.put("user_metadata", Map.of("role", "admin"));
        Jwt jwt = jwtWithClaims(claims);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertEquals(List.of("ROLE_CLIENT"), authorityNames(authorities));
    }

    @Test
    @DisplayName("An absent app_metadata claim grants nothing")
    void missingAppMetadataGrantsNothing() {
        // Arrange
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "ana@example.com");
        Jwt jwt = jwtWithClaims(claims);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("An app_metadata claim that is not a map grants nothing")
    void nonMapAppMetadataGrantsNothing() {
        // Arrange
        Jwt jwt = jwtWithAppMetadata("role=admin");

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("An app_metadata list grants nothing")
    void listAppMetadataGrantsNothing() {
        // Arrange
        Jwt jwt = jwtWithAppMetadata(List.of("admin"));

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("An app_metadata map without a role key grants nothing")
    void appMetadataWithoutRoleGrantsNothing() {
        // Arrange
        Jwt jwt = jwtWithAppMetadata(Map.of("provider", "email"));

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("An empty app_metadata map grants nothing")
    void emptyAppMetadataGrantsNothing() {
        // Arrange
        Jwt jwt = jwtWithAppMetadata(Map.of());

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @ParameterizedTest(name = "a blank role [{0}] grants nothing")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void blankRoleGrantsNothing(String role) {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", role);
        Jwt jwt = jwtWithAppMetadata(metadata);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("A null role grants nothing")
    void nullRoleGrantsNothing() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", null);
        Jwt jwt = jwtWithAppMetadata(metadata);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("A numeric role is not a String and grants nothing")
    void numericRoleGrantsNothing() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", 42);
        Jwt jwt = jwtWithAppMetadata(metadata);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("A role given as a list is not a String and grants nothing")
    void listRoleGrantsNothing() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", List.of("admin"));
        Jwt jwt = jwtWithAppMetadata(metadata);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("A boolean role is not a String and grants nothing")
    void booleanRoleGrantsNothing() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", Boolean.TRUE);
        Jwt jwt = jwtWithAppMetadata(metadata);

        // Act
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Assert
        assertTrue(authorities.isEmpty());
    }

    @Test
    @DisplayName("The returned collection is immutable, so no caller can add an authority")
    void returnedAuthoritiesAreImmutable() {
        // Arrange
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithAppMetadata(Map.of("role", "client")));

        // Act
        Executable act = () -> authorities.add(() -> "ROLE_ADMIN");

        // Assert
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, act);
    }

    /** The framework adds its own factor authorities (FACTOR_BEARER); only the roles matter here. */
    private static List<String> roleNames(Collection<? extends org.springframework.security.core.GrantedAuthority> a) {
        return a.stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(name -> name.startsWith("ROLE_"))
                .toList();
    }

    @Test
    @DisplayName("toAuthenticationConverter wires the authorities converter into the Spring Security token")
    void authenticationConverterUsesTheAuthoritiesConverter() {
        // Arrange
        JwtAuthenticationConverter authenticationConverter =
                SupabaseJwtAuthConverter.toAuthenticationConverter(converter);
        Jwt jwt = jwtWithAppMetadata(Map.of("role", "admin"));

        // Act
        JwtAuthenticationToken token = (JwtAuthenticationToken) authenticationConverter.convert(jwt);

        // Assert
        assertNotNull(token);
        assertEquals(List.of("ROLE_ADMIN"), roleNames(token.getAuthorities()));
    }

    @Test
    @DisplayName("toAuthenticationConverter grants no role when the role sits in user_metadata")
    void authenticationConverterIgnoresUserMetadata() {
        // Arrange
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_metadata", Map.of("role", "admin"));
        JwtAuthenticationConverter authenticationConverter =
                SupabaseJwtAuthConverter.toAuthenticationConverter(converter);
        Jwt jwt = jwtWithClaims(claims);

        // Act
        JwtAuthenticationToken token = (JwtAuthenticationToken) authenticationConverter.convert(jwt);

        // Assert
        assertNotNull(token);
        assertEquals(List.of(), roleNames(token.getAuthorities()));
    }

    @Test
    @DisplayName("The public constants keep the contract the rest of the code relies on")
    void constantsAreStable() {
        // Act
        String appMetadataClaim = SupabaseJwtAuthConverter.APP_METADATA_CLAIM;
        String roleKey = SupabaseJwtAuthConverter.ROLE_KEY;
        String rolePrefix = SupabaseJwtAuthConverter.ROLE_PREFIX;

        // Assert
        assertEquals("app_metadata", appMetadataClaim);
        assertEquals("role", roleKey);
        assertEquals("ROLE_", rolePrefix);
    }
}
