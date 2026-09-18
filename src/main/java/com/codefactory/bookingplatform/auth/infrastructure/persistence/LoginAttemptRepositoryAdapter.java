package com.codefactory.bookingplatform.auth.infrastructure.persistence;

import com.codefactory.bookingplatform.auth.domain.port.LoginAttemptRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Repository
public class LoginAttemptRepositoryAdapter implements LoginAttemptRepository {

    private final LoginAttemptJpaRepository jpaRepository;

    public LoginAttemptRepositoryAdapter(LoginAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void recordAttempt(String email, boolean success, Instant attemptedAt) {
        jpaRepository.save(new LoginAttemptEntity(email.toLowerCase(Locale.ROOT), success, attemptedAt));
    }

    @Override
    public List<Instant> findFailuresSince(String email, Instant since) {
        return jpaRepository
                .findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter(email.toLowerCase(Locale.ROOT), since)
                .stream()
                .map(LoginAttemptEntity::getAttemptedAt)
                .toList();
    }
}
