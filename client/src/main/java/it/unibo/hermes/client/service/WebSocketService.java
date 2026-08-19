package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.OutboundMessageDto;
import it.unibo.hermes.client.infrastructure.websocket.StompSessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages the STOMP-over-WebSocket connection lifecycle.
 */
@Service
public class WebSocketService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private final WebSocketStompClient stompClient;
    private final AppProperties props;
    private final AtomicReference<StompSession> session = new AtomicReference<>();

    // Callbacks injected by ConnectionController
    private volatile Consumer<InboundMessageDto> onMessage = msg -> {
    };
    private volatile Consumer<AckDto> onAck = ack -> {
    };
    private volatile Runnable onConnected = () -> {
    };
    private volatile Consumer<Throwable> onError = e -> {
    };
    private volatile Runnable onDisconnected = () -> {
    };

    public WebSocketService(WebSocketStompClient stompClient, AppProperties props) {
        this.stompClient = stompClient;
        this.props = props;
    }

    public void setOnMessage(Consumer<InboundMessageDto> cb) {
        this.onMessage = cb;
    }

    public void setOnAck(Consumer<AckDto> cb) {
        this.onAck = cb;
    }

    public void setOnConnected(Runnable cb) {
        this.onConnected = cb;
    }

    public void setOnError(Consumer<Throwable> cb) {
        this.onError = cb;
    }

    public void setOnDisconnected(Runnable cb) {
        this.onDisconnected = cb;
    }

    /**
     * Opens the WebSocket connection using the given JWT.
     * The token is sent as a query parameter and also in the STOMP CONNECT frame.
     *
     * @return a future that completes when the STOMP CONNECT handshake is done.
     */
    public CompletableFuture<StompSession> connect(String rawToken) {
        String url = props.getWsUrl() + "?token=" + rawToken;
        log.info("Connecting to WebSocket: {}", props.getWsUrl());

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        StompHeaders stompConnectHeaders = new StompHeaders();
        stompConnectHeaders.add("Authorization", "Bearer " + rawToken);

        StompSessionHandler handler = new StompSessionHandler(
                props,
                msg -> onMessage.accept(msg),
                ack -> onAck.accept(ack),
                () -> onConnected.run(),
                err -> onError.accept(err),
                () -> {
                    session.set(null);
                    onDisconnected.run();
                });

        return stompClient.connectAsync(url, httpHeaders, stompConnectHeaders, handler)
                .thenApply(s -> {
                    session.set(s);
                    return s;
                })
                .exceptionally(e -> {
                    log.error("WebSocket connection failed: {}", e.getMessage());
                    onError.accept(e);
                    return null;
                });
    }

    /**
     * Sends a message via STOMP.
     *
     * @return true if the message was submitted, false if not connected.
     */
    public boolean sendMessage(OutboundMessageDto dto) {
        StompSession s = session.get();
        if (s == null || !s.isConnected()) {
            log.warn("Cannot send: not connected");
            return false;
        }
        s.send(props.getStompSendDestination(), dto);
        return true;
    }

    public boolean isConnected() {
        StompSession s = session.get();
        return s != null && s.isConnected();
    }

    public void disconnect() {
        StompSession s = session.getAndSet(null);
        if (s != null && s.isConnected()) {
            log.info("Disconnecting WebSocket");
            s.disconnect();
        }
    }
}
