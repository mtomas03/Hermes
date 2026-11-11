package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.SyncPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SyncResponseDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SyncResponseDto.MessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller providing endpoints for conversation synchronisation.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncPort syncPort;

    /**
     * Creates the synchronisation controller.
     *
     * @param syncPort the primary port providing conversation history
     */
    public SyncController(SyncPort syncPort) {
        this.syncPort = syncPort;
    }

    /**
     * Returns the full message history of a conversation to one of its participants.
     *
     * @param conversationId the conversation to synchronise
     * @param user           the authenticated user
     * @return the conversation history
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<SyncResponseDto> sync(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserDetails user) {
        List<MessageDto> messages = syncPort.syncConversation(user.getUsername(), conversationId).stream()
                .map(m -> new MessageDto(
                        m.messageId().toString(),
                        m.conversationId(),
                        m.senderUsername(),
                        m.recipientUsername(),
                        m.content(),
                        m.logicalTimestamp(),
                        m.status().name()))
                .toList();
        return ResponseEntity.ok(new SyncResponseDto(conversationId, messages));
    }
}
