package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("findById returns the domain client mapped from the stored entity")
    void findByIdMapsTheStoredEntity() {
        UUID id = UUID.randomUUID();
        ClientEntity entity = new ClientEntity();
        Client domain = sampleClient(id);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<Client> found = adapter.findById(id);

        assertTrue(found.isPresent());
        assertSame(domain, found.get());
        verify(mapper).toDomain(entity);
    }

    @Test
    @DisplayName("An unknown id yields an empty optional and the mapper is never invoked")
    void findByIdOnUnknownIdReturnsEmpty() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(adapter.findById(id).isEmpty());
        verify(mapper, never()).toDomain(any());
    }

    @ParameterizedTest(name = "[{index}] lookup by \"{0}\" queries \"{1}\"")
    @CsvSource({
            "ana.perez@example.com, ana.perez@example.com",
            "ANA.PEREZ@EXAMPLE.COM, ana.perez@example.com",
            "Ana.Perez@Example.Com,  ana.perez@example.com"
    })
    @DisplayName("Email lookups are case insensitive: the address is normalised before the query")
    void findByEmailNormalisesTheEmail(String input, String expected) {
        UUID id = UUID.randomUUID();
        ClientEntity entity = new ClientEntity();
        when(jpaRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(sampleClient(id));

        Optional<Client> found = adapter.findByEmail(input);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(jpaRepository).findByEmailIgnoreCase(email.capture());
        assertEquals(expected, email.getValue());
        assertEquals(id, found.orElseThrow().getId());
    }

    @Test
    @DisplayName("An email with no client yields an empty optional")
    void findByEmailOnUnknownEmailReturnsEmpty() {
        when(jpaRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertTrue(adapter.findByEmail("ana.perez@example.com").isEmpty());
        verify(mapper, never()).toDomain(any());
    }

    @ParameterizedTest(name = "[{index}] existsByEmail(\"{0}\") queries \"{1}\"")
    @CsvSource({
            "ana.perez@example.com, ana.perez@example.com",
            "ANA.PEREZ@EXAMPLE.COM, ana.perez@example.com"
    })
    @DisplayName("Duplicate email detection normalises the address, so casing cannot bypass uniqueness")
    void existsByEmailNormalisesTheEmail(String input, String expected) {
        when(jpaRepository.existsByEmailIgnoreCase(expected)).thenReturn(true);

        assertTrue(adapter.existsByEmail(input));

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(jpaRepository).existsByEmailIgnoreCase(email.capture());
        assertEquals(expected, email.getValue());
    }

    @Test
    @DisplayName("A free email address reports no duplicate")
    void existsByEmailIsFalseWhenTheEmailIsFree() {
        when(jpaRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        assertFalse(adapter.existsByEmail("ana.perez@example.com"));
    }

    @ParameterizedTest(name = "[{index}] document \"{0}\" is forwarded verbatim")
    @ValueSource(strings = {"1017", "cc-1017", "CC-1017"})
    @DisplayName("The document is forwarded verbatim: uniqueness is enforced by the case insensitive query")
    void existsByDocumentForwardsTheDocumentVerbatim(String document) {
        when(jpaRepository.existsByDocumentIgnoreCase(document)).thenReturn(true);

        assertTrue(adapter.existsByDocument(document));

        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(jpaRepository).existsByDocumentIgnoreCase(captured.capture());
        assertEquals(document, captured.getValue());
    }

    @Test
    @DisplayName("An unknown document reports no duplicate")
    void existsByDocumentIsFalseWhenTheDocumentIsFree() {
        when(jpaRepository.existsByDocumentIgnoreCase(anyString())).thenReturn(false);

        assertFalse(adapter.existsByDocument("1017"));
    }

    @Test
    @DisplayName("save crosses the boundary twice: domain to entity on the way in, entity to domain on the way out")
    void saveGoesThroughTheMapperInBothDirections() {
        UUID id = UUID.randomUUID();
        Client incoming = sampleClient(id);
        ClientEntity toPersist = new ClientEntity();
        ClientEntity persisted = new ClientEntity();
        Client outgoing = sampleClient(id);
        when(mapper.toEntity(incoming)).thenReturn(toPersist);
        when(jpaRepository.save(toPersist)).thenReturn(persisted);
        when(mapper.toDomain(persisted)).thenReturn(outgoing);

        Client saved = adapter.save(incoming);

        assertSame(outgoing, saved);
        ArgumentCaptor<ClientEntity> captor = ArgumentCaptor.forClass(ClientEntity.class);
        verify(mapper).toEntity(incoming);
        verify(jpaRepository).save(captor.capture());
        assertSame(toPersist, captor.getValue());
        verify(mapper).toDomain(persisted);
    }
}
