package it.unibo.hermes.client;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.NavigationController;
import it.unibo.hermes.client.infrastructure.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client entry-point.
 */
public class HermesClientApp {

    private static final Logger log = LoggerFactory.getLogger(HermesClientApp.class);

    private static final AtomicBoolean CLOSED = new AtomicBoolean(false);
    private static volatile AnnotationConfigApplicationContext context;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(HermesClientApp::shutdownContext, "hermes-shutdown"));

        SwingUtilities.invokeLater(() -> {
            log.info("Initialising Spring application context");
            AnnotationConfigApplicationContext appContext = new AnnotationConfigApplicationContext(AppConfig.class);
            context = appContext;
            log.info("Spring context ready");

            NavigationController nav = appContext.getBean(NavigationController.class);
            nav.initPrimaryFrame();
            nav.showLogin();

            AuthController auth = appContext.getBean(AuthController.class);
            CompletableFuture.runAsync(auth::restoreSession)
                    .whenComplete((ignored, ex) -> {
                        if (ex != null) {
                            log.error("Unexpected failure during startup session restore", ex);
                        }
                    });

            nav.setPrimaryFrameVisible(true);
        });
    }

    public static void shutdownContext() {
        if (!CLOSED.compareAndSet(false, true)) {
            return;
        }
        log.info("Application shutting down");
        AnnotationConfigApplicationContext appContext = context;
        if (appContext != null && appContext.isActive()) {
            appContext.close();
        }
    }
}
