package it.unibo.hermes.gateway.application.service;

import edu.umd.cs.findbugs.annotations.NonNull;
import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.InboundMessagePort;
import it.unibo.hermes.gateway.application.port.secondary.MessagePublisherPort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.exception.InvalidUsernameException;
import it.unibo.hermes.gateway.domain.model.ConversationIds;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.domain.model.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service that processes inbound client messages through the message backbone or the direct store fallback.
 */
@Service
public class InboundMessageService implements InboundMessagePort {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);
    private static final int KNOWN_USERNAMES_CAPACITY = 10_000;

    private final MessagePublisherPort messagePublisher;
    private final MessageStorePort messageStore;
    private final UserAccountPort userAccounts;
    private final Map<String, Boolean> knownUsernames = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > KNOWN_USERNAMES_CAPACITY;
                }
            });

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
     * @param userAccounts     the port used to verify that the recipient is a registered user
     */
    public InboundMessageService(
            MessagePublisherPort messagePublisher,
            MessageStorePort messageStore,
            UserAccountPort userAccounts) {
        this.messagePublisher = messagePublisher;
        this.messageStore = messageStore;
        this.userAccounts = userAccounts;
    }

    private SendMessageCommand validate(SendMessageCommand command) {
        String sender = command.senderUsername();

        if (command.recipientUsername() == null || command.recipientUsername().isBlank()) {
            throw new InvalidMessageException(
                    "MISSING_RECIPIENT",
                    "Field 'recipientUsername' is required");
        }
        if (command.content() == null || command.content().isBlank()) {
            throw new InvalidMessageException(
                    "MISSING_CONTENT",
                    "Field 'content' is required");
        }
        String recipient;
        try {
            recipient = Username.normalise(command.recipientUsername()).value();
        } catch (InvalidUsernameException e) {
            throw new InvalidMessageException(
                    "INVALID_RECIPIENT",
                    "Field 'recipientUsername' " + Username.FORMAT_DESCRIPTION);
        }
        if (sender.trim().equalsIgnoreCase(recipient)) {
            throw new InvalidMessageException(
                    "SELF_SEND", "Cannot send a message to yourself");
        }

        String derivedConversationId = ConversationIds.canonical(sender, recipient);
        if (!derivedConversationId.equals(command.conversationId())) {
            log.warn("Rejected message from '{}': conversationId '{}' does not match the participants",
                    sender, command.conversationId());
            throw new InvalidMessageException("INVALID_CONVERSATION",
                    "Field 'conversationId' does not match the conversation participants");
        }
        if (!recipientExists(recipient)) {
            throw new InvalidMessageException(
                    "UNKNOWN_RECIPIENT", "Recipient '" + recipient + "' does not exist");
        }
        return new SendMessageCommand(
                command.messageId(), derivedConversationId, sender, recipient,
                command.content(), command.logicalTimestamp());
    }

    private boolean recipientExists(String recipient) {
        if (knownUsernames.containsKey(recipient)) {
            return true;
        }
        try {
            boolean exists = userAccounts.existsByUsername(recipient);
            if (exists) {
                knownUsernames.put(recipient, Boolean.TRUE);
            }
            return exists;
        } catch (Exception e) {
            log.warn("Cannot verify recipient existence, accepting the message: {}", e.getClass().getSimpleName());
            return true;
        }
    }

    private Message toMessage(SendMessageCommand command, MessageStatus status) {
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
        SendMessageCommand validated = validate(command);
        Message pending = toMessage(validated, MessageStatus.PENDING);

        if (tryPublish(pending)) {
            log.debug("Message {} published to Kafka (ts={})", command.messageId(), command.logicalTimestamp());
            return pending;
        }

        log.warn("Kafka unavailable - falling back to Cassandra for message {}", command.messageId());
        Message stored = toMessage(validated, MessageStatus.STORED);
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
