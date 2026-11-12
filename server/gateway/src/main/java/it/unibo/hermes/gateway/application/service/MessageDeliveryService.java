package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.DeliverMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.MessageDeliveryPort;
import it.unibo.hermes.gateway.application.port.secondary.ClientNotifierPort;
import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service delivering messages to recipients connected to this Gateway instance.
 */
@Service
public class MessageDeliveryService implements MessageDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryService.class);

    private final ConnectedClientsPort connectedClients;
    private final ClientNotifierPort clientNotifier;
    private final String currentGatewayId;

    /**
     * Creates the delivery service.
     *
     * @param connectedClients the port exposing the clients connected to this instance
     * @param clientNotifier   the port pushing messages to clients
     * @param currentGatewayId the identifier of this Gateway instance
     */
    public MessageDeliveryService(
            ConnectedClientsPort connectedClients,
            ClientNotifierPort clientNotifier,
            @Value("${hermes.gateway.instance-id:gateway-1}") String currentGatewayId) {
        this.connectedClients = connectedClients;
        this.clientNotifier = clientNotifier;
        this.currentGatewayId = currentGatewayId;
    }

    @Override
    public void deliver(DeliverMessageCommand command) {
        if (!currentGatewayId.equals(command.targetGatewayId())) {
            log.trace("Skipping message {} intended for gateway {}", command.messageId(), command.targetGatewayId());
            return;
        }

        if (!connectedClients.isConnected(command.recipientUsername())) {
            log.warn("Recipient {} not connected on gateway {} - client may have disconnected",
                    command.recipientUsername(), currentGatewayId);
            return;
        }

        log.info("Delivering message {} via STOMP to recipient {}", command.messageId(), command.recipientUsername());

        Message message = new Message(
                UUID.fromString(command.messageId()),
                command.conversationId(),
                command.senderUsername(),
                command.recipientUsername(),
                command.content(),
                command.logicalTimestamp(),
                MessageStatus.DELIVERED);

        try {
            clientNotifier.pushMessage(message);
            log.debug("Message {} pushed successfully to user {}", command.messageId(), command.recipientUsername());
        } catch (Exception e) {
            log.error("Failed to push message {} to recipient {}: {}",
                    command.messageId(), command.recipientUsername(), e.getMessage());
        }
    }
}
