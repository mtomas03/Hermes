package it.unibo.hermes.e2e.support;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Utility class for polling a condition until it becomes true or a timeout elapses.
 */
public final class Poller {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMillis(250);

    private Poller() {
    }

    /**
     * Repeatedly evaluates a condition until it produces a value or the timeout elapses.
     *
     * @param attempt     produces {@link Optional#empty()} while the condition is not yet true
     *                    or the observed value once it is
     * @param timeout     the maximum total time to wait
     * @param description a description of what is being waited for, included in
     *                    the failure message
     * @param <T>         the type of the awaited value
     * @return the observed value
     * @throws AssertionError if the timeout elapses without {@code attempt} returning a value
     */
    public static <T> T pollUntil(Supplier<Optional<T>> attempt, Duration timeout, String description) {
        return pollUntil(attempt, timeout, DEFAULT_INTERVAL, description);
    }

    /**
     * Same as {@link #pollUntil(Supplier, Duration, String)} but with an explicit poll interval.
     *
     * @param attempt     produces {@link Optional#empty()} while the condition is not yet true,
     *                    or the observed value once it is
     * @param timeout     the maximum total time to wait
     * @param interval    the delay between successive attempts
     * @param description a human-readable description of what is being waited for
     * @param <T>         the type of the awaited value
     * @return the observed value
     * @throws AssertionError if the timeout elapses without {@code attempt} returning a value
     */
    public static <T> T pollUntil(Supplier<Optional<T>> attempt, Duration timeout, Duration interval,
                                  String description) {
        Instant deadline = Instant.now().plus(timeout);
        Throwable lastError = null;
        int attempts = 0;

        while (Instant.now().isBefore(deadline)) {
            attempts++;
            try {
                Optional<T> result = attempt.get();
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (Exception e) {
                failFastOnServerError(e, description);
                lastError = e;
            }
            sleepQuietly(interval);
        }

        String message = "Timed out after " + timeout + " (" + attempts + " attempts) waiting for: "
                + description;
        if (lastError != null) {
            throw new AssertionError(message + " - last error: " + describe(lastError), lastError);
        }
        throw new AssertionError(message);
    }

    /**
     * Repeatedly evaluates a boolean condition
     * until it becomes {@code true} or the timeout elapses.
     *
     * @param condition   the condition to wait for
     * @param timeout     the maximum total time to wait
     * @param description a description of what is being waited for
     * @throws AssertionError if the timeout elapses without the condition becoming true
     */
    public static void pollUntilTrue(Supplier<Boolean> condition, Duration timeout, String description) {
        pollUntil(() -> Boolean.TRUE.equals(condition.get()) ? Optional.of(true) : Optional.empty(),
                timeout, description);
    }

    /**
     * Decides whether an HTTP status may resolve on its own and is therefore worth retrying.
     *
     * @param status the HTTP status code
     * @param path   the request path, if available
     * @return {@code true} if the status is considered retryable, {@code false} otherwise
     */
    public static boolean isRetryable(int status, String path) {
        return switch (status) {
            case 408, 425, 429, 502, 503, 504 -> true;
            case 403 -> path != null && path.startsWith("/api/v1/sync/");
            default -> status < 400;
        };
    }

    private static void failFastOnServerError(Throwable error, String description) {
        WebClientResponseException http = findHttpError(error);
        if (http == null) {
            return;
        }
        String path = http.getRequest() != null ? http.getRequest().getURI().getPath() : null;
        if (!isRetryable(http.getStatusCode().value(), path)) {
            throw new AssertionError("HTTP error while waiting for: " + description + " - " + describe(http), http);
        }
    }

    private static WebClientResponseException findHttpError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException http) {
                return http;
            }
        }
        return null;
    }

    private static String describe(Throwable error) {
        WebClientResponseException http = findHttpError(error);
        if (http == null) {
            return String.valueOf(error);
        }
        return "HTTP " + http.getStatusCode().value() + " "
                + (http.getRequest() != null ? http.getRequest().getMethod()
                + " " + http.getRequest().getURI() : "")
                + " - body: " + http.getResponseBodyAsString();
    }

    private static void sleepQuietly(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while polling", e);
        }
    }
}
