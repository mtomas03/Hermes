package it.unibo.hermes.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralises all externally configurable properties.
 */
@Component
public class AppProperties {

    @Value("${hermes.server.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${hermes.server.ws-url:ws://localhost:8080/ws}")
    private String wsUrl;

    @Value("${hermes.api.register:/api/v1/auth/register}")
    private String registerPath;

    @Value("${hermes.api.login:/api/v1/auth/login}")
    private String loginPath;

    @Value("${hermes.api.conversations:/api/v1/conversations}")
    private String conversationsPath;

    @Value("${hermes.api.messages.sync:/api/v1/messages/sync}")
    private String syncPath;

    @Value("${hermes.api.users.search:/api/v1/users/search}")
    private String userSearchPath;

    @Value("${hermes.client.db-path:hermes-local.db}")
    private String dbPath;

    @Value("${hermes.client.reconnect-max-attempts:10}")
    private int reconnectMaxAttempts;

    @Value("${hermes.client.reconnect-base-delay-ms:1000}")
    private long reconnectBaseDelayMs;

    @Value("${hermes.client.reconnect-max-delay-ms:30000}")
    private long reconnectMaxDelayMs;

    @Value("${hermes.stomp.subscribe.messages:/user/queue/messages}")
    private String stompMessagesDestination;

    @Value("${hermes.stomp.subscribe.acks:/user/queue/acks}")
    private String stompAcksDestination;

    @Value("${hermes.stomp.send.message:/app/chat.sendMessage}")
    private String stompSendDestination;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public String getRegisterPath() {
        return registerPath;
    }

    public String getLoginPath() {
        return loginPath;
    }

    public String getConversationsPath() {
        return conversationsPath;
    }

    public String getSyncPath() {
        return syncPath;
    }

    public String getUserSearchPath() {
        return userSearchPath;
    }

    public String getDbPath() {
        return dbPath;
    }

    public int getReconnectMaxAttempts() {
        return reconnectMaxAttempts;
    }

    public long getReconnectBaseDelayMs() {
        return reconnectBaseDelayMs;
    }

    public long getReconnectMaxDelayMs() {
        return reconnectMaxDelayMs;
    }

    public String getStompMessagesDestination() {
        return stompMessagesDestination;
    }

    public String getStompAcksDestination() {
        return stompAcksDestination;
    }

    public String getStompSendDestination() {
        return stompSendDestination;
    }
}
