package it.unibo.hermes.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void applySyncWithNoMessagesShouldNotTouchPersistenceMessages() {
        SyncService service = new SyncService(WebClient.create(), props, persistence, messageService);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of());

        service.applySync(response);

        verifyNoInteractions(persistence);
        verifyNoInteractions(messageService);
    }

    @Test
    void applySyncShouldPersistEachSyncedMessageAndSyncLamportClock() {
        SyncService service = new SyncService(WebClient.create(), props, persistence, messageService);
        InboundMessageDto inbound1 = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice",
                "hi", 5L, "SENT");
        InboundMessageDto inbound2 = new InboundMessageDto(
                "m2", "alice-bob", "bob", "alice",
                "how are you?", 8L, "SENT");
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(inbound1, inbound2));

        service.applySync(response);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence, times(2)).saveMessage(captor.capture());
        assertEquals("m1", captor.getAllValues().get(0).getMessageId());
        assertEquals("m2", captor.getAllValues().get(1).getMessageId());

        verify(messageService).syncConversationClock("alice-bob", 8L);
    }

    /*@Test
    void applySyncShouldSaveCursorWhenCursorMessageIdIsPresent() {
        SyncService service = new SyncService(WebClient.create(), props, persistence, messageService);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of());

        service.applySync(response);

        ArgumentCaptor<SyncCursor> captor = ArgumentCaptor.forClass(SyncCursor.class);
        verify(persistence).saveCursor(captor.capture());
        assertEquals("alice-bob", captor.getValue().getConversationId());
        assertEquals("m2", captor.getValue().getLastSyncedMessageId());
    }*/

    @Test
    void fetchConversationsShouldReturnListFromServer() throws Exception {
        ConversationDto dto = new ConversationDto("alice-bob", "alice", "bob");
        WebClient client = stubClient(mapper.writeValueAsString(new ConversationDto[]{dto}));
        SyncService service = new SyncService(client, props, persistence, messageService);

        StepVerifier.create(service.fetchConversations("Bearer t"))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void syncConversationShouldReturnServerResponse() throws Exception {
        SyncResponseDto dto = new SyncResponseDto("alice-bob", List.of());
        WebClient client = stubClient(mapper.writeValueAsString(dto));
        SyncService service = new SyncService(client, props, persistence, messageService);

        StepVerifier.create(service.syncConversation("alice-bob", null, "Bearer t"))
                .assertNext(resp -> assertEquals("alice-bob", resp.conversationId()))
                .verifyComplete();
    }
}
