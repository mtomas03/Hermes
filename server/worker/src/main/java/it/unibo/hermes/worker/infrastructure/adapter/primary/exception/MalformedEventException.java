package it.unibo.hermes.worker.infrastructure.adapter.primary.exception;

/**
 * Thrown when a Kafka record cannot be deserialized.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String eventType, String topic, int partition, long offset, String reason) {
        super("Cannot deserialize " + eventType + " - topic=" + topic + " partition=" + partition
                + " offset=" + offset + " reason=" + reason);
    }
}
