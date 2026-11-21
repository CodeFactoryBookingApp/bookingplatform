package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.notification.domain.port.BookingReminderSettings;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingReminderSettingsAdapter implements BookingReminderSettings {

    private final JdbcClient jdbc;

    public BookingReminderSettingsAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReminderPreferences> preferences(UUID userId) {
        return jdbc.sql("""
                select reminders_enabled, quiet_from, quiet_until, timezone
                  from booking_notification_preferences where user_id = :id
                """).param("id", userId).query((rs, n) -> new ReminderPreferences(rs.getBoolean("reminders_enabled"),
                rs.getObject("quiet_from", LocalTime.class), rs.getObject("quiet_until", LocalTime.class),
                rs.getString("timezone"))).optional();
    }

    @Override
    public void savePreferences(UUID userId, ReminderPreferences preferences) {
        jdbc.sql("""
                insert into booking_notification_preferences (user_id, reminders_enabled, quiet_from, quiet_until, timezone)
                values (:id, :enabled, :quietFrom, :quietUntil, :timezone)
                on conflict (user_id) do update set reminders_enabled = excluded.reminders_enabled,
                    quiet_from = excluded.quiet_from, quiet_until = excluded.quiet_until, timezone = excluded.timezone
                """).param("id", userId).param("enabled", preferences.remindersEnabled())
                .param("quietFrom", preferences.quietFrom()).param("quietUntil", preferences.quietUntil())
                .param("timezone", preferences.timezone()).update();
    }

    @Override
    public List<Integer> leadMinutes(UUID organizationId) {
        return jdbc.sql("select lead_minutes from organization_booking_reminders where organization_id = :id")
                .param("id", organizationId)
                .query((rs, n) -> Arrays.stream((Integer[]) rs.getArray("lead_minutes").getArray()).toList())
                .optional().orElse(DEFAULT_LEAD_MINUTES);
    }

    @Override
    public void saveLeadMinutes(UUID organizationId, List<Integer> leadMinutes) {
        jdbc.sql("""
                insert into organization_booking_reminders (organization_id, lead_minutes)
                values (:id, cast(:lead as integer[]))
                on conflict (organization_id) do update set lead_minutes = excluded.lead_minutes
                where organization_booking_reminders.lead_minutes is distinct from excluded.lead_minutes
                """).param("id", organizationId)
                .param("lead", leadMinutes.toString().replace('[', '{').replace(']', '}')).update();
    }
}
