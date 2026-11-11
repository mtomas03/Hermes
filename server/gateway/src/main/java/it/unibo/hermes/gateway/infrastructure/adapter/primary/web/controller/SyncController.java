package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.SyncPort;
import it.unibo.hermes.gateway.application.service.SyncService;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SyncResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for full conversation synchronisation.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncPort syncPort;

    /**
     * Constructs the synchronisation controller.
     *
     * @param syncPort the inbound port executing message synchronisation logic
     */
    public SyncController(SyncService syncPort) {
        this.syncPort = syncPort;
    }

    /**
     * Returns the complete message history of the requested conversation
     * for the authenticated participant.
     *
     * @param conversationId the ID of the conversation to synchronise
     * @param user           the authenticated user making the request
     * @return a ResponseEntity containing the SyncResponseDto with the full message history
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<SyncResponseDto> sync(
            @PathVariable String conversationId,
            @AuthenticationPrincipal UserDetails user) {

        return ResponseEntity.ok(
                syncPort.syncConversation(user.getUsername(), conversationId));
    }
}
