#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DEVICE="${ADB_DEVICE:-RZCXC17RFEW}"
ADB_BIN="${ADB_BIN:-$(command -v adb || echo "/Users/manthangarg/Library/Android/sdk/platform-tools/adb")}"
EXIT_CODE=0

echo "=== KissanSevaAI Local Demo Readiness Check ==="

# 1. Ports
for port_label in "27017:MongoDB" "5050:Node Backend" "8000:FastAPI ML"; do
  port="${port_label%%:*}"
  label="${port_label##*:}"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[OK] $label listening on port $port"
  else
    echo "[FAIL] $label NOT listening on port $port" >&2
    EXIT_CODE=1
  fi
done

# 2. Backend /health
BACKEND_HEALTH="$(curl -fsS -m 5 http://127.0.0.1:5050/health 2>/dev/null || true)"
if echo "$BACKEND_HEALTH" | grep -q '"mongodb":"connected"'; then
  echo "[OK] Backend /health: $BACKEND_HEALTH"
else
  echo "[FAIL] Backend /health check failed: ${BACKEND_HEALTH:-unreachable}" >&2
  EXIT_CODE=1
fi

# 3. ML /health (V1 active + V3 shadow)
ML_HEALTH="$(curl -fsS -m 5 http://127.0.0.1:8000/health 2>/dev/null || true)"
if echo "$ML_HEALTH" | grep -q '"active_model":"v1"' && echo "$ML_HEALTH" | grep -q '"shadow_mode_enabled":true'; then
  echo "[OK] ML /health (V1 active, V3 shadow): $ML_HEALTH"
else
  echo "[FAIL] ML /health check failed: ${ML_HEALTH:-unreachable}" >&2
  EXIT_CODE=1
fi

# 4. AI / Market Status
AI_STATUS="$(curl -fsS -m 5 http://127.0.0.1:5050/api/ai/status 2>/dev/null || true)"
if [[ -n "$AI_STATUS" ]]; then
  echo "[INFO] AI/Market status: $AI_STATUS"
fi

# 5. ADB Device & Reverse Tunnels
if [[ -x "$ADB_BIN" ]]; then
  DEV_STATE="$("$ADB_BIN" devices | awk -v dev="$TARGET_DEVICE" 'NR>1 && $1==dev {print $2}')"
  if [[ "$DEV_STATE" == "device" ]]; then
    echo "[OK] Samsung Galaxy A35 ($TARGET_DEVICE) authorized over USB"
    REV_LIST="$("$ADB_BIN" -s "$TARGET_DEVICE" reverse --list 2>/dev/null || true)"
    if echo "$REV_LIST" | grep -q "tcp:5050" && echo "$REV_LIST" | grep -q "tcp:8000"; then
      echo "[OK] ADB reverse tunnels active (5050, 8000)"
    else
      echo "[FAIL] ADB reverse tunnels missing on $TARGET_DEVICE" >&2
      EXIT_CODE=1
    fi
  else
    echo "[WARN] Device $TARGET_DEVICE is '${DEV_STATE:-disconnected}' (USB offline)" >&2
    EXIT_CODE=1
  fi
else
  echo "[FAIL] adb binary not found at $ADB_BIN" >&2
  EXIT_CODE=1
fi

if [[ "$EXIT_CODE" -eq 0 ]]; then
  echo "DEMO_READY: YES"
else
  echo "DEMO_READY: NO"
fi
exit "$EXIT_CODE"
