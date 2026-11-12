package it.unibo.hermes.gateway.infrastructure.adapter.secondary.notification;

import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageFromGatewayDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SystemMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StompClientNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StompClientNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new StompClientNotifier(messagingTemplate);
    }

    @Test
    void shouldPushMessageToRecipientQueueWithWireFormatPreserved() {
        UUID messageId = UUID.randomUUID();

        notifier.pushMessage(new Message(
                messageId, "alice-bob",
                "alice", "bob",
                "hi", 3L, MessageStatus.DELIVERED));

        verify(messagingTemplate).convertAndSendToUser("bob", "/queue/messages",
                new MessageFromGatewayDto(
                        messageId.toString(), "alice-bob",
                        "alice", "bob",
                        "hi", 3L, "DELIVERED"));
    }

    @Test
    void shouldSendForceReconnectOnSystemQueue() {
        notifier.requestReconnect("alice", "BACKBONE_RECOVERED");

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/system",
                new SystemMessageDto("FORCE_RECONNECT", "BACKBONE_RECOVERED"));
    }
}
