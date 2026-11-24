package com.codefactory.bookingplatform.booking.application;

import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Filter;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Page;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Row;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Summary;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingQueries;
import com.codefactory.bookingplatform.organization.application.OrganizationAccess;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-018 "Consulta de reservas": scope, filter validation and pagination of the provider's search.
 *
 * <p>Techniques: equivalence partitioning over the caller (any member of the provider vs outsider)
 * and boundary values on the time range (to = from is empty, to = from + 1 s is valid), on the code
 * length (0 after trimming, 1, 64, 65) and on the page window; all errors are reported together.
 */
class SearchBookingsUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-11-24T13:00:00Z");

    private OrganizationRepository organizations;
    private BookingQueries queries;
    private SearchBookingsUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        queries = mock(BookingQueries.class);
        useCase = new SearchBookingsUseCase(new OrganizationAccess(organizations, mock(AuditActor.class)), queries);
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.of(MemberRole.STAFF));
    }

    private static Filter range(Instant from, Instant to) {
        return new Filter(from, to, null, null, null, null, null, null);
    }

    private static Filter code(String code) {
        return new Filter(null, null, null, null, null, null, null, code);
    }

    private static Row row() {
        return new Row(UUID.randomUUID(), "RES-1", UUID.randomUUID(), "Ana", UUID.randomUUID(), "Corte",
                UUID.randomUUID(), "Sede", FROM, FROM.plusSeconds(1800), 1, new BigDecimal("45000.00"), "COP",
                BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("HU-018: someone outside the provider gets 404 and its bookings are never queried")
    void outsiderCannotSearch() {
        // Arrange
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.search(ORG, ACTOR, range(null, null), 0, 20));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verify(queries, never()).search(any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("HU-018: an empty time range (to equal to from) is a 400 naming 'to'")
    void emptyRangeIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.search(ORG, ACTOR, range(FROM, FROM), 0, 20));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(Map.of("to", "must be after from"), ex.details());
        verify(queries, never()).search(any(), any(), anyInt(), anyLong());
    }

    @ParameterizedTest(name = "from={0}, to={1}")
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "2026-11-24T13:00:00Z | NULL",
            "NULL | 2026-11-24T13:00:00Z"
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("HU-018: an open range (only from, or only to) is a valid filter")
    void openRangesAreValid(Instant from, Instant to) {
        // Arrange
        Filter filter = range(from, to);

        // Act
        Page page = useCase.search(ORG, ACTOR, filter, 0, 20);

        // Assert
        assertEquals(new Page(List.of(), null, 0, 20), page);
        verify(queries).search(ORG, filter, 20, 0);
    }

    @Test
    @DisplayName("HU-018: an outsider cannot export the provider's bookings")
    void outsiderCannotExport() {
        // Arrange
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.export(ORG, ACTOR, range(null, null)));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verify(queries, never()).export(any(), any());
    }

    @Test
    @DisplayName("HU-018: an outsider cannot look up a booking code of the provider")
    void outsiderCannotLookUpACode() {
        // Arrange
        when(organizations.findMemberRole(ORG, ACTOR)).thenReturn(Optional.empty());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.byCode(ORG, ACTOR, "RES-1"));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
        verify(queries, never()).search(any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("HU-018: a blank code in the by-code lookup is a 400, not a search without filter")
    void blankCodeLookUpIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.byCode(ORG, ACTOR, " "));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        verify(queries, never()).search(any(), any(), anyInt(), anyLong());
    }

    @ParameterizedTest(name = "code of {0} characters")
    @ValueSource(ints = {1, 64})
    @DisplayName("HU-018: a code of 1 to 64 characters is a valid exact filter")
    void codeLengthWithinBounds(int length) {
        // Arrange
        Filter filter = code("R".repeat(length));
        Row found = row();
        when(queries.search(ORG, filter, 20, 0)).thenReturn(List.of(found));

        // Act
        Page page = useCase.search(ORG, ACTOR, filter, 0, 20);

        // Assert
        assertEquals(List.of(found), page.items());
    }

    @ParameterizedTest(name = "code [{0}]")
    @ValueSource(strings = {"", "   ", "RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR"})
    @DisplayName("HU-018: a blank code or one longer than 64 characters is a 400 naming 'code'")
    void codeOutOfBoundsIsRejected(String value) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.search(ORG, ACTOR, code(value), 0, 20));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(Map.of("code", "must contain between 1 and 64 characters"), ex.details());
    }

    @Test
    @DisplayName("HU-018: every invalid parameter is reported at once, the page window together with the filters")
    void allErrorsTogether() {
        // Arrange
        Filter filter = new Filter(FROM, FROM.minusSeconds(1), null, null, null, null, null, " ");

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.search(ORG, ACTOR, filter, -1, 101));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertEquals(List.of("page", "size", "to", "code"), List.copyOf(ex.details().keySet()));
    }

    @Test
    @DisplayName("HU-018: the page asks for 'size' rows from page x size, and the summary covers all matching rows")
    void pageOffsetAndSummary() {
        // Arrange
        Filter filter = range(FROM, FROM.plusSeconds(1));
        Row found = row();
        Summary summary = new Summary(7, Map.of(BookingStatus.CONFIRMED, 7L), Map.of("COP", new BigDecimal("700")),
                BigDecimal.ZERO);
        when(queries.search(ORG, filter, 3, 6)).thenReturn(List.of(found));
        when(queries.summarize(ORG, filter)).thenReturn(summary);

        // Act
        Page page = useCase.search(ORG, ACTOR, filter, 2, 3);

        // Assert
        assertEquals(new Page(List.of(found), summary, 2, 3), page);
    }

    @Test
    @DisplayName("HU-018: a far page does not overflow the offset")
    void farPageOffsetDoesNotOverflow() {
        // Arrange
        Filter filter = range(null, null);
        when(queries.search(ORG, filter, 100, 100L * Integer.MAX_VALUE)).thenReturn(List.of());

        // Act
        Page page = useCase.search(ORG, ACTOR, filter, Integer.MAX_VALUE, 100);

        // Assert
        assertEquals(List.of(), page.items());
        verify(queries).search(ORG, filter, 100, 100L * Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("HU-018: the export applies the same filter validation as the search")
    void exportValidatesTheFilters() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.export(ORG, ACTOR, range(FROM, FROM)));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        verify(queries, never()).export(any(), any());
    }

    @Test
    @DisplayName("HU-018: the export returns every row of the provider matching the filters")
    void exportReturnsAllRows() {
        // Arrange
        Filter filter = range(FROM, FROM.plusSeconds(86400));
        List<Row> rows = List.of(row(), row());
        when(queries.export(ORG, filter)).thenReturn(rows);

        // Act
        List<Row> exported = useCase.export(ORG, ACTOR, filter);

        // Assert
        assertSame(rows, exported);
    }

    @Test
    @DisplayName("HU-018: searching by code looks for exactly that code inside the provider")
    void byCodeFindsTheBooking() {
        // Arrange
        Row found = row();
        when(queries.search(ORG, Filter.byCode("RES-1"), 1, 0)).thenReturn(List.of(found));

        // Act
        Row result = useCase.byCode(ORG, ACTOR, "RES-1");

        // Assert
        assertSame(found, result);
    }

    @Test
    @DisplayName("HU-018: a code that is not in the provider is 404")
    void byCodeNotFound() {
        // Arrange
        when(queries.search(ORG, Filter.byCode("RES-9"), 1, 0)).thenReturn(List.of());

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.byCode(ORG, ACTOR, "RES-9"));

        // Assert
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.errorCode());
    }
}
