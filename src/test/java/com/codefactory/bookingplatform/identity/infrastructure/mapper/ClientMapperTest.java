package com.codefactory.bookingplatform.identity.infrastructure.mapper;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.infrastructure.persistence.ClientEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The MapStruct mapper is the only bridge between the pure domain aggregate and the JPA entity.
 * These tests lock that no field is dropped in either direction: a silent loss of status or
 * notification channel would break the HU-001 rules that depend on them.
 */
class ClientMapperTest {

    private final ClientMapper mapper = new ClientMapperImpl();

    private static Client domainClient(UUID id, NotificationChannel channel, ClientStatus status) {
        return new Client(id, "Ana Perez", "1017", LocalDate.of(1998, 4, 12), "ana.perez@example.com",
                "3001112233", "Medellin", channel, status);
    }

    private static ClientEntity entity(UUID id, NotificationChannel channel, ClientStatus status) {
        ClientEntity entity = new ClientEntity();
        entity.setId(id);
        entity.setFullName("Ana Perez");
        entity.setDocument("1017");
        entity.setBirthDate(LocalDate.of(1998, 4, 12));
        entity.setEmail("ana.perez@example.com");
        entity.setPhone("3001112233");
        entity.setCity("Medellin");
        entity.setNotificationChannel(channel);
        entity.setStatus(status);
        return entity;
    }

    @ParameterizedTest(name = "[{index}] {0} / {1} survive the trip to the entity")
    @CsvSource({
            "EMAIL,    PENDING_VERIFICATION",
            "SMS,      ACTIVE",
            "WHATSAPP, SUSPENDED"
    })
    @DisplayName("toEntity copies every attribute, including the status and the notification channel")
    void toEntityCopiesEveryAttribute(NotificationChannel channel, ClientStatus status) {
        UUID id = UUID.randomUUID();

        ClientEntity result = mapper.toEntity(domainClient(id, channel, status));

        assertEquals(id, result.getId());
        assertEquals("Ana Perez", result.getFullName());
        assertEquals("1017", result.getDocument());
        assertEquals(LocalDate.of(1998, 4, 12), result.getBirthDate());
        assertEquals("ana.perez@example.com", result.getEmail());
        assertEquals("3001112233", result.getPhone());
        assertEquals("Medellin", result.getCity());
        assertEquals(channel, result.getNotificationChannel());
        assertEquals(status, result.getStatus());
    }

    @ParameterizedTest(name = "[{index}] {0} / {1} survive the trip to the domain")
    @CsvSource({
            "EMAIL,    PENDING_VERIFICATION",
            "SMS,      ACTIVE",
            "WHATSAPP, SUSPENDED"
    })
    @DisplayName("toDomain rebuilds the aggregate with every attribute, including the status")
    void toDomainCopiesEveryAttribute(NotificationChannel channel, ClientStatus status) {
        UUID id = UUID.randomUUID();

        Client result = mapper.toDomain(entity(id, channel, status));

        assertEquals(id, result.getId());
        assertEquals("Ana Perez", result.getFullName());
        assertEquals("1017", result.getDocument());
        assertEquals(LocalDate.of(1998, 4, 12), result.getBirthDate());
        assertEquals("ana.perez@example.com", result.getEmail());
        assertEquals("3001112233", result.getPhone());
        assertEquals("Medellin", result.getCity());
        assertEquals(channel, result.getNotificationChannel());
        assertEquals(status, result.getStatus());
    }

    @Test
    @DisplayName("A domain to entity to domain round trip loses nothing")
    void roundTripLosesNothing() {
        UUID id = UUID.randomUUID();
        Client original = domainClient(id, NotificationChannel.WHATSAPP, ClientStatus.SUSPENDED);

        Client back = mapper.toDomain(mapper.toEntity(original));

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

    @Test
    @DisplayName("A brand new client keeps its behaviour after the round trip: only ACTIVE may confirm bookings")
    void roundTripPreservesTheBusinessState() {
        Client pending = Client.pendingVerification(UUID.randomUUID(), "Ana Perez", "1017",
                LocalDate.of(1998, 4, 12), "ana.perez@example.com", "3001112233", "Medellin",
                NotificationChannel.EMAIL);

        Client back = mapper.toDomain(mapper.toEntity(pending));

        assertEquals(ClientStatus.PENDING_VERIFICATION, back.getStatus());
        back.verifyEmail();
        assertEquals(ClientStatus.ACTIVE, back.getStatus());
    }

    @Test
    @DisplayName("Mapping a null client yields null instead of an empty entity")
    void toEntityOfNullIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    @DisplayName("Mapping a null entity yields null instead of an empty aggregate")
    void toDomainOfNullIsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    @DisplayName("The mapper does not stamp the audit columns: they belong to JPA auditing")
    void mappedEntityCarriesNoAuditStamps() {
        ClientEntity result = mapper.toEntity(domainClient(UUID.randomUUID(),
                NotificationChannel.EMAIL, ClientStatus.ACTIVE));

        assertNull(result.getCreatedAt());
        assertNull(result.getUpdatedAt());
    }
}
