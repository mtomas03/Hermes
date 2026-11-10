package it.unibo.hermes.client;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.NavigationController;
import it.unibo.hermes.client.infrastructure.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;
import java.awt.*;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Client entry-point.
 */
public class HermesClientApp {

    private static final Logger log = LoggerFactory.getLogger(HermesClientApp.class);

    private static AnnotationConfigApplicationContext context;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            log.info("Initialising Spring application context");
            context = new AnnotationConfigApplicationContext(AppConfig.class);
            log.info("Spring context ready");

            NavigationController nav = context.getBean(NavigationController.class);
            nav.initPrimaryFrame();
            nav.showLogin();

            AuthController auth = context.getBean(AuthController.class);
            runAsync(auth::restoreSession);

            nav.setPrimaryFrameVisible(true);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Application shutting down");
                if (context != null) {
                    context.close();
                }
            }));
        });
    }
}
