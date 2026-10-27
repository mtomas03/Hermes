package it.unibo.hermes.e2e;

import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End test suite for PostgreSQL outage and recovery with stateless JWT authentication.
 *
 * <p> When the PostgreSQL instance goes down, the underlying PVC and stored data are preserved.
 * During the outage, registration and login attempts and user searches fail, but previously issued valid JWTs
 * continue to work for both REST requests and new STOMP WebSocket connections. After PostgreSQL recovery,
 * existing accounts can log in again, new registrations succeed and the system continues to function normally.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class PostgresOutageStatelessJwtE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration SCALE_TIMEOUT = Duration.ofSeconds(300);
    private static final String NAMESPACE = E2EConfig.get().namespace();
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    private TestClient alice;
    private TestClient bob;

    @AfterEach
    void tearDown() {
        if (alice != null) alice.close();
        if (bob != null) bob.close();
    }

    @Test
    void validJwtKeepsWorkingWhilePostgresIsDownAndAccountsSurviveRecovery() {
        alice = new TestClient(TestIds.testId("pgA"));
        bob = new TestClient(TestIds.testId("pgB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conv = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String messageBefore = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("beforePgOutage"));
        Poller.pollUntil(() -> bob.findLocalMessage(conv, messageBefore), TIMEOUT, "baseline message delivery");
        Poller.pollUntil(() -> alice.findRemoteMessage(conv, messageBefore, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT, "baseline message ACKNOWLEDGED");

        String outageUser = TestIds.testId("pgOutage");
        String password = TestIds.testPassword();
        byte[] signingKey = Base64.getDecoder().decode(
                Kubectl.secretValue("hermes-secrets", "JWT_SECRET", NAMESPACE));

        try {
            Kubectl.scaleStatefulSetAndAwait(
                    "postgres-statefulset", "hermes-postgres", 0, NAMESPACE, SCALE_TIMEOUT);

            // initial authentication needs PostgreSQL
            Poller.pollUntilTrue(() -> HttpClient.postJson("/api/v1/auth/register",
                            HttpClient.credentials(outageUser, password), TIMEOUT).status() == 503,
                    TIMEOUT, "registration to answer 503 while PostgreSQL is down");
            HttpClient.Response login = HttpClient.postJson("/api/v1/auth/login",
                    HttpClient.credentials(alice.getUsername(), TestIds.testPassword()), TIMEOUT);
            assertEquals(503, login.status(), "login must report unavailability, not a password error");
            assertFalse(login.body().contains("token"), "no JWT may be issued during the outage");
            HttpClient.Response wrongPw = HttpClient.postJson("/api/v1/auth/login",
                    HttpClient.credentials(alice.getUsername(), "definitely-wrong"), TIMEOUT);
            assertEquals(503, wrongPw.status(), "an infrastructure error must not look like a wrong password");

            // REST with the already issued JWT, no PostgreSQL involved
            assertEquals(200, HttpClient.get("/api/v1/conversations", alice.bearerHeader(), TIMEOUT).status());
            assertEquals(messageBefore, alice.findRemoteMessage(conv, messageBefore, TIMEOUT).orElseThrow().messageId());

            // user search reads PostgreSQL
            assertEquals(503, HttpClient.get("/api/v1/users/search?username=" + bob.getUsername(),
                    alice.bearerHeader(), TIMEOUT).status());

            // a new STOMP connection authenticated by the same JWT, then real messaging
            alice.disconnect();
            alice.connectAndAwait(TIMEOUT);
            String during = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("duringPgOutage"));
            Poller.pollUntil(() -> bob.findLocalMessage(conv, during), TIMEOUT,
                    "message sent over a new WebSocket during the outage to reach the recipient");
            Poller.pollUntil(() -> alice.findRemoteMessage(conv, during, TIMEOUT)
                            .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                    TIMEOUT, "message sent during the outage to be persisted and ACKNOWLEDGED");

            // invalid tokens are still rejected
            String expired = ForgedJwt.hs256(alice.getUsername(), Instant.now().minusSeconds(120), signingKey);
            String badSignature = ForgedJwt.hs256(alice.getUsername(), Instant.now().plusSeconds(600),
                    "an-unrelated-key-of-at-least-32-bytes".getBytes());
            for (String bad : new String[]{expired, badSignature, "not.a.jwt"}) {
                int status = HttpClient.get("/api/v1/conversations", "Bearer " + bad, TIMEOUT).status();
                assertTrue(status == 401 || status == 403,
                        "invalid token must be rejected, got " + status);
            }
        } finally {
            Kubectl.scaleStatefulSetAndAwait(
                    "postgres-statefulset", "hermes-postgres", 1, NAMESPACE, SCALE_TIMEOUT);
        }

        // recovery without restarting any Gateway
        Poller.pollUntilTrue(() -> HttpClient.postJson("/api/v1/auth/login",
                        HttpClient.credentials(alice.getUsername(), TestIds.testPassword()), TIMEOUT).status() == 200,
                SCALE_TIMEOUT, "login of an existing account to work again after PostgreSQL recovery");
        int dup = HttpClient.postJson("/api/v1/auth/register",
                HttpClient.credentials(alice.getUsername(), TestIds.testPassword()), TIMEOUT).status();
        assertTrue(dup == 400 || dup == 409, "existing account must not be duplicated, got " + dup);

        String postRecoveryNewcomer = TestIds.testId("pgNewPost");
        Poller.pollUntilTrue(() -> HttpClient.postJson("/api/v1/auth/register",
                        HttpClient.credentials(postRecoveryNewcomer, password), TIMEOUT).status() == 201,
                TIMEOUT, "new registrations must work again");
        Poller.pollUntilTrue(() -> HttpClient.postJson("/api/v1/auth/login",
                        HttpClient.credentials(postRecoveryNewcomer, password), TIMEOUT).status() == 200,
                TIMEOUT, "login for newly registered account must work");
        assertEquals(200, HttpClient.postJson("/api/v1/auth/login",
                HttpClient.credentials(postRecoveryNewcomer, password), TIMEOUT).status());
    }
}
