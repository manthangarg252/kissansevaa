#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
TARGET_REF="${1:-}"

if [[ -z "$TARGET_REF" && -f "$ROOT_DIR/backups/.last_known_good_commit" ]]; then
  TARGET_REF="$(cat "$ROOT_DIR/backups/.last_known_good_commit")"
fi

if [[ -z "$TARGET_REF" ]]; then
  echo "Usage: $0 <git-commit-or-tag>" >&2
  exit 1
fi

echo "Rolling back repository code cleanly to: $TARGET_REF"
git -C "$ROOT_DIR" checkout "$TARGET_REF"

ENV_FILE="$ENV_FILE" "$ROOT_DIR/infra/scripts/deploy.sh"
