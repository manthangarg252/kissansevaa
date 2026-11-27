#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  echo "Run: cp .env.prod.example .env.prod && chmod 600 .env.prod" >&2
  exit 1
fi

if grep -q 'REPLACE_WITH_' "$ENV_FILE"; then
  echo "ERROR: $ENV_FILE still contains REPLACE_WITH_ placeholders. Fill all required secrets first." >&2
  exit 1
fi

# Verify required non-Git model weights exist before bringing up ML service
REQUIRED_ASSETS=(
  "$ROOT_DIR/ml-service/model_assets/dinov2_vits14_cls_mean.onnx"
  "$ROOT_DIR/ml-service/model_assets/crop_index.faiss"
  "$ROOT_DIR/ml-service/model_assets/classes.json"
  "$ROOT_DIR/ml-service/model_candidates/v3_svm_repaired/model_assets/crop_index_v3_repaired.faiss"
  "$ROOT_DIR/ml-service/model_candidates/v3_svm_repaired/model_assets/crop_classifier_svm_linear_cal_C2_0_v3_repaired_weights.npz"
)
for asset in "${REQUIRED_ASSETS[@]}"; do
  if [[ ! -f "$asset" ]]; then
    echo "ERROR: Required model asset missing: $asset" >&2
    exit 1
  fi
done

# Ensure non-root container user (mluser UID 10001) can read mounted weights
chmod -R a+rX "$ROOT_DIR/ml-service/model_assets" "$ROOT_DIR/ml-service/model_candidates/v3_svm_repaired/model_assets"

mkdir -p "$ROOT_DIR/backups"
if git -C "$ROOT_DIR" rev-parse HEAD >/dev/null 2>&1; then
  git -C "$ROOT_DIR" rev-parse HEAD > "$ROOT_DIR/backups/.last_known_good_commit"
fi

echo "Building and starting KissanSevaAI production stack..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans

echo "Waiting for services to become healthy..."
ENV_FILE="$ENV_FILE" "$ROOT_DIR/infra/scripts/healthcheck.sh" --wait 90
echo "Deploy complete."
