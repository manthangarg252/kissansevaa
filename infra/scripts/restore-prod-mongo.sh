#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

ARCHIVE_PATH="${1:-}"
TARGET_DB=""
SOURCE_DB="${MONGO_APP_DB:-kissansevaai}"
DROP_EXISTING="false"

if [[ -z "$ARCHIVE_PATH" || ! -f "$ARCHIVE_PATH" ]]; then
  echo "Usage: $0 <path-to-archive.gz> [--target-db <db_name>] [--drop]" >&2
  exit 1
fi
shift || true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-db)
      TARGET_DB="$2"
      shift 2
      ;;
    --source-db)
      SOURCE_DB="$2"
      shift 2
      ;;
    --drop)
      DROP_EXISTING="true"
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

TARGET_DB="${TARGET_DB:-${MONGO_APP_DB:-kissansevaai}}"
gzip -t "$ARCHIVE_PATH"

RESTORE_ARGS=(
  --username "$MONGO_INITDB_ROOT_USERNAME"
  --password "$MONGO_INITDB_ROOT_PASSWORD"
  --authenticationDatabase admin
  --archive
  --gzip
  --nsInclude "${SOURCE_DB}.*"
  --nsFrom "${SOURCE_DB}.*"
  --nsTo "${TARGET_DB}.*"
)
if [[ "$DROP_EXISTING" == "true" ]]; then
  RESTORE_ARGS+=(--drop)
fi

echo "Restoring $ARCHIVE_PATH (${SOURCE_DB}.* -> ${TARGET_DB}.*)..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mongodb \
  mongorestore "${RESTORE_ARGS[@]}" < "$ARCHIVE_PATH"

echo "Restore completed into database '$TARGET_DB'."
