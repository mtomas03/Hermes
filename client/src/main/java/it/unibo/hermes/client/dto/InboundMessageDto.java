package it.unibo.hermes.client.dto;

/**
 * Data transfer object representing an inbound message received by the client.
 *
 * @param messageId          the unique identifier of the message
 * @param conversationId     the unique identifier of the conversation
 * @param senderUsername     the username of the sender
 * @param recipientUsername  the username of the recipient
 * @param content            the content of the message
 * @param logicalTimestamp   the Lamport logical timestamp of the message
 * @param messageStatus      the current status of the message
 */
public record InboundMessageDto(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp,
        String messageStatus
) {}
