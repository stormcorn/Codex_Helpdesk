#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-/opt/fullstack}"
PROJECT_NAME="${PROJECT_NAME:-helpdesk}"
API_URL="${API_URL:-http://localhost:5173/api/hello}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"
SLEEP_SECONDS=2
MAX_RETRIES=$((MAX_WAIT_SECONDS / SLEEP_SECONDS))

cd "$PROJECT_DIR"

echo "== GIT SYNC (origin/main) =="
git fetch origin main
git reset --hard origin/main

echo
echo "== DEPLOY =="
docker compose -p "$PROJECT_NAME" up -d --build --force-recreate backend frontend

echo
echo "== WAIT FOR API READY (max ${MAX_WAIT_SECONDS}s) =="
ok=0
for i in $(seq 1 "$MAX_RETRIES"); do
  code="$(curl -s -o /tmp/helpdesk_api_check.out -w "%{http_code}" "$API_URL" || true)"
  if [ "$code" = "200" ]; then
    echo "API ready (HTTP 200)"
    ok=1
    break
  fi
  echo "[$i/$MAX_RETRIES] waiting... (http=$code)"
  sleep "$SLEEP_SECONDS"
done

echo
echo "== STATUS =="
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | egrep 'NAMES|fullstack-frontend|fullstack-backend|fullstack-postgres'

if [ "$ok" != "1" ]; then
  echo
  echo "== BACKEND LOGS (tail) =="
  docker logs --tail 120 fullstack-backend || true
  echo
  echo "== FRONTEND LOGS (tail) =="
  docker logs --tail 80 fullstack-frontend || true
  echo "Deploy failed: API not ready within timeout" >&2
  exit 1
fi

echo
echo "== FRONTEND ASSET HASH =="
INDEX="$(curl -sS http://localhost:5173)"
echo "$INDEX" | grep -o '/assets/index-[^"]*\.js' | head -n 1 || true

echo
echo "== API RESPONSE =="
cat /tmp/helpdesk_api_check.out || true
echo
