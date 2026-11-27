#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
WAIT_SECONDS=0

if [[ "${1:-}" == "--wait" && -n "${2:-}" ]]; then
  WAIT_SECONDS="$2"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

deadline=$((SECONDS + WAIT_SECONDS))
while true; do
  if docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T backend \
      curl -fsS http://127.0.0.1:5000/health >/dev/null 2>&1 \
    && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T ml-service \
      curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1; then
    break
  fi
  if (( SECONDS >= deadline )); then
    echo "ERROR: Services failed health verification within ${WAIT_SECONDS}s." >&2
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    exit 1
  fi
  sleep 3
done

echo "=== Compose Services ==="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

echo "=== Backend /health ==="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T backend \
  curl -fsS http://127.0.0.1:5000/health
echo ""

echo "=== ML Service /health ==="
ML_JSON="$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T ml-service curl -fsS http://127.0.0.1:8000/health)"
echo "$ML_JSON"

if ! echo "$ML_JSON" | grep -q '"active_model":"v1"'; then
  echo "WARNING: active_model is not v1!" >&2
fi
if ! echo "$ML_JSON" | grep -q '"shadow_mode_enabled":true'; then
  echo "WARNING: shadow_mode_enabled is not true!" >&2
fi

echo "=== MongoDB Ping ==="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mongodb \
  mongosh --quiet \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --eval 'db.adminCommand({ ping: 1 })'
