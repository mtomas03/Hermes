package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.ConversationPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller providing endpoints for retrieving user conversations.
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationPort conversationPort;

    /**
     * Creates the conversation controller.
     *
     * @param conversationPort the primary port providing conversations
     */
    public ConversationController(ConversationPort conversationPort) {
        this.conversationPort = conversationPort;
    }

    /**
     * Returns the conversations of the authenticated user.
     *
     * @param userDetails the authenticated user
     * @return the user's conversations
     */
    @GetMapping
    public ResponseEntity<List<ConversationDto>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ConversationDto> conversations = conversationPort.getUserConversations(userDetails.getUsername())
                .stream()
                .map(c -> new ConversationDto(c.conversationId(), c.owner(), c.otherParticipant()))
                .toList();
        return ResponseEntity.ok(conversations);
    }
}
