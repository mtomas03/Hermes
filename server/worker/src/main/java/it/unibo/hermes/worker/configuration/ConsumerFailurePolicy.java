package it.unibo.hermes.worker.configuration;

import it.unibo.hermes.worker.application.exception.PersistenceInconsistencyException;
import it.unibo.hermes.worker.domain.exception.DomainException;
import it.unibo.hermes.worker.infrastructure.adapter.primary.exception.MalformedEventException;

import java.util.List;

/**
 * Defines the policy for handling failures in Kafka consumers.
 */
public final class ConsumerFailurePolicy {

    /** Failure types that are never retried. */
    public static final List<Class<? extends Exception>> NON_RETRYABLE = List.of(
            DomainException.class,
            PersistenceInconsistencyException.class,
            MalformedEventException.class);

    private ConsumerFailurePolicy() {
    }

    /**
     * Determines if a failure is a poison failure:
     * a failure that will never succeed, even if retried.
     *
     * @param failure the exception thrown while processing a record
     * @return {@code true} if the failure is a poison failure,
     *         {@code false} otherwise
     */
    public static boolean isPoison(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            for (Class<? extends Exception> type : NON_RETRYABLE) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
