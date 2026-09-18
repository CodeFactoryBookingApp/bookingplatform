package com.codefactory.bookingplatform.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth-policy")
public record AuthPolicyProperties(int maxFailedAttempts, int lockWindowMinutes) {
}
