package it.unibo.hermes.e2e.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A simple wrapper around the {@code kubectl} CLI for those few error/recovery scenarios
 * that require direct action on the cluster.
 */
public final class Kubectl {

    private Kubectl() {
    }

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
     * @param kind      {@code "statefulset"} or {@code "deployment"}
     * @param name      the name of the StatefulSet or Deployment
     * @param namespace the Kubernetes namespace
     * @return the number of ready replicas
     */
    public static int readyReplicas(String kind, String name, String namespace) {
        String out = run(Duration.ofSeconds(15), "kubectl", "-n", namespace, "get", kind + "/" + name,
                "-o", "jsonpath={.status.readyReplicas}").trim();
        return out.isEmpty() ? 0 : Integer.parseInt(out);
    }

    /**
     * Returns the logs of a Pod's container.
     *
     * @param pod       the Pod name
     * @param namespace the Kubernetes namespace
     * @param timeout   bounded wait for the command
     * @return the container log
     */
    public static String podLogs(String pod, String namespace, Duration timeout) {
        return run(timeout, "kubectl", "-n", namespace, "logs", pod);
    }

    /**
     * Returns the names of the Pods matching a label selector,
     * including Pods that are still terminating.
     *
     * @param labelSelector a label selector
     * @param namespace     the Kubernetes namespace
     * @return the Pod names, sorted
     */
    public static List<String> podNames(String labelSelector, String namespace) {
        return run(Duration.ofSeconds(15), "kubectl", "-n", namespace, "get", "pods", "-l", labelSelector,
                "-o", "name").lines()
                .filter(line -> line.startsWith("pod/")) // skips "No resources found"
                .map(line -> line.substring("pod/".length()).trim())
                .sorted().toList();
    }

    /**
     * Waits until the given Deployment has the expected number of Pods ready
     * and belonging to the same ReplicaSet.
     *
     * <p> Pod names are {@code <deployment>-<replicaset hash>-<suffix>}:
     * dropping the suffix identifies the ReplicaSet.
     *
     * @param deployment    the Deployment name
     * @param labelSelector the label selecting its Pods
     * @param replicas      the expected number of Pods
     * @param namespace     the Kubernetes namespace
     * @param timeout       the maximum time to wait
     * @return the Pod names, sorted
     */
    public static List<String> awaitReadyPods(String deployment, String labelSelector, int replicas,
                                              String namespace, Duration timeout) {
        return Poller.pollUntil(() -> {
            List<String> pods = podNames(labelSelector, namespace);
            boolean sameReplicaSet = !pods.isEmpty() && pods.getFirst().startsWith(deployment + "-")
                    && pods.stream().map(p -> p.substring(0, p.lastIndexOf('-'))).distinct().count() == 1;
            return pods.size() == replicas && sameReplicaSet
                    && readyReplicas("deployment", deployment, namespace) == replicas
                    ? Optional.of(pods) : Optional.empty();
        }, timeout, replicas + " Ready " + deployment + " Pods of a single ReplicaSet");
    }

    private static String run(Duration timeout, String... command) {
        Path outFile = null;
        try {
            outFile = Files.createTempFile("kubectl-", ".out");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outFile.toFile())
                    .start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            String output = Files.readString(outFile);
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
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AssertionError("Could not run kubectl command: " + String.join(" ", command), e);
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
