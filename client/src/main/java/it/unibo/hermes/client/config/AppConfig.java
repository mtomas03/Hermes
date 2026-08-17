package it.unibo.hermes.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Spring configuration.
 */
@Configuration
@ComponentScan(basePackages = "it.unibo.hermes.client")
@PropertySource("classpath:application.properties")
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Reactive HTTP client for REST calls.
     */
    @Bean
    public WebClient webClient(AppProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    /**
     * STOMP-over-WebSocket client.
     */
    @Bean
    public WebSocketStompClient stompClient(ObjectMapper objectMapper) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        return client;
    }
}
