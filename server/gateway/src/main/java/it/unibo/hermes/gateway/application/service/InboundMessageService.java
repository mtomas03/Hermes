package it.unibo.hermes.gateway.application.service;

import edu.umd.cs.findbugs.annotations.NonNull;
import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.InboundMessagePort;
import it.unibo.hermes.gateway.application.port.secondary.MessagePublisherPort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.ConversationIds;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service that processes inbound client messages through the message backbone or the direct store fallback.
 */
@Service
public class InboundMessageService implements InboundMessagePort {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final MessagePublisherPort messagePublisher;
    private final MessageStorePort messageStore;

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${hermes.kafka.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Creates the inbound message service.
     *
     * @param messagePublisher the port publishing accepted messages to the backbone
     * @param messageStore     the port persisting messages directly when the backbone is unavailable
     */
    public InboundMessageService(MessagePublisherPort messagePublisher, MessageStorePort messageStore) {
        this.messagePublisher = messagePublisher;
        this.messageStore = messageStore;
    }

    private static void validate(SendMessageCommand command) {
        String sender = command.senderUsername();
        String recipient = getRecipient(command, sender);
        if (!ConversationIds.isCanonical(command.conversationId(), sender, recipient)) {
            log.warn("Rejected message from '{}': conversationId '{}' does not match the participants",
                    sender, command.conversationId());
            throw new InvalidMessageException("INVALID_CONVERSATION",
                    "Field 'conversationId' does not match the conversation participants");
        }
    }

    @NonNull
    private static String getRecipient(SendMessageCommand command, String sender) {
        String recipient = command.recipientUsername();

        if (recipient == null || recipient.isBlank()) {
            throw new InvalidMessageException("MISSING_RECIPIENT", "Field 'recipientUsername' is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new InvalidMessageException("MISSING_CONTENT", "Field 'content' is required");
        }
        if (sender.equals(recipient)) {
            throw new InvalidMessageException("SELF_SEND", "Cannot send a message to yourself");
        }
        return recipient;
    }

    private static Message toMessage(SendMessageCommand command, MessageStatus status) {
        return new Message(
                UUID.fromString(command.messageId()),
                command.conversationId(),
                command.senderUsername(),
                command.recipientUsername(),
                command.content(),
                command.logicalTimestamp(),
                status);
    }

    /**
     * Processes and routes an incoming chat message through the backbone or the direct store fallback.
     *
     * @param command the message received from the client
     * @return the accepted message, used to construct the acknowledgement response
     * @throws InvalidMessageException         if the message violates a business rule
     * @throws PersistenceUnavailableException if both the backbone and the fallback store are unreachable
     */
    @Override
    public Message publish(SendMessageCommand command) {
        validate(command);
        Message pending = toMessage(command, MessageStatus.PENDING);

        if (tryPublish(pending)) {
            log.debug("Message {} published to Kafka (ts={})", command.messageId(), command.logicalTimestamp());
            return pending;
        }

        log.warn("Kafka unavailable - falling back to Cassandra for message {}", command.messageId());
        Message stored = toMessage(command, MessageStatus.STORED);
        try {
            messageStore.saveFallback(stored);
            log.info("Message {} written to Cassandra across both tables via fallback path", command.messageId());
            return stored;
        } catch (Exception cassEx) {
            throw new PersistenceUnavailableException(
                    "Both Kafka and Cassandra are unavailable; message rejected", cassEx);
        }
    }

    private boolean tryPublish(Message message) {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                messagePublisher.publish(message);
                return true;
            } catch (BackboneUnavailableException e) {
                log.warn("Kafka publish attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(backoff);
                    backoff = (long) (backoff * backoffMultiplier);
                }
            }
        }
        return false;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
