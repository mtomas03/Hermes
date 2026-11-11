package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.SyncPort;
import it.unibo.hermes.gateway.domain.exception.ConversationAccessDeniedException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SyncResponseDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private SyncPort syncService;

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
                .thenReturn(List.of());

        ResponseEntity<SyncResponseDto> response = controller.sync("alice-bob", user("alice"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(syncService).syncConversation("alice", "alice-bob");
    }

    @Test
    void shouldPropagateAccessDeniedForNonParticipant() {
        when(syncService.syncConversation("charlie", "alice-bob"))
                .thenThrow(new ConversationAccessDeniedException("User is not a participant of this conversation"));

        assertThatThrownBy(() -> controller.sync("alice-bob", user("charlie")))
                .isInstanceOf(ConversationAccessDeniedException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void shouldMapDomainMessagesIntoResponseDto() {
        UUID messageId = UUID.randomUUID();
        when(syncService.syncConversation("alice", "alice-bob")).thenReturn(List.of(
                new Message(messageId, "alice-bob",
                        "alice", "bob",
                        "hi", 2L, MessageStatus.STORED)));

        SyncResponseDto response = controller.sync("alice-bob", user("alice")).getBody();

        assertNotNull(response);
        assertThat(response.conversationId()).isEqualTo("alice-bob");
        assertThat(response.messages()).hasSize(1);
        SyncResponseDto.MessageDto dto = response.messages().getFirst();
        assertThat(dto.messageId()).isEqualTo(messageId.toString());
        assertThat(dto.conversationId()).isEqualTo("alice-bob");
        assertThat(dto.senderUsername()).isEqualTo("alice");
        assertThat(dto.recipientUsername()).isEqualTo("bob");
        assertThat(dto.content()).isEqualTo("hi");
        assertThat(dto.logicalTimestamp()).isEqualTo(2L);
        assertThat(dto.messageStatus()).isEqualTo("STORED");
    }
}
