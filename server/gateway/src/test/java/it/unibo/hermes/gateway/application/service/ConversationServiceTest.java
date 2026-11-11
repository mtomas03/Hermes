package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.cassandra.ConversationByUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private CassandraAdapter cassandraAdapter;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(cassandraAdapter);
    }

    @Test
    void shouldReturnMappedConversationDtosForUser() {
        ConversationByUser conv1 = mock(ConversationByUser.class);
        when(conv1.getConversationId()).thenReturn("alice-bob");
        when(conv1.getOtherParticipant()).thenReturn("bob");

        ConversationByUser conv2 = mock(ConversationByUser.class);
        when(conv2.getConversationId()).thenReturn("alice-charlie");
        when(conv2.getOtherParticipant()).thenReturn("charlie");

        when(cassandraAdapter.findConversationsByUsername("alice"))
                .thenReturn(List.of(conv1, conv2));

        List<ConversationDto> result = conversationService.getUserConversations("alice");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new ConversationDto(
                "alice-bob", "alice", "bob"));
        assertThat(result.get(1)).isEqualTo(new ConversationDto(
                "alice-charlie", "alice", "charlie"));
        verify(cassandraAdapter).findConversationsByUsername("alice");
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoConversations() {
        when(cassandraAdapter.findConversationsByUsername("alice"))
                .thenReturn(List.of());

        List<ConversationDto> result = conversationService.getUserConversations("alice");

        assertThat(result).isEmpty();
        verify(cassandraAdapter).findConversationsByUsername("alice");
    }
}
