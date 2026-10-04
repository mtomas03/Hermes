package it.unibo.hermes.e2e.support;

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
                lastError = e;
            }
            sleepQuietly(interval);
        }

        String message = "Timed out after " + timeout + " (" + attempts + " attempts) waiting for: "
                + description;
        if (lastError != null) {
            throw new AssertionError(message + " - last error: " + lastError, lastError);
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

    private static void sleepQuietly(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while polling", e);
        }
    }
}
