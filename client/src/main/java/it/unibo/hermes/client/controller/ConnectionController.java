package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.infrastructure.config.AppProperties;
import it.unibo.hermes.client.infrastructure.exception.MessagePersistenceException;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.dto.AckDto;
import it.unibo.hermes.client.model.dto.ErrorDto;
import it.unibo.hermes.client.model.dto.InboundMessageDto;
import it.unibo.hermes.client.model.dto.SystemMessageDto;
import it.unibo.hermes.client.model.service.MessageService;
import it.unibo.hermes.client.model.service.WebSocketService;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.model.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the full WebSocket connection lifecycle:
 * connect, disconnect and bounded-progressive exponential retry on failure.
 *
 * <p>The UI reacts to state changes via {@link ClientStateModel} observable properties
 */
@Component
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);
    private static final Set<String> PERMANENT_REJECTIONS =
            Set.of("MISSING_RECIPIENT", "MISSING_CONTENT", "SELF_SEND", "INVALID_CONVERSATION");

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final WebSocketService wsService;
    private final ClientStateModel stateModel;
    private final AppProperties props;
    private final MessageService msgService;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicLong sessionGeneration = new AtomicLong(0);
    private final Object sessionLock = new Object();
    private volatile ScheduledFuture<?> pending = null;
    private volatile String currentToken;
    private volatile Runnable onConnectedCallback = () -> {
    };

    public ConnectionController(WebSocketService wsService,
                                ClientStateModel stateModel,
                                AppProperties props,
                                MessageService msgService) {
        this.wsService = wsService;
        this.stateModel = stateModel;
        this.props = props;
        this.msgService = msgService;
    }

    /**
     * Sets a callback to be executed when the WebSocket connection is established.
     *
     * @param callback the callback to execute on connection
     */
    public void setOnConnectedCallback(Runnable callback) {
        this.onConnectedCallback = callback;
    }

    public void connect(String rawToken) {
        long gen;
        synchronized (sessionLock) {
            cancelPending();
            this.currentToken = rawToken;
            gen = sessionGeneration.incrementAndGet();
        }

        wsService.setOnConnected(() -> {
            if (!isSessionActive(gen)) {
                return;
            }
            log.info("WebSocket connected");
            retryCount.set(0);
            stateModel.setConnectionState(ConnectionState.CONNECTED);
            stateModel.setStatusMessage("Online");
            onConnectedCallback.run();
        });

        wsService.setOnReadyToSend(() -> scheduleOutboxFlush(gen));
        wsService.setOnMessage(dto -> handleInboundMessage(dto, gen));
        wsService.setOnAck(ack -> handleAck(ack, gen));
        wsService.setOnAppError(err -> handleAppError(err, gen));
        wsService.setOnSystemMessage(msg -> handleSystemMessage(msg, gen));

        wsService.setOnError(err ->
                log.warn("WebSocket error: {}", err.getMessage()));

        wsService.setOnDisconnected(() -> {
            if (!isSessionActive(gen)) {
                return;
            }
            log.info("WebSocket disconnected");
            stateModel.setConnectionState(ConnectionState.DISCONNECTED);
            stateModel.setStatusMessage("Offline");
            scheduleRetry(rawToken);
        });

        stateModel.setConnectionState(ConnectionState.CONNECTING);
        stateModel.setStatusMessage("Connecting");
        wsService.connect(rawToken);
    }

    /**
     * Disconnects the WebSocket, invalidates any in-flight inbound handlers,
     * resets Lamport clocks and cancels any pending reconnect attempts.
     */
    public void disconnect() {
        synchronized (sessionLock) {
            sessionGeneration.incrementAndGet();
            cancelPending();
            this.currentToken = null;
            wsService.disconnect();
        }
        msgService.resetClocks();
        stateModel.setConnectionState(ConnectionState.DISCONNECTED);
        stateModel.setStatusMessage("Disconnected");
    }

    private void handleInboundMessage(InboundMessageDto dto, long gen) {
        log.debug("Inbound: {} in conv {}", dto.messageId(), dto.conversationId());

        synchronized (sessionLock) {
            if (!isSessionActive(gen)) {
                log.debug("Ignoring inbound message {} from stale or logged-out session", dto.messageId());
                return;
            }

            User currentUser = stateModel.getCurrentUser();
            if (currentUser != null && !isMessageForUser(dto, currentUser.username())) {
                log.warn("Ignoring inbound message {} not belonging to current user {}",
                        dto.messageId(), currentUser.username());
                return;
            }

            Message msg;
            try {
                msg = msgService.receiveAndPersist(dto);
            } catch (MessagePersistenceException e) {
                log.error("Could not persist inbound message {} - no ACK will be sent: {}",
                        dto.messageId(), e.getMessage());
                return;
            }

            boolean isRecipient = currentUser == null
                    || currentUser.username().equalsIgnoreCase(dto.recipientUsername());
            if (isRecipient && msg.getStatus().canTransitionTo(MessageStatus.ACKNOWLEDGED)) {
                wsService.sendAck(dto.messageId());
                msgService.markAcknowledged(dto.messageId());
                msg.setStatus(MessageStatus.ACKNOWLEDGED);
            }

            // Only update the active message list if this conv is currently open
            Conversation selected = stateModel.getSelectedConversation();
            if (selected != null && selected.conversationId().equals(dto.conversationId())) {
                stateModel.appendMessage(msg);
            }
        }
    }

    private void handleAck(AckDto ack, long gen) {
        synchronized (sessionLock) {
            if (!isSessionActive(gen)) {
                log.debug("Ignoring ACK {} from stale or logged-out session", ack.messageId());
                return;
            }
            log.debug("ACK for {}: {}", ack.messageId(), ack.status());
            msgService.acknowledgeDelivery(ack.messageId(), ack.status());
        }
    }

    private void handleAppError(ErrorDto err, long gen) {
        synchronized (sessionLock) {
            if (!isSessionActive(gen)) {
                return;
            }
            log.warn("Server rejected message [{}]: {} - {}", err.messageId(), err.code(), err.reason());
            if (err.messageId() != null && PERMANENT_REJECTIONS.contains(err.code())) {
                msgService.markRejected(err.messageId());
            }
        }
    }

    private void scheduleOutboxFlush(long gen) {
        scheduler.execute(() -> {
            if (!isSessionActive(gen)) {
                return;
            }
            User self = stateModel.getCurrentUser();
            if (self == null) {
                log.warn("Outbox not flushed: no local user");
                return;
            }
            try {
                msgService.flushOutbox(self.username());
            } catch (RuntimeException e) {
                log.warn("Outbox flush failed, messages stay pending: {}", e.getMessage());
            }
        });
    }

    private void handleSystemMessage(SystemMessageDto msg, long gen) {
        if (!isSessionActive(gen)) {
            return;
        }
        log.info("System message received: {} ({})", msg.type(), msg.reason());
        if ("FORCE_RECONNECT".equals(msg.type())) {
            log.warn("Gateway requested a forced reconnect ({}) - reconnecting to resync", msg.reason());
            String token = currentToken;
            if (token != null && isSessionActive(gen)) {
                stateModel.setConnectionState(ConnectionState.RECONNECTING);
                stateModel.setStatusMessage("Re-synchronising");
                wsService.disconnect();
                wsService.connect(token);
            } else {
                log.warn("Cannot force reconnect: session is no longer active");
            }
        }
    }

    private boolean isSessionActive(long gen) {
        return sessionGeneration.get() == gen && currentToken != null;
    }

    private boolean isMessageForUser(InboundMessageDto dto, String username) {
        return username.equalsIgnoreCase(dto.recipientUsername())
                || username.equalsIgnoreCase(dto.senderUsername());
    }

    private void scheduleRetry(String rawToken) {
        if (!rawToken.equals(currentToken)) {
            log.info("Reconnect retry skipped: session logged out or token changed");
            return;
        }

        int attempt = retryCount.incrementAndGet();
        if (attempt > props.getReconnectMaxAttempts()) {
            log.error("Max reconnect attempts ({}) reached", props.getReconnectMaxAttempts());
            stateModel.setConnectionState(ConnectionState.FAILED);
            stateModel.setStatusMessage("Connection failed - please restart");
            return;
        }

        // Exponential back-off capped at maxDelayMs
        long delay = Math.min(
                props.getReconnectBaseDelayMs() * (1L << Math.min(attempt - 1, 10)),
                props.getReconnectMaxDelayMs());

        log.info("Reconnect attempt {} in {} ms", attempt, delay);
        stateModel.setConnectionState(ConnectionState.RECONNECTING);
        stateModel.setStatusMessage("Reconnecting (attempt " + attempt + ")");

        pending = scheduler.schedule(() -> {
            if (!rawToken.equals(currentToken)) {
                log.info("Scheduled reconnect attempt #{} cancelled: logged out or token changed", attempt);
                return;
            }
            log.info("Attempting reconnect #{}", attempt);
            wsService.connect(rawToken);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelPending() {
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
    }
}
