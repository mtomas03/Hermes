package it.unibo.hermes.gateway.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaHealthProbeTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    @Mock
    private AdminClient adminClient;

    @Mock
    private ListTopicsResult listTopicsResult;

    @Mock
    private KafkaFuture<Set<String>> kafkaFuture;

    private KafkaHealthProbe probe;

    @BeforeEach
    void setUp() {
        probe = new KafkaHealthProbe(BOOTSTRAP_SERVERS);
    }

    @Test
    void isHealthySuccess() throws Exception {
        try (MockedStatic<AdminClient> mockedAdmin = mockStatic(AdminClient.class)) {
            mockedAdmin.when(() -> AdminClient.create(any(Properties.class)))
                    .thenReturn(adminClient);

            when(adminClient.listTopics()).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(kafkaFuture);
            when(kafkaFuture.get(2, TimeUnit.SECONDS))
                    .thenReturn(Set.of("message-created", "message-delivery"));

            assertThat(probe.isHealthy()).isTrue();
            verify(adminClient).listTopics();
        }
    }

    @Test
    void isHealthyFailureException() {
        try (MockedStatic<AdminClient> mockedAdmin = mockStatic(AdminClient.class)) {
            mockedAdmin.when(() -> AdminClient.create(any(Properties.class)))
                    .thenThrow(new RuntimeException("Kafka broker connection timeout"));

            assertThat(probe.isHealthy()).isFalse();
        }
    }

    @Test
    void isHealthyFailureFutureExecution() throws Exception {
        try (MockedStatic<AdminClient> mockedAdmin = mockStatic(AdminClient.class)) {
            mockedAdmin.when(() -> AdminClient.create(any(Properties.class)))
                    .thenReturn(adminClient);

            when(adminClient.listTopics()).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(kafkaFuture);
            when(kafkaFuture.get(2, TimeUnit.SECONDS))
                    .thenThrow(new RuntimeException("Timeout fetching topic metadata"));

            assertThat(probe.isHealthy()).isFalse();
        }
    }
}
