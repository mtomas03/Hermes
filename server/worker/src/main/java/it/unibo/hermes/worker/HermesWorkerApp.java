package it.unibo.hermes.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry-point for the Hermes Delivery Worker.
 */
@SpringBootApplication
public class HermesWorkerApp {
    public static void main(String[] args) {
        SpringApplication.run(HermesWorkerApp.class, args);
    }
}
