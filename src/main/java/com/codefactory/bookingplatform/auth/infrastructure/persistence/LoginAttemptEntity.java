package com.codefactory.bookingplatform.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "ix_login_attempts_email_time", columnList = "email,attempted_at")
})
@Getter
@Setter
@NoArgsConstructor
public class LoginAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public LoginAttemptEntity(String email, boolean success, Instant attemptedAt) {
        this.email = email;
        this.success = success;
        this.attemptedAt = attemptedAt;
    }
}
