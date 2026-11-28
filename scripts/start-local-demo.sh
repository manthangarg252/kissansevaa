#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/.demo_pids"
TARGET_DEVICE="${ADB_DEVICE:-RZCXC17RFEW}"
ADB_BIN="${ADB_BIN:-$(command -v adb || echo "/Users/manthangarg/Library/Android/sdk/platform-tools/adb")}"
PYTHON_BIN="$ROOT_DIR/ml-service/venv/bin/python3"

mkdir -p "$PID_DIR" "$ROOT_DIR/ml-service/logs" "$ROOT_DIR/backend/logs"

is_port_open() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

echo "=== Starting KissanSevaAI Local USB Demo Stack ==="

# 1. Start FastAPI ML Service on :8000 (V1 active + V3 shadow) if not already listening
if is_port_open 8000; then
  echo "[ML] Port 8000 already listening; reusing running ML service."
else
  if [[ ! -x "$PYTHON_BIN" ]]; then
    echo "ERROR: ML Python venv not found at $PYTHON_BIN" >&2
    exit 1
  fi
  echo "[ML] Starting FastAPI ML service on :8000 (ACTIVE_CROP_MODEL=v1, ENABLE_V3_SHADOW=true)..."
  (
    cd "$ROOT_DIR/ml-service"
    ACTIVE_CROP_MODEL=v1 ENABLE_V3_SHADOW=true V3_LOAD_FULL_FAISS=true \
      nohup "$PYTHON_BIN" app.py > "$ROOT_DIR/ml-service/logs/ml-demo.log" 2>&1 &
    echo $! > "$PID_DIR/ml.pid"
  )
fi

# 2. Start Persistent MongoDB (:27017) + Node.js Backend (:5050) if not already listening
if is_port_open 5050; then
  echo "[Backend] Port 5050 already listening; reusing running Node backend."
else
  echo "[Backend] Starting persistent MongoDB + Node Express backend on :5050..."
  (
    cd "$ROOT_DIR"
    USE_IN_MEMORY_DB=false DEV_PORT=5050 \
      nohup node backend/scripts/startDevServer.js > "$ROOT_DIR/backend/logs/node-demo.log" 2>&1 &
    echo $! > "$PID_DIR/node.pid"
  )
fi

# 3. Wait for both /health endpoints
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1 && curl -fsS http://127.0.0.1:5050/health >/dev/null 2>&1; then
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo "ERROR: Services failed to become healthy within 60s." >&2
    tail -n 30 "$ROOT_DIR/ml-service/logs/ml-demo.log" "$ROOT_DIR/backend/logs/node-demo.log" 2>/dev/null || true
    exit 1
  fi
  sleep 2
done

# 4. Configure Samsung Galaxy A35 USB reverse forwarding
if [[ -x "$ADB_BIN" ]]; then
  DEV_STATE="$("$ADB_BIN" devices | awk -v dev="$TARGET_DEVICE" 'NR>1 && $1==dev {print $2}')"
  if [[ "$DEV_STATE" == "device" ]]; then
    "$ADB_BIN" -s "$TARGET_DEVICE" reverse tcp:5050 tcp:5050
    "$ADB_BIN" -s "$TARGET_DEVICE" reverse tcp:8000 tcp:8000
    echo "[ADB] Configured reverse ports 5050 and 8000 on $TARGET_DEVICE."
  else
    echo "[ADB] WARNING: Target device $TARGET_DEVICE state is '${DEV_STATE:-disconnected}'. Connect USB cable and authorize debugging." >&2
  fi
else
  echo "[ADB] WARNING: adb binary not found at $ADB_BIN." >&2
fi

"$ROOT_DIR/scripts/check-local-demo.sh"
