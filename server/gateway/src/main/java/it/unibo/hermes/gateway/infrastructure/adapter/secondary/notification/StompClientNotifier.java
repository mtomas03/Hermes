package it.unibo.hermes.gateway.infrastructure.adapter.secondary.notification;

import it.unibo.hermes.gateway.application.port.secondary.ClientNotifierPort;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageFromGatewayDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SystemMessageDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter implementing {@link ClientNotifierPort}
 * by pushing STOMP frames to user queues.
 */
@Component
public class StompClientNotifier implements ClientNotifierPort {

    private static final String FORCE_RECONNECT = "FORCE_RECONNECT";

    private final SimpMessagingTemplate messagingTemplate;

    public StompClientNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void pushMessage(Message message) {
        MessageFromGatewayDto payload = new MessageFromGatewayDto(
                message.messageId().toString(),
                message.conversationId(),
                message.senderUsername(),
                message.recipientUsername(),
                message.content(),
                message.logicalTimestamp(),
                message.status().name());
        messagingTemplate.convertAndSendToUser(message.recipientUsername(), "/queue/messages", payload);
    }

    @Override
    public void requestReconnect(String username, String reason) {
        messagingTemplate.convertAndSendToUser(username, "/queue/system", new SystemMessageDto(FORCE_RECONNECT, reason));
    }
}
