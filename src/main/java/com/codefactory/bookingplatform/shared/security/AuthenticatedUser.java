package com.codefactory.bookingplatform.shared.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Who is calling, as controllers hand it to use cases: the Supabase user id and whether the
 * platform role is ADMIN (ADR-0008). Built from the token Spring Security already validated.
 */
public record AuthenticatedUser(UUID id, boolean admin) {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public static AuthenticatedUser from(JwtAuthenticationToken authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return new AuthenticatedUser(UUID.fromString(authentication.getToken().getSubject()), admin);
    }
}
