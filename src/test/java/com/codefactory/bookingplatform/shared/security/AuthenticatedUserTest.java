package com.codefactory.bookingplatform.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The caller as use cases see it: the subject of the token and whether its platform role is ADMIN. */
class AuthenticatedUserTest {

    @ParameterizedTest(name = "[{index}] {0} -> admin={1}")
    @CsvSource({"ROLE_ADMIN, true", "ROLE_PROVIDER, false", "ROLE_CLIENT, false"})
    @DisplayName("The id is the token subject and only ROLE_ADMIN counts as administrator")
    void readsSubjectAndAdminFlag(String authority, boolean admin) {
        // Arrange
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(id.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));

        // Act
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        // Assert
        assertEquals(id, user.id());
        assertEquals(admin, user.admin());
    }
}
