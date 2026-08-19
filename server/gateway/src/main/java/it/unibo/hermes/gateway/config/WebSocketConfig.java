package it.unibo.hermes.gateway.config;

import it.unibo.hermes.gateway.security.WebSocketJwtHandshakeInterceptor;
import it.unibo.hermes.gateway.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring configuration class registering the WebSocket endpoint and its connection management components.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * Creates the WebSocket configuration with the session handler and authentication handshake interceptor.
     *
     * @param chatWebSocketHandler    the handler processing websocket incoming messages
     * @param jwtHandshakeInterceptor the interceptor validating JWT tokens during the HTTP upgrade handshake
     */
    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    /**
     * Maps the WebSocket endpoint to the chat handler and attaches the authentication handshake interceptor.
     *
     * @param registry the registry used to map WebSocket routes to their respective handlers
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}