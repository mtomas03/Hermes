package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.DeliverMessageCommand;
import it.unibo.hermes.gateway.application.port.secondary.ClientNotifierPort;
import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryServiceTest {

    private static final String MESSAGE_ID = UUID.randomUUID().toString();

    @Mock
    private ConnectedClientsPort connectedClients;
    @Mock
    private ClientNotifierPort clientNotifier;

    private MessageDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new MessageDeliveryService(connectedClients, clientNotifier, "gateway-1");
    }

    private DeliverMessageCommand command(String gatewayId) {
        return new DeliverMessageCommand(
                MESSAGE_ID, "alice-bob",
                "alice", "bob", gatewayId,
                "hi", 5L);
    }

    @Test
    void shouldPushDeliveredMessageToConnectedRecipientOnThisGateway() {
        when(connectedClients.isConnected("bob")).thenReturn(true);

        service.deliver(command("gateway-1"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(clientNotifier).pushMessage(captor.capture());
        Message pushed = captor.getValue();
        assertThat(pushed.messageId()).isEqualTo(UUID.fromString(MESSAGE_ID));
        assertThat(pushed.conversationId()).isEqualTo("alice-bob");
        assertThat(pushed.senderUsername()).isEqualTo("alice");
        assertThat(pushed.recipientUsername()).isEqualTo("bob");
        assertThat(pushed.content()).isEqualTo("hi");
        assertThat(pushed.logicalTimestamp()).isEqualTo(5L);
        assertThat(pushed.status()).isEqualTo(MessageStatus.DELIVERED);
    }

    @Test
    void shouldSkipMessagesIntendedForAnotherGateway() {
        service.deliver(command("gateway-2"));

        verifyNoInteractions(connectedClients, clientNotifier);
    }

    @Test
    void shouldNotPushWhenRecipientIsNotConnected() {
        when(connectedClients.isConnected("bob")).thenReturn(false);

        service.deliver(command("gateway-1"));

        verifyNoInteractions(clientNotifier);
    }

    @Test
    void shouldSwallowPushFailures() {
        when(connectedClients.isConnected("bob")).thenReturn(true);
        doThrow(new RuntimeException("broken pipe")).when(clientNotifier).pushMessage(any(Message.class));

        service.deliver(command("gateway-1"));

        verify(clientNotifier).pushMessage(any(Message.class));
    }

    @Test
    void shouldRejectNonUuidMessageIdAsPoisonRecord() {
        when(connectedClients.isConnected("bob")).thenReturn(true);
        DeliverMessageCommand bad = new DeliverMessageCommand(
                "nope", "alice-bob",
                "alice", "bob", "gateway-1",
                "hi", 5L);

        assertThatThrownBy(() -> service.deliver(bad)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(clientNotifier);
    }
}
