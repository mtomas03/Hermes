package it.unibo.hermes.client.infrastructure.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Wrapper around Jackson that catches checked exceptions.
 */
@Component
public class JsonMapper {

    private static final Logger log = LoggerFactory.getLogger(JsonMapper.class);

    private final ObjectMapper objectMapper;

    public JsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> fromJson(String json, Class<T> type) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialise {} from JSON: {}", type.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> toJson(Object obj) {
        try {
            return Optional.of(objectMapper.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise {} to JSON: {}", obj.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
