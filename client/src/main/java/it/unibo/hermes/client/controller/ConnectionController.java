package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.model.state.ConnectionState;
import it.unibo.hermes.client.service.MessageService;
import it.unibo.hermes.client.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the full WebSocket connection lifecycle:
 * connect, disconnect and bounded-progressive exponential retry on failure.
 *
 * <p>The UI reacts to state changes via {@link ClientStateModel} observable properties
 */
@Component
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);

    private final WebSocketService wsService;
    private final ClientStateModel stateModel;
    private final AppProperties props;
    private final MessageService msgService;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final AtomicInteger retryCount = new AtomicInteger(0);
    private ScheduledFuture<?> pending = null;

    /**
     * Invoked once after a successful STOMP handshake (and subscriptions).
     */
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

    public void setOnConnectedCallback(Runnable cb) {
        this.onConnectedCallback = cb;
    }

    /**
     * Wires callbacks on the WS service and initiates the STOMP connection.
     */
    public void connect(String rawToken) {
        wsService.setOnConnected(() -> {
            log.info("WebSocket connected");
            retryCount.set(0);
            stateModel.setConnectionState(ConnectionState.CONNECTED);
            stateModel.setStatusMessage("Online");
            onConnectedCallback.run();
        });

        wsService.setOnMessage(this::handleInboundMessage);
        wsService.setOnAck(this::handleAck);

        wsService.setOnError(err ->
                log.warn("WebSocket error: {}", err.getMessage()));

        wsService.setOnDisconnected(() -> {
            log.info("WebSocket disconnected");
            stateModel.setConnectionState(ConnectionState.DISCONNECTED);
            stateModel.setStatusMessage("Offline");
            scheduleRetry(rawToken);
        });

        stateModel.setConnectionState(ConnectionState.CONNECTING);
        stateModel.setStatusMessage("Connecting…");
        wsService.connect(rawToken);
    }

    public void disconnect() {
        cancelPending();
        wsService.disconnect();
        stateModel.setConnectionState(ConnectionState.DISCONNECTED);
        stateModel.setStatusMessage("Disconnected");
    }

    private void handleInboundMessage(InboundMessageDto dto) {
        log.debug("Inbound: {} in conv {}", dto.messageId(), dto.conversationId());

        // Persist first (idempotent)
        Message msg = msgService.receiveAndPersist(dto);

        // Only update the active message list if this conv is currently open
        Conversation selected = stateModel.getSelectedConversation();
        if (selected != null && selected.getConversationId().equals(dto.conversationId())) {
            stateModel.appendMessage(msg);
        }
    }

    private void handleAck(AckDto ack) {
        log.debug("ACK for {}: {}", ack.messageId(), ack.status());
        msgService.acknowledgeDelivery(ack.messageId());
    }

    private void scheduleRetry(String rawToken) {
        int attempt = retryCount.incrementAndGet();
        if (attempt > props.getReconnectMaxAttempts()) {
            log.error("Max reconnect attempts ({}) reached", props.getReconnectMaxAttempts());
            stateModel.setConnectionState(ConnectionState.FAILED);
            stateModel.setStatusMessage("Connection failed – please restart");
            return;
        }

        // Exponential back-off capped at maxDelayMs
        long delay = Math.min(
                props.getReconnectBaseDelayMs() * (1L << Math.min(attempt - 1, 10)),
                props.getReconnectMaxDelayMs());

        log.info("Reconnect attempt {} in {} ms", attempt, delay);
        stateModel.setConnectionState(ConnectionState.RECONNECTING);
        stateModel.setStatusMessage("Reconnecting (attempt " + attempt + ")…");

        pending = scheduler.schedule(() -> {
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
