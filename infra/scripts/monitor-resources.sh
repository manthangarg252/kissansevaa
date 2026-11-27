#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

echo "=== Host Disk Usage ==="
df -h /

echo "=== Memory ==="
free -m 2>/dev/null || vm_stat

echo "=== Docker Disk Usage ==="
docker system df

echo "=== Container Resources ==="
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}"

echo "=== Container Restart Counts ==="
for cid in $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q); do
  docker inspect --format '{{.Name}} state={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} restarts={{.RestartCount}}' "$cid"
done
