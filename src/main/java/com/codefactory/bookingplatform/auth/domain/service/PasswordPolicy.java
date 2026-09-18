package com.codefactory.bookingplatform.auth.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Password security policy (HU-021). Pure domain rule set, framework free.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {
    }

    public static List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            violations.add("Password must be at most " + MAX_LENGTH + " characters long");
        }
        if (password != null) {
            if (!UPPERCASE.matcher(password).find()) {
                violations.add("Password must contain at least one uppercase letter");
            }
            if (!LOWERCASE.matcher(password).find()) {
                violations.add("Password must contain at least one lowercase letter");
            }
            if (!DIGIT.matcher(password).find()) {
                violations.add("Password must contain at least one digit");
            }
            if (!SPECIAL.matcher(password).find()) {
                violations.add("Password must contain at least one special character");
            }
        }
        return violations;
    }

    public static boolean isValid(String password) {
        return violations(password).isEmpty();
    }
}
