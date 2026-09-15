package it.unibo.hermes.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

/**
 * Entry-point for the Hermes Delivery Worker.
 */
@SpringBootApplication(exclude = { RedisRepositoriesAutoConfiguration.class })
@EnableCassandraRepositories(basePackages = "it.unibo.hermes.worker.repository.cassandra")
public class HermesWorkerApp {
    public static void main(String[] args) {
        SpringApplication.run(HermesWorkerApp.class, args);
    }
}
