#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

DEPLOY_TARGET="${DEPLOY_SSH_TARGET:-}"
DEPLOY_PATH="${DEPLOY_PATH:-/opt/fullstack}"
DEPLOY_SCRIPT_PATH="${DEPLOY_SCRIPT_PATH:-./scripts/deploy_helpdesk.sh}"
PUSH_REMOTE="${PUSH_REMOTE:-origin}"
PUSH_BRANCH="${PUSH_BRANCH:-main}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/release_and_deploy.sh "commit message" [ssh-target]
  ./scripts/release_and_deploy.sh --deploy-only [ssh-target]

Env vars (recommended to avoid typing every time):
  DEPLOY_SSH_TARGET   e.g. deployuser@deploy-host
  DEPLOY_PATH         remote repo path (default: /opt/fullstack)
  DEPLOY_SCRIPT_PATH  remote deploy script path (default: ./scripts/deploy_helpdesk.sh)
  PUSH_REMOTE         git remote (default: origin)
  PUSH_BRANCH         git branch (default: main)

Examples:
  export DEPLOY_SSH_TARGET="<deploy-user>@<deploy-host>"
  ./scripts/release_and_deploy.sh "fix: update ticket mobile layout"
  ./scripts/release_and_deploy.sh --deploy-only
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

MODE="release"
COMMIT_MSG=""

if [[ "${1:-}" == "--deploy-only" ]]; then
  MODE="deploy-only"
  DEPLOY_TARGET="${2:-$DEPLOY_TARGET}"
else
  COMMIT_MSG="${1:-}"
  DEPLOY_TARGET="${2:-$DEPLOY_TARGET}"
fi

if [[ -z "$DEPLOY_TARGET" ]]; then
  echo "Error: missing deploy target. Set DEPLOY_SSH_TARGET or pass [ssh-target]." >&2
  usage
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "$PUSH_BRANCH" ]]; then
  echo "Warning: current branch is '$CURRENT_BRANCH' (expected '$PUSH_BRANCH')." >&2
fi

if [[ "$MODE" == "release" ]]; then
  if [[ -z "$COMMIT_MSG" ]]; then
    echo "Error: commit message is required for release mode." >&2
    usage
    exit 1
  fi

  echo "== GIT STATUS =="
  git status --short

  if [[ -n "$(git status --short)" ]]; then
    echo
    echo "== GIT COMMIT =="
    git add -A
    git commit -m "$COMMIT_MSG"
  else
    echo "No local changes to commit. Continue with push/deploy."
  fi

  echo
  echo "== GIT PUSH =="
  git push "$PUSH_REMOTE" "$PUSH_BRANCH"
fi

echo
echo "== REMOTE DEPLOY =="
echo "Target: $DEPLOY_TARGET"
ssh "$DEPLOY_TARGET" "cd '$DEPLOY_PATH' && '$DEPLOY_SCRIPT_PATH'"

echo
echo "Release + deploy completed."
