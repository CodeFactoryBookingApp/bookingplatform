package com.codefactory.bookingplatform.auth.infrastructure.config;

import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.shared.config.AuthPolicyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AuthConfig {

    @Bean
    public LoginLockPolicy loginLockPolicy(AuthPolicyProperties properties) {
        return new LoginLockPolicy(properties.maxFailedAttempts(), Duration.ofMinutes(properties.lockWindowMinutes()));
    }
}
