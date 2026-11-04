package it.unibo.hermes.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private LocalPersistenceService persistence;
    @Mock
    private MessageService messageService;
    @Mock
    private WebSocketService webSocketService;

    private AppProperties props;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        ReflectionTestUtils.setField(props, "conversationsPath", "/api/v1/conversations");
        ReflectionTestUtils.setField(props, "syncPath", "/api/v1/messages/sync");
    }

    private WebClient stubClient(String jsonBody) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body(jsonBody)
                                .build()))
                .build();
    }

    @Test
    void applySyncWithNoMessagesShouldNotTouchPersistenceOrClock() {
        SyncService service = new SyncService(WebClient.create(), props, persistence, messageService, webSocketService);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of());

        service.applySync(response, "alice");

        verifyNoInteractions(persistence);
        verifyNoInteractions(messageService);
    }

    @Test
    void applySyncShouldPersistEachSyncedMessageAndSyncLamportClock() {
        SyncService service = new SyncService(WebClient.create(), props, persistence, messageService, webSocketService);
        InboundMessageDto inbound1 = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, MessageStatus.ACKNOWLEDGED.name());
        InboundMessageDto inbound2 = new InboundMessageDto(
                "m2", "alice-bob", "bob", "alice",
                "how are you?", 8L, MessageStatus.ACKNOWLEDGED.name());
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(inbound1, inbound2));

        service.applySync(response, "alice");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence, times(2)).saveMessage(captor.capture());
        assertEquals("m1", captor.getAllValues().get(0).getMessageId());
        assertEquals(MessageStatus.ACKNOWLEDGED, captor.getAllValues().get(0).getStatus());
        assertEquals("m2", captor.getAllValues().get(1).getMessageId());
        assertEquals(MessageStatus.ACKNOWLEDGED, captor.getAllValues().get(1).getStatus());
        verify(messageService).syncConversationClock("alice-bob", 5L);
        verify(messageService).syncConversationClock("alice-bob", 8L);
    }

    @Test
    void applySyncAcknowledgesRecipientMessageAndUpdatesLocalStatusAfterPersistence() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "alice", MessageStatus.STORED.name()))), "alice");

        InOrder order = inOrder(persistence, webSocketService);
        order.verify(persistence).saveMessage(any(Message.class));
        order.verify(webSocketService).sendAck("m1");
        order.verify(persistence).updateMessageStatus("m1", MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void applySyncDoesNotAcknowledgeWhenLocalPersistenceFails() {
        SyncService service = serviceWithPersistenceResult(false);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "alice", MessageStatus.STORED.name()))), "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncPreservesRemoteStatusForOwnMessagesWithoutForcingSent() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "alice", "bob", MessageStatus.ACKNOWLEDGED.name()))), "alice");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence).saveMessage(captor.capture());
        assertEquals(MessageStatus.ACKNOWLEDGED, captor.getValue().getStatus());
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncDoesNotUpdateStatusWhenMessageIsAlreadyAcknowledged() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "alice", MessageStatus.ACKNOWLEDGED.name()))), "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncDoesNotAcknowledgeMessagesSentByTheLocalUser() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "alice", "bob", MessageStatus.STORED.name()))), "alice");

        verify(persistence).saveMessage(any(Message.class));
        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncDoesNotResendAcknowledgementsTheServerAlreadyRecorded() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob",
                List.of(createInboundMessage("m1", "bob", "alice", MessageStatus.ACKNOWLEDGED.name()))), "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncDoesNotAcknowledgeWhenTheLocalUserIsUnknown() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "alice", MessageStatus.STORED.name()))), null);

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatus(any(), any());
    }

    @Test
    void applySyncMatchesTheRecipientIgnoringCase() {
        SyncService service = serviceWithPersistenceResult(true);

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "Alice", MessageStatus.STORED.name()))), "alice");

        verify(webSocketService).sendAck("m1");
        verify(persistence).updateMessageStatus("m1", MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void repeatedSyncReAcknowledgesMessagesTheServerHasNotConfirmedYet() {
        SyncService service = serviceWithPersistenceResult(true);
        SyncResponseDto response = new SyncResponseDto("alice-bob",
                List.of(
                        createInboundMessage("m1", "bob", "alice", MessageStatus.STORED.name()),
                        createInboundMessage("m2", "bob", "alice", MessageStatus.ACKNOWLEDGED.name())));

        service.applySync(response, "alice");
        service.applySync(response, "alice");

        verify(webSocketService, times(2)).sendAck("m1");
        verify(webSocketService, never()).sendAck("m2");
        verify(persistence, times(4)).saveMessage(any(Message.class));
        verify(persistence, times(2)).updateMessageStatus("m1", MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void fetchConversationsShouldReturnListFromServer() throws Exception {
        ConversationDto dto = new ConversationDto("alice-bob", "alice", "bob");
        WebClient client = stubClient(mapper.writeValueAsString(new ConversationDto[]{dto}));
        SyncService service = new SyncService(client, props, persistence, messageService, webSocketService);

        StepVerifier.create(service.fetchConversations("Bearer t"))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void syncConversationShouldReturnServerResponse() throws Exception {
        SyncResponseDto dto = new SyncResponseDto("alice-bob", List.of());
        WebClient client = stubClient(mapper.writeValueAsString(dto));
        SyncService service = new SyncService(client, props, persistence, messageService, webSocketService);

        StepVerifier.create(service.syncConversation("alice-bob", "Bearer t"))
                .assertNext(resp -> assertEquals("alice-bob", resp.conversationId()))
                .verifyComplete();
    }

    private static InboundMessageDto createInboundMessage(
            String id, String sender, String recipient, String messageStatus) {
        return new InboundMessageDto(
                id, "alice-bob",
                sender, recipient,
                "hi" + id,
                5L, messageStatus);
    }

    private SyncService serviceWithPersistenceResult(boolean persisted) {
        when(persistence.saveMessage(any(Message.class))).thenReturn(persisted);
        return new SyncService(WebClient.create(), props, persistence, messageService, webSocketService);
    }
}
