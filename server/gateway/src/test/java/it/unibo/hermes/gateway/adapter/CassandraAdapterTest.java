package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.entity.MessageByConversation;
import it.unibo.hermes.gateway.entity.MessageById;
import it.unibo.hermes.gateway.repository.cassandra.MessageByConversationRepository;
import it.unibo.hermes.gateway.repository.cassandra.MessageByIdRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CassandraAdapterTest {

    @Mock
    private MessageByConversationRepository messageByConversationRepository;

    @Mock
    private MessageByIdRepository messageByIdRepository;

    @InjectMocks
    private CassandraAdapter cassandraAdapter;

    @Test
    void shouldSaveToBothRepositories() {
        UUID messageId = UUID.randomUUID();
        MessageByConversation fallbackMessage = new MessageByConversation(
                messageId,
                "alice-bob",
                "alice",
                "bob",
                "Fallback content",
                1L,
                MessageStatus.STORED
        );

        cassandraAdapter.saveFallback(fallbackMessage);

        verify(messageByConversationRepository).save(fallbackMessage);
        ArgumentCaptor<MessageById> byIdCaptor = ArgumentCaptor.forClass(MessageById.class);
        verify(messageByIdRepository).save(byIdCaptor.capture());
        MessageById savedById = byIdCaptor.getValue();
        assertThat(savedById.getMessageId()).isEqualTo(messageId);
        assertThat(savedById.getConversationId()).isEqualTo("alice-bob");
        assertThat(savedById.getSenderUsername()).isEqualTo("alice");
        assertThat(savedById.getRecipientUsername()).isEqualTo("bob");
        assertThat(savedById.getContent()).isEqualTo("Fallback content");
        assertThat(savedById.getLogicalTimestamp()).isEqualTo(1L);
        assertThat(savedById.getDeliveryStatus()).isEqualTo(MessageStatus.STORED.name());
    }

    @Test
    void shouldReturnMessagesAfterTimestamp() {
        String conversationId = "alice-bob";
        long lastTimestamp = 0L;
        List<MessageByConversation> expected = List.of(
                new MessageByConversation(
                        UUID.randomUUID(), conversationId, "alice", "bob",
                        "hi", 1L, MessageStatus.STORED),
                new MessageByConversation(
                        UUID.randomUUID(), conversationId, "bob", "alice",
                        "hello", 2L, MessageStatus.STORED)
        );

        when(messageByConversationRepository.findMessagesAfter(conversationId, lastTimestamp))
                .thenReturn(expected);
        List<MessageByConversation> result = cassandraAdapter.findMessageAfter(conversationId, lastTimestamp);

        assertThat(result).isEqualTo(expected).hasSize(2);
        verify(messageByConversationRepository).findMessagesAfter(conversationId, lastTimestamp);
    }

    @Test
    void shouldReturnAllMessagesForConversation() {
        String conversationId = "alice-bob";
        List<MessageByConversation> expected = List.of(
                new MessageByConversation(
                        UUID.randomUUID(), conversationId, "alice", "bob",
                        "hi", 1L, MessageStatus.STORED)
        );

        when(messageByConversationRepository.findAllByConversationId(conversationId))
                .thenReturn(expected);
        List<MessageByConversation> result = cassandraAdapter.findAllMessages(conversationId);

        assertThat(result).isEqualTo(expected).hasSize(1);
        verify(messageByConversationRepository).findAllByConversationId(conversationId);
    }
}
