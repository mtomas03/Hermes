package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.RestAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ClientStateModel stateModel;
    @Mock
    private MessageService messageService;
    @Mock
    private LocalPersistenceService persistence;
    @Mock
    private RestAuthService authService;
    @Mock
    private Consumer<String> onError;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(stateModel, messageService, persistence, authService);
    }

    @Test
    void selectingAConversationShouldLoadItsLocalHistoryIntoState() {
        Conversation conv = new Conversation("alice-bob", new User("bob"), Instant.now());
        Message msg = new Message("m1", "alice-bob", "bob", "alice", "hi", 1L, Instant.now(), MessageStatus.SENT);
        when(persistence.loadMessages("alice-bob")).thenReturn(List.of(msg));

        controller.selectConversation(conv);

        verify(stateModel).setSelectedConversation(conv);
        verify(stateModel).replaceMessages(List.of(msg));
    }

    @Test
    void sendingMessageWithoutSelectedConversationShouldReportError() {
        when(stateModel.getSelectedConversation()).thenReturn(null);

        controller.sendMessage("hello", onError);

        verify(onError).accept("No conversation selected.");
        verifyNoInteractions(messageService);
    }

    @Test
    void sendingMessageWithoutAuthenticatedUserShouldReportError() {
        Conversation conv = new Conversation("alice-bob", new User("bob"), Instant.now());
        when(stateModel.getSelectedConversation()).thenReturn(conv);
        when(stateModel.getCurrentUser()).thenReturn(null);

        controller.sendMessage("hello", onError);

        verify(onError).accept("Not authenticated.");
        verifyNoInteractions(messageService);
    }

    @Test
    void sendingBlankMessageShouldBeSilentlyIgnored() {
        Conversation conv = new Conversation("alice-bob", new User("bob"), Instant.now());
        when(stateModel.getSelectedConversation()).thenReturn(conv);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));

        controller.sendMessage("   ", onError);

        verifyNoInteractions(messageService);
        verifyNoInteractions(onError);
    }

    @Test
    void sendingValidMessageShouldDelegateToMessageServiceAndAppendOptimistically() {
        Conversation conv = new Conversation("alice-bob", new User("bob"), Instant.now());
        Message sent = new Message("m1", "alice-bob", "alice", "bob", "hello", 1L, Instant.now(), MessageStatus.SENT);
        when(stateModel.getSelectedConversation()).thenReturn(conv);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));
        when(messageService.send("alice", "alice-bob", "bob", "hello")).thenReturn(sent);

        controller.sendMessage("  hello  ", onError);

        // Content is trimmed before being handed to the service
        verify(messageService).send("alice", "alice-bob", "bob", "hello");
        verify(stateModel).appendMessage(sent);
        verifyNoInteractions(onError);
    }

    @Test
    void startingConversationWithSelfShouldBeRejected() {
        AuthToken expiredToken =
                new AuthToken("t", Instant.now().minusSeconds(60));
        when(stateModel.getAuthToken()).thenReturn(expiredToken);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));

        controller.startConversationWith("ALICE", onError);

        verify(onError).accept("Cannot start a conversation with yourself.");
        verifyNoInteractions(authService);
    }

    @Test
    void startingConversationWithoutAuthTokenShouldReportError() {
        when(stateModel.getAuthToken()).thenReturn(null);

        controller.startConversationWith("bob", onError);

        verify(onError).accept("Not authenticated.");
        verifyNoInteractions(authService);
    }

    @Test
    void startingConversationWithNewUserShouldCreateAndSelectIt() {
        AuthToken expiredToken =
                new AuthToken("t", Instant.now().minusSeconds(60));
        when(stateModel.getAuthToken()).thenReturn(expiredToken);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));
        when(authService.searchUser(eq("bob"), anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new it.unibo.hermes.client.dto.UserDto("bob")));
        when(persistence.findConversation(anyString())).thenReturn(java.util.Optional.empty());
        when(stateModel.getConversations()).thenReturn(new java.util.ArrayList<>());
        when(persistence.loadMessages(anyString())).thenReturn(List.of());

        controller.startConversationWith("bob", onError);

        // The search runs on a background scheduler
        verify(persistence, timeout(2000)).saveConversation(any(Conversation.class));
        verify(stateModel, timeout(2000)).setSelectedConversation(any(Conversation.class));
        verifyNoInteractions(onError);
    }
}
