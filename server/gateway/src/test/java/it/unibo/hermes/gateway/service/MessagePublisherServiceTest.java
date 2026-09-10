package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraMessageAdapter;
import it.unibo.hermes.gateway.adapter.KafkaPublisherAdapter;
import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePublisherServiceTest {

    @Mock
    private KafkaPublisherAdapter kafkaAdapter;
    @Mock
    private CassandraMessageAdapter cassandraAdapter;

    private MessagePublisherService service;

    @BeforeEach
    void setUp() {
        service = new MessagePublisherService(kafkaAdapter, cassandraAdapter);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(service, "backoffMultiplier", 1.0);
    }

    private WsMessage inboundMessage() {
        WsMessage msg = new WsMessage();
        msg.setRecipientUsername("bob");
        msg.setConversationId("alice-bob");
        msg.setContent("hello");
        msg.setPhysicalTimestamp(Instant.now());
        msg.setLogicalTimestamp(1L);
        msg.setMessageId(UUID.randomUUID().toString());
        return msg;
    }

    @Test
    void shouldPublishToKafkaOnFirstAttempt() {
        doNothing().when(kafkaAdapter).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inboundMessage(), "alice");

        assertThat(event.getSenderUsername()).isEqualTo("alice");
        assertThat(event.getRecipientUsername()).isEqualTo("bob");
        verify(kafkaAdapter, times(1)).publish(any(MessageEvent.class));
        verifyNoInteractions(cassandraAdapter);
    }

    @Test
    void shouldFallBackToCassandraWhenKafkaFailsAllAttempts() {
        doThrow(new BackboneUnavailableException("down", new RuntimeException()))
                .when(kafkaAdapter).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inboundMessage(), "alice");

        assertThat(event).isNotNull();
        verify(kafkaAdapter, times(2)).publish(any(MessageEvent.class)); // maxAttempts = 2
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(cassandraAdapter).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MessageStatus.STORED);
    }

    @Test
    void shouldRejectMessageWhenBothKafkaAndCassandraAreUnavailable() {
        doThrow(new BackboneUnavailableException("down", new RuntimeException()))
                .when(kafkaAdapter).publish(any(MessageEvent.class));
        doThrow(new RuntimeException("cassandra down")).when(cassandraAdapter).save(any(Message.class));

        assertThatThrownBy(() -> service.publish(inboundMessage(), "alice"))
                .isInstanceOf(PersistenceUnavailableException.class);
    }

    @Test
    void conversationIdShouldBeDerivedFromSenderAndRecipient() {
        doNothing().when(kafkaAdapter).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inboundMessage(), "alice");

        assertThat(event.getConversationId()).isEqualTo(Message.conversationId("alice", "bob"));
    }
}
