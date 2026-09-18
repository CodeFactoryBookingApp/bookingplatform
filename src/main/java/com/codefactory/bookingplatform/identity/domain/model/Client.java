package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Client aggregate root (HU-001). Pure domain model: no Spring, no JPA.
 * The identifier is the Supabase auth.users id, assigned at provisioning time.
 */
@Getter
public class Client {

    private final UUID id;
    private final String fullName;
    private final String document;
    private final LocalDate birthDate;
    private final String email;
    private final String phone;
    private final String city;
    private final NotificationChannel notificationChannel;
    private ClientStatus status;

    public Client(UUID id, String fullName, String document, LocalDate birthDate, String email,
                  String phone, String city, NotificationChannel notificationChannel, ClientStatus status) {
        this.id = id;
        this.fullName = fullName;
        this.document = document;
        this.birthDate = birthDate;
        this.email = email;
        this.phone = phone;
        this.city = city;
        this.notificationChannel = notificationChannel;
        this.status = status;
    }

    public static Client pendingVerification(UUID id, String fullName, String document, LocalDate birthDate,
                                             String email, String phone, String city,
                                             NotificationChannel notificationChannel) {
        return new Client(id, fullName, document, birthDate, email, phone, city,
                notificationChannel, ClientStatus.PENDING_VERIFICATION);
    }

    public void verifyEmail() {
        if (status == ClientStatus.ACTIVE) {
            return;
        }
        if (status != ClientStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Client " + status + " cannot be moved to ACTIVE by email verification");
        }
        this.status = ClientStatus.ACTIVE;
    }

    public void suspend() {
        if (status == ClientStatus.PENDING_VERIFICATION) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A pending verification client cannot be suspended");
        }
        this.status = ClientStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != ClientStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Only suspended clients can be reactivated");
        }
        this.status = ClientStatus.ACTIVE;
    }

    /**
     * Business invariant (HU-001): only a verified (ACTIVE) client may confirm bookings.
     * Enforced here so future booking flows reuse the same rule.
     */
    public boolean canConfirmBooking() {
        return status == ClientStatus.ACTIVE;
    }
}
