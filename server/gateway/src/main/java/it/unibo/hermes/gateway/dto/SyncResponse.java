package it.unibo.hermes.gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object representing the response payload containing missing chat messages for a specific conversation.
 *
 * <p> This record shows the chronological sequence of messages retrieved from the persistent message database that
 * the requesting client has not received, relative to the logical timestamp of the last message of which the client is aware.
 *
 * @param conversationId the identifier of the conversation being synchronised
 * @param messages       the sorted list of messages that the user has not yet retrieved
 */
public record SyncResponse(
        String conversationId,
        List<MessageDto> messages
) {
    /**
     * Data transfer object representing a permanently saved chat message retrieved from the database during synchronisation.
     *
     * @param messageId         the unique server-assigned identifier of the message
     * @param senderUsername    the username of the sending participant
     * @param recipientUsername the username of the receiving participant
     * @param content           the textual body of the message
     * @param logicalTimestamp  the Lamport clock value preserving causal message order
     * @param physicalTimestamp the physical timestamp recording when the message was created
     * @param status            the current lifecycle status of the message
     */
    public record MessageDto(
            String messageId,
            String senderUsername,
            String recipientUsername,
            String content,
            long logicalTimestamp,
            Instant physicalTimestamp,
            String status
    ) {
    }
}