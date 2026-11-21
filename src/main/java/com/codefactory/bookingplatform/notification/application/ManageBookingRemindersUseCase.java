package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.notification.domain.port.BookingReminderSettings;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ManageBookingRemindersUseCase {

    private final BookingReminderSettings settings;

    public ManageBookingRemindersUseCase(BookingReminderSettings settings) {
        this.settings = settings;
    }

    public ReminderPreferences preferences(UUID userId) {
        return settings.preferences(userId).orElse(ReminderPreferences.DEFAULTS);
    }

    @Transactional
    public ReminderPreferences changePreferences(UUID userId, ReminderPreferences preferences) {
        if (preferences.timezone() != null) {
            try {
                ZoneId.of(preferences.timezone());
            } catch (DateTimeException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid reminder timezone");
            }
        }
        settings.savePreferences(userId, preferences);
        return preferences;
    }

    public List<Integer> organizationLeads(UUID organizationId) {
        return settings.leadMinutes(organizationId);
    }

    public List<Integer> changeOrganizationLeads(UUID organizationId, List<Integer> minutes) {
        List<Integer> sorted = minutes.stream().distinct().sorted().toList();
        if (sorted.size() != minutes.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reminder lead times must be distinct");
        }
        settings.saveLeadMinutes(organizationId, sorted);
        return sorted;
    }

}
