package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.InboundMessagePort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageToGatewayDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer.MessageProducer;
import it.unibo.hermes.gateway.infrastructure.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service that processes inbound client messages through Kafka or Cassandra.
 */
@Service
public class InboundMessageService implements InboundMessagePort {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final MessageProducer messageProducer;
    private final CassandraAdapter cassandraAdapter;

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${hermes.kafka.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Creates the inbound message service.
     *
     * @param messageProducer  the producer handling message creation event publication to Kafka
     * @param cassandraAdapter the adapter managing direct fallback persistence in Cassandra
     */
    public InboundMessageService(MessageProducer messageProducer,
                                 CassandraAdapter cassandraAdapter) {
        this.messageProducer = messageProducer;
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Processes and routes an incoming STOMP chat message through Kafka or direct Cassandra fallback.
     *
     * @param inbound        the validated message received from the client
     * @param senderUsername the authenticated username of the sender
     * @return the accepted message event used to construct the acknowledgement response
     * @throws PersistenceUnavailableException if both Kafka and Cassandra are unreachable
     */
    @Override
    public MessageEvent publish(MessageToGatewayDto inbound, String senderUsername) {
        String recipientUsername = inbound.recipientUsername();
        String conversationId = inbound.conversationId();
        Long logicalTimestamp = inbound.logicalTimestamp();
        String messageContent = inbound.content();
        UUID messageId = UUID.fromString(inbound.messageId());

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

    private boolean tryPublishToKafka(MessageEvent event) {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                messageProducer.publish(event);
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
