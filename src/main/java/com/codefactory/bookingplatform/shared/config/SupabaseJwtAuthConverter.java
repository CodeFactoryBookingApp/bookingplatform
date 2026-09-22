package com.codefactory.bookingplatform.shared.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Supabase JWT claims to Spring Security authorities.
 * The application role lives in app_metadata.role (server-managed, not user-editable).
 * Deliberately ignores user_metadata, which clients can modify.
 */
@Component
public class SupabaseJwtAuthConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String APP_METADATA_CLAIM = "app_metadata";
    public static final String ROLE_KEY = "role";
    public static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object appMetadata = jwt.getClaim(APP_METADATA_CLAIM);
        if (appMetadata instanceof Map<?, ?> metadata) {
            Object role = metadata.get(ROLE_KEY);
            if (role instanceof String roleValue && !roleValue.isBlank()) {
                // Locale.ROOT: the authority name is a protocol value, not display text.
                // Under a Turkish default locale "admin" would upper case to "ADMİN"
                // and every hasRole("ADMIN") check would silently fail.
                return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + roleValue.toUpperCase(Locale.ROOT)));
            }
        }
        return List.of();
    }

    public static JwtAuthenticationConverter toAuthenticationConverter(SupabaseJwtAuthConverter authoritiesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Converter<Jwt, Collection<GrantedAuthority>>) authoritiesConverter::convert);
        return converter;
    }
}
