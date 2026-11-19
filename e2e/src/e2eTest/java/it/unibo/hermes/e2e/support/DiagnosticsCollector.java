package it.unibo.hermes.e2e.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * On test failure, collects a snapshot of cluster state via {@code kubectl}.
 */
public class DiagnosticsCollector implements TestWatcher {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsCollector.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration TOTAL_BUDGET = Duration.ofMinutes(3);
    private static final String LOG_TAIL = "300";
    private static final String LOG_SINCE = "15m";
    private static final int EVENTS_TAIL_LINES = 150;
    private static final Map<String, String> LOG_SELECTORS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gateway", "app=hermes-gateway");
        map.put("worker", "app=hermes-worker");
        map.put("kafka", "app=hermes-kafka");
        LOG_SELECTORS = Collections.unmodifiableMap(map);
    }

    private static String lastLines(Path file, int tailLines) throws IOException {
        String output = Files.readString(file, StandardCharsets.UTF_8);
        if (tailLines <= 0) {
            return output;
        }
        List<String> lines = output.lines().toList();
        // keep the header line of tabular output together with the most recent rows
        if (lines.size() <= tailLines + 1) {
            return output;
        }
        StringBuilder sb = new StringBuilder(lines.getFirst()).append('\n');
        lines.subList(lines.size() - tailLines, lines.size()).forEach(l -> sb.append(l).append('\n'));
        return sb.toString();
    }

    /**
     * Collects diagnostics on test failure.
     *
     * @param context the context
     * @param cause   the exception that caused the test to fail
     */
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String testName = context.getDisplayName();
        log.error("- E2E TEST FAILED: {}", testName, cause);
        try {
            collect(E2EConfig.get().namespace());
        } catch (RuntimeException e) {
            log.warn("Diagnostics collection aborted: {}", e.getMessage());
        }
        log.error("- End of diagnostics for {}", testName);
    }

    private void collect(String namespace) {
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET.toMillis();

        run(deadline, 0, "kubectl", "-n", namespace, "get", "pods", "-o", "wide");
        run(deadline, 0, "kubectl", "-n", namespace, "get", "deployments");
        run(deadline, EVENTS_TAIL_LINES, "kubectl", "-n", namespace, "--request-timeout=10s",
                "get", "events", "--sort-by=.lastTimestamp");

        for (Map.Entry<String, String> component : LOG_SELECTORS.entrySet()) {
            List<String> pods = podsOf(namespace, component.getValue());
            if (pods.isEmpty()) {
                log.error("--- no {} pod found with selector {} ---", component.getKey(), component.getValue());
                continue;
            }
            for (String pod : pods) {
                run(deadline, 0, "kubectl", "-n", namespace, "logs", pod, "--all-containers=true",
                        "--tail=" + LOG_TAIL, "--since=" + LOG_SINCE, "--timestamps", "--prefix=true");
            }
        }
    }

    private List<String> podsOf(String namespace, String selector) {
        try {
            return Kubectl.podNames(selector, namespace);
        } catch (RuntimeException | AssertionError e) {
            log.warn("Could not list pods with selector {}: {}", selector, e.getMessage());
            return List.of();
        }
    }

    private void run(long deadline, int tailLines, String... command) {
        long remainingMs = TimeUnit.MILLISECONDS.toMillis(deadline - System.currentTimeMillis());
        if (remainingMs <= 0) {
            log.warn("Diagnostics time budget elapsed, skipping: {}", String.join(" ", command));
            return;
        }
        long timeoutMs = Math.min(COMMAND_TIMEOUT.toMillis(), remainingMs);
        Path outFile = null;
        try {
            outFile = Files.createTempFile("diagnostics-", ".out");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outFile.toFile())
                    .start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Diagnostic command timed out after {} ms and was killed (partial output follows): {}",
                        timeoutMs, String.join(" ", command));
            }
            log.error("--- {} ---\n{}", String.join(" ", command), lastLines(outFile, tailLines));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Diagnostics interrupted while running '{}'", String.join(" ", command));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not collect diagnostics via '{}': {}", String.join(" ", command), e.getMessage());
        } finally {
            if (outFile != null) {
                try {
                    Files.deleteIfExists(outFile);
                } catch (IOException ignored) {
                    // temp file clean-up is best effort
                }
            }
        }
    }
}
