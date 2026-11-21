package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.notification.application.ManageBookingRemindersUseCase;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConfigureBookingRemindersUseCase {

    private final OrganizationAccess access;
    private final ManageBookingRemindersUseCase reminders;

    public ConfigureBookingRemindersUseCase(OrganizationAccess access, ManageBookingRemindersUseCase reminders) {
        this.access = access;
        this.reminders = reminders;
    }

    @Transactional(readOnly = true)
    public List<Integer> get(UUID organization, UUID actor) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER);
        return reminders.organizationLeads(organization);
    }

    @Transactional
    public List<Integer> change(UUID organization, UUID actor, List<Integer> leadMinutes) {
        access.requireRole(organization, actor, MemberRole.OWNER, MemberRole.MANAGER);
        return reminders.changeOrganizationLeads(organization, leadMinutes);
    }
}
