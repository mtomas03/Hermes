package it.unibo.hermes.e2e.support;

import it.unibo.hermes.client.config.AppConfig;
import it.unibo.hermes.client.controller.ConnectionController;
import it.unibo.hermes.client.dto.AuthResponseDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.service.AuthService;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.SyncService;
import it.unibo.hermes.client.service.WebSocketService;
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

        E2EConfig cfg = E2EConfig.get();
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("hermes.server.base-url", cfg.baseUrl());
        overrides.put("hermes.server.ws-url", cfg.wsUrl());
        overrides.put("hermes.client.db-path", dbFile.toAbsolutePath().toString());

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
        syncService.applySync(response);
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
        } catch (Exception ignored) {}

        try {
            context.close();
        } catch (Exception ignored) {}

        try {
            Files.deleteIfExists(dbFile);
        } catch (IOException ignored) {}
    }

    private static Path allocateTempDbFile(String username) {
        try {
            Path file = Files.createTempFile("hermes-e2e-" + username + "-", ".db");
            Files.deleteIfExists(file);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Could not allocate a temp SQLite file for " + username, e);
        }
    }
}
