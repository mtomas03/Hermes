package it.unibo.hermes.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry-point for the Hermes Gateway microservice.
 */
@SpringBootApplication
@EnableScheduling
public class HermesGatewayApp {
    public static void main(String[] args) {
        SpringApplication.run(HermesGatewayApp.class, args);
    }
}