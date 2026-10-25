package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.organization.domain.model.DuplicateOrganizationException;
import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.Organization;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.port.OrganizationRepository;
import com.codefactory.bookingplatform.organization.domain.port.ReferenceDataRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-002 provider registration.
 *
 * <ul>
 *   <li>Black-box partitioning of the input: valid, unknown category or city, duplicated
 *   identification, inconsistent operating rules.</li>
 *   <li>Normalisation of email, identification and the optional fields, checked on the aggregate
 *   handed to the repository.</li>
 *   <li>White-box: every rejection before provisioning leaves the identity provider untouched, and
 *   every failure after it is compensated (the same contract as the client registration, D7).</li>
 * </ul>
 */
class RegisterProviderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final UUID OWNER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final UUID CITY = UUID.randomUUID();

    private OrganizationRepository organizations;
    private ReferenceDataRepository referenceData;
    private UserProvisioning userProvisioning;
    private RegisterProviderUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        referenceData = mock(ReferenceDataRepository.class);
        userProvisioning = mock(UserProvisioning.class);
        useCase = new RegisterProviderUseCase(organizations, referenceData, userProvisioning,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(referenceData.categoryExists(CATEGORY)).thenReturn(true);
        when(referenceData.cityExists(CITY)).thenReturn(true);
        when(userProvisioning.provisionProviderUser(anyString(), anyString())).thenReturn(OWNER);
    }

    private static RegisterProviderCommand command(String contactEmail, String timezone, int min, int max) {
        return new RegisterProviderCommand(" Barberia@Example.com ", "Str0ng!Pass", " Barbería El Poblado ",
                CATEGORY, IdentificationType.NIT, " 900123456-1 ", contactEmail, " 6041112233 ", timezone,
                List.of(new RegisterProviderCommand.LocationData(" Sede Poblado ", " Cra 43A # 5-15 ", CITY, " ")),
                min, max, 1440);
    }

    private static RegisterProviderCommand command() {
        return command(null, null, 60, 43200);
    }

    private Organization registeredOrganization() {
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizations).register(captor.capture(), eq(OWNER), eq(NOW));
        return captor.getValue();
    }

    @Nested
    @DisplayName("A valid registration")
    class ValidRegistration {

        @Test
        @DisplayName("HU-002: provisions a PROVIDER account, stores the business pending approval and sends the verification email")
        void registersPendingProvider() {
            // Act
            ProviderRegistration registration = useCase.register(command());

            // Assert
            assertEquals(OWNER, registration.ownerUserId());
            assertEquals("barberia@example.com", registration.email());
            assertEquals(OrganizationStatus.PENDING_APPROVAL, registration.status());
            InOrder order = inOrder(userProvisioning, organizations);
            order.verify(userProvisioning).provisionProviderUser("barberia@example.com", "Str0ng!Pass");
            order.verify(organizations).register(any(Organization.class), eq(OWNER), eq(NOW));
            order.verify(userProvisioning).resendSignupVerification("barberia@example.com");
            assertEquals(registration.organizationId(), registeredOrganization().getId());
        }

        @Test
        @DisplayName("Text fields are trimmed and the identification is upper-cased before storing")
        void normalisesTheInput() {
            // Act
            useCase.register(command());

            // Assert
            Organization organization = registeredOrganization();
            assertEquals("Barbería El Poblado", organization.getName());
            assertEquals("900123456-1", organization.getIdentificationNumber());
            assertEquals("6041112233", organization.getContactPhone());
            assertEquals("Sede Poblado", organization.getLocations().get(0).name());
            assertEquals("Cra 43A # 5-15", organization.getLocations().get(0).address());
        }

        @Test
        @DisplayName("Without contact email and time zone, the account email and America/Bogota are used; a blank location zone means none")
        void optionalFieldsTakeTheirDefaults() {
            // Act
            useCase.register(command(" ", "", 60, 43200));

            // Assert
            Organization organization = registeredOrganization();
            assertEquals("barberia@example.com", organization.getContactEmail());
            assertEquals(RegisterProviderUseCase.DEFAULT_TIMEZONE, organization.getTimezone());
            assertNull(organization.getLocations().get(0).timezone());
        }

        @Test
        @DisplayName("Given contact email and time zone are kept; the email is lower-cased")
        void givenOptionalFieldsAreKept() {
            // Act
            useCase.register(command("Citas@Barberia.com", "America/Lima", 60, 43200));

            // Assert
            Organization organization = registeredOrganization();
            assertEquals("citas@barberia.com", organization.getContactEmail());
            assertEquals("America/Lima", organization.getTimezone());
        }
    }

    @Nested
    @DisplayName("Rejections before anything is created")
    class RejectionsBeforeProvisioning {

        @Test
        @DisplayName("HU-002: a minimum notice greater than the maximum is a 400 and no account is created")
        void inconsistentRulesAreRejected() {
            // Arrange
            RegisterProviderCommand command = command(null, null, 5000, 100);

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            verifyNoInteractions(userProvisioning);
        }

        @Test
        @DisplayName("HU-002: an identification already registered is a 409 DUPLICATE_IDENTIFICATION and no account is created")
        void duplicatedIdentificationIsRejected() {
            // Arrange
            when(organizations.existsByIdentification(IdentificationType.NIT, "900123456-1")).thenReturn(true);
            RegisterProviderCommand command = command();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_IDENTIFICATION, ex.errorCode());
            verifyNoInteractions(userProvisioning);
        }

        @Test
        @DisplayName("An unknown category and an unknown city are reported together, by field, and no account is created")
        void unknownReferencesAreRejected() {
            // Arrange
            when(referenceData.categoryExists(CATEGORY)).thenReturn(false);
            when(referenceData.cityExists(CITY)).thenReturn(false);
            RegisterProviderCommand command = command();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command));

            // Assert
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
            assertEquals("unknown category", ex.details().get("categoryId"));
            assertEquals("unknown city", ex.details().get("locations[0].cityId"));
            verifyNoInteractions(userProvisioning);
        }
    }

    @Nested
    @DisplayName("Compensation when the registration fails after the account exists")
    class Compensation {

        @Test
        @DisplayName("A concurrent registration that took the identification is a 409, and the new account is deleted")
        void concurrentDuplicateIsCompensated() {
            // Arrange
            doThrow(new DuplicateOrganizationException(new IllegalStateException("unique violation")))
                    .when(organizations).register(any(Organization.class), eq(OWNER), eq(NOW));
            RegisterProviderCommand command = command();

            // Act
            BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command));

            // Assert
            assertEquals(ErrorCode.DUPLICATE_IDENTIFICATION, ex.errorCode());
            verify(userProvisioning).deprovisionUser(OWNER);
            verify(userProvisioning, never()).resendSignupVerification(anyString());
        }

        @Test
        @DisplayName("If the verification email cannot be sent, the account is deleted and the failure propagates")
        void failureAfterStoringIsCompensated() {
            // Arrange
            BusinessException upstream = new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR);
            doThrow(upstream).when(userProvisioning).resendSignupVerification(anyString());
            RegisterProviderCommand command = command();

            // Act
            Executable act = () -> useCase.register(command);

            // Assert
            assertSame(upstream, assertThrows(BusinessException.class, act));
            verify(userProvisioning).deprovisionUser(OWNER);
        }
    }
}
