package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraAdapter;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.producer.MessageCreatedProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service that processes inbound client messages through Kafka or Cassandra.
 */
@Service
public class InboundMessageService {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final MessageCreatedProducer messageCreatedProducer;
    private final CassandraAdapter cassandraAdapter;

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${hermes.kafka.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Creates the message publisher service.
     *
     * @param messageCreatedProducer the producer handling message creation event publication to Kafka
     * @param cassandraAdapter       the adapter managing direct fallback persistence in Cassandra
     */
    public InboundMessageService(MessageCreatedProducer messageCreatedProducer,
                                 CassandraAdapter cassandraAdapter) {
        this.messageCreatedProducer = messageCreatedProducer;
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Processes and routes an incoming WebSocket message event through Kafka or direct Cassandra fallback.
     *
     * @param inbound        the validated message received from the client
     * @param senderUsername the authenticated username of the sender
     * @return the accepted message event used to construct the acknowledgement response
     * @throws PersistenceUnavailableException if both Kafka and Cassandra are unreachable
     */
    public MessageEvent publish(WsMessage inbound, String senderUsername) {
        String recipientUsername = inbound.getRecipientUsername();
        String conversationId = inbound.getConversationId();
        Long logicalTimestamp = inbound.getLogicalTimestamp();
        String messageContent = inbound.getContent();
        UUID messageId = UUID.fromString(inbound.getMessageId());

        MessageEvent event = new MessageEvent(
                messageId,
                conversationId,
                senderUsername,
                recipientUsername,
                messageContent,
                logicalTimestamp
        );

        if (tryPublishToKafka(event)) {
            log.debug("Message {} published to Kafka (ts={})", messageId, logicalTimestamp);
            return event;
        }

        log.warn("Kafka unavailable - falling back to Cassandra for message {}", messageId);
        try {
            cassandraAdapter.saveFallback(new MessageByConversation(
                    messageId,
                    conversationId,
                    senderUsername,
                    recipientUsername,
                    messageContent,
                    logicalTimestamp,
                    MessageStatus.STORED
            ));
            log.info("Message {} written to Cassandra across both tables via fallback path", messageId);
            return event;
        } catch (Exception cassEx) {
            throw new PersistenceUnavailableException(
                    "Both Kafka and Cassandra are unavailable; message rejected", cassEx);
        }
    }

    /**
     * Attempts to publish a message event to Kafka applying exponential backoff retry delays.
     *
     * @param event the message event to publish
     * @return {@code true} if the event was published successfully, {@code false} if all attempts failed
     */
    private boolean tryPublishToKafka(MessageEvent event) {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                messageCreatedProducer.publish(event);
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

    /**
     * Pauses the current thread execution for the specified duration during retry backoff periods.
     *
     * @param ms the duration to sleep in milliseconds
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
