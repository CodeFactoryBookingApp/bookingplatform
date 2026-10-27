package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HU-003 filters of the provider listing: defaults ("Restablecer filtros" is no filter at all,
 * newest first) and the partition valid / invalid parameters, all the invalid ones reported in a
 * single 400. PIT does not mutate record constructors, so these rules do not show in its report.
 */
class ProviderFilterTest {

    @Test
    @DisplayName("HU-003: without sort the listing is by registration date; a blank city is no filter, a given one is trimmed")
    void defaults() {
        // Act
        ProviderFilter blank = new ProviderFilter(null, null, "   ", null, null, null, true, 0, 20);
        ProviderFilter given = new ProviderFilter(null, null, " Medellín ", null, null, ProviderFilter.Sort.NAME, false, 0, 20);

        // Assert
        assertEquals(ProviderFilter.Sort.REGISTERED, blank.sort());
        assertNull(blank.city());
        assertEquals("Medellín", given.city());
        assertEquals(ProviderFilter.Sort.NAME, given.sort());
    }

    @Test
    @DisplayName("HU-003: an inverted date range and a bad page and size are reported together in one 400, by parameter")
    void everyInvalidParameterIsReported() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> new ProviderFilter(null, null, null,
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 24), null, true, -1, 0));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(List.of("registeredTo", "page", "size"), List.copyOf(ex.details().keySet()));
    }
}
