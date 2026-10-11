package it.unibo.hermes.e2e.support;

import java.time.Duration;

/**
 * E2E test suite configuration.
 */
public final class E2EConfig {

    private static final E2EConfig INSTANCE = new E2EConfig();

    private final String baseUrl;
    private final String wsUrl;
    private final String namespace;
    private final Duration defaultTimeout;

    private E2EConfig() {
        this.baseUrl = System.getProperty("hermes.e2e.baseUrl", "http://hermes.local");
        this.wsUrl = System.getProperty("hermes.e2e.wsUrl", "ws://hermes.local/ws");
        this.namespace = System.getProperty("hermes.e2e.namespace", "hermes-namespace");
        this.defaultTimeout = Duration.ofSeconds(
                Long.parseLong(System.getProperty("hermes.e2e.timeoutSeconds", "60")));
    }

    public static E2EConfig get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String wsUrl() {
        return wsUrl;
    }

    public String namespace() {
        return namespace;
    }

    public Duration defaultTimeout() {
        return defaultTimeout;
    }
}
