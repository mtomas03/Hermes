package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.ConversationPort;
import it.unibo.hermes.gateway.domain.model.Conversation;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private ConversationPort conversationService;

    private ConversationController controller;

    @BeforeEach
    void setUp() {
        controller = new ConversationController(conversationService);
    }

    private UserDetails user() {
        return User.withUsername("alice")
                .password("x")
                .roles("USER")
                .build();
    }

    @Test
    void getConversationsShouldReturnUserConversations() {
        ConversationDto dto1 = new ConversationDto(
                "alice-bob", "alice", "bob");
        ConversationDto dto2 = new ConversationDto(
                "alice-charlie", "alice", "charlie");
        when(conversationService.getUserConversations("alice")).thenReturn(List.of(
                new Conversation("alice-bob", "alice", "bob"),
                new Conversation("alice-charlie", "alice", "charlie")));

        ResponseEntity<List<ConversationDto>> response = controller.getConversations(user());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(dto1, dto2);
        verify(conversationService).getUserConversations("alice");
    }

    @Test
    void getConversationsShouldReturnEmptyListWhenNoConversationsExist() {
        when(conversationService.getUserConversations("alice"))
                .thenReturn(List.of());

        ResponseEntity<List<ConversationDto>> response = controller.getConversations(user());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(conversationService).getUserConversations("alice");
    }
}
