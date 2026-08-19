package it.unibo.hermes.client.infrastructure.websocket;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.*;

import java.lang.reflect.Type;
import java.util.function.Consumer;

/**
 * STOMP session handler. Subscribes to user queues after connection
 * and dispatches inbound messages to registered callbacks.
 */
public class StompSessionHandler extends StompSessionHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(StompSessionHandler.class);

    private final AppProperties props;
    private final Consumer<InboundMessageDto> onMessage;
    private final Consumer<AckDto> onAck;
    private final Runnable onConnected;
    private final Consumer<Throwable> onError;
    private final Runnable onDisconnected;

    public StompSessionHandler(AppProperties props,
                               Consumer<InboundMessageDto> onMessage,
                               Consumer<AckDto> onAck,
                               Runnable onConnected,
                               Consumer<Throwable> onError,
                               Runnable onDisconnected) {
        this.props = props;
        this.onMessage = onMessage;
        this.onAck = onAck;
        this.onConnected = onConnected;
        this.onError = onError;
        this.onDisconnected = onDisconnected;
    }

    @Override
    public void afterConnected(StompSession session, @NonNull StompHeaders connectedHeaders) {
        log.info("STOMP connected - session {}", session.getSessionId());

        // Subscribe to inbound messages
        session.subscribe(props.getStompMessagesDestination(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(@NonNull StompHeaders h) {
                return InboundMessageDto.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders h, Object payload) {
                if (payload instanceof InboundMessageDto msg) {
                    log.debug("Received inbound message: {}", msg.messageId());
                    onMessage.accept(msg);
                }
            }
        });

        // Subscribe to delivery ACKs
        session.subscribe(props.getStompAcksDestination(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(@NonNull StompHeaders h) {
                return AckDto.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders h, Object payload) {
                if (payload instanceof AckDto ack) {
                    log.debug("Received ACK for message: {}", ack.messageId());
                    onAck.accept(ack);
                }
            }
        });

        onConnected.run();
    }

    @Override
    public void handleException(@NonNull StompSession session, StompCommand cmd,
                                @NonNull StompHeaders headers, @NonNull byte[] payload, @NonNull Throwable ex) {
        log.error("STOMP exception [{}]: {}", cmd, ex.getMessage(), ex);
        onError.accept(ex);
    }

    @Override
    public void handleTransportError(@NonNull StompSession session, Throwable ex) {
        log.warn("STOMP transport error: {}", ex.getMessage());
        onDisconnected.run();
        onError.accept(ex);
    }
}
