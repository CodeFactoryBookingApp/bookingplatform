package com.codefactory.bookingplatform.identity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The status vocabulary is persisted as text and travels in the API contract,
 * so the set of constants and their exact spelling are part of the contract.
 *
 * Techniques applied: equivalence partitioning over the enum domain
 * (valid names vs. unknown names) and contract pinning of the ordering.
 */
class ClientStatusTest {

    @Test
    @DisplayName("The client lifecycle declares exactly three statuses in registration order")
    void declaresExactlyThreeStatuses() {
        assertEquals(
                List.of(ClientStatus.PENDING_VERIFICATION, ClientStatus.ACTIVE, ClientStatus.SUSPENDED),
                Arrays.asList(ClientStatus.values()));
    }

    @ParameterizedTest
    @DisplayName("Every status round-trips through its persisted name")
    @EnumSource(ClientStatus.class)
    void statusRoundTripsThroughItsName(ClientStatus status) {
        assertEquals(status, ClientStatus.valueOf(status.name()));
    }

    @ParameterizedTest
    @DisplayName("An unknown or wrongly cased status name is rejected instead of silently mapped")
    @ValueSource(strings = {"PENDING", "active", "DELETED", "", " ACTIVE"})
    void unknownStatusNameIsRejected(String name) {
        assertThrows(IllegalArgumentException.class, () -> ClientStatus.valueOf(name));
    }

    @Test
    @DisplayName("PENDING_VERIFICATION is the first status so it is the natural default for a new client")
    void pendingVerificationIsTheFirstStatus() {
        assertEquals(0, ClientStatus.PENDING_VERIFICATION.ordinal());
    }
}
