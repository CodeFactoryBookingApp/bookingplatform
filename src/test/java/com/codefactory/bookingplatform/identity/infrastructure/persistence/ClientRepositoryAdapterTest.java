package com.codefactory.bookingplatform.identity.infrastructure.persistence;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.infrastructure.mapper.ClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
