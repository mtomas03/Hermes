package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.SyncResponse;
import it.unibo.hermes.gateway.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private SyncService syncService;

    private SyncController controller;

    @BeforeEach
    void setUp() {
        controller = new SyncController(syncService);
    }

    private UserDetails principal(String username) {
        return User.withUsername(username).password("x").roles("USER").build();
    }

    @Test
    void shouldReturnSyncResponseUsingAuthenticatedUsername() {
        SyncResponse expected = new SyncResponse("alice-bob", List.of());
        when(syncService.syncMissing("alice", "alice-bob", 5L))
                .thenReturn(expected);

        ResponseEntity<SyncResponse> response = controller.sync(
                "alice-bob", 5L, principal("alice"));

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void shouldDefaultAfterCursorToFullHistoryWhenNotProvided() {
        SyncResponse expected = new SyncResponse("alice-bob", List.of());
        when(syncService.syncMissing("alice", "alice-bob", -1L))
                .thenReturn(expected);

        ResponseEntity<SyncResponse> response = controller.sync(
                "alice-bob", -1L, principal("alice"));

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void shouldPropagateAccessDeniedForNonParticipant() {
        when(syncService.syncMissing("carol", "alice-bob", -1L))
                .thenThrow(new AccessDeniedException("not a participant"));

        assertThatThrownBy(() -> controller.sync(
                "alice-bob", -1L, principal("carol")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
