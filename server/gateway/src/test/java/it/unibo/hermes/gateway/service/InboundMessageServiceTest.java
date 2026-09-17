package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraAdapter;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.WsMessage;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.producer.MessageCreatedProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboundMessageServiceTest {

    @Mock
    private MessageCreatedProducer messageCreatedProducer;

    @Mock
    private CassandraAdapter cassandraAdapter;

    private InboundMessageService service;

    @BeforeEach
    void setUp() {
        service = new InboundMessageService(messageCreatedProducer, cassandraAdapter);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        ReflectionTestUtils.setField(service, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(service, "backoffMultiplier", 1.0);
    }

    private WsMessage createInboundMessage() {
        WsMessage msg = new WsMessage();
        msg.setRecipientUsername("bob");
        msg.setConversationId("alice-bob");
        msg.setContent("Hello Bob!");
        msg.setLogicalTimestamp(1L);
        msg.setMessageId(UUID.randomUUID().toString());
        return msg;
    }

    @Test
    void shouldPublishToKafkaOnFirstAttempt() {
        WsMessage inbound = createInboundMessage();
        doNothing().when(messageCreatedProducer).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inbound, "alice");

        assertThat(event).isNotNull();
        assertThat(event.senderUsername()).isEqualTo("alice");
        assertThat(event.recipientUsername()).isEqualTo("bob");
        assertThat(event.content()).isEqualTo("Hello Bob!");
        verify(messageCreatedProducer, times(1)).publish(any(MessageEvent.class));
        verifyNoInteractions(cassandraAdapter);
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        WsMessage inbound = createInboundMessage();
        doThrow(new BackboneUnavailableException("Kafka glitch", new RuntimeException()))
                .doNothing()
                .when(messageCreatedProducer).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inbound, "alice");

        assertThat(event).isNotNull();
        verify(messageCreatedProducer, times(2)).publish(any(MessageEvent.class));
        verifyNoInteractions(cassandraAdapter);
    }

    @Test
    void shouldFallBackToCassandraWhenKafkaFailsAllAttempts() {
        WsMessage inbound = createInboundMessage();
        doThrow(new BackboneUnavailableException("Kafka down", new RuntimeException()))
                .when(messageCreatedProducer).publish(any(MessageEvent.class));

        MessageEvent event = service.publish(inbound, "alice");

        assertThat(event).isNotNull();
        verify(messageCreatedProducer, times(2)).publish(any(MessageEvent.class));
        ArgumentCaptor<MessageByConversation> captor = ArgumentCaptor.forClass(MessageByConversation.class);
        verify(cassandraAdapter).saveFallback(captor.capture());
        MessageByConversation fallbackEntity = captor.getValue();
        assertThat(fallbackEntity.getSenderUsername()).isEqualTo("alice");
        assertThat(fallbackEntity.getRecipientUsername()).isEqualTo("bob");
        assertThat(fallbackEntity.getDeliveryStatus()).isEqualTo(MessageStatus.STORED);
    }

    @Test
    void shouldRejectMessageWhenBothKafkaAndCassandraAreUnavailable() {
        WsMessage inbound = createInboundMessage();
        doThrow(new BackboneUnavailableException("Kafka down", new RuntimeException()))
                .when(messageCreatedProducer).publish(any(MessageEvent.class));
        doThrow(new RuntimeException("Cassandra unreachable"))
                .when(cassandraAdapter).saveFallback(any(MessageByConversation.class));

        assertThatThrownBy(() -> service.publish(inbound, "alice"))
                .isInstanceOf(PersistenceUnavailableException.class)
                .hasMessageContaining("Both Kafka and Cassandra are unavailable");

        verify(messageCreatedProducer, times(2)).publish(any(MessageEvent.class));
        verify(cassandraAdapter, times(1)).saveFallback(any(MessageByConversation.class));
    }
}