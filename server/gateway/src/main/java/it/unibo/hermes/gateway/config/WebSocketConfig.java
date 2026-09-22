package it.unibo.hermes.gateway.config;

import it.unibo.hermes.gateway.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket configuration class that sets up the STOMP endpoint,
 * message broker and JWT authentication interceptor.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    /**
     * Registers the WebSocket STOMP endpoint.
     *
     * @param registry  the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    /**
     * Configures the application destination prefix and the in-memory broker used for
     * {@code /queue} destinations, with native STOMP heartbeats enabled.
     *
     * @param registry  the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/queue")
                .setTaskScheduler(heartbeatTaskScheduler());
    }

    /**
     * Registers the JWT authentication interceptor on the client-inbound channel, so every
     * inbound STOMP frame (starting with CONNECT) is checked before reaching the broker.
     *
     * @param registration  the inbound channel registration
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    /**
     * Creates a single-threaded task scheduler for the STOMP broker
     * to send heartbeats to clients.
     *
     * @return a single-threaded task scheduler
     */
    @Bean
    public TaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
