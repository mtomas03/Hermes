package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.model.state.ConnectionState;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionControllerTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;

    @Mock
    private WebSocketService wsService;
    @Mock
    private ClientStateModel stateModel;
    @Mock
    private MessageService msgService;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<Consumer<InboundMessageDto>> inboundMessageCaptor;
    @Captor
    private ArgumentCaptor<Consumer<AckDto>> ackCaptor;

    private AppProperties props;
    private ConnectionController controller;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        controller = new ConnectionController(wsService, stateModel, props, msgService);
    }

    @Test
    void connectShouldMoveToConnectingStateAndDelegateToWebSocketService() {
        controller.connect("token123");

        verify(stateModel).setConnectionState(ConnectionState.CONNECTING);
        verify(wsService).connect("token123");
    }

    @Test
    void onConnectedCallbackShouldMoveToConnectedStateAndTriggerCallback() {
        Runnable postConnectAction = mock(Runnable.class);
        controller.setOnConnectedCallback(postConnectAction);
        controller.connect("token123");

        verify(wsService).setOnConnected(runnableCaptor.capture());

        // Simulate the WebSocketService reporting a successful handshake
        runnableCaptor.getValue().run();

        verify(stateModel).setConnectionState(ConnectionState.CONNECTED);
        verify(postConnectAction).run();
    }

    @Test
    void disconnectShouldStopWebSocketAndMoveToDisconnectedState() {
        controller.disconnect();

        verify(wsService).disconnect();
        verify(stateModel).setConnectionState(ConnectionState.DISCONNECTED);
    }

    @Test
    void disconnectionWithNoRetriesConfiguredShouldGoStraightToFailed() {
        controller.connect("token123");
        verify(wsService).setOnDisconnected(runnableCaptor.capture());

        runnableCaptor.getValue().run();

        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setConnectionState(ConnectionState.FAILED);
    }

    @Test
    void disconnectionWithRetriesAvailableShouldScheduleAReconnectAttempt() {
        ReflectionTestUtils.setField(props, "reconnectMaxAttempts", 3);
        ReflectionTestUtils.setField(props, "reconnectBaseDelayMs", 5L);
        ReflectionTestUtils.setField(props, "reconnectMaxDelayMs", 50L);
        controller.connect("token123");

        verify(wsService).setOnDisconnected(runnableCaptor.capture());

        runnableCaptor.getValue().run();

        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setConnectionState(ConnectionState.RECONNECTING);
        verify(wsService, timeout(ASYNC_TIMEOUT_MS).times(2)).connect("token123");
    }

    @Test
    void inboundMessageForTheOpenConversationShouldBeAppendedToActiveMessages() {
        Conversation open = new Conversation("alice-bob", new User("bob"));
        when(stateModel.getSelectedConversation()).thenReturn(open);
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 1L, "SENT");
        Message persisted = new Message(
                "m1", "alice-bob", "bob", "alice",
                "hi", 1L, MessageStatus.SENT);
        when(msgService.receiveAndPersist(dto)).thenReturn(persisted);

        controller.connect("token123");
        verify(wsService).setOnMessage(inboundMessageCaptor.capture());

        inboundMessageCaptor.getValue().accept(dto);

        verify(msgService).receiveAndPersist(dto);
        verify(stateModel).appendMessage(persisted);
    }

    @Test
    void inboundMessageForADifferentConversationShouldNotBeAppended() {
        Conversation openedElsewhere = new Conversation("conv-other", new User("carol"));
        when(stateModel.getSelectedConversation()).thenReturn(openedElsewhere);
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice", "hi", 1L, "SENT");
        when(msgService.receiveAndPersist(dto)).thenReturn(
                new Message(
                        "m1", "alice-bob", "bob", "alice",
                        "hi", 1L, MessageStatus.SENT));

        controller.connect("token123");
        verify(wsService).setOnMessage(inboundMessageCaptor.capture());

        inboundMessageCaptor.getValue().accept(dto);

        verify(stateModel, never()).appendMessage(any());
    }

    @Test
    void ackShouldBeForwardedToMessageService() {
        controller.connect("token123");
        verify(wsService).setOnAck(ackCaptor.capture());
        AckDto ack = new AckDto("m1", "DELIVERED");

        ackCaptor.getValue().accept(ack);

        verify(msgService).acknowledgeDelivery("m1");
    }
}