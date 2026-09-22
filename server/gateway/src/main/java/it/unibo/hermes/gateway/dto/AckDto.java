package it.unibo.hermes.gateway.dto;

/**
 * Data Transfer Object representing an ACK pushed by the Gateway to the sender's STOMP
 * subscription at {@code /user/queue/acks}, confirming that a previously sent message has been
 * published to Kafka or written via the Cassandra fallback path.
 *
 * @param messageId the unique identifier of the acknowledged message
 * @param status    the acknowledgement status
 */
public record AckDto(
        String messageId,
        String status
) {}
