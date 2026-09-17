package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.ConversationDto;
import it.unibo.hermes.gateway.service.ConversationService;
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

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationDto>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<ConversationDto> conversations =
                conversationService.getUserConversations(userDetails.getUsername());

        return ResponseEntity.ok(conversations);
    }
}