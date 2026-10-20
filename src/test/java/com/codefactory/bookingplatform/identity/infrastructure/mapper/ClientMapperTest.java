package com.codefactory.bookingplatform.identity.infrastructure.mapper;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The MapStruct mapper is the only bridge between the pure domain aggregate and the JPA entity.
 * MapStruct only warns about unmapped properties, so a renamed field would be dropped silently:
 * this round trip locks that no attribute (status and notification channel included) is lost.
 */
class ClientMapperTest {

    private final ClientMapper mapper = new ClientMapperImpl();

    private static Client domainClient(UUID id, NotificationChannel channel, ClientStatus status) {
        return new Client(id, "Ana Perez", "1017", LocalDate.of(1998, 4, 12), "ana.perez@example.com",
                "3001112233", "Medellin", channel, status);
    }

    @Test
    @DisplayName("A domain to entity to domain round trip loses nothing")
    void roundTripLosesNothing() {
        // Arrange
        UUID id = UUID.randomUUID();
        Client original = domainClient(id, NotificationChannel.WHATSAPP, ClientStatus.SUSPENDED);

        // Act
        Client back = mapper.toDomain(mapper.toEntity(original));

        // Assert
        assertEquals(original.getId(), back.getId());
        assertEquals(original.getFullName(), back.getFullName());
        assertEquals(original.getDocument(), back.getDocument());
        assertEquals(original.getBirthDate(), back.getBirthDate());
        assertEquals(original.getEmail(), back.getEmail());
        assertEquals(original.getPhone(), back.getPhone());
        assertEquals(original.getCity(), back.getCity());
        assertEquals(original.getNotificationChannel(), back.getNotificationChannel());
        assertEquals(original.getStatus(), back.getStatus());
    }
}
