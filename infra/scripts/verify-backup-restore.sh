#!/usr/bin/env bash
set -euo pipefail

# Safely tests backup + restore into an ephemeral scratch DB without modifying live data.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
ARCHIVE_PATH="${1:-}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

APP_DB="${MONGO_APP_DB:-kissansevaai}"
SCRATCH_DB="${APP_DB}_restore_verify_$$"

cleanup() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mongodb \
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --eval "db.getSiblingDB('$SCRATCH_DB').dropDatabase()" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ -z "$ARCHIVE_PATH" ]]; then
  ENV_FILE="$ENV_FILE" "$ROOT_DIR/infra/scripts/backup-prod-mongo.sh"
  ARCHIVE_PATH="$(ls -1t "$ROOT_DIR"/backups/prod-"$APP_DB"-*.archive.gz | head -n 1)"
fi

ENV_FILE="$ENV_FILE" "$ROOT_DIR/infra/scripts/restore-prod-mongo.sh" "$ARCHIVE_PATH" --target-db "$SCRATCH_DB" --drop

echo "=== Verified Restored Collections in $SCRATCH_DB ==="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mongodb \
  mongosh --quiet \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --eval "const d = db.getSiblingDB('$SCRATCH_DB'); d.getCollectionNames().forEach(c => print(c + ': ' + d.getCollection(c).countDocuments()));"
