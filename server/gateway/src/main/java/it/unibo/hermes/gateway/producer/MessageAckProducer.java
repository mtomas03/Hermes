package it.unibo.hermes.gateway.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.event.MessageAckEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer responsible for forwarding client delivery ACK events to Kafka.
 */
@Component
public class MessageAckProducer {

    private static final Logger log = LoggerFactory.getLogger(MessageAckProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ackTopic;

    public MessageAckProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hermes.topics.message-acknowledged:message-acknowledged}") String ackTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ackTopic = ackTopic;
    }

    /**
     * Asynchronously publishes a client ACK confirmation event.
     *
     * @param ackEvent the ACK event containing message and recipient info
     */
    public void publishAck(MessageAckEvent ackEvent) {
        try {
            String payload = objectMapper.writeValueAsString(ackEvent);
            kafkaTemplate.send(ackTopic, ackEvent.recipientUsername(), payload);
            log.info("Published MessageAckEvent for message {} from recipient {}",
                    ackEvent.messageId(), ackEvent.recipientUsername());
        } catch (Exception e) {
            log.error("Failed to publish ACK event for message {}: {}",
                    ackEvent.messageId(), e.getMessage());
        }
    }
}