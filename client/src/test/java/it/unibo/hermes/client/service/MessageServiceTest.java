package it.unibo.hermes.client.service;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private WebSocketService wsService;
    @Mock
    private LocalPersistenceService persistence;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(wsService, persistence);
    }

    @Test
    void sendingAMessageShouldSaveItLocallyBeforeTransmission() {
        when(wsService.sendMessage(any())).thenReturn(true);

        messageService.send("alice", "alice-bob", "bob", "hello");

        // Saved locally before/alongside the WS send, in PENDING at insert time
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence).saveMessage(captor.capture());
        Message saved = captor.getValue();
        assertEquals("alice-bob", saved.getConversationId());
        assertEquals("alice", saved.getSenderUsername());
        assertEquals("bob", saved.getRecipientUsername());
        assertEquals("hello", saved.getContent());
    }

    @Test
    void sendingAMessageSuccessfullyShouldMarkItSent() {
        when(wsService.sendMessage(any())).thenReturn(true);

        Message result = messageService.send("alice", "alice-bob", "bob", "hello");

        assertEquals(MessageStatus.SENT, result.getStatus());
        verify(persistence).updateMessageStatus(eq(result.getMessageId()), eq(MessageStatus.SENT));
    }

    @Test
    void sendingAMessageWhenWebSocketUnavailableShouldStayPending() {
        when(wsService.sendMessage(any())).thenReturn(false);

        Message result = messageService.send("alice", "alice-bob", "bob", "hello");

        assertEquals(MessageStatus.PENDING, result.getStatus());
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void sendShouldSubmitOutboundDtoWithGivenContent() {
        when(wsService.sendMessage(any())).thenReturn(true);

        messageService.send("alice", "alice-bob", "bob", "hello");

        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(wsService).sendMessage(captor.capture());
        OutboundMessageDto dto = captor.getValue();
        assertEquals("alice-bob", dto.conversationId());
        assertEquals("alice", dto.myUsername());
        assertEquals("bob", dto.recipientUsername());
        assertEquals("hello", dto.content());
    }

    @Test
    void receivingAnInboundMessageShouldPersistItAsSent() {
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice", "hi there", 3L, Instant.now(), "SENT");

        Message result = messageService.receiveAndPersist(dto);

        assertEquals("m1", result.getMessageId());
        assertEquals(MessageStatus.SENT, result.getStatus());
        verify(persistence).saveMessage(result);
    }

    @Test
    void acknowledgingDeliveryShouldMarkMessageSent() {
        messageService.acknowledgeDelivery("m1");

        verify(persistence).updateMessageStatus("m1", MessageStatus.SENT);
    }
}
