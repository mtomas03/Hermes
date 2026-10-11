package it.unibo.hermes.e2e.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * On test failure, collects a snapshot of cluster state via {@code kubectl}.
 */
public class DiagnosticsCollector implements TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsCollector.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Collects diagnostics on test failure.
     *
     * @param context the context
     * @param cause   the exception that caused the test to fail
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String namespace = E2EConfig.get().namespace();
        String testName = context.getDisplayName();

        log.error("- E2E TEST FAILED: {}", testName, cause);

        run("kubectl", "-n", namespace, "get", "pods", "-o", "wide");
        run("kubectl", "-n", namespace, "get", "events", "--sort-by=.lastTimestamp");
        run("kubectl", "-n", namespace, "get", "deployments");

        for (String deployment : List.of("gateway-deployment", "worker-deployment")) {
            run("kubectl", "-n", namespace, "logs",
                    "deployment/" + deployment, "--all-containers=true", "--tail=100",
                    "--prefix=true");
        }

        log.error("- End of diagnostics for {}", testName);
    }

    private void run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            String output = readOutput(process);

            log.error("--- {} ---\n{}", String.join(" ", command), output);

            if (!finished) {
                process.destroyForcibly();
                log.warn("Diagnostic command timed out and was killed: {}", String.join(" ", command));
            }
        } catch (Exception e) {
            log.warn("Could not collect diagnostics via '{}': {} ",
                    String.join(" ", command), e.getMessage());
        }
    }

    private String readOutput(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = reader.readLine()) != null) {
                sb.append(l).append('\n');
            }
        } catch (Exception e) {
            sb.append("(failed to read command output: ").append(e.getMessage()).append(")");
        }
        return sb.toString();
    }
}
