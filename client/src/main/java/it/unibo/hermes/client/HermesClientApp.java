package it.unibo.hermes.client;

import it.unibo.hermes.client.config.AppConfig;
import it.unibo.hermes.client.controller.NavigationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;

/**
 * Client entry-point. Bootstraps Spring context,
 * then delegates UI management to NavigationController.
 */
public class HermesClientApp {

    private static final Logger log = LoggerFactory.getLogger(HermesClientApp.class);

    private static AnnotationConfigApplicationContext context;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            log.info("Initialising Spring application context");
            context = new AnnotationConfigApplicationContext(AppConfig.class);
            log.info("Spring context ready");

            JFrame frame = new JFrame("Hermes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 560);
            frame.setMinimumSize(new java.awt.Dimension(800, 560));
            frame.setLocationRelativeTo(null);

            NavigationController nav = context.getBean(NavigationController.class);
            nav.initPrimaryFrame(frame);
            //nav.showLogin();
            nav.showChat();

            frame.setVisible(true);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Application shutting down");
                if (context != null) {
                    context.close();
                }
            }));
        });
    }
}
