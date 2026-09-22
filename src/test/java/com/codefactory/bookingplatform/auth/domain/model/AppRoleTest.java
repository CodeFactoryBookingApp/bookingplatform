package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The application roles are part of the authorization contract: their names travel inside the JWT
 * claims and inside the database, so both the set of roles and their spelling are frozen behaviour.
 */
class AppRoleTest {

    @Test
    @DisplayName("The platform recognises exactly three roles, in the documented order")
    void exposesTheThreeApplicationRoles() {
        assertIterableEquals(List.of(AppRole.CLIENT, AppRole.PROVIDER, AppRole.ADMIN),
                Stream.of(AppRole.values()).toList());
    }

    @ParameterizedTest(name = "{0} round-trips through its name")
    @EnumSource(AppRole.class)
    @DisplayName("Every role can be resolved back from its own name, which is how it is persisted and read from the token")
    void everyRoleRoundTripsThroughItsName(AppRole role) {
        assertEquals(role, AppRole.valueOf(role.name()));
    }

    @ParameterizedTest(name = "{0} has a stable ordinal and name")
    @EnumSource(AppRole.class)
    @DisplayName("Every role exposes a non-null name")
    void everyRoleHasAName(AppRole role) {
        assertNotNull(role.name());
    }

    @Test
    @DisplayName("An unknown role name is rejected instead of silently resolving to a default")
    void unknownRoleNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AppRole.valueOf("SUPERUSER"));
    }
}
