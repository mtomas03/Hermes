package it.unibo.hermes.e2e.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JUnit extension verifying that the Hermes entry point is actually reachable
 * and routing before any test in the class runs.
 */
public class MinikubeReadiness implements BeforeAllCallback {

    private static final Logger log = LoggerFactory.getLogger(MinikubeReadiness.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        E2EConfig config = E2EConfig.get();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        Poller.pollUntilTrue(() -> isReachable(http, config.baseUrl()),
                config.defaultTimeout(),
                "Hermes Ingress at " + config.baseUrl() + " to accept connections ");

        log.info("Hermes Ingress at {} is reachable - proceeding with E2E suite", config.baseUrl());
    }

    private boolean isReachable(HttpClient http, String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() > 0;
        } catch (Exception e) {
            log.debug("Ingress not yet reachable: {}", e.getMessage());
            return false;
        }
    }
}
