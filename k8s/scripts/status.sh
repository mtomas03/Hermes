#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl is required" >&2; exit 1; }
NAMESPACE="hermes-namespace"

echo 'Pods'
kubectl -n "$NAMESPACE" get pods -o wide || true
echo
echo 'Services'
kubectl -n "$NAMESPACE" get svc || true
echo
echo 'Ingress'
kubectl -n "$NAMESPACE" get ingress || true
echo
echo 'Jobs'
kubectl -n "$NAMESPACE" get jobs || true
