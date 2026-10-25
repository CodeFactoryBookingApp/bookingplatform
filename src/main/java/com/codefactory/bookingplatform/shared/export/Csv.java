package com.codefactory.bookingplatform.shared.export;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV as spreadsheets read it (RFC 4180), for the exports of the admin listings (HU-003) and the
 * reports to come (HU-018, HU-023).
 *
 * <ul>
 *   <li>Fields with a comma, a quote or a line break are quoted, and quotes are doubled.</li>
 *   <li>A field starting with = + - @ or a tab is prefixed with an apostrophe: otherwise a name
 *   typed as "=HYPERLINK(...)" would run as a formula when the file is opened (CSV injection).</li>
 *   <li>The text starts with a UTF-8 byte order mark, so Excel shows accents correctly.</li>
 * </ul>
 */
public final class Csv {

    public static final String MEDIA_TYPE = "text/csv;charset=UTF-8";

    private static final String BOM = "﻿";
    private static final String LINE_END = "\r\n";

    private Csv() {
    }

    public static String of(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder(BOM).append(line(header));
        rows.forEach(row -> csv.append(line(row)));
        return csv.toString();
    }

    private static String line(List<String> fields) {
        return fields.stream().map(Csv::field).collect(Collectors.joining(",")) + LINE_END;
    }

    static String field(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = startsLikeAFormula(value) ? "'" + value : value;
        boolean quote = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        return quote ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    private static boolean startsLikeAFormula(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t';
    }
}
