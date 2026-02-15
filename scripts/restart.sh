#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="fast"
if [[ "${1:-}" == "--build" ]]; then
  MODE="build"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found in PATH"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "docker daemon is not ready"
  exit 1
fi

echo "[restart] mode=$MODE"

if [[ "$MODE" == "build" ]]; then
  docker compose up -d --build
else
  docker compose restart
fi

echo "[restart] services"
docker compose ps
