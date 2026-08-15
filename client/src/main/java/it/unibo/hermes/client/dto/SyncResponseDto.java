package it.unibo.hermes.client.dto;

import java.util.List;

public record SyncResponseDto(
        String conversationId,
        List<InboundMessageDto> messages,
        String cursorMessageId) {
}
