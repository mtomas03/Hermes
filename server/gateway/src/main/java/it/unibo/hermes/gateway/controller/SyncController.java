package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.SyncResponseDto;
import it.unibo.hermes.gateway.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for full conversation synchronisation.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    /**
     * Constructs the synchronisation controller with the required synchronisation service.
     *
     * @param syncService the service executing message synchronisation logic
     */
    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Returns the complete message history of the requested conversation
     * for the authenticated participant.
     *
     * @param conversationId    the ID of the conversation to synchronise
     * @param user              the authenticated user making the request
     * @return a ResponseEntity containing the SyncResponseDto with the full message history
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<SyncResponseDto> sync(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                syncService.syncConversation(user.getUsername(), conversationId));
    }
}
