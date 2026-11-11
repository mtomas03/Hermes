package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.ConversationPort;
import it.unibo.hermes.gateway.application.service.ConversationService;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationPort conversationPort;

    /**
     * Constructs the conversation controller.
     *
     * @param conversationPort the inbound port responsible for handling conversation-related operations
     */
    public ConversationController(ConversationService conversationPort) {
        this.conversationPort = conversationPort;
    }

    /**
     * Retrieves the list of conversations for the authenticated user.
     *
     * @param userDetails the details of the authenticated user
     * @return a ResponseEntity containing the list of ConversationDto objects
     */
    @GetMapping
    public ResponseEntity<List<ConversationDto>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<ConversationDto> conversations =
                conversationPort.getUserConversations(userDetails.getUsername());

        return ResponseEntity.ok(conversations);
    }
}
