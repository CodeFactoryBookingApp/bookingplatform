package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.notification.application.ManageBookingRemindersUseCase;
import com.codefactory.bookingplatform.organization.domain.model.MemberRole;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-015: the lead times of the booking reminders of a provider, with the real
 * {@link OrganizationAccess}. The rules about the lead times themselves belong to the notification
 * module; here, who may read and change them, and that the provider's own ones are the ones used.
 *
 * <p>Technique: decision table role x operation (OWNER and MANAGER read and change them; STAFF
 * gets 403 and an outsider 404, and in both cases the reminders are not touched).
 */
class ConfigureBookingRemindersUseCaseTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private OrganizationRepository organizations;
    private ManageBookingRemindersUseCase reminders;
    private ConfigureBookingRemindersUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        reminders = mock(ManageBookingRemindersUseCase.class);
        useCase = new ConfigureBookingRemindersUseCase(new OrganizationAccess(organizations, mock(AuditActor.class)),
                reminders);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = MemberRole.class, names = {"OWNER", "MANAGER"})
    @DisplayName("HU-015: the owner or a manager reads the provider's lead times")
    void ownerOrManagerReadsTheLeadTimes(MemberRole role) {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(role));
        when(reminders.organizationLeads(ORG)).thenReturn(List.of(60, 1440));

        // Act
        List<Integer> leads = useCase.get(ORG, USER);

        // Assert
        assertEquals(List.of(60, 1440), leads);
    }

    @Test
    @DisplayName("HU-015: a change answers with the lead times as the notification module stored them for this provider")
    void changeAnswersWithTheStoredLeadTimes() {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.of(MemberRole.MANAGER));
        when(reminders.changeOrganizationLeads(ORG, List.of(1440, 60))).thenReturn(List.of(60, 1440));

        // Act
        List<Integer> leads = useCase.change(ORG, USER, List.of(1440, 60));

        // Assert
        assertEquals(List.of(60, 1440), leads);
    }

    @ParameterizedTest(name = "[{index}] {0} by {1} -> {2}")
    @CsvSource({
            "READ,   STAFF, ACCESS_DENIED",
            "CHANGE, STAFF, ACCESS_DENIED",
            "READ,   ,      RESOURCE_NOT_FOUND",
            "CHANGE, ,      RESOURCE_NOT_FOUND"
    })
    @DisplayName("HU-015 / ADR-0008: STAFF gets 403 and an outsider 404; the reminders are not read nor changed")
    void staffAndOutsidersAreRefused(String operation, MemberRole role, ErrorCode expected) {
        // Arrange
        when(organizations.findMemberRole(ORG, USER)).thenReturn(Optional.ofNullable(role));

        // Act
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            if (operation.equals("READ")) {
                useCase.get(ORG, USER);
            } else {
                useCase.change(ORG, USER, List.of(60));
            }
        });

        // Assert
        assertEquals(expected, ex.errorCode());
        verifyNoInteractions(reminders);
    }
}
