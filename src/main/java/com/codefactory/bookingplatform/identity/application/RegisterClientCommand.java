package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;

import java.time.LocalDate;

public record RegisterClientCommand(
        String fullName,
        String document,
        LocalDate birthDate,
        String email,
        String phone,
        String city,
        NotificationChannel notificationChannel,
        String password) {
}
