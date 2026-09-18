package com.codefactory.bookingplatform.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtIssuer, String jwksUri) {
}
