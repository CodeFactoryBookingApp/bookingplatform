package com.codefactory.bookingplatform.shared.listing;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The parameters every listing shares: sort column, direction, page and a date range. Invalid
 * values are a 400 that names the parameter, never a 500 or a silent default.
 */
public final class ListingParams {

    public static final int MAX_PAGE_SIZE = 100;

    private ListingParams() {
    }

    /** A sort column given in any case ("price", "PRICE") among the values of {@code columns}. */
    public static <E extends Enum<E>> E sort(String value, Class<E> columns) {
        try {
            return Enum.valueOf(columns, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(columns.getEnumConstants())
                    .map(column -> column.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", "));
            throw invalid("sort", "must be one of: " + allowed);
        }
    }

    public static boolean descending(String direction) {
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw invalid("direction", "must be asc or desc");
        };
    }

    /** Adds to {@code errors} what is wrong with a page number and size. */
    public static void checkWindow(int page, int size, Map<String, String> errors) {
        if (page < 0) {
            errors.put("page", "must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /** Adds to {@code errors} a date range that ends before it starts. */
    public static void checkRange(LocalDate from, LocalDate to, String toField, Map<String, String> errors) {
        if (from != null && to != null && to.isBefore(from)) {
            errors.put(toField, "must not be before the start of the range");
        }
    }

    private static BusinessException invalid(String parameter, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid listing parameters",
                Map.of(parameter, message));
    }
}
