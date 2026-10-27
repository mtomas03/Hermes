package it.unibo.hermes.e2e.support;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * A simple wrapper around the {@code kubectl} CLI for those few error/recovery scenarios
 * that require direct action on the cluster.
 */
public final class Kubectl {

    private Kubectl() {}

    /**
     * Triggers a rolling restart of a Deployment and waits for the rollout to complete.
     *
     * @param deployment the Deployment name, e.g. {@code "worker-deployment"}
     * @param namespace  the Kubernetes namespace
     * @param timeout    bounded wait for the rollout to finish
     */
    public static void restartAndAwaitRollout(String deployment, String namespace, Duration timeout) {
        run(timeout, "kubectl", "-n", namespace, "rollout", "restart", "deployment/" + deployment);
        run(timeout, "kubectl", "-n", namespace, "rollout", "status", "deployment/" + deployment,
                "--timeout=" + timeout.toSeconds() + "s");
    }

    /**
     * Scales a StatefulSet and waits until the change is observable:
     * for {@code replicas == 0} until no pod labelled {@code app=<appLabel>} is left,
     * otherwise until the rollout is complete.
     *
     * @param statefulSet the StatefulSet name
     * @param appLabel    the label used to identify the pods of the StatefulSet
     * @param replicas    the desired number of replicas
     * @param namespace   the Kubernetes namespace
     * @param timeout     bounded wait for the change to be observable
     */
    public static void scaleStatefulSetAndAwait(String statefulSet, String appLabel, int replicas,
                                                String namespace, Duration timeout) {
        scaleStatefulSet(statefulSet, replicas, namespace, timeout);
        if (replicas == 0) {
            Poller.pollUntilTrue(
                    () -> run(Duration.ofSeconds(15), "kubectl", "-n", namespace, "get", "pods",
                            "-l", "app=" + appLabel, "-o", "name").isBlank(),
                    timeout, "all pods of " + statefulSet + " to be terminated");
        } else {
            run(timeout, "kubectl", "-n", namespace, "rollout", "status", "statefulset/" + statefulSet,
                    "--timeout=" + timeout.toSeconds() + "s");
        }
    }

    /**
     * Scales a StatefulSet to the desired number of replicas.
     *
     * @param statefulSet the StatefulSet name
     * @param replicas    the desired number of replicas
     * @param namespace   the Kubernetes namespace
     * @param timeout     bounded wait for the command to complete
     */
    public static void scaleStatefulSet(String statefulSet, int replicas, String namespace, Duration timeout) {
        run(timeout, "kubectl", "-n", namespace, "scale", "statefulset/" + statefulSet,
                "--replicas=" + replicas);
    }

    /**
     * Triggers a rolling restart of a StatefulSet and waits for the rollout to complete.
     * The pod is recreated on its existing PersistentVolumeClaim,
     * so persisted data survives (used for the Cassandra restart).
     *
     * @param statefulSet the StatefulSet name, e.g. {@code "cassandra-statefulset"}
     * @param namespace   the Kubernetes namespace
     * @param timeout     bounded wait for the rollout to finish
     */
    public static void restartStatefulSetAndAwait(String statefulSet, String namespace, Duration timeout) {
        run(timeout, "kubectl", "-n", namespace, "rollout", "restart", "statefulset/" + statefulSet);
        run(timeout, "kubectl", "-n", namespace, "rollout", "status", "statefulset/" + statefulSet,
                "--timeout=" + timeout.toSeconds() + "s");
    }

    /**
     * Runs a kubectl command and returns its output.
     *
     * @param timeout bounded wait for the command
     * @param args    the arguments after {@code kubectl}
     * @return the combined stdout/stderr
     */
    public static String customKubectlCommand(Duration timeout, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "kubectl";
        System.arraycopy(args, 0, cmd, 1, args.length);
        return run(timeout, cmd);
    }

    /**
     * Reads a Secret value from the cluster and decodes it from Base64.
     *
     * @param secret    the Secret name
     * @param key       the key of the Secret data to read
     * @param namespace the Kubernetes namespace
     * @return the decoded value of the key
     */
    public static String secretValue(String secret, String key, String namespace) {
        String b64 = run(Duration.ofSeconds(15), "kubectl", "-n", namespace, "get", "secret", secret,
                "-o", "jsonpath={.data." + key + "}").trim();
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    /**
     * Returns the number of ready replicas of a StatefulSet or Deployment (0 if none).
     *
     * @param kind {@code "statefulset"} or {@code "deployment"}
     * @param name the name of the StatefulSet or Deployment
     * @param namespace the Kubernetes namespace
     * @return the number of ready replicas
     */
    public static int readyReplicas(String kind, String name, String namespace) {
        String out = run(Duration.ofSeconds(15), "kubectl", "-n", namespace, "get", kind + "/" + name,
                "-o", "jsonpath={.status.readyReplicas}").trim();
        return out.isEmpty() ? 0 : Integer.parseInt(out);
    }

    private static String run(Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = readOutput(process);
            if (!finished) {
                process.destroyForcibly();
                throw new AssertionError("kubectl command timed out after " + timeout + ": "
                        + String.join(" ", command) + "\nOutput so far:\n" + output);
            }
            if (process.exitValue() != 0) {
                throw new AssertionError("kubectl command failed (exit " + process.exitValue() + "): "
                        + String.join(" ", command) + "\nOutput:\n" + output);
            }
            return output;
        } catch (Exception e) {
            throw new AssertionError("Could not run kubectl command: "
                    + String.join(" ", command), e);
        }
    }

    private static String readOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
