package it.unibo.hermes.e2e;

import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for user registration and authentication.
 *
 * <p> Verifies that the PostgreSQL-based REST authentication mechanism is working properly.
 * Verifies the successful creation of new accounts, successful login yielding a valid JWT
 * and the authorisation of protected endpoints. Also tests negative security paths,
 * ensuring the Gateway correctly rejects invalid credentials and blocks access
 * when a bearer token is missing or malformed.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class RegistrationAndAuthenticationE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();

    private TestClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void registerAndAuthenticateUser() {
        String username = TestIds.testId("auth");
        client = new TestClient(username);

        client.register(TIMEOUT);
        client.login(TIMEOUT);

        assertNotNull(client.getToken(), "Login must return a JWT");

        assertTrue(client.getToken().length() > 10, "JWT should be a non-trivial token, not a placeholder");
    }

    @Test
    void rejectsLoginWithInvalidCredentials() {
        String username = TestIds.testId("badpw");
        client = new TestClient(username);
        client.register(TIMEOUT);

        try (TestClient wrongPasswordAttempt =
                     new TestClient(username, "definitely-wrong-password")) {
            assertThrows(RuntimeException.class, () -> wrongPasswordAttempt.login(TIMEOUT),
                    "Login with the wrong password must be rejected");
        }
    }

    @Test
    void rejectsProtectedAccessWithoutValidToken() {
        String username = TestIds.testId("noauth");
        client = new TestClient(username);
        client.register(TIMEOUT);
        client.login(TIMEOUT);

        assertThrows(RuntimeException.class,
                () -> client.attemptFetchConversationsWithHeader("Bearer not-a-real-jwt", TIMEOUT),
                "A protected endpoint must reject an invalid bearer token");
        assertThrows(RuntimeException.class,
                () -> client.attemptFetchConversationsWithHeader("", TIMEOUT),
                "A protected endpoint must reject a missing/empty Authorization header");
    }
}
