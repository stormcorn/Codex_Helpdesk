#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  echo "created $ENV_FILE from .env.example"
fi

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <SENDGRID_API_KEY> [FROM_EMAIL] [FROM_NAME]"
  exit 1
fi

API_KEY="$1"
FROM_EMAIL="${2:-no-reply@example.com}"
FROM_NAME="${3:-Helpdesk}"

upsert() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i.bak "s#^${key}=.*#${key}=${value}#" "$ENV_FILE"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

upsert "APP_EMAIL_PROVIDER" "sendgrid"
upsert "APP_EMAIL_SENDGRID_API_KEY" "$API_KEY"
upsert "APP_EMAIL_FROM_EMAIL" "$FROM_EMAIL"
upsert "APP_EMAIL_FROM_NAME" "$FROM_NAME"
rm -f "$ENV_FILE.bak"

echo "APP_EMAIL_PROVIDER=sendgrid"
echo "APP_EMAIL_FROM_EMAIL=$FROM_EMAIL"
echo "APP_EMAIL_FROM_NAME=$FROM_NAME"
echo "done. restart backend to apply:"
echo "  docker compose up -d --build --force-recreate backend"
