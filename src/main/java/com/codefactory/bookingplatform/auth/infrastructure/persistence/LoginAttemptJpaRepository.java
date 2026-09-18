package com.codefactory.bookingplatform.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LoginAttemptJpaRepository extends JpaRepository<LoginAttemptEntity, Long> {

    List<LoginAttemptEntity> findByEmailIgnoreCaseAndSuccessFalseAndAttemptedAtAfter(String email, Instant since);
}
