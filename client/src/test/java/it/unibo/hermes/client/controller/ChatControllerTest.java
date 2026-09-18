package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.UserDto;
import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.AuthService;
import it.unibo.hermes.client.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private UserService userService;
    @Mock
    private Consumer<String> onError;
    @Mock
    private SyncController syncController;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(
                stateModel, messageService, persistence, userService, syncController);
    }

    @Test
    void selectingAConversationShouldLoadItsLocalHistoryIntoStateAndTriggerSync() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        String messageId = UUID.randomUUID().toString();
        Message msg = new Message(
                messageId, "alice-bob", "bob", "alice",
                "hi", 1L, MessageStatus.SENT);
        when(persistence.loadMessages("alice-bob")).thenReturn(List.of(msg));

        controller.selectConversation(conv);

        verify(stateModel).setSelectedConversation(conv);
        verify(stateModel).replaceMessages(List.of(msg));
        verify(syncController).syncConversationIfNeeded("alice-bob");
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
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        when(stateModel.getSelectedConversation()).thenReturn(conv);
        when(stateModel.getCurrentUser()).thenReturn(null);

        controller.sendMessage("hello", onError);

        verify(onError).accept("Not authenticated.");
        verifyNoInteractions(messageService);
    }

    @Test
    void sendingBlankMessageShouldBeSilentlyIgnored() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        when(stateModel.getSelectedConversation()).thenReturn(conv);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));

        controller.sendMessage("   ", onError);

        verifyNoInteractions(messageService);
        verifyNoInteractions(onError);
    }

    @Test
    void sendingValidMessageShouldDelegateToMessageServiceAndAppendOptimistically() {
        Conversation conv = new Conversation("alice-bob", new User("bob"));
        String messageId = UUID.randomUUID().toString();
        Message sent = new Message(
                messageId, "alice-bob", "alice", "bob",
                "hello", 1L, MessageStatus.SENT);
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
        AuthToken validToken = new AuthToken("jwt-token", Instant.now().plusSeconds(3600));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));

        controller.startConversationWith("ALICE", onError);

        verify(onError).accept("Cannot start a conversation with yourself.");
        verifyNoInteractions(userService);
    }

    @Test
    void startingConversationWithoutAuthTokenShouldReportError() {
        when(stateModel.getAuthToken()).thenReturn(null);

        controller.startConversationWith("bob", onError);

        verify(onError).accept("Not authenticated.");
        verifyNoInteractions(userService);
    }

    @Test
    void startingConversationWithNewUserShouldCreateAndSelectIt() {
        AuthToken validToken = new AuthToken("jwt-token", Instant.now().plusSeconds(3600));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        when(stateModel.getCurrentUser()).thenReturn(new User("alice"));
        when(userService.searchUser(eq("bob"), anyString()))
                .thenReturn(Mono.just(new UserDto("bob")));
        when(persistence.findConversation(anyString())).thenReturn(Optional.empty());
        when(stateModel.getConversations()).thenReturn(new ArrayList<>());
        when(persistence.loadMessages(anyString())).thenReturn(List.of());

        controller.startConversationWith("bob", onError);

        verify(persistence, timeout(2000)).saveConversation(any(Conversation.class));
        verify(stateModel, timeout(2000)).setSelectedConversation(any(Conversation.class));
        verify(syncController, timeout(2000)).syncConversationIfNeeded(anyString());
        verifyNoInteractions(onError);
    }
}
