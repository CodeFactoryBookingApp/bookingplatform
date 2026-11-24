package com.codefactory.bookingplatform.scheduling.domain.model;

import com.codefactory.bookingplatform.scheduling.domain.model.AgendaException.Type;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HU-007: what a valid agenda exception is, and the block a special schedule stands for. */
class AgendaExceptionTest {

    private static final UUID AGENDA = UUID.randomUUID();
    /** A Thursday. */
    private static final LocalDate CHRISTMAS_EVE = LocalDate.of(2026, 12, 24);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime ONE_PM = LocalTime.of(13, 0);

    private static BusinessException rejected(Executable act) {
        return assertThrows(BusinessException.class, act);
    }

    @Test
    @DisplayName("HU-007: a closure has no hours of its own and keeps its reason")
    void closedFactoryBuildsAClosureWithoutHours() {
        // Act
        AgendaException closure = AgendaException.closed(AGENDA, CHRISTMAS_EVE, "Festivo");

        // Assert
        assertEquals(Type.CLOSED, closure.type());
        assertNull(closure.specialStart());
        assertNull(closure.specialEnd());
        assertEquals("Festivo", closure.reason());
    }

    @Test
    @DisplayName("HU-007: special hours stand for one block on the weekday of their date")
    void specialHoursBecomeABlockOfThatWeekday() {
        // Arrange
        AgendaException special = AgendaException.specialHours(AGENDA, CHRISTMAS_EVE, NINE, ONE_PM, null);

        // Act
        TimeBlock block = special.asBlock();

        // Assert
        assertEquals(new TimeBlock(DayOfWeek.THURSDAY, NINE, ONE_PM), block);
    }

    @Test
    @DisplayName("HU-007: a closure has no block to replace the week with")
    void aClosureHasNoBlock() {
        // Arrange
        AgendaException closure = AgendaException.closed(AGENDA, CHRISTMAS_EVE, null);

        // Act
        Executable act = closure::asBlock;

        // Assert
        assertThrows(IllegalStateException.class, act);
    }

    @Test
    @DisplayName("HU-007: a closure that carries hours is rejected")
    void closureWithHoursIsRejected() {
        // Act
        BusinessException error = rejected(() ->
                new AgendaException(UUID.randomUUID(), AGENDA, CHRISTMAS_EVE, Type.CLOSED, NINE, null, null));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, error.errorCode());
        assertTrue(error.details().containsKey("specialStart"));
    }

    @Test
    @DisplayName("HU-007: special hours need both a start and an end")
    void specialHoursWithoutEndAreRejected() {
        // Act
        BusinessException error = rejected(() -> AgendaException.specialHours(AGENDA, CHRISTMAS_EVE, NINE, null, null));

        // Assert
        assertTrue(error.details().containsKey("specialStart"));
    }

    @Test
    @DisplayName("HU-007: special hours must end after they start (boundary: equal)")
    void specialHoursEndingAtTheirStartAreRejected() {
        // Act
        BusinessException error = rejected(() -> AgendaException.specialHours(AGENDA, CHRISTMAS_EVE, NINE, NINE, null));

        // Assert
        assertTrue(error.details().containsKey("specialEnd"));
    }

    @Test
    @DisplayName("HU-007: agenda, date and type are required, and every missing one is reported")
    void missingAgendaDateAndTypeAreAllReported() {
        // Act
        BusinessException error = rejected(() ->
                new AgendaException(UUID.randomUUID(), null, null, null, null, null, null));

        // Assert
        assertTrue(error.details().keySet().containsAll(Set.of("agendaId", "date", "type")));
    }
}
