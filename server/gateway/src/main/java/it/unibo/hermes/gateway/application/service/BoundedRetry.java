package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;

import java.time.Duration;

/**
 * Utility class providing a retry loop with exponential backoff and a total time budget.
 */
public final class BoundedRetry {

    private BoundedRetry() {
    }

    /**
     * Runs the attempt until it succeeds, the attempts are exhausted or the budget is spent.
     *
     * @param attempt              the operation to run
     * @param maxAttempts          the maximum number of attempts (at least 1)
     * @param initialBackoffMs     the pause before the second attempt
     * @param backoffMultiplier    the growth factor of the pause
     * @param budget               the total time the whole retry loop may take
     * @param stopOnUnknownOutcome if true, an attempt that timed out ends the loop with
     *                             {@link Result#OUTCOME_UNKNOWN} instead of being retried
     * @return the outcome
     */
    static Outcome run(
            Attempt attempt, int maxAttempts, long initialBackoffMs,
            double backoffMultiplier, Duration budget,
            boolean stopOnUnknownOutcome) {
        long deadline = System.currentTimeMillis() + budget.toMillis();
        long backoffMs = initialBackoffMs;
        BackboneUnavailableException last = null;

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) {
                break;
            }
            try {
                attempt.run(Duration.ofMillis(remainingMs));
                return new Outcome(Result.SUCCESS, null);
            } catch (BackboneUnavailableException e) {
                last = e;
                if (stopOnUnknownOutcome && e.isOutcomeUnknown()) {
                    return new Outcome(Result.OUTCOME_UNKNOWN, e);
                }
                if (attemptNumber < maxAttempts) {
                    long leftMs = Math.max(0, (deadline - System.currentTimeMillis()));
                    sleep(Math.min(backoffMs, leftMs));
                    backoffMs = (long) (backoffMs * backoffMultiplier);
                }
            }
        }
        return new Outcome(Result.FAILED, last);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    enum Result {
        /**
         * The attempt succeeded within the budget.
         */
        SUCCESS,
        /**
         * Every attempt failed or the budget ran out of time.
         */
        FAILED,
        /**
         * An attempt timed out and retrying was disabled for that case: the operation may have been performed.
         */
        OUTCOME_UNKNOWN
    }

    @FunctionalInterface
    interface Attempt {
        /**
         * Runs the operation.
         *
         * @param remainingTime the time left in the budget.
         * @throws BackboneUnavailableException if the attempt fails or times out
         */
        void run(Duration remainingTime);
    }

    record Outcome(Result result, BackboneUnavailableException lastFailure) {
    }
}
