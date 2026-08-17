package it.unibo.hermes.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration class that defines the default settings for Jackson's JSON serialization.
 */
@Configuration
public class JacksonConfig {

    /**
     * Create and configure the {@link ObjectMapper} bean so that it serializes all temporal values
     * as ISO-8601 strings rather than as numeric timestamps.
     *
     * @return the object mapper with ISO-8601 date formatting
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}