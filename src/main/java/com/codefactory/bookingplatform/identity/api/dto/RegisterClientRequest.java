package com.codefactory.bookingplatform.identity.api.dto;

import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Client self-registration payload (HU-001)")
public record RegisterClientRequest(

        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "fullName must be at most 120 characters")
        String fullName,

        @NotBlank(message = "document is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{5,20}$",
                message = "document must be 5-20 alphanumeric characters or hyphens")
        String document,

        @NotNull(message = "birthDate is required")
        @Past(message = "birthDate must be in the past")
        LocalDate birthDate,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 160, message = "email must be at most 160 characters")
        String email,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$",
                message = "phone must contain 7-15 digits, optionally prefixed with +")
        String phone,

        @NotBlank(message = "city is required")
        @Size(max = 80, message = "city must be at most 80 characters")
        String city,

        @NotNull(message = "notificationChannel is required")
        NotificationChannel notificationChannel,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password) {
}
