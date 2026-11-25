package com.codefactory.bookingplatform.e2e.support;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the emails the local Supabase Auth sends: Mailpit captures them instead of delivering them. */
public final class MailpitClient {

    public static final String CONFIRM_EMAIL_SUBJECT = "Confirm Your Email";
    public static final String RESET_PASSWORD_SUBJECT = "Reset Your Password";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final long POLL_MILLIS = 300;
    private static final Pattern TOKEN_PARAM = Pattern.compile("[?&]token=([^&\\s\"<>]+)");

    private MailpitClient() {
    }

    /**
     * Waits for the newest email to {@code address} with {@code subject} and returns the {@code token}
     * query parameter of its link, which is the token hash the API takes to verify or reset.
     */
    public static String awaitToken(String address, String subject) {
        String body = awaitBody(address, subject);
        Matcher matcher = TOKEN_PARAM.matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("The '" + subject + "' email to " + address + " has no link with a token:\n" + body);
        }
        return matcher.group(1);
    }

    /** Waits for the newest email to {@code address} with {@code subject} and returns its text body. */
    public static String awaitBody(String address, String subject) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            String id = newestMessageId(address, subject);
            if (id != null) {
                return RestAssured.given().baseUri(E2eConfig.MAILPIT_URL)
                        .get("/api/v1/message/{id}", id)
                        .then().statusCode(200)
                        .extract().path("Text");
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No '" + subject + "' email to " + address + " reached Mailpit within "
                        + TIMEOUT.toSeconds() + "s");
            }
            sleep();
        }
    }

    private static String newestMessageId(String address, String subject) {
        JsonPath search = RestAssured.given().baseUri(E2eConfig.MAILPIT_URL)
                .queryParam("query", "to:" + address)
                .get("/api/v1/search")
                .then().statusCode(200)
                .extract().jsonPath();
        // Mailpit lists the newest message first.
        List<Map<String, Object>> messages = search.getList("messages");
        return messages.stream()
                .filter(message -> subject.equals(message.get("Subject")))
                .map(message -> (String) message.get("ID"))
                .findFirst()
                .orElse(null);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an email", e);
        }
    }
}
