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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence).saveMessage(captor.capture());
        Message saved = captor.getValue();
        assertEquals("alice-bob", saved.getConversationId());
        assertEquals("alice", saved.getSenderUsername());
        assertEquals("bob", saved.getRecipientUsername());
        assertEquals("hello", saved.getContent());
        assertEquals(1L, saved.getLogicalTimestamp());
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
    void sendShouldSubmitOutboundDtoWithGivenContentAndLogicalTimestamp() {
        when(wsService.sendMessage(any())).thenReturn(true);

        messageService.send("alice", "alice-bob", "bob", "hello");

        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(wsService).sendMessage(captor.capture());
        OutboundMessageDto dto = captor.getValue();
        assertEquals("alice-bob", dto.conversationId());
        assertEquals("alice", dto.myUsername());
        assertEquals("bob", dto.recipientUsername());
        assertEquals("hello", dto.content());
        assertEquals(1L, dto.logicalTimestamp());
    }

    @Test
    void sendingMultipleMessagesInSameConversationShouldIncrementLamportClock() {
        when(wsService.sendMessage(any())).thenReturn(true);

        Message msg1 = messageService.send("alice", "alice-bob", "bob", "first");
        Message msg2 = messageService.send("alice", "alice-bob", "bob", "second");

        assertEquals(1L, msg1.getLogicalTimestamp());
        assertEquals(2L, msg2.getLogicalTimestamp());
    }

    @Test
    void receivingAnInboundMessageShouldPersistItAndAdvanceLamportClock() {
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, "SENT");
        Message result = messageService.receiveAndPersist(dto);

        verify(persistence).saveMessage(result);
        when(wsService.sendMessage(any())).thenReturn(true);
        Message nextSent = messageService.send("alice", "alice-bob", "bob", "reply");
        assertEquals(7L, nextSent.getLogicalTimestamp());
    }

    @Test
    void clockShouldInitializeFromLocalPersistenceOnFirstUse() {
        when(persistence.getLastLogicalTimestamp("alice-bob")).thenReturn(10L);
        when(wsService.sendMessage(any())).thenReturn(true);

        Message result = messageService.send("alice", "alice-bob", "bob", "hello");

        assertEquals(11L, result.getLogicalTimestamp());
        verify(persistence).getLastLogicalTimestamp("alice-bob");
    }

    @Test
    void acknowledgingDeliveryShouldMarkMessageSent() {
        messageService.acknowledgeDelivery("m1");

        verify(persistence).updateMessageStatus("m1", MessageStatus.SENT);
    }
}