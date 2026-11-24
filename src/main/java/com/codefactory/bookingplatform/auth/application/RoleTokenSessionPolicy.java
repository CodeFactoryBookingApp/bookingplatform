package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import com.codefactory.bookingplatform.shared.config.SupabaseJwtAuthConverter;
import com.codefactory.bookingplatform.shared.security.TokenSessionPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleTokenSessionPolicy implements TokenSessionPolicy {

    private final PlatformRoleChanges changes;

    public RoleTokenSessionPolicy(PlatformRoleChanges changes) {
        this.changes = changes;
    }

    @Override
    public boolean accepts(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
        return changes.find(userId).map(change -> accepts(jwt, change)).orElse(true);
    }

    private static boolean accepts(Jwt jwt, RoleChange change) {
        Object claim = jwt.getClaim(SupabaseJwtAuthConverter.APP_METADATA_CLAIM);
        if (change.pending() || !(claim instanceof Map<?, ?> metadata)
                || !change.revision().toString().equals(metadata.get(RoleChange.REVISION_CLAIM))
                || !change.role().name().equals(metadata.get(SupabaseJwtAuthConverter.ROLE_KEY))) {
            return false;
        }
        // A refresh can carry the new metadata but the authentication methods still belong
        // to the original session. Supabase timestamps have second precision.
        Object methods = jwt.getClaim("amr");
        return methods instanceof List<?> entries && !entries.isEmpty()
                && entries.stream().allMatch(entry -> authenticatedAfter(entry, change.completedAt().getEpochSecond()));
    }

    private static boolean authenticatedAfter(Object entry, long cutoff) {
        return entry instanceof Map<?, ?> method && method.get("timestamp") instanceof Number timestamp
                && timestamp.longValue() > cutoff;
    }
}
