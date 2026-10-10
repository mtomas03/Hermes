package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for conversation-level authorisation.
 *
 * <p> An authenticated user is not automatically allowed to read or write any conversation:
 * membership is decided server-side from the participants of the messages actually exchanged,
 * never from a client-supplied id.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class ConversationAuthorizationE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();

    private TestClient alice;
    private TestClient bob;
    private TestClient charlie;
    private TestClient david;

    @AfterEach
    void tearDown() {
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
        if (charlie != null) {
            charlie.close();
        }
        if (david != null) {
            david.close();
        }
    }

    @Test
    void nonParticipantCannotSyncAnotherConversationAndLearnsNothingFromTheRefusal() {
        alice = new TestClient(TestIds.testId("authzA"));
        bob = new TestClient(TestIds.testId("authzB"));
        charlie = new TestClient(TestIds.testId("authzC"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        charlie.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        Poller.pollUntil(() -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT), TIMEOUT,
                "message " + messageId + " to be persisted server-side");

        WebClientResponseException existing = assertThrows(WebClientResponseException.class,
                () -> charlie.syncConversation(conversationId, TIMEOUT));
        assertEquals(403, existing.getStatusCode().value(), "a non-participant must be forbidden");
        String responseBody = existing.getResponseBodyAsString();
        assertFalse(responseBody.contains(body), "the refusal must not leak message content");
        assertFalse(responseBody.contains(messageId), "the refusal must not leak message ids");

        String unknownConversation = TestIds.conversationId(TestIds.testId("ghostA"), TestIds.testId("ghostB"));
        WebClientResponseException unknown = assertThrows(WebClientResponseException.class,
                () -> charlie.syncConversation(unknownConversation, TIMEOUT));
        assertEquals(403, unknown.getStatusCode().value(),
                "an unknown conversation must look the same as a forbidden one");
    }

    @Test
    void messageWithForgedConversationIdIsRejectedAndDoesNotGrantAccess() {
        alice = new TestClient(TestIds.testId("forgeA"));
        bob = new TestClient(TestIds.testId("forgeB"));
        charlie = new TestClient(TestIds.testId("forgeC"));
        david = new TestClient(TestIds.testId("forgeD"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        charlie.registerAndLogin(TIMEOUT);
        david.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        charlie.connectAndAwait(TIMEOUT);
        david.connectAndAwait(TIMEOUT);

        String victimConversation = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String messageBodyOfLegitConvId = TestIds.messageBody(TestIds.shortId());
        String messageIdOfLegitConvId = alice.sendMessageTo(bob.getUsername(), messageBodyOfLegitConvId);
        Poller.pollUntil(() -> alice.findRemoteMessage(victimConversation, messageIdOfLegitConvId, TIMEOUT), TIMEOUT,
                "legitimate message " + messageIdOfLegitConvId + " to be persisted server-side");

        String messageBodyOfForgedConvId = TestIds.messageBody(TestIds.shortId());
        String messageIdOfForgedConvId = david.sendMessageWithConversationId(
                victimConversation, charlie.getUsername(), messageBodyOfForgedConvId);
        String ownConversation = TestIds.conversationId(david.getUsername(), charlie.getUsername());
        String barrierId = david.sendMessageTo(
                charlie.getUsername(), TestIds.messageBody(TestIds.shortId()));
        Poller.pollUntil(() -> charlie.findLocalMessage(ownConversation, barrierId), TIMEOUT,
                "barrier message " + barrierId + " to reach " + charlie.getUsername());

        assertTrue(charlie.findLocalMessage(victimConversation, messageIdOfForgedConvId).isEmpty(),
                "a message with a forged conversation id must not be delivered");
        assertThrows(WebClientResponseException.Forbidden.class,
                () -> david.syncConversation(victimConversation, TIMEOUT),
                "the forged message must not have made David a participant of the victim conversation");
        boolean polluted = alice.syncConversation(victimConversation, TIMEOUT).messages().stream()
                .map(InboundMessageDto::messageId)
                .anyMatch(messageIdOfForgedConvId::equals);
        assertFalse(polluted, "the victim conversation must not contain the forged message");
    }
}
