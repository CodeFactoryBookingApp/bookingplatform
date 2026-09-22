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
 * The notification channel chosen at registration is persisted as text and is
 * part of the registration request contract.
 *
 * Techniques applied: equivalence partitioning over the enum domain
 * (valid names vs. unknown names) and contract pinning of the constant set.
 */
class NotificationChannelTest {

    @Test
    @DisplayName("Registration offers exactly three notification channels")
    void declaresExactlyThreeChannels() {
        assertEquals(
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.WHATSAPP),
                Arrays.asList(NotificationChannel.values()));
    }

    @ParameterizedTest
    @DisplayName("Every channel round-trips through its persisted name")
    @EnumSource(NotificationChannel.class)
    void channelRoundTripsThroughItsName(NotificationChannel channel) {
        assertEquals(channel, NotificationChannel.valueOf(channel.name()));
    }

    @ParameterizedTest
    @DisplayName("An unsupported or wrongly cased channel name is rejected instead of silently mapped")
    @ValueSource(strings = {"PUSH", "email", "Whatsapp", "", "TELEGRAM"})
    void unsupportedChannelNameIsRejected(String name) {
        assertThrows(IllegalArgumentException.class, () -> NotificationChannel.valueOf(name));
    }
}
