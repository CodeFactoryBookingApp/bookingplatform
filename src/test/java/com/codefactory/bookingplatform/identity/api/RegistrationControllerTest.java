package com.codefactory.bookingplatform.identity.api;

import com.codefactory.bookingplatform.identity.api.dto.ConfirmEmailRequest;
import com.codefactory.bookingplatform.identity.api.dto.ConfirmEmailResponse;
import com.codefactory.bookingplatform.identity.api.dto.RegisterClientRequest;
import com.codefactory.bookingplatform.identity.api.dto.RegisterClientResponse;
import com.codefactory.bookingplatform.identity.api.dto.ResendVerificationRequest;
import com.codefactory.bookingplatform.identity.application.ConfirmEmailUseCase;
import com.codefactory.bookingplatform.identity.application.RegisterClientCommand;
import com.codefactory.bookingplatform.identity.application.RegisterClientUseCase;
import com.codefactory.bookingplatform.identity.application.RegistrationOutcome;
import com.codefactory.bookingplatform.identity.application.ResendVerificationUseCase;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The registration controller only translates between the HTTP payload and the use cases. What has
 * to hold is that every field of the request reaches the command untouched and in the right slot,
 * and that each endpoint talks to its own use case.
 */
class RegistrationControllerTest {

    private static final UUID CLIENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final RegisterClientUseCase registerClientUseCase = mock(RegisterClientUseCase.class);
    private final ResendVerificationUseCase resendVerificationUseCase = mock(ResendVerificationUseCase.class);
    private final ConfirmEmailUseCase confirmEmailUseCase = mock(ConfirmEmailUseCase.class);

    private final RegistrationController controller = new RegistrationController(
            registerClientUseCase, resendVerificationUseCase, confirmEmailUseCase);

    private RegisterClientRequest request() {
        return new RegisterClientRequest(
                "Ana Perez",
                "1017245896",
                LocalDate.of(1995, 3, 14),
                "ana.perez@example.com",
                "+573001112233",
                "Medellin",
                NotificationChannel.EMAIL,
                "Str0ng!Pass");
    }

    @Test
    @DisplayName("Registration hands every submitted field to the use case in its own slot")
    void registrationMapsTheWholeRequest() {
        // Arrange
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.PENDING_VERIFICATION));
        RegisterClientRequest request = request();

        // Act
        controller.register(request);

        // Assert
        ArgumentCaptor<RegisterClientCommand> captor = ArgumentCaptor.forClass(RegisterClientCommand.class);
        verify(registerClientUseCase).register(captor.capture());
        RegisterClientCommand command = captor.getValue();
        assertEquals("Ana Perez", command.fullName());
        assertEquals("1017245896", command.document());
        assertEquals(LocalDate.of(1995, 3, 14), command.birthDate());
        assertEquals("ana.perez@example.com", command.email());
        assertEquals("+573001112233", command.phone());
        assertEquals("Medellin", command.city());
        assertEquals(NotificationChannel.EMAIL, command.notificationChannel());
        assertEquals("Str0ng!Pass", command.password());
    }

    @Test
    @DisplayName("Registration answers with the client id, email and status the use case produced")
    void registrationAnswersWithTheOutcome() {
        // Arrange
        when(registerClientUseCase.register(any()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.PENDING_VERIFICATION));
        RegisterClientRequest request = request();

        // Act
        RegisterClientResponse response = controller.register(request);

        // Assert
        assertEquals(CLIENT_ID, response.clientId());
        assertEquals("ana.perez@example.com", response.email());
        assertEquals(ClientStatus.PENDING_VERIFICATION, response.status());
    }

    @Test
    @DisplayName("A verification resend reaches the resend use case with the submitted email")
    void resendForwardsTheEmail() {
        // Arrange
        ResendVerificationRequest request = new ResendVerificationRequest("ana.perez@example.com");

        // Act
        controller.resendVerification(request);

        // Assert
        verify(resendVerificationUseCase).resend("ana.perez@example.com");
    }

    @Test
    @DisplayName("Email confirmation forwards the one-time token hash to the confirmation use case")
    void confirmForwardsTheTokenHash() {
        // Arrange
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.ACTIVE));
        ConfirmEmailRequest request = new ConfirmEmailRequest("token-hash");

        // Act
        controller.confirmEmail(request);

        // Assert
        verify(confirmEmailUseCase).confirm("token-hash");
    }

    @Test
    @DisplayName("A confirmed email is answered with the activated client")
    void confirmAnswersWithTheActivatedClient() {
        // Arrange
        when(confirmEmailUseCase.confirm(anyString()))
                .thenReturn(new RegistrationOutcome(CLIENT_ID, "ana.perez@example.com", ClientStatus.ACTIVE));
        ConfirmEmailRequest request = new ConfirmEmailRequest("token-hash");

        // Act
        ConfirmEmailResponse response = controller.confirmEmail(request);

        // Assert
        assertEquals(CLIENT_ID, response.clientId());
        assertEquals("ana.perez@example.com", response.email());
        assertEquals(ClientStatus.ACTIVE, response.status());
    }
}
