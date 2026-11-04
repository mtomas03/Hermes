package it.unibo.hermes.e2e.support;

import it.unibo.hermes.client.config.AppConfig;
import it.unibo.hermes.client.controller.ConnectionController;
import it.unibo.hermes.client.dto.AuthResponseDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.model.state.ConnectionState;
import it.unibo.hermes.client.service.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hermes client without the GUI.
 */
public final class TestClient implements AutoCloseable {

    private final AnnotationConfigApplicationContext context;
    private final AuthService authService;
    private final MessageService messageService;
    private final LocalPersistenceService persistenceService;
    private final SyncService syncService;
    private final ConnectionController connectionController;
    private final WebSocketService webSocketService;
    private final Path dbFile;
    private final AtomicInteger disconnections = new AtomicInteger();

    private final String username;
    private final String password;
    private volatile String token;

    public TestClient(String username) {
        this(username, TestIds.testPassword());
    }

    public TestClient(String username, String password) {
        this.username = username;
        this.password = password;
        this.dbFile = allocateTempDbFile(username);

        E2EConfig config = E2EConfig.get();
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("hermes.server.base-url", config.baseUrl());
        overrides.put("hermes.server.ws-url", config.wsUrl());
        overrides.put("hermes.client.db-path", dbFile.toAbsolutePath().toString());
        overrides.put("hermes.client.heartbeat-interval-ms", "2000");

        this.context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("e2e-overrides", overrides));
        context.register(AppConfig.class);
        context.refresh();

        this.authService = context.getBean(AuthService.class);
        this.messageService = context.getBean(MessageService.class);
        this.persistenceService = context.getBean(LocalPersistenceService.class);
        this.syncService = context.getBean(SyncService.class);
        this.connectionController = context.getBean(ConnectionController.class);
        this.webSocketService = context.getBean(WebSocketService.class);
        context.getBean(ClientStateModel.class).addConnectionStateListener(event -> {
            if (event.getNewValue() == ConnectionState.DISCONNECTED) {
                disconnections.incrementAndGet();
            }
        });
    }

    /**
     * Extracts the {@code message_id} of each message in a sync response.
     *
     * @param messages the messages returned by a sync
     * @return the message ids in the same order
     */
    public static List<String> ids(List<InboundMessageDto> messages) {
        return messages.stream()
                .map(InboundMessageDto::messageId)
                .toList();
    }

    /**
     * Registers this client's account on the Gateway via REST.
     *
     * @param timeout bounded wait for the registration call to complete
     */
    public void register(Duration timeout) {
        authService.register(username, password).block(timeout);
    }

    /**
     * Logs in via REST and stores the returned JWT for subsequent STOMP/REST calls.
     *
     * @param timeout bounded wait for the login call to complete
     */
    public void login(Duration timeout) {
        AuthResponseDto response = authService.login(username, password).block(timeout);
        if (response == null || response.token() == null) {
            throw new IllegalStateException("Login returned no token for user " + username);
        }
        this.token = response.token();
        context.getBean(ClientStateModel.class)
                .setCurrentUser(new User(username));
    }

    /**
     * Registers and logs in this client, each with the given timeout.
     *
     * @param timeout bounded wait applied to each of the two calls
     */
    public void registerAndLogin(Duration timeout) {
        register(timeout);
        login(timeout);
    }

    /**
     * Opens the real STOMP connection and blocks until the Gateway
     * has actually accepted it or the timeout elapses.
     *
     * @param timeout bounded wait for the CONNECTED frame
     */
    public void connectAndAwait(Duration timeout) {
        if (token == null) {
            throw new IllegalStateException("Must login before connect for user " + username);
        }
        CompletableFuture<Void> connected = new CompletableFuture<>();
        connectionController.setOnConnectedCallback(() -> connected.complete(null));
        connectionController.connect(token);
        try {
            connected.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new AssertionError(
                    "STOMP CONNECT did not complete for user " + username
                            + " within " + timeout, e);
        }
    }

    /**
     * Gracefully closes the STOMP session,
     * without tearing down the Spring context.
     */
    public void disconnect() {
        connectionController.disconnect();
    }

    /**
     * Checks whether the STOMP session is currently connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return webSocketService.isConnected();
    }

    /**
     * Counts how many times this client's connection state became {@code DISCONNECTED}.
     *
     * @return the number of disconnections
     */
    public int disconnectionCount() {
        return disconnections.get();
    }

    /**
     * Counts the messages of a conversation with the given id in this client's local state.
     *
     * @param conversationId the conversation
     * @param messageId      the message id
     * @return how many local rows carry that id (a correct history has at most one)
     */
    public long countLocalCopies(String conversationId, String messageId) {
        return localMessages(conversationId).stream().filter(m -> m.getMessageId().equals(messageId)).count();
    }

    /**
     * Sends a message to the given recipient through the STOMP {@code /app/chat.sendMessage}
     * path, first ensuring the local conversation row exists.
     *
     * @param recipientUsername the recipient's username
     * @param content           the message body
     * @return the messageId of this message
     */
    public String sendMessageTo(String recipientUsername, String content) {
        String conversationId = TestIds.conversationId(username, recipientUsername);
        ensureLocalConversation(conversationId, recipientUsername);
        Message sent = messageService.send(username, conversationId, recipientUsername, content);
        return sent.getMessageId();
    }

    /**
     * Sends a message whose conversation id is chosen by the caller,
     * instead of being derived from the two participants.
     *
     * @param conversationId    the conversation id to use
     * @param recipientUsername the recipient's username
     * @param content           the message content
     * @return the messageId of this message
     */
    public String sendMessageWithConversationId(
            String conversationId, String recipientUsername, String content) {
        ensureLocalConversation(conversationId, recipientUsername);
        return messageService.send(
                username, conversationId, recipientUsername, content).getMessageId();
    }

    /**
     * Re-submits an already sent message with the same {@code message_id}.
     *
     * @param conversationId the conversation id of the message
     * @param messageId      the message id to re-submit
     */
    public void resubmitMessage(String conversationId, String messageId) {
        Message original = findLocalMessage(conversationId, messageId)
                .orElseThrow(() -> new IllegalStateException("No local message " + messageId));
        boolean sent = webSocketService.sendMessage(new OutboundMessageDto(
                original.getMessageId(), original.getConversationId(), original.getSenderUsername(),
                original.getRecipientUsername(), original.getContent(), original.getLogicalTimestamp()));
        if (!sent) {
            throw new IllegalStateException("Cannot resubmit " + messageId + ": not connected");
        }
    }

    /**
     * Sends a delivery ACK for a message through the real STOMP session.
     *
     * @param messageId the message id to acknowledge
     */
    public void sendAck(String messageId) {
        webSocketService.sendAck(messageId);
    }

    /**
     * Ensures a local conversation row exists for the given id, creating it if absent.
     *
     * @param conversationId the identifier of the conversation
     * @param peerUsername   the other participant's username
     */
    public void ensureLocalConversation(String conversationId, String peerUsername) {
        if (persistenceService.findConversation(conversationId).isEmpty()) {
            persistenceService.saveConversation(new Conversation(conversationId, new User(peerUsername)));
        }
    }

    /**
     * Reads this client's locally persisted (SQLite) messages for a conversation.
     *
     * @param conversationId the conversation to read
     * @return the locally persisted messages for that conversation
     */
    public List<Message> localMessages(String conversationId) {
        return persistenceService.loadMessages(conversationId);
    }

    /**
     * Looks up a single locally persisted message by id.
     *
     * @param conversationId the conversation the message belongs to
     * @param messageId      the message id to look for
     * @return the message, if it has been locally persisted
     */
    public Optional<Message> findLocalMessage(String conversationId, String messageId) {
        return localMessages(conversationId).stream()
                .filter(m -> m.getMessageId().equals(messageId))
                .findFirst();
    }

    /**
     * Calls the full sync REST endpoint for a conversation.
     *
     * @param conversationId the conversation to sync
     * @param timeout        bounded wait for the REST call
     * @return the raw sync response as returned by the Gateway
     */
    public SyncResponseDto syncConversation(String conversationId, Duration timeout) {
        if (token == null) {
            throw new IllegalStateException("Must login before syncConversation() for user " + username);
        }
        SyncResponseDto response = syncService.syncConversation(conversationId, bearerHeader()).block(timeout);
        if (response == null) {
            throw new IllegalStateException("Sync returned no response for conversation " + conversationId);
        }
        return response;
    }

    /**
     * Gets this client's token formatted as an {@code Authorization} header value.
     *
     * @return this client's token
     */
    public String bearerHeader() {
        if (token == null) {
            throw new IllegalStateException("Must login before bearerHeader() for user " + username);
        }
        return "Bearer " + token;
    }

    /**
     * Performs a Full Sync and applies it to local SQLite persistence.
     *
     * @param conversationId the conversation to sync
     * @param timeout        bounded wait for the REST call
     * @return the messages returned by the server for this conversation
     */
    public List<InboundMessageDto> syncAndPersist(String conversationId, Duration timeout) {
        SyncResponseDto response = syncConversation(conversationId, timeout);
        syncService.applySync(response, username);
        return response.messages();
    }

    /**
     * Finds a message's status from a full sync response by id, without persisting it.
     *
     * @param conversationId the conversation to sync
     * @param messageId      the message to look for
     * @param timeout        bounded wait for the REST call
     * @return the message as seen by the server, if present in the sync response
     */
    public Optional<InboundMessageDto> findRemoteMessage(String conversationId, String messageId,
                                                         Duration timeout) {
        return syncConversation(conversationId, timeout).messages().stream()
                .filter(m -> m.messageId().equals(messageId))
                .findFirst();
    }

    /**
     * Waits until the server reports the message
     * as {@link MessageStatus#ACKNOWLEDGED} by the recipient.
     *
     * @param conversationId the conversation
     * @param messageId      the message id
     * @param timeout        the maximum time to wait
     * @return the server-side view of the acknowledged message
     * @throws AssertionError if the message does not become ACKNOWLEDGED in time
     */
    public InboundMessageDto awaitAcknowledged(String conversationId, String messageId, Duration timeout) {
        AtomicReference<String> lastSeen = new AtomicReference<>("no sync response yet");
        Duration requestTimeout = timeout.compareTo(E2EConfig.get().defaultTimeout()) < 0
                ? timeout : E2EConfig.get().defaultTimeout();
        try {
            return Poller.pollUntil(() -> {
                Optional<InboundMessageDto> remote = findRemoteMessage(conversationId, messageId, requestTimeout);
                lastSeen.set(remote.map(InboundMessageDto::messageStatus).orElse("message absent from sync"));
                return remote.filter(m ->
                        MessageStatus.ACKNOWLEDGED.name().equals(m.messageStatus()));
            }, timeout, "server-side state of message " + messageId + " to become " + MessageStatus.ACKNOWLEDGED);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + " | last status seen in sync: " + lastSeen.get(), e);
        }
    }

    /**
     * Attempts a protected REST call with an arbitrary {@code Authorization} header value,
     * bypassing this client's own stored token.
     *
     * <p> Used to verify that the Gateway actually rejects invalid/missing credentials.
     *
     * @param rawAuthorizationHeaderValue the exact header value to send, e.g. {@code "Bearer garbage"}
     * @param timeout                     bounded wait for the REST call
     * @throws RuntimeException if the Gateway rejects the request, as it should
     */
    public void attemptFetchConversationsWithHeader(String rawAuthorizationHeaderValue, Duration timeout) {
        syncService.fetchConversations(rawAuthorizationHeaderValue).block(timeout);
    }

    /**
     * Gets this client's username.
     *
     * @return this client's username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Gets this client's token.
     *
     * @return this client's token
     */
    public String getToken() {
        return this.token;
    }

    /**
     * Tears down this client: disconnects if connected, closes the Spring context
     * and deletes its temporary SQLite file.
     */
    @Override
    public void close() {
        try {
            if (isConnected()) {
                disconnect();
            }
        } catch (Exception ignored) {
        }

        try {
            context.close();
        } catch (Exception ignored) {
        }

        try {
            Files.deleteIfExists(dbFile);
        } catch (IOException ignored) {
        }
    }

    private Path allocateTempDbFile(String username) {
        try {
            Path file = Files.createTempFile("hermes-e2e-" + username + "-", ".db");
            Files.deleteIfExists(file);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate a temp SQLite file for " + username, e);
        }
    }
}
