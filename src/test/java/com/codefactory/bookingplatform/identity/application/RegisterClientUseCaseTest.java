package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-001 registration use case.
 *
 * Techniques applied:
 * - Decision table over the validation rules (age, duplicate email, duplicate
 *   document) including the precedence between them.
 * - Boundary value analysis on the age rule, driven by a fixed Clock.
 * - Black-box partitioning of the normalisation rules (case and surrounding
 *   blanks) verified with ArgumentCaptor on the persisted aggregate.
 * - White-box branch coverage: the happy path, the three guard clauses and
 *   both entries into the catch block that compensates the provisioning.
 * - Interaction testing: what must be called, in which order, and what must
 *   never be called when a rule fails.
 */
class RegisterClientUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final UUID PROVISIONED_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ClientRepository clientRepository;
    private UserProvisioning userProvisioning;
    private RegisterClientUseCase useCase;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        userProvisioning = mock(UserProvisioning.class);
        useCase = new RegisterClientUseCase(clientRepository, userProvisioning, CLOCK);
    }

    private RegisterClientCommand command() {
        return command("ana@example.com", "cc12345678", LocalDate.of(1995, 4, 10));
    }

    private RegisterClientCommand command(String email, String document, LocalDate birthDate) {
        return new RegisterClientCommand("Ana Perez", document, birthDate, email,
                "+573001234567", "Bogota", NotificationChannel.EMAIL, "Str0ng!Pass");
    }

    private void provisioningSucceeds() {
        when(userProvisioning.provisionClientUser(anyString(), anyString())).thenReturn(PROVISIONED_ID);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Client capturedSavedClient() {
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- Happy path ----------------------------------------------------------

    @Test
    @DisplayName("A valid adult registration provisions the credential, stores the profile and triggers the verification email")
    void happyPathRegistersTheClient() {
        provisioningSucceeds();

        RegistrationOutcome outcome = useCase.register(command());

        assertEquals(PROVISIONED_ID, outcome.clientId());
        assertEquals("ana@example.com", outcome.email());
        assertEquals(ClientStatus.PENDING_VERIFICATION, outcome.status());

        InOrder order = inOrder(clientRepository, userProvisioning);
        order.verify(clientRepository).existsByEmail("ana@example.com");
        order.verify(clientRepository).existsByDocument("CC12345678");
        order.verify(userProvisioning).provisionClientUser("ana@example.com", "Str0ng!Pass");
        order.verify(clientRepository).save(any(Client.class));
        order.verify(userProvisioning).resendSignupVerification("ana@example.com");
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("The stored client starts in PENDING_VERIFICATION and never in ACTIVE")
    void storedClientStartsPendingVerification() {
        provisioningSucceeds();

        useCase.register(command());

        Client saved = capturedSavedClient();
        assertEquals(ClientStatus.PENDING_VERIFICATION, saved.getStatus());
        assertEquals(PROVISIONED_ID, saved.getId());
    }

    @Test
    @DisplayName("The client is never deprovisioned when the registration completes")
    void noCompensationOnSuccess() {
        provisioningSucceeds();

        useCase.register(command());

        verify(userProvisioning, never()).deprovisionUser(any(UUID.class));
    }

    // --- Normalisation -------------------------------------------------------

    @Test
    @DisplayName("Email is lowercased, document uppercased and name, phone and city are trimmed before persisting")
    void normalisesEveryFieldBeforePersisting() {
        provisioningSucceeds();
        RegisterClientCommand raw = new RegisterClientCommand("  Ana Perez  ", "  cc12345678  ",
                LocalDate.of(1995, 4, 10), "Ana.Perez@EXAMPLE.COM", "  +573001234567  ", "  Bogota  ",
                NotificationChannel.SMS, "Str0ng!Pass");

        useCase.register(raw);

        Client saved = capturedSavedClient();
        assertEquals("Ana Perez", saved.getFullName());
        assertEquals("CC12345678", saved.getDocument());
        assertEquals("ana.perez@example.com", saved.getEmail());
        assertEquals("+573001234567", saved.getPhone());
        assertEquals("Bogota", saved.getCity());
        assertEquals(NotificationChannel.SMS, saved.getNotificationChannel());
        assertEquals(LocalDate.of(1995, 4, 10), saved.getBirthDate());
    }

    @Test
    @DisplayName("The identity provider receives the normalised email, not the one typed by the user")
    void providerReceivesNormalisedEmail() {
        provisioningSucceeds();

        useCase.register(command("Ana.Perez@EXAMPLE.COM", "cc12345678", LocalDate.of(1995, 4, 10)));

        verify(userProvisioning).provisionClientUser("ana.perez@example.com", "Str0ng!Pass");
        verify(userProvisioning).resendSignupVerification("ana.perez@example.com");
    }

    @Test
    @DisplayName("The duplicate document check runs against the normalised document, not the typed one")
    void documentUniquenessIsCheckedNormalised() {
        provisioningSucceeds();

        useCase.register(command("ana@example.com", "  cc-123 ab  ", LocalDate.of(1995, 4, 10)));

        verify(clientRepository).existsByDocument("CC-123 AB");
    }

    @Test
    @DisplayName("The duplicate email check runs against the lowercased email")
    void emailUniquenessIsCheckedNormalised() {
        provisioningSucceeds();

        useCase.register(command("ANA@EXAMPLE.COM", "cc12345678", LocalDate.of(1995, 4, 10)));

        verify(clientRepository).existsByEmail("ana@example.com");
    }

    @Test
    @DisplayName("DEFECT PIN: surrounding blanks in the email are not trimmed, unlike every other field")
    void emailIsLowercasedButNotTrimmed() {
        // Documented gap, see the QA report: RegisterClientUseCase line 45 applies
        // toLowerCase without trim(), so "  Ana@Example.com " reaches the provider
        // and the uniqueness check with its blanks. Pinned here so the fix is visible.
        provisioningSucceeds();

        useCase.register(command("  Ana@Example.com ", "cc12345678", LocalDate.of(1995, 4, 10)));

        verify(clientRepository).existsByEmail("  ana@example.com ");
        assertEquals("  ana@example.com ", capturedSavedClient().getEmail());
    }

    // --- Age rule ------------------------------------------------------------

    @Test
    @DisplayName("A minor is rejected with MINOR_NOT_ALLOWED before any repository or provider call")
    void minorIsRejected() {
        RegisterClientCommand minor = command("ana@example.com", "cc12345678", TODAY.minusYears(18).plusDays(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(minor));

        assertEquals(ErrorCode.MINOR_NOT_ALLOWED, ex.errorCode());
        verifyNoInteractions(clientRepository);
        verifyNoInteractions(userProvisioning);
    }

    @ParameterizedTest(name = "born {0} -> accepted? {1}")
    @DisplayName("The age boundary is evaluated against the fixed clock: the 18th birthday is already valid")
    @CsvSource({
            "2008-09-19, false",
            "2008-09-18, true",
            "2008-09-17, true"
    })
    void ageBoundaryAgainstFixedClock(LocalDate birthDate, boolean accepted) {
        provisioningSucceeds();
        RegisterClientCommand cmd = command("ana@example.com", "cc12345678", birthDate);

        if (accepted) {
            assertEquals(ClientStatus.PENDING_VERIFICATION, useCase.register(cmd).status());
        } else {
            assertEquals(ErrorCode.MINOR_NOT_ALLOWED,
                    assertThrows(BusinessException.class, () -> useCase.register(cmd)).errorCode());
        }
    }

    @Test
    @DisplayName("Age is measured with the injected clock, so a client who is a minor today is not registered")
    void ageUsesTheInjectedClock() {
        Clock fiveYearsEarlier = Clock.fixed(
                LocalDate.of(2021, 9, 18).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        RegisterClientUseCase pastUseCase =
                new RegisterClientUseCase(clientRepository, userProvisioning, fiveYearsEarlier);
        RegisterClientCommand justEighteenToday = command("ana@example.com", "cc12345678", TODAY.minusYears(18));

        assertEquals(ErrorCode.MINOR_NOT_ALLOWED,
                assertThrows(BusinessException.class, () -> pastUseCase.register(justEighteenToday)).errorCode());
    }

    // --- Uniqueness rules and their precedence -------------------------------

    @Test
    @DisplayName("A duplicate email is rejected with DUPLICATE_EMAIL before the document is even checked")
    void duplicateEmailIsRejected() {
        when(clientRepository.existsByEmail("ana@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command()));

        assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.errorCode());
        verify(clientRepository).existsByEmail("ana@example.com");
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userProvisioning);
    }

    @Test
    @DisplayName("A duplicate document is rejected with DUPLICATE_DOCUMENT and no credential is provisioned")
    void duplicateDocumentIsRejected() {
        when(clientRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(clientRepository.existsByDocument("CC12345678")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command()));

        assertEquals(ErrorCode.DUPLICATE_DOCUMENT, ex.errorCode());
        verify(clientRepository, never()).save(any(Client.class));
        verifyNoInteractions(userProvisioning);
    }

    @Test
    @DisplayName("A minor with an email already registered fails for being a minor, not for the duplicate")
    void ageRuleWinsOverDuplicateEmail() {
        when(clientRepository.existsByEmail(anyString())).thenReturn(true);
        RegisterClientCommand minorWithTakenEmail =
                command("ana@example.com", "cc12345678", TODAY.minusYears(10));

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(minorWithTakenEmail));

        assertEquals(ErrorCode.MINOR_NOT_ALLOWED, ex.errorCode());
        verifyNoInteractions(clientRepository);
    }

    @Test
    @DisplayName("A minor with a document already registered fails for being a minor, not for the duplicate")
    void ageRuleWinsOverDuplicateDocument() {
        when(clientRepository.existsByDocument(anyString())).thenReturn(true);
        RegisterClientCommand minorWithTakenDocument =
                command("ana@example.com", "cc12345678", TODAY.minusYears(3));

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(minorWithTakenDocument));

        assertEquals(ErrorCode.MINOR_NOT_ALLOWED, ex.errorCode());
        verifyNoInteractions(clientRepository);
    }

    @Test
    @DisplayName("A duplicate email wins over a duplicate document when both collide")
    void duplicateEmailWinsOverDuplicateDocument() {
        when(clientRepository.existsByEmail(anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.register(command()));

        assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.errorCode());
        verify(clientRepository, never()).existsByDocument(anyString());
    }

    // --- Compensation of the provisioned credential --------------------------

    @Nested
    @DisplayName("Compensation when the registration fails after provisioning")
    class Compensation {

        @Test
        @DisplayName("If storing the profile fails the provisioned credential is deleted and the original failure is rethrown")
        void saveFailureCompensatesProvisioning() {
            when(userProvisioning.provisionClientUser(anyString(), anyString())).thenReturn(PROVISIONED_ID);
            RuntimeException boom = new IllegalStateException("database down");
            when(clientRepository.save(any(Client.class))).thenThrow(boom);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> useCase.register(command()));

            assertSame(boom, thrown);
            verify(userProvisioning).deprovisionUser(PROVISIONED_ID);
            verify(userProvisioning, never()).resendSignupVerification(anyString());
        }

        @Test
        @DisplayName("If the verification email cannot be sent the provisioned credential is deleted and the original failure is rethrown")
        void resendFailureCompensatesProvisioning() {
            provisioningSucceeds();
            BusinessException upstream = new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR);
            doThrow(upstream).when(userProvisioning).resendSignupVerification(anyString());

            BusinessException thrown = assertThrows(BusinessException.class, () -> useCase.register(command()));

            assertSame(upstream, thrown);
            assertEquals(ErrorCode.UPSTREAM_AUTH_ERROR, thrown.errorCode());
            verify(userProvisioning).deprovisionUser(PROVISIONED_ID);
        }

        @Test
        @DisplayName("The compensation targets exactly the id that was provisioned")
        void compensationUsesTheProvisionedId() {
            UUID otherId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            when(userProvisioning.provisionClientUser(anyString(), anyString())).thenReturn(otherId);
            when(clientRepository.save(any(Client.class))).thenThrow(new IllegalStateException("boom"));

            assertThrows(RuntimeException.class, () -> useCase.register(command()));

            ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
            verify(userProvisioning).deprovisionUser(captor.capture());
            assertEquals(otherId, captor.getValue());
        }

        @Test
        @DisplayName("If provisioning itself fails nothing is stored and there is nothing to compensate")
        void provisioningFailureNeedsNoCompensation() {
            BusinessException upstream = new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR);
            when(userProvisioning.provisionClientUser(anyString(), anyString())).thenThrow(upstream);

            BusinessException thrown = assertThrows(BusinessException.class, () -> useCase.register(command()));

            assertSame(upstream, thrown);
            verify(userProvisioning, never()).deprovisionUser(any(UUID.class));
            verify(clientRepository, never()).save(any(Client.class));
        }

        @Test
        @DisplayName("DEFECT PIN: if the compensation also fails, its error replaces the original cause")
        void compensationFailureMasksTheOriginalCause() {
            // Documented gap, see the QA report: RegisterClientUseCase line 73 calls
            // deprovisionUser outside any guard, so a failing compensation hides the
            // real reason the registration failed.
            when(userProvisioning.provisionClientUser(anyString(), anyString())).thenReturn(PROVISIONED_ID);
            when(clientRepository.save(any(Client.class))).thenThrow(new IllegalStateException("database down"));
            doThrow(new IllegalStateException("provider down")).when(userProvisioning).deprovisionUser(PROVISIONED_ID);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> useCase.register(command()));

            assertEquals("provider down", thrown.getMessage());
        }
    }
}
