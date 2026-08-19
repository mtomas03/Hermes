package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraMessageAdapter;
import it.unibo.hermes.gateway.adapter.KafkaPublisherAdapter;
import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Core message routing service that processes inbound client messages through Kafka or fallback storage.
 */
@Service
public class MessagePublisherService {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisherService.class);

    private final KafkaPublisherAdapter kafkaAdapter;
    private final CassandraMessageAdapter cassandraAdapter;

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${hermes.kafka.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Creates the message publisher service.
     *
     * @param kafkaAdapter     the adapter handling event publication to the Kafka messaging backbone
     * @param cassandraAdapter the adapter managing direct message persistence in Cassandra
     */
    public MessagePublisherService(KafkaPublisherAdapter kafkaAdapter,
                                   CassandraMessageAdapter cassandraAdapter) {
        this.kafkaAdapter = kafkaAdapter;
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Processes and routes an incoming WebSocket message event through Kafka or direct Cassandra fallback.
     *
     * @param inbound  the validated message frame received from the client
     * @param senderId the authenticated username of the sending client
     * @return the accepted message event used to construct the acknowledgement response
     * @throws PersistenceUnavailableException if both Kafka and Cassandra are unreachable
     */
    public MessageEvent publish(WsMessage inbound, String senderId) {
        String recipientUsername = inbound.getRecipientUsername();
        String conversationId = Message.conversationId(senderId, recipientUsername);
        Long logicalTimestamp = inbound.getLogicalTimestamp();
        String messageContent = inbound.getContent();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                messageId,
                conversationId,
                senderId,
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
            cassandraAdapter.save(new Message(
                    conversationId,
                    logicalTimestamp,
                    senderId,
                    recipientUsername,
                    messageContent,
                    MessageStatus.STORED
            ));
            log.info("Message {} written to Cassandra via fallback path", messageId);
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
                kafkaAdapter.publish(event);
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