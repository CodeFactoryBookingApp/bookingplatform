package com.codefactory.bookingplatform.catalog.infrastructure.persistence;

import com.codefactory.bookingplatform.catalog.domain.model.DuplicateNameException;
import com.codefactory.bookingplatform.catalog.domain.model.ResourceType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which integrity violation is a duplicate name. The writes themselves run against PostgreSQL in ResourceManagementIT. */
class ResourceRepositoryAdapterTest {

    private ResourceTypeJpaRepository types;
    private ResourceRepositoryAdapter adapter;
    private final ResourceType type = new ResourceType(UUID.randomUUID(), UUID.randomUUID(), "Silla", null);

    @BeforeEach
    void setUp() {
        types = mock(ResourceTypeJpaRepository.class);
        adapter = new ResourceRepositoryAdapter(types, mock(ResourceJpaRepository.class),
                mock(ResourceMaintenanceJpaRepository.class));
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException("23505"), constraint));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"uk_resource_types_org_name", "uk_resources_location_name"})
    @DisplayName("The unique name constraints of V1 become a DuplicateNameException")
    void nameViolationIsADuplicate(String constraint) {
        // Arrange
        when(types.saveAndFlush(any(ResourceTypeEntity.class))).thenThrow(violation(constraint));

        // Act
        DuplicateNameException ex = assertThrows(DuplicateNameException.class, () -> adapter.saveType(type));

        // Assert
        assertSame(DataIntegrityViolationException.class, ex.getCause().getClass());
    }

    @Test
    @DisplayName("Any other integrity violation is rethrown untouched")
    void otherViolationIsRethrown() {
        // Arrange
        DataIntegrityViolationException other = violation("fk_resource_types_org");
        when(types.saveAndFlush(any(ResourceTypeEntity.class))).thenThrow(other);

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.saveType(type));

        // Assert
        assertSame(other, ex);
    }
}
