package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.exception.MessagePersistenceException;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.dto.InboundMessageDto;
import it.unibo.hermes.client.model.dto.OutboundMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private WebSocketService wsService;
    @Mock
    private LocalPersistenceService persistence;

    private MessageService messageService;
    private final List<Message> fakeOutbox = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fakeOutbox.clear();
        messageService = new MessageService(wsService, persistence);

        lenient().when(persistence.saveMessage(any())).thenAnswer(inv -> {
            fakeOutbox.add(inv.getArgument(0));
            return true;
        });
        lenient().when(persistence.loadPendingOutbound(anyString()))
                .thenAnswer(inv -> new ArrayList<>(fakeOutbox));
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
    void sendingAMessageSuccessfullyDoesNotMarkItSentUntilTheServerAcceptsIt() {
        when(wsService.sendMessage(any())).thenReturn(true);

        Message result = messageService.send(
                "alice", "alice-bob",
                "bob", "hello");

        assertEquals(MessageStatus.PENDING, result.getStatus());
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void sendingAMessageWhenWebSocketUnavailableShouldStayPending() {
        when(wsService.sendMessage(any())).thenReturn(false);

        Message result = messageService.send(
                "alice", "alice-bob",
                "bob", "hello");

        assertEquals(MessageStatus.PENDING, result.getStatus());
        verify(persistence).saveMessage(any(Message.class));
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void messageIsStoredInTheOutboxBeforeItIsSubmitted() {
        when(wsService.sendMessage(any())).thenReturn(false);

        messageService.send(
                "alice", "alice-bob",
                "bob", "hello");

        InOrder order = inOrder(persistence, wsService);
        order.verify(persistence).saveMessage(any(Message.class));
        order.verify(wsService).sendMessage(any());
    }

    @Test
    void flushAfterReconnectionRetransmitsTheStoredMessageWithItsOriginalId() {
        when(wsService.sendMessage(any())).thenReturn(false, true);
        Message queued = messageService.send(
                "alice", "alice-bob",
                "bob", "hello");
        when(persistence.loadPendingOutbound("alice")).thenReturn(List.of(queued));

        int submitted = messageService.flushOutbox("alice");

        assertEquals(1, submitted);
        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(wsService, times(2))
                .sendMessage(captor.capture());
        OutboundMessageDto first = captor.getAllValues().getFirst();
        OutboundMessageDto retried = captor.getAllValues().get(1);
        assertEquals(queued.getMessageId(), retried.messageId());
        assertEquals(first, retried);
        verify(persistence, never()).updateMessageStatus(any(), any()); // only the server ACK removes it from the outbox
    }

    @Test
    void flushRetransmitsMessagesOldestFirstAndStopsAtTheFirstFailedSubmission() {
        Message m1 = createPendingMessage("m1", 1L);
        Message m2 = createPendingMessage("m2", 2L);
        Message m3 = createPendingMessage("m3", 3L);
        when(persistence.loadPendingOutbound("alice")).thenReturn(List.of(m1, m2, m3));
        when(wsService.sendMessage(any())).thenReturn(true, false);

        int submitted = messageService.flushOutbox("alice");

        assertEquals(1, submitted);
        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(wsService, times(2)).sendMessage(captor.capture());
        assertEquals(List.of("m1", "m2"), captor.getAllValues().stream()
                .map(OutboundMessageDto::messageId).toList());
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void failedFlushLeavesMessagesPendingAndALaterFlushRetransmitsThem() {
        Message m1 = createPendingMessage("m1", 1L);
        when(persistence.loadPendingOutbound("alice")).thenReturn(List.of(m1));
        when(wsService.sendMessage(any())).thenReturn(false, true);

        assertEquals(0, messageService.flushOutbox("alice"));
        assertEquals(1, messageService.flushOutbox("alice"));

        verify(wsService, times(2))
                .sendMessage(argThat(dto -> dto.messageId().equals("m1")));
    }

    @Test
    void flushWithAnEmptyOutboxSendsNothing() {
        when(persistence.loadPendingOutbound("alice")).thenReturn(List.of());

        assertEquals(0, messageService.flushOutbox("alice"));

        verifyNoInteractions(wsService);
    }

    @Test
    void messageLeavesTheOutboxOnlyWhenTheServerAcceptanceArrives() {
        messageService.acknowledgeDelivery("m1");

        verify(persistence).updateMessageStatus("m1", MessageStatus.SENT);
    }

    @Test
    void permanentlyRejectedMessageIsMarkedFailedSoItIsNotRetransmitted() {
        messageService.markRejected("m1");

        verify(persistence).updateMessageStatus("m1", MessageStatus.FAILED);
    }

    @Test
    void concurrentFlushesAreSerialisedAndNeverInterleave() throws Exception {
        Message m1 = createPendingMessage("m1", 1L);
        Message m2 = createPendingMessage("m2", 2L);
        when(persistence.loadPendingOutbound("alice"))
                .thenReturn(List.of(m1, m2))
                .thenReturn(List.of());

        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstInside = new CountDownLatch(1);
        when(wsService.sendMessage(any())).thenAnswer(invocation -> {
            OutboundMessageDto dto = invocation.getArgument(0);
            order.add(dto.messageId());
            if ("m1".equals(dto.messageId()) && order.size() == 1) {
                firstInside.countDown();
                // gives the second flush the chance to interleave
                Thread.sleep(100);
            }
            return true;
        });

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var future1 = pool.submit(() -> messageService.flushOutbox("alice"));
            firstInside.await();
            var future2 = pool.submit(() -> messageService.flushOutbox("alice"));
            assertEquals(2, future1.get(5, TimeUnit.SECONDS));
            assertEquals(0, future2.get(5, TimeUnit.SECONDS));
        }

        assertEquals(List.of("m1", "m2"), order);

        verify(persistence, times(2)).loadPendingOutbound("alice");
    }

    @Test
    void sendRacingAFlushShouldBeCoalescedAndTransmittedInOrder() throws Exception {
        Message m1 = createPendingMessage("m1", 1L);
        when(persistence.loadPendingOutbound("alice"))
                .thenReturn(List.of(m1))
                .thenAnswer(inv -> {
                    List<Message> result = new ArrayList<>();
                    result.add(m1);
                    result.addAll(fakeOutbox);
                    return result;
                })
                .thenReturn(List.of());

        CountDownLatch flushInside = new CountDownLatch(1);
        List<String> transmittedIds = Collections.synchronizedList(new ArrayList<>());
        when(wsService.sendMessage(any())).thenAnswer(invocation -> {
            OutboundMessageDto dto = invocation.getArgument(0);
            transmittedIds.add(dto.messageId());
            if ("m1".equals(dto.messageId()) && transmittedIds.size() == 1) {
                flushInside.countDown();
                Thread.sleep(150);
            }
            return true;
        });
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var flushFuture = pool.submit(() -> messageService.flushOutbox("alice"));
            flushInside.await();
            var sendFuture = pool.submit(() -> messageService
                    .send("alice", "alice-bob", "bob", "racing"));
            sendFuture.get(5, TimeUnit.SECONDS);
            flushFuture.get(5, TimeUnit.SECONDS);
        }

        assertTrue(transmittedIds.size() >= 2);
        assertEquals("m1", transmittedIds.getFirst());
        assertTrue(transmittedIds.stream().anyMatch(id -> !id.equals("m1")));
        verify(persistence, atLeast(2)).loadPendingOutbound("alice");
    }

    @Test
    void sendShouldSubmitOutboundDtoWithGivenContentAndLogicalTimestamp() {
        when(wsService.sendMessage(any())).thenReturn(true);

        messageService.send("alice", "alice-bob", "bob", "hello");

        ArgumentCaptor<OutboundMessageDto> captor = ArgumentCaptor.forClass(OutboundMessageDto.class);
        verify(wsService).sendMessage(captor.capture());
        OutboundMessageDto dto = captor.getValue();
        assertEquals("alice-bob", dto.conversationId());
        assertEquals("alice", dto.senderUsername());
        assertEquals("bob", dto.recipientUsername());
        assertEquals("hello", dto.content());
        assertEquals(1L, dto.logicalTimestamp());
    }

    @Test
    void sendingMultipleMessagesInSameConversationShouldIncrementLamportClock() {
        when(wsService.sendMessage(any())).thenReturn(true);

        Message msg1 = messageService.send(
                "alice", "alice-bob",
                "bob", "first");
        Message msg2 = messageService.send(
                "alice", "alice-bob",
                "bob", "second");

        assertEquals(1L, msg1.getLogicalTimestamp());
        assertEquals(2L, msg2.getLogicalTimestamp());
    }

    @Test
    void receivingAnInboundMessageShouldPersistItAndAdvanceLamportClock() {
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, MessageStatus.SENT.name());
        Message result = messageService.receiveAndPersist(dto);

        verify(persistence).saveMessage(result);
        when(wsService.sendMessage(any())).thenReturn(true);
        Message nextSent = messageService.send(
                "alice", "alice-bob",
                "bob", "reply");
        assertEquals(7L, nextSent.getLogicalTimestamp());
    }

    @Test
    void receivingAnInboundMessageWhenPersistenceFailsShouldThrowAndNotAdvanceClock() {
        when(persistence.saveMessage(any())).thenReturn(false);
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, MessageStatus.SENT.name());

        assertThrows(MessagePersistenceException.class, () -> messageService.receiveAndPersist(dto));
    }

    @Test
    void receivingTheSameInboundMessageTwiceShouldStillBeConsideredPersistedBothTimes() {
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, MessageStatus.SENT.name());

        Message first = messageService.receiveAndPersist(dto);
        Message second = messageService.receiveAndPersist(dto);

        assertEquals(first.getMessageId(), second.getMessageId());
        verify(persistence, times(2)).saveMessage(any());
    }

    @Test
    void clockShouldInitializeFromLocalPersistenceOnFirstUse() {
        when(persistence.getLastLogicalTimestamp("alice-bob")).thenReturn(10L);
        when(wsService.sendMessage(any())).thenReturn(true);

        Message result = messageService.send(
                "alice", "alice-bob",
                "bob", "hello");

        assertEquals(11L, result.getLogicalTimestamp());
        verify(persistence).getLastLogicalTimestamp("alice-bob");
    }

    @Test
    void acknowledgingDeliveryShouldMarkMessageSent() {
        messageService.acknowledgeDelivery("m1");

        verify(persistence).updateMessageStatus("m1", MessageStatus.SENT);
    }

    private Message createPendingMessage(String messageId, long logicalTimestamp) {
        return new Message(
                messageId, "alice-bob",
                "alice", "bob",
                "hi " + messageId,
                logicalTimestamp, MessageStatus.PENDING);
    }
}
