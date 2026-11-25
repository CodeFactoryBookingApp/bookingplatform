package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.notification.domain.port.BookingReminderSettings;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-015: personal reminder preferences and the provider's reminder lead times. Equivalence
 * partitioning on the time zone (absent / valid region / valid offset / unknown) and on the lead
 * times (distinct and unsorted / repeated / empty).
 */
class ManageBookingRemindersUseCaseTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID ORGANIZATION = UUID.randomUUID();

    private BookingReminderSettings settings;
    private ManageBookingRemindersUseCase useCase;

    @BeforeEach
    void setUp() {
        settings = mock(BookingReminderSettings.class);
        useCase = new ManageBookingRemindersUseCase(settings);
    }

    private static ReminderPreferences withZone(String timezone) {
        return new ReminderPreferences(true, LocalTime.of(21, 0), LocalTime.of(6, 30), timezone);
    }

    @Test
    @DisplayName("HU-015: a user who never saved preferences gets the defaults (reminders on, quiet 22:00-07:00)")
    void defaultsWhenNothingWasSaved() {
        // Arrange
        when(settings.preferences(USER)).thenReturn(Optional.empty());

        // Act
        ReminderPreferences preferences = useCase.preferences(USER);

        // Assert
        assertEquals(new ReminderPreferences(true, LocalTime.of(22, 0), LocalTime.of(7, 0), null), preferences);
    }

    @Test
    @DisplayName("HU-015: saved preferences are returned as saved")
    void savedPreferencesAreReturned() {
        // Arrange
        when(settings.preferences(USER)).thenReturn(Optional.of(withZone("America/Bogota")));

        // Act
        ReminderPreferences preferences = useCase.preferences(USER);

        // Assert
        assertEquals(withZone("America/Bogota"), preferences);
    }

    @ParameterizedTest(name = "[{index}] timezone {0}")
    @NullSource
    @ValueSource(strings = {"America/Bogota", "UTC", "-05:00"})
    @DisplayName("HU-015: preferences with no time zone, or a valid one, are saved and returned")
    void validTimezoneIsSaved(String timezone) {
        // Act
        ReminderPreferences saved = useCase.changePreferences(USER, withZone(timezone));

        // Assert
        verify(settings).savePreferences(USER, withZone(timezone));
        assertEquals(withZone(timezone), saved);
    }

    @ParameterizedTest(name = "[{index}] timezone \"{0}\"")
    @ValueSource(strings = {"", "Mars/Olympus", "GMT+25", "America/Bogota "})
    @DisplayName("HU-015: an unknown time zone is a 400 and nothing is saved")
    void unknownTimezoneIsRejected(String timezone) {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.changePreferences(USER, withZone(timezone)));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        verify(settings, never()).savePreferences(any(), any());
    }

    @Test
    @DisplayName("HU-015: lead times are saved and returned in ascending order")
    void leadTimesAreSorted() {
        // Act
        List<Integer> saved = useCase.changeOrganizationLeads(ORGANIZATION, List.of(1440, 60, 120));

        // Assert
        assertEquals(List.of(60, 120, 1440), saved);
        verify(settings).saveLeadMinutes(ORGANIZATION, List.of(60, 120, 1440));
    }

    @Test
    @DisplayName("HU-015: a repeated lead time is a 400 (it would send the same reminder twice) and nothing is saved")
    void repeatedLeadTimeIsRejected() {
        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.changeOrganizationLeads(ORGANIZATION, List.of(120, 60, 120)));

        // Assert
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        verify(settings, never()).saveLeadMinutes(any(), any());
    }

    @Test
    @DisplayName("HU-015: an empty list is allowed and turns the provider's reminders off")
    void emptyLeadTimesAreAllowed() {
        // Act
        List<Integer> saved = useCase.changeOrganizationLeads(ORGANIZATION, List.of());

        // Assert
        assertEquals(List.of(), saved);
        verify(settings).saveLeadMinutes(ORGANIZATION, List.of());
    }
}
