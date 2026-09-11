package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.UserDto;
import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.RestAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handles all chat-related user actions:
 * selecting a conversation, sending a message, and starting a new conversation.
 *
 * <p> This controller is the only point of coordination between the chat GUI,
 * the messaging service, local persistence and the observable state model.
 */
@Component
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ClientStateModel stateModel;
    private final MessageService messageService;
    private final LocalPersistenceService persistence;
    private final RestAuthService authService;

    public ChatController(ClientStateModel stateModel,
                          MessageService messageService,
                          LocalPersistenceService persistence,
                          RestAuthService authService) {
        this.stateModel = stateModel;
        this.messageService = messageService;
        this.persistence = persistence;
        this.authService = authService;
    }

    /**
     * Selects a conversation: updates the state model and loads local message history.
     * Called when the user clicks a conversation in the sidebar.
     */
    public void selectConversation(Conversation conv) {
        stateModel.setSelectedConversation(conv);
        List<Message> msgs = persistence.loadMessages(conv.conversationId());
        stateModel.replaceMessages(msgs);
        log.debug("Selected {} - {} local messages", conv.conversationId(), msgs.size());
    }

    /**
     * Sends a text message in the currently selected conversation.
     * The message is saved locally (PENDING) and submitted via WebSocket immediately.
     * Optimistic UI append happens here; the view observes {@code activeMessages}.
     *
     * @param content trimmed text to send.
     * @param onError called with a user-readable message if something is wrong.
     */
    public void sendMessage(String content, Consumer<String> onError) {
        Conversation conv = stateModel.getSelectedConversation();
        User self = stateModel.getCurrentUser();

        if (conv == null) {
            onError.accept("No conversation selected.");
            return;
        }
        if (self == null) {
            onError.accept("Not authenticated.");
            return;
        }
        if (content == null || content.isBlank()) return;

        Message msg = messageService.send(
                self.username(),
                conv.conversationId(),
                conv.recipientUsername().username(),
                content.trim());

        // Optimistic append: view reacts via ObservableList listener
        stateModel.appendMessage(msg);
        log.debug("Sent message {} in {}", msg.getMessageId(), conv.conversationId());
    }

    /**
     * Looks up {@code username} via the backend and opens (or re-uses)
     * a 1-to-1 conversation with that user.
     *
     * @param username the username to search for.
     * @param onError  called with a user-readable message on failure.
     */
    public void startConversationWith(String username, Consumer<String> onError) {
        AuthToken token = stateModel.getAuthToken();
        User self = stateModel.getCurrentUser();

        if (token == null || token.isValid()) {
            onError.accept("Not authenticated.");
            return;
        }
        if (self.username().equalsIgnoreCase(username)) {
            onError.accept("Cannot start a conversation with yourself.");
            return;
        }

        authService.searchUser(username, token.bearerHeader())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        dto -> openOrCreate(dto, self),
                        err -> onError.accept("User not found: " + username));
    }

    private void openOrCreate(UserDto dto, User self) {
        String conversationId = buildConversationId(self.username(), dto.username());
        User peer = new User(dto.username());

        Conversation conv = persistence.findConversation(conversationId)
                .orElseGet(() -> {
                    Conversation c = new Conversation(conversationId, peer);
                    persistence.saveConversation(c);
                    return c;
                });

        boolean exists = stateModel.getConversations().stream()
                .anyMatch(c -> c.conversationId().equals(conversationId));
        if (!exists) {
            // Prepend so newest appears at top
            stateModel.getConversations().addFirst(conv);
        }

        selectConversation(conv);
    }

    private String buildConversationId(String username1, String username2) {
        String u1 = username1.trim().toLowerCase();
        String u2 = username2.trim().toLowerCase();

        return u1.compareTo(u2) <= 0
                ? u1 + "-" + u2
                : u2 + "-" + u1;
    }
}
