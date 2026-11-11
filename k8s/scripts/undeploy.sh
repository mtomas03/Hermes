#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
K8S_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl is required" >&2; exit 1; }

echo "Tearing down Hermes"
kubectl delete -k "$K8S_ROOT" --ignore-not-found=true
