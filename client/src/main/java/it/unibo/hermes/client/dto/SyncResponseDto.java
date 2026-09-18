package it.unibo.hermes.client.dto;

import java.util.List;

/**
 * Data transfer object representing the response of a synchronisation request.
 *
 * @param conversationId  the unique identifier of the conversation
 * @param messages        the list of inbound messages in the conversation
 */
public record SyncResponseDto(
        String conversationId,
        List<InboundMessageDto> messages
) {}
