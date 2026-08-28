package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.SyncResponse;
import it.unibo.hermes.gateway.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller handling pull-based message synchronisation requests.
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
     * Retrieves missed conversation messages for the authenticated user since the specified logical timestamp.
     *
     * @param conversationId the unique canonical identifier for the conversation
     * @param after          the last known logical timestamp held by the client, or -1 to retrieve complete conversation history
     * @param user           the security details of the authenticated requesting participant
     * @return a response entity containing the synchronisation payload with missing messages
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<SyncResponse> sync(
            @PathVariable String conversationId,
            @RequestParam(name = "after", defaultValue = "-1") long after,
            @AuthenticationPrincipal UserDetails user) {

        SyncResponse response = syncService.syncMissing(
                user.getUsername(), conversationId, after);
        return ResponseEntity.ok(response);
    }
}