package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.SyncResponseDto;
import it.unibo.hermes.gateway.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

    private UserDetails user(String username) {
        return User.withUsername(username)
                .password("x")
                .roles("USER")
                .build();
    }

    @Test
    void shouldReturnSyncResponseUsingAuthenticatedUsername() {
        SyncResponseDto expected = new SyncResponseDto("alice-bob", List.of());
        when(syncService.syncConversation("alice", "alice-bob"))
                .thenReturn(expected);

        ResponseEntity<SyncResponseDto> response = controller.sync("alice-bob", user("alice"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(syncService).syncConversation("alice", "alice-bob");
    }

    @Test
    void shouldPropagateAccessDeniedForNonParticipant() {
        when(syncService.syncConversation("carol", "alice-bob"))
                .thenThrow(new AccessDeniedException("User is not a participant of this conversation"));

        assertThatThrownBy(() -> controller.sync("alice-bob", user("carol")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not a participant");
    }
}
