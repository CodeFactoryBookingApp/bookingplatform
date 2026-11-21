package com.codefactory.bookingplatform.auth.infrastructure.supabase;

import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts provider payloads to auth values and rejects malformed responses as upstream errors. */
final class GoTrueResponses {

    private GoTrueResponses() {
    }

    static ConfirmedUser confirmedUser(Map<String, Object> response) {
        // OTP verification normally returns the user inside the session. The bare
        // user is also accepted, and email is optional because callers use the ID.
        Map<?, ?> user = response.get("user") instanceof Map<?, ?> nested ? nested : response;
        return new ConfirmedUser(requireUuid(user, "id"), Objects.toString(user.get("email"), null));
    }

    static Object requireField(Map<?, ?> response, String field) {
        if (response == null || response.get(field) == null) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, missing field: " + field);
        }
        return response.get(field);
    }

    static UUID requireUuid(Map<?, ?> response, String field) {
        String raw = String.valueOf(requireField(response, field));
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, field is not a valid UUID: " + field, ex);
        }
    }

    /** A missing lifetime defaults to one hour. A provided lifetime must be a whole number. */
    static long requireExpiresIn(Map<String, Object> response) {
        try {
            return Long.parseLong(String.valueOf(response.getOrDefault("expires_in", "3600")));
        } catch (NumberFormatException ex) {
            throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE,
                    "Unexpected identity provider response, field is not a number: expires_in", ex);
        }
    }
}
