package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.domain.model.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationStorePort conversationStore;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationStore);
    }

    @Test
    void shouldReturnConversationsOfUser() {
        List<Conversation> stored = List.of(
                new Conversation("alice-bob", "alice", "bob"),
                new Conversation("alice-charlie", "alice", "charlie"));
        when(conversationStore.findByUsername("alice")).thenReturn(stored);

        List<Conversation> result = conversationService.getUserConversations("alice");

        assertThat(result).containsExactlyElementsOf(stored);
        verify(conversationStore).findByUsername("alice");
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoConversations() {
        when(conversationStore.findByUsername("alice")).thenReturn(List.of());

        assertThat(conversationService.getUserConversations("alice")).isEmpty();
        verify(conversationStore).findByUsername("alice");
    }
}
