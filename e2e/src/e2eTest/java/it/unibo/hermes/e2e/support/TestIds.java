package it.unibo.hermes.e2e.support;

import java.util.UUID;

/**
 * Generates unique identifiers for a single E2E test run, so tests never
 * collide with data from a previous run or with each other.
 */
public final class TestIds {

    private TestIds() {}

    /**
     * Generates a short, unique suffix for this run/user.
     *
     * @return an 8-character lowercase hex fragment of a random UUID
     */
    public static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Builds a unique identifier for this test, recognisable as E2E-generated in server logs.
     *
     * @param role a short role label
     * @return an identifier of the form {@code e2e-<role>-<shortId>}
     */
    public static String testId(String role) {
        return "e2e-" + role + "-" + shortId();
    }

    /**
     * Returns the password used for all E2E test accounts.
     *
     * @return the test password
     */
    public static String testPassword() {
        return "E2E_test_password";
    }

    /**
     * Builds a recognisable and unique message body.
     *
     * @param runId a per-run identifier
     * @return a message body of the form {@code E2E_TEST_<runId>_<uniqueSuffix>}
     */
    public static String messageBody(String runId) {
        return "E2E_TEST_" + runId + "_" + shortId();
    }

    /**
     * Returns a conversation id for a pair of participants, regardless of the order
     *
     * @param usernameA the first participant's username
     * @param usernameB the second participant's username
     * @return the deterministic conversation id for this pair of participants
     */
    public static String conversationId(String usernameA, String usernameB) {
        String a = usernameA.trim().toLowerCase();
        String b = usernameB.trim().toLowerCase();
        return a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a;
    }
}
