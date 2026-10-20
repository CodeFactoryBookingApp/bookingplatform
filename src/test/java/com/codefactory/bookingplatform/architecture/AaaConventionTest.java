package com.codefactory.bookingplatform.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the Arrange-Act-Assert convention from eroding as the suite grows.
 *
 * Every test method in src/test/java must show its phases as line comments, in order:
 * an optional {@code // Arrange}, exactly one {@code // Act} and then {@code // Assert}.
 * A test with two Act blocks is testing two behaviours and should be split.
 */
class AaaConventionTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    private static final Pattern TEST_METHOD = Pattern.compile(
            "@(Test|ParameterizedTest|RepeatedTest)\\b[^{;]*?\\bvoid\\s+(\\w+)\\s*\\([^)]*\\)[^{;]*\\{");

    @Test
    @DisplayName("every test method declares Act once and then Assert, with Arrange before Act when present")
    void everyTestFollowsArrangeActAssert() throws IOException {
        // Arrange
        List<Path> sources = testSources();

        // Act
        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            violations.addAll(violationsIn(source));
        }

        // Assert
        assertTrue(sources.size() > 20, "the scan must actually reach the test sources");
        assertEquals(List.of(), violations, "tests that do not follow Arrange-Act-Assert");
    }

    private static List<Path> testSources() throws IOException {
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<String> violationsIn(Path source) throws IOException {
        String code = Files.readString(source);
        List<String> violations = new ArrayList<>();
        Matcher method = TEST_METHOD.matcher(code);
        while (method.find()) {
            String body = bodyStartingAt(code, method.end());
            String problem = problemWith(body);
            if (problem != null) {
                violations.add(source.getFileName() + "#" + method.group(2) + ": " + problem);
            }
        }
        return violations;
    }

    /**
     * Returns the text between the opening brace (already consumed) and its matching closing brace.
     * Braces inside string, text-block and char literals or comments do not count: tests that send
     * malformed JSON carry unbalanced braces in their literals.
     */
    private static String bodyStartingAt(String code, int afterOpeningBrace) {
        int depth = 1;
        int i = afterOpeningBrace;
        while (i < code.length() && depth > 0) {
            if (code.startsWith("\"\"\"", i)) {
                i = code.indexOf("\"\"\"", i + 3) + 3;
            } else if (code.startsWith("//", i)) {
                i = code.indexOf('\n', i);
            } else if (code.startsWith("/*", i)) {
                i = code.indexOf("*/", i + 2) + 2;
            } else if (code.charAt(i) == '"' || code.charAt(i) == '\'') {
                i = endOfLiteral(code, i);
            } else {
                char c = code.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                i++;
            }
        }
        return code.substring(afterOpeningBrace, i - 1);
    }

    private static int endOfLiteral(String code, int opening) {
        char quote = code.charAt(opening);
        int i = opening + 1;
        while (code.charAt(i) != quote) {
            i += code.charAt(i) == '\\' ? 2 : 1;
        }
        return i + 1;
    }

    private static String problemWith(String body) {
        int arrange = body.indexOf("// Arrange");
        int act = body.indexOf("// Act");
        int assertion = body.indexOf("// Assert");
        if (act < 0) {
            return "missing // Act";
        }
        if (body.indexOf("// Act", act + 1) >= 0) {
            return "more than one // Act";
        }
        if (assertion < 0) {
            return "missing // Assert";
        }
        if (assertion < act) {
            return "// Assert before // Act";
        }
        if (arrange >= 0 && arrange > act) {
            return "// Arrange after // Act";
        }
        return null;
    }
}
