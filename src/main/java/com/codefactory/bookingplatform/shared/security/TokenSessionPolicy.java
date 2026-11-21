package com.codefactory.bookingplatform.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

@FunctionalInterface
public interface TokenSessionPolicy {

    boolean accepts(Jwt jwt);
}
