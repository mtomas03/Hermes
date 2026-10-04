package it.unibo.hermes.gateway.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Kafka wire format declared in this module's {@code application.yml}.
 *
 * <p>Gateway and Worker exchange plain UTF-8 JSON strings on every topic; producers and consumers
 * (de)serialize events explicitly with {@code ObjectMapper}. The configured Kafka value
 * serializer/deserializer must therefore pass the string through untouched: a JSON serializer would
 * quote the already-serialized payload, and a JSON deserializer would reject header-less records.
 * The real Kafka client config classes instantiate the serdes, exactly as the runtime does.
 */
class KafkaWireFormatConfigTest {

    private static final String TOPIC = "any-topic";
    private static final String JSON = "{\"messageId\":\"m-1\",\"gatewayId\":\"gateway-1\"}";

    private static KafkaProperties loadKafkaProperties() throws IOException {
        List<PropertySource<?>> loaded =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));
        return binder.bind("spring.kafka", KafkaProperties.class).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void producerWritesJsonPayloadAsIsWithoutRequotingIt() throws IOException {
        ProducerConfig config = new ProducerConfig(loadKafkaProperties().buildProducerProperties(null));

        Serializer<Object> valueSerializer =
                config.getConfiguredInstance(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, Serializer.class);

        assertThat(valueSerializer.serialize(TOPIC, JSON)).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void consumerReadsRecordsWithoutTypeHeadersAsPlainStrings() throws IOException {
        ConsumerConfig config = new ConsumerConfig(loadKafkaProperties().buildConsumerProperties(null));

        Deserializer<?> valueDeserializer =
                config.getConfiguredInstance(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Deserializer.class);

        assertThat(valueDeserializer.deserialize(TOPIC, JSON.getBytes(StandardCharsets.UTF_8))).isEqualTo(JSON);
    }
}
