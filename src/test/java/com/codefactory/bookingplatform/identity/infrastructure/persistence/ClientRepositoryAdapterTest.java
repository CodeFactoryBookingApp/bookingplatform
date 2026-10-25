package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.DuplicateClientException;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Translation contract of the client adapter. The JPA repository and the MapStruct mapper are
 * mocked: what is asserted is the email normalisation and that every crossing of the boundary
 * goes through the mapper. Real persistence lives in the Testcontainers integration tests.
 */
class ClientRepositoryAdapterTest {

    private ClientJpaRepository jpaRepository;
    private ClientMapper mapper;
    private ClientRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(ClientJpaRepository.class);
        mapper = mock(ClientMapper.class);
        adapter = new ClientRepositoryAdapter(jpaRepository, mapper);
    }

    private static Client sampleClient(UUID id) {
        return new Client(id, "Ana Perez", "1017", LocalDate.of(1998, 4, 12), "ana.perez@example.com",
                "3001112233", "Medellin", NotificationChannel.EMAIL, ClientStatus.ACTIVE);
    }

    @ParameterizedTest(name = "[{index}] lookup by \"{0}\" queries \"{1}\"")
    @CsvSource({
            "ana.perez@example.com, ana.perez@example.com",
            "ANA.PEREZ@EXAMPLE.COM, ana.perez@example.com",
            "Ana.Perez@Example.Com,  ana.perez@example.com"
    })
    @DisplayName("Email lookups are case insensitive: the address is normalised before the query")
    void findByEmailNormalisesTheEmail(String input, String expected) {
        // Arrange
        UUID id = UUID.randomUUID();
        ClientEntity entity = new ClientEntity();
        when(jpaRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(sampleClient(id));

        // Act
        Optional<Client> found = adapter.findByEmail(input);

        // Assert
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(jpaRepository).findByEmailIgnoreCase(email.capture());
        assertEquals(expected, email.getValue());
        assertEquals(id, found.orElseThrow().getId());
    }

    @ParameterizedTest(name = "[{index}] existsByEmail(\"{0}\") queries \"{1}\"")
    @CsvSource({
            "ana.perez@example.com, ana.perez@example.com",
            "ANA.PEREZ@EXAMPLE.COM, ana.perez@example.com"
    })
    @DisplayName("Duplicate email detection normalises the address, so casing cannot bypass uniqueness")
    void existsByEmailNormalisesTheEmail(String input, String expected) {
        // Arrange
        when(jpaRepository.existsByEmailIgnoreCase(expected)).thenReturn(true);

        // Act
        boolean exists = adapter.existsByEmail(input);

        // Assert
        assertTrue(exists);
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(jpaRepository).existsByEmailIgnoreCase(email.capture());
        assertEquals(expected, email.getValue());
    }

    private static DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraint));
    }

    @Test
    @DisplayName("save flushes, so the INSERT runs inside the caller's try and not at commit (D7)")
    void saveFlushesImmediately() {
        // Arrange
        UUID id = UUID.randomUUID();
        Client client = sampleClient(id);
        ClientEntity entity = new ClientEntity();
        when(mapper.toEntity(client)).thenReturn(entity);
        when(jpaRepository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(client);

        // Act
        Client saved = adapter.save(client);

        // Assert
        assertEquals(id, saved.getId());
        verify(jpaRepository).saveAndFlush(entity);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            "uk_clients_email, EMAIL",
            "ux_clients_email_lower, EMAIL",
            "uk_clients_document, DOCUMENT"
    })
    @DisplayName("A unique violation on email or document becomes a DuplicateClientException naming the field")
    void uniqueViolationIsTranslated(String constraint, DuplicateClientException.Field expected) {
        // Arrange
        Client client = sampleClient(UUID.randomUUID());
        when(mapper.toEntity(client)).thenReturn(new ClientEntity());
        when(jpaRepository.saveAndFlush(any(ClientEntity.class))).thenThrow(uniqueViolation(constraint));

        // Act
        DuplicateClientException thrown = assertThrows(DuplicateClientException.class, () -> adapter.save(client));

        // Assert
        assertEquals(expected, thrown.field());
    }

    @Test
    @DisplayName("Any other integrity violation is rethrown untouched: it is not a duplicate")
    void otherViolationIsRethrown() {
        // Arrange
        Client client = sampleClient(UUID.randomUUID());
        DataIntegrityViolationException violation = uniqueViolation("ck_clients_phone_formato");
        when(mapper.toEntity(client)).thenReturn(new ClientEntity());
        when(jpaRepository.saveAndFlush(any(ClientEntity.class))).thenThrow(violation);

        // Act
        DataIntegrityViolationException thrown =
                assertThrows(DataIntegrityViolationException.class, () -> adapter.save(client));

        // Assert
        assertSame(violation, thrown);
    }

    @Test
    @DisplayName("A violation that does not name its constraint is rethrown untouched")
    void violationWithoutConstraintNameIsRethrown() {
        // Arrange
        Client client = sampleClient(UUID.randomUUID());
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unknown");
        when(mapper.toEntity(client)).thenReturn(new ClientEntity());
        when(jpaRepository.saveAndFlush(any(ClientEntity.class))).thenThrow(violation);

        // Act
        DataIntegrityViolationException thrown =
                assertThrows(DataIntegrityViolationException.class, () -> adapter.save(client));

        // Assert
        assertSame(violation, thrown);
    }
}
