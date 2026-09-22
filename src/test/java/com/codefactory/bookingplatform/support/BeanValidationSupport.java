package com.codefactory.bookingplatform.support;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared Jakarta Validator for the DTO boundary tests. Building the factory is
 * expensive, so it is created once and reused by every DTO test class.
 */
public final class BeanValidationSupport {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private BeanValidationSupport() {
    }

    public static Validator validator() {
        return VALIDATOR;
    }

    /** Every violation message raised on {@code property}, empty when the field is accepted. */
    public static <T> Set<String> messagesFor(T bean, String property) {
        return VALIDATOR.validate(bean).stream()
                .filter(v -> property.equals(v.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    /** Names of every property that raised at least one violation. */
    public static <T> Set<String> invalidProperties(T bean) {
        return VALIDATOR.validate(bean).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    public static String repeat(char character, int length) {
        return String.valueOf(character).repeat(length);
    }

    /**
     * A syntactically valid email of exactly {@code totalLength} characters.
     * The local part is capped at the 64 characters RFC 5321 allows and the
     * domain is split into labels short enough to stay valid, so the only
     * constraint the result can ever trip is the length one.
     * Valid for totalLength between 131 and 190.
     */
    public static String emailOfLength(int totalLength) {
        int localPart = 64;
        int secondLabel = totalLength - 130;
        return repeat('a', localPart) + "@" + repeat('b', 60) + "." + repeat('c', secondLabel) + ".com";
    }

    public static String digits(int length) {
        return repeat('7', length);
    }
}
