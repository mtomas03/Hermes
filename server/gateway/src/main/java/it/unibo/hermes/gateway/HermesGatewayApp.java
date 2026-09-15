package it.unibo.hermes.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry-point for the Hermes Gateway.
 */
@EnableScheduling
@SpringBootApplication(exclude = { RedisRepositoriesAutoConfiguration.class })
@EnableJpaRepositories(basePackages = "it.unibo.hermes.gateway.repository.jpa")
@EnableCassandraRepositories(basePackages = "it.unibo.hermes.gateway.repository.cassandra")
public class HermesGatewayApp {
    public static void main(String[] args) {
        SpringApplication.run(HermesGatewayApp.class, args);
    }
}
