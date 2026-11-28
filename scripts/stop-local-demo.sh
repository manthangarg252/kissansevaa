#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/.demo_pids"
MONGO_PID_FILE="$ROOT_DIR/backend/data/mongod.pid"

echo "=== Stopping KissanSevaAI Local Demo Services (preserving MongoDB data) ==="

stop_pid_file() {
  local file="$1"
  local label="$2"
  if [[ -f "$file" ]]; then
    local pid
    pid="$(tr -d '[:space:]' < "$file")"
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      for _ in $(seq 1 10); do
        kill -0 "$pid" >/dev/null 2>&1 || break
        sleep 0.5
      done
      echo "[OK] Stopped $label (PID $pid)"
    fi
    rm -f "$file"
  fi
}

stop_pid_file "$PID_DIR/node.pid" "Node Backend"
stop_pid_file "$PID_DIR/ml.pid" "FastAPI ML Service"

# Fallback: stop any remaining dev server / ML app.py processes cleanly without touching data
pkill -f "backend/scripts/startDevServer.js" >/dev/null 2>&1 || true
pkill -f "ml-service.*app.py" >/dev/null 2>&1 || true

# Cleanly stop persistent mongod if running via PID file (never deletes backend/data/db)
stop_pid_file "$MONGO_PID_FILE" "Persistent MongoDB"

echo "All demo services stopped safely. MongoDB data at backend/data/db is intact."
