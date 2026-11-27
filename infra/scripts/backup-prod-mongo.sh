#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
RETENTION_COUNT="${RETENTION_COUNT:-14}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

DB_NAME="${MONGO_APP_DB:-kissansevaai}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_ARCHIVE="$BACKUP_DIR/prod-${DB_NAME}-${TIMESTAMP}.archive.gz"

mkdir -p "$BACKUP_DIR"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mongodb \
  mongodump \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --db "$DB_NAME" \
    --archive \
    --gzip \
  > "$OUT_ARCHIVE"

gzip -t "$OUT_ARCHIVE"
chmod 600 "$OUT_ARCHIVE"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUT_ARCHIVE" > "${OUT_ARCHIVE}.sha256"
else
  shasum -a 256 "$OUT_ARCHIVE" > "${OUT_ARCHIVE}.sha256"
fi

# Retain only the newest RETENTION_COUNT backups
archives=()
while IFS= read -r archive_line; do
  [[ -n "$archive_line" ]] && archives+=("$archive_line")
done < <(ls -1t "$BACKUP_DIR"/prod-"$DB_NAME"-*.archive.gz 2>/dev/null || true)
if (( ${#archives[@]} > RETENTION_COUNT )); then
  for old in "${archives[@]:$RETENTION_COUNT}"; do
    rm -f "$old" "${old}.sha256"
  done
fi

echo "Production backup complete: $OUT_ARCHIVE"
