package it.unibo.hermes.gateway.dto;

import java.util.List;

/**
 * Data transfer object representing the response of a synchronisation request.
 *
 * @param conversationId the unique identifier of the conversation
 * @param messages       the list of chat messages retrieved from the database
 */
public record SyncResponseDto(
        String conversationId,
        List<MessageDto> messages
) {
    /**
     * Data transfer object representing a chat message
     * retrieved from the database during synchronisation.
     *
     * @param messageId         the unique identifier of the message
     * @param conversationId    the unique identifier of the conversation
     * @param senderUsername    the username of the sending participant
     * @param recipientUsername the username of the receiving participant
     * @param content           the content of the message
     * @param logicalTimestamp  the Lamport clock timestamp for causal message ordering
     * @param messageStatus     the current lifecycle status of the message
     */
    public record MessageDto(
            String messageId,
            String conversationId,
            String senderUsername,
            String recipientUsername,
            String content,
            Long logicalTimestamp,
            String messageStatus
    ) {}
}
