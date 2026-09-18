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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registrations")
@Tag(name = "Client Registration", description = "HU-001 - client self-registration and email verification")
public class RegistrationController {

    private final RegisterClientUseCase registerClientUseCase;
    private final ResendVerificationUseCase resendVerificationUseCase;
    private final ConfirmEmailUseCase confirmEmailUseCase;

    public RegistrationController(RegisterClientUseCase registerClientUseCase,
                                  ResendVerificationUseCase resendVerificationUseCase,
                                  ConfirmEmailUseCase confirmEmailUseCase) {
        this.registerClientUseCase = registerClientUseCase;
        this.resendVerificationUseCase = resendVerificationUseCase;
        this.confirmEmailUseCase = confirmEmailUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new client",
            description = "Creates the client with status PENDING_VERIFICATION and triggers the verification email. "
                    + "Rejects minors, duplicated email/document and invalid formats.")
    public RegisterClientResponse register(@Valid @RequestBody RegisterClientRequest request) {
        RegisterClientCommand command = new RegisterClientCommand(
                request.fullName(),
                request.document(),
                request.birthDate(),
                request.email(),
                request.phone(),
                request.city(),
                request.notificationChannel(),
                request.password());
        RegistrationOutcome outcome = registerClientUseCase.register(command);
        return RegisterClientResponse.from(outcome.clientId(), outcome.email(), outcome.status());
    }

    @PostMapping("/verification-resends")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Resend the verification email",
            description = "Always answered 202 to avoid user enumeration; a valid link is regenerated for known emails.")
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationUseCase.resend(request.email());
    }

    @PostMapping("/email-verifications")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Confirm the client email",
            description = "Consumes the one-time token_hash from the verification email link and activates the client.")
    public ConfirmEmailResponse confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        RegistrationOutcome outcome = confirmEmailUseCase.confirm(request.tokenHash());
        return ConfirmEmailResponse.from(outcome.clientId(), outcome.email(), outcome.status());
    }
}
