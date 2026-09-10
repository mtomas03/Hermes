package it.unibo.hermes.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.SyncCursor;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private LocalPersistenceService persistence;

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
        SyncService service = new SyncService(WebClient.create(), props, persistence);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(), null);

        service.applySync(response);

        verifyNoInteractions(persistence);
    }

    @Test
    void applySyncShouldPersistEachSyncedMessage() {
        SyncService service = new SyncService(WebClient.create(), props, persistence);
        InboundMessageDto inbound = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice", "hi", 5L, Instant.now(), "SENT");
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(inbound), null);

        service.applySync(response);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(persistence).saveMessage(captor.capture());
        assertEquals("m1", captor.getValue().getMessageId());
    }

    @Test
    void applySyncShouldAdvanceCursorWhenProvided() {
        SyncService service = new SyncService(WebClient.create(), props, persistence);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(), "m-latest");

        service.applySync(response);

        ArgumentCaptor<SyncCursor> captor = ArgumentCaptor.forClass(SyncCursor.class);
        verify(persistence).saveCursor(captor.capture());
        assertEquals("m-latest", captor.getValue().getLastSyncedMessageId());
    }

    @Test
    void applySyncShouldNotAdvanceCursorWhenAbsent() {
        SyncService service = new SyncService(WebClient.create(), props, persistence);
        SyncResponseDto response = new SyncResponseDto("alice-bob", List.of(), null);

        service.applySync(response);

        verifyNoInteractions(persistence);
    }

    @Test
    void fetchConversationsShouldReturnListFromServer() throws Exception {
        ConversationDto dto = new ConversationDto("alice-bob", "alice", "bob", "m1", Instant.now());
        WebClient client = stubClient(mapper.writeValueAsString(new ConversationDto[]{dto}));
        SyncService service = new SyncService(client, props, persistence);

        StepVerifier.create(service.fetchConversations("Bearer t"))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void syncConversationShouldReturnServerResponse() throws Exception {
        SyncResponseDto dto = new SyncResponseDto("alice-bob", List.of(), "m1");
        WebClient client = stubClient(mapper.writeValueAsString(dto));
        SyncService service = new SyncService(client, props, persistence);

        StepVerifier.create(service.syncConversation("alice-bob", null, "Bearer t"))
                .assertNext(resp -> assertEquals("alice-bob", resp.conversationId()))
                .verifyComplete();
    }
}
