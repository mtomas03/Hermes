package it.unibo.hermes.client.model.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.infrastructure.config.AppProperties;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.dto.ConversationDto;
import it.unibo.hermes.client.model.dto.InboundMessageDto;
import it.unibo.hermes.client.model.dto.SyncResponseDto;
import it.unibo.hermes.client.model.repository.PersistenceException;
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

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    private static InboundMessageDto createInboundMessage(
            String id, String sender, String recipient, String messageStatus) {
        return new InboundMessageDto(
                id, "alice-bob",
                sender, recipient,
                "hi" + id,
                5L, messageStatus);
    }

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

    private SyncService createSyncService() {
        return new SyncService(WebClient.create(), props, persistence, messageService, webSocketService);
    }

    @Test
    void applySyncWithNoMessagesShouldNotTouchPersistenceOrClock() {
        SyncService service = createSyncService();
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of());

        service.applySync(response, "alice");

        verifyNoInteractions(persistence);
        verifyNoInteractions(messageService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void applySyncShouldPersistSyncedMessagesInBatchAndSyncLamportClock() {
        SyncService service = createSyncService();
        InboundMessageDto inbound1 = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, MessageStatus.ACKNOWLEDGED.name());
        InboundMessageDto inbound2 = new InboundMessageDto(
                "m2", "alice-bob", "bob", "alice",
                "hi", 8L, MessageStatus.ACKNOWLEDGED.name());
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(inbound1, inbound2));

        service.applySync(response, "alice");

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistence).saveMessagesBatch(captor.capture());
        List<Message> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("m1", saved.get(0).getMessageId());
        assertEquals(MessageStatus.ACKNOWLEDGED, saved.get(0).getStatus());
        assertEquals("m2", saved.get(1).getMessageId());
        assertEquals(MessageStatus.ACKNOWLEDGED, saved.get(1).getStatus());
        verify(messageService).syncConversationClock("alice-bob", 5L);
        verify(messageService).syncConversationClock("alice-bob", 8L);
    }

    @Test
    void applySyncAcknowledgesRecipientMessageAndUpdatesLocalStatusAfterPersistence() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "bob", "alice",
                        MessageStatus.STORED.name()))), "alice");

        InOrder order = inOrder(persistence, webSocketService);
        order.verify(persistence).saveMessagesBatch(anyList());
        order.verify(webSocketService).sendAck("m1");
        order.verify(persistence).updateMessageStatusesBatch(
                List.of("m1"), MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void applySyncBatchesMultipleAcknowledgementsInSinglePersistenceCall() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "bob",
                        "alice", MessageStatus.STORED.name()),
                createInboundMessage(
                        "m2", "bob",
                        "alice", MessageStatus.SENT.name()))),
                "alice");

        InOrder order = inOrder(persistence, webSocketService);
        order.verify(persistence).saveMessagesBatch(anyList());
        order.verify(webSocketService).sendAck("m1");
        order.verify(webSocketService).sendAck("m2");
        order.verify(persistence).updateMessageStatusesBatch(
                List.of("m1", "m2"), MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void applySyncDoesNotAcknowledgeAndPropagatesErrorWhenLocalPersistenceFails() {
        SyncService service = createSyncService();
        doThrow(new PersistenceException("DB write failed", new SQLException()))
                .when(persistence).saveMessagesBatch(anyList());

        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(
                createInboundMessage("m1", "bob", "alice", MessageStatus.STORED.name())));

        assertThrows(PersistenceException.class, () -> service.applySync(response, "alice"));

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applySyncPreservesRemoteStatusForOwnMessagesWithoutForcingSent() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "alice",
                        "bob", MessageStatus.ACKNOWLEDGED.name()))),
                "alice");

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistence).saveMessagesBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(MessageStatus.ACKNOWLEDGED, captor.getValue().getFirst().getStatus());
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    void applySyncDoesNotUpdateStatusWhenMessageIsAlreadyAcknowledged() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "bob",
                        "alice", MessageStatus.ACKNOWLEDGED.name()))),
                "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    void applySyncDoesNotAcknowledgeMessagesSentByTheLocalUser() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "alice",
                        "bob", MessageStatus.STORED.name()))),
                "alice");

        verify(persistence).saveMessagesBatch(anyList());
        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    void applySyncDoesNotResendAcknowledgementsTheServerAlreadyRecorded() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob",
                List.of(createInboundMessage(
                        "m1", "bob",
                        "alice", MessageStatus.ACKNOWLEDGED.name()))),
                "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    void applySyncDoesNotAcknowledgeWhenTheLocalUserIsUnknown() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "bob",
                        "alice", MessageStatus.STORED.name()))),
                null);

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }

    @Test
    void applySyncMatchesTheRecipientIgnoringCase() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "bob",
                        "Alice", MessageStatus.STORED.name()))),
                "alice");

        verify(webSocketService).sendAck("m1");
        verify(persistence).updateMessageStatusesBatch(List.of("m1"), MessageStatus.ACKNOWLEDGED);
    }

    @Test
    void repeatedSyncReAcknowledgesMessagesTheServerHasNotConfirmedYet() {
        SyncService service = createSyncService();
        SyncResponseDto response = new SyncResponseDto("alice-bob",
                List.of(createInboundMessage("m1", "bob",
                                "alice", MessageStatus.STORED.name()),
                        createInboundMessage("m2", "bob",
                                "alice", MessageStatus.ACKNOWLEDGED.name())));

        service.applySync(response, "alice");
        service.applySync(response, "alice");

        verify(webSocketService, times(2)).sendAck("m1");
        verify(webSocketService, never()).sendAck("m2");
        verify(persistence, times(2)).saveMessagesBatch(anyList());
        verify(persistence, times(2)).updateMessageStatusesBatch(
                List.of("m1"), MessageStatus.ACKNOWLEDGED);
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

    @Test
    @SuppressWarnings("unchecked")
    void applySyncMapsDeliveringAndDeliveredExplicitlyInsteadOfStored() {
        SyncService service = createSyncService();
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(
                createInboundMessage(
                        "m1", "alice", "bob", "DELIVERING"),
                createInboundMessage(
                        "m2", "alice", "bob", "DELIVERED")));

        service.applySync(response, "alice");

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistence).saveMessagesBatch(captor.capture());
        assertEquals(MessageStatus.DELIVERING, captor.getValue().get(0).getStatus());
        assertEquals(MessageStatus.DELIVERED, captor.getValue().get(1).getStatus());
    }

    @Test
    void applySyncDoesNotReAcknowledgeMessagesAlreadyDeliveredOnServer() {
        SyncService service = createSyncService();

        service.applySync(new SyncResponseDto(
                        "alice-bob",
                        List.of(createInboundMessage(
                                "m1", "bob", "alice", "DELIVERED"))),
                "alice");

        verifyNoInteractions(webSocketService);
        verify(persistence, never()).updateMessageStatusesBatch(any(), any());
    }
}
