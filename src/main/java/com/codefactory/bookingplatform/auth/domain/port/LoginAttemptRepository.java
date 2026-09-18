package com.codefactory.bookingplatform.auth.domain.port;

import java.time.Instant;
import java.util.List;

public interface LoginAttemptRepository {

    void recordAttempt(String email, boolean success, Instant attemptedAt);

    List<Instant> findFailuresSince(String email, Instant since);
}
