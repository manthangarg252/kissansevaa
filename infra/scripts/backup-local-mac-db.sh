#!/usr/bin/env bash
set -euo pipefail

# Creates a non-destructive gzipped mongodump backup (and uploads tar.gz if present)
# of the local Mac MongoDB database at backend/data/db without modifying local data.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
DB_NAME="${MONGO_APP_DB:-kissansevaai}"
LOCAL_DATA_DIR="${LOCAL_DATA_DIR:-$ROOT_DIR/backend/data/db}"
LOCAL_UPLOADS_DIR="${LOCAL_UPLOADS_DIR:-$ROOT_DIR/backend/uploads}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_ARCHIVE="$BACKUP_DIR/local-${DB_NAME}-${TIMESTAMP}.archive.gz"

mkdir -p "$BACKUP_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker CLI is required for containerized mongodump when host mongodump is unavailable." >&2
  exit 1
fi

TEMP_DATA_COPY=""
EPHEMERAL_CID=""
cleanup() {
  if [[ -n "$EPHEMERAL_CID" ]]; then
    docker rm -f "$EPHEMERAL_CID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$TEMP_DATA_COPY" && -d "$TEMP_DATA_COPY" ]]; then
    rm -rf "$TEMP_DATA_COPY"
  fi
}
trap cleanup EXIT

if lsof -iTCP:27017 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Detected live local mongod on port 27017; streaming live mongodump via mongo:8.0..."
  docker run --rm --add-host=host.docker.internal:host-gateway mongo:8.0 \
    mongodump --host host.docker.internal --port 27017 --db "$DB_NAME" --archive --gzip \
    > "$OUT_ARCHIVE"
else
  if [[ ! -d "$LOCAL_DATA_DIR" ]]; then
    echo "ERROR: Neither live mongod on 27017 nor directory $LOCAL_DATA_DIR found." >&2
    exit 1
  fi
  echo "Local mongod is stopped; snapshotting $LOCAL_DATA_DIR read-safely via copy before mongodump..."
  TEMP_DATA_COPY="$(mktemp -d)"
  cp -R "$LOCAL_DATA_DIR/." "$TEMP_DATA_COPY/"
  rm -f "$TEMP_DATA_COPY/mongod.lock"

  EPHEMERAL_CID="$(docker run -d -v "$TEMP_DATA_COPY:/data/db" mongo:8.0 mongod --bind_ip_all)"
  for _ in $(seq 1 20); do
    if docker exec "$EPHEMERAL_CID" mongosh --quiet --eval 'db.adminCommand({ ping: 1 }).ok' | grep -q 1; then
      break
    fi
    sleep 1
  done
  docker exec "$EPHEMERAL_CID" mongodump --db "$DB_NAME" --archive --gzip > "$OUT_ARCHIVE"
fi

gzip -t "$OUT_ARCHIVE"
chmod 600 "$OUT_ARCHIVE"
shasum -a 256 "$OUT_ARCHIVE" > "${OUT_ARCHIVE}.sha256" 2>/dev/null || sha256sum "$OUT_ARCHIVE" > "${OUT_ARCHIVE}.sha256"

if [[ -d "$LOCAL_UPLOADS_DIR" ]] && [[ -n "$(ls -A "$LOCAL_UPLOADS_DIR" 2>/dev/null)" ]]; then
  UPLOADS_ARCHIVE="$BACKUP_DIR/local-uploads-${TIMESTAMP}.tar.gz"
  tar -czf "$UPLOADS_ARCHIVE" -C "$LOCAL_UPLOADS_DIR" .
  chmod 600 "$UPLOADS_ARCHIVE"
  echo "Created uploads archive: $UPLOADS_ARCHIVE"
fi

echo "Local DB backup succeeded (source data untouched): $OUT_ARCHIVE"
