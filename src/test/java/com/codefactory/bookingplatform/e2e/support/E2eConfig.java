package com.codefactory.bookingplatform.e2e.support;

/**
 * Where the end-to-end suite finds the local stack ({@code scripts/e2e/stack.sh env}). Every value
 * has the local default except the Supabase secret key: it is needed to create the admin account
 * and has no safe default, so a missing one stops the run with a message that says what to do.
 */
public final class E2eConfig {

    public static final String API_URL = env("E2E_API_URL", "http://localhost:8080");
    public static final String SUPABASE_URL = env("SUPABASE_URL", "http://127.0.0.1:54321");
    public static final String MAILPIT_URL = env("MAILPIT_URL", "http://127.0.0.1:54324");

    private E2eConfig() {
    }

    /** The Supabase secret key (service role) used to bootstrap the admin account. */
    public static String supabaseSecretKey() {
        String key = System.getenv("SUPABASE_SECRET_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("SUPABASE_SECRET_KEY is not set. Start the stack with "
                    + "`scripts/e2e/stack.sh up` and export its variables: eval \"$(scripts/e2e/stack.sh env)\"");
        }
        return key;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
