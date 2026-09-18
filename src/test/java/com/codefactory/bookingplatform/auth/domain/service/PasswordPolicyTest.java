package com.codefactory.bookingplatform.auth.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    @DisplayName("Strong password passes the policy")
    void strongPasswordIsValid() {
        assertTrue(PasswordPolicy.isValid("Str0ng!Pass"));
    }

    @Test
    @DisplayName("Short password is rejected")
    void shortPasswordIsRejected() {
        List<String> violations = PasswordPolicy.violations("Ab1!");
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Password without required character classes reports every missing rule")
    void reportsAllMissingRules() {
        List<String> violations = PasswordPolicy.violations("todolowercase");
        assertEqualsAtLeast(violations, 3);
    }

    @Test
    @DisplayName("Null password is rejected without NPE")
    void nullPasswordIsRejected() {
        assertFalse(PasswordPolicy.isValid(null));
    }

    private void assertEqualsAtLeast(List<String> violations, int minimum) {
        assertTrue(violations.size() >= minimum,
                "Expected at least " + minimum + " violations but got " + violations);
    }
}
