#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  echo "created $ENV_FILE from .env.example"
fi

if grep -q '^APP_EMAIL_PROVIDER=' "$ENV_FILE"; then
  sed -i.bak 's/^APP_EMAIL_PROVIDER=.*/APP_EMAIL_PROVIDER=console/' "$ENV_FILE"
else
  printf '\nAPP_EMAIL_PROVIDER=console\n' >> "$ENV_FILE"
fi
rm -f "$ENV_FILE.bak"

echo "APP_EMAIL_PROVIDER=console"
echo "done. restart backend to apply:"
echo "  docker compose up -d --build --force-recreate backend"
