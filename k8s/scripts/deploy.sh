#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
K8S_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd -- "$K8S_ROOT/.." && pwd)"
NAMESPACE="hermes-namespace"

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v minikube >/dev/null 2>&1 || fail "minikube is required"
command -v kubectl >/dev/null 2>&1 || fail "kubectl is required"
command -v docker >/dev/null 2>&1 || fail "docker is required"
[[ -x "$REPO_ROOT/gradlew" ]] || fail "Gradle wrapper not found at $REPO_ROOT/gradlew"

cd "$SCRIPT_DIR"

echo "[1/8] Starting Minikube"
if ! minikube status --format='{{.Host}}' 2>/dev/null | grep -q '^Running$'; then
  minikube start
fi

echo "[2/8] Enabling NGINX Ingress"
minikube addons enable ingress

echo "[3/8] Building Gateway and Worker images"
"$SCRIPT_DIR/build-images.sh"

echo "[4/8] Recreating Cassandra schema ConfigMap and Job"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" create configmap cassandra-schema --from-file=schema.cql="$REPO_ROOT/server/db/schema.cql" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" delete job cassandra-schema --ignore-not-found=true

echo "[5/8] Applying Kubernetes manifests"
kubectl apply -k "$K8S_ROOT"

echo "[6/8] Waiting for the infrastructure"
kubectl -n "$NAMESPACE" rollout status statefulset/postgres-statefulset --timeout=180s
kubectl -n "$NAMESPACE" rollout status statefulset/cassandra-statefulset --timeout=240s
kubectl -n "$NAMESPACE" rollout status statefulset/kafka-statefulset --timeout=240s
kubectl -n "$NAMESPACE" rollout status deployment/redis-deployment --timeout=120s

echo "[7/8] Initialising Cassandra schema"
kubectl -n "$NAMESPACE" wait --for=condition=complete job/cassandra-schema --timeout=300s

echo "[8/8] Waiting for applications"
kubectl -n "$NAMESPACE" rollout status deployment/gateway-deployment --timeout=240s
kubectl -n "$NAMESPACE" rollout status deployment/worker-deployment --timeout=240s

echo
echo "Hermes deployment is ready."
echo "Minikube IP: $(minikube ip)"
echo "Add the following line to your hosts file:"
echo "$(minikube ip) hermes.local"
echo
echo "Gateway HTTP:  http://hermes.local/"
echo "Gateway WS:    ws://hermes.local/ws"
