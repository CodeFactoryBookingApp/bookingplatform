package com.codefactory.bookingplatform.notification.domain.port;

import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingReminderSettings {

    List<Integer> DEFAULT_LEAD_MINUTES = List.of(120, 1440);

    Optional<ReminderPreferences> preferences(UUID userId);

    void savePreferences(UUID userId, ReminderPreferences preferences);

    List<Integer> leadMinutes(UUID organizationId);

    void saveLeadMinutes(UUID organizationId, List<Integer> leadMinutes);
}
