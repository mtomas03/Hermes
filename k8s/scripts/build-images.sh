#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
K8S_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd -- "$K8S_ROOT/.." && pwd)"

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker is required"
command -v minikube >/dev/null 2>&1 || fail "minikube is required"
[[ -x "$REPO_ROOT/gradlew" ]] || fail "Gradle wrapper not found at $REPO_ROOT/gradlew"

if ! minikube status --format='{{.Host}}' 2>/dev/null | grep -q '^Running$'; then
  echo "Minikube is not running. Start it with deploy.sh first."
  exit 1
fi

eval "$(minikube docker-env)"

build_image() {
  local module="$1"
  local image="$2"
  local gradle_task=":${module//\//:}:bootJar"

  echo "- Building Gradle artifact for $module ($gradle_task)"
  (cd "$REPO_ROOT" && ./gradlew "$gradle_task" -x test)

  echo "- Building Docker image $image"
  docker build --pull=false -t "$image" -f "$REPO_ROOT/$module/Dockerfile" "$REPO_ROOT/$module"
}

build_image "server/gateway" "hermes/gateway:local"
build_image "server/worker" "hermes/worker:local"

echo "Images built inside Minikube's Docker daemon."
