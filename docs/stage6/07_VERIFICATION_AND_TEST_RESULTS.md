# Stage 6 — Verification & Test Results

All checks below were executed on the local MacBook host and local Docker Desktop (`29.6.2`, `linux/aarch64`) after all Stage 6 code and container changes. **No live Google Cloud Platform resources were provisioned**, so public DNS/Let's Encrypt ACME issuance over ports `80`/`443` and live GCP VM runtime remain manual post-handoff steps ([08_MANUAL_GCP_CONSOLE_STEPS.md](./08_MANUAL_GCP_CONSOLE_STEPS.md)).

## Summary Matrix

| Suite / Verification Target | Scope & Command | Result |
| --- | --- | --- |
| ML Service unit & contract tests | `cd ml-service && venv/bin/pytest tests/ -v` | **16/16 passed** (`1.25s`, 5 pre-existing deprecation warnings) |
| Node.js backend tests | `cd backend && node --test tests/*.test.js` | **12/12 passed** (`0 failed`), including new `GET /health` test (`503 degraded` / `200 healthy` / no secret leakage) |
| Android JVM unit tests | `./android/gradlew -p android testDebugUnitTest -PKISSAN_PROD_API_URL=https://api.kissansevaai.example.com` (JDK 21) | **81/81 passed** (`0 failed`, `0 skipped`), including 4 new `Stage6CloudConfigTest` cases |
| Android Debug & Release APKs | `./android/gradlew -p android assembleDebug assembleRelease -PKISSAN_PROD_API_URL=https://api.kissansevaai.example.com` | **Built cleanly** (`app-debug.apk` `26M`, `app-release-unsigned.apk` `3.6M` R8-minified; release `BuildConfig.BASE_URL` = `"https://api.kissansevaai.example.com"`) |
| Android Gradle invalid URL rejection | `./android/gradlew -p android help -PKISSAN_PROD_API_URL=http://127.0.0.1:5050 --offline` | **Confirmed rejected** (`BUILD FAILED`) |
| Shell script syntax validation | `bash -n infra/mongo/init-app-user.sh infra/scripts/*.sh` | **Passed** (`exit 0`) |
| Docker Compose YAML validation | `docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --quiet` | **Passed** |
| Production Docker image builds | `docker compose --env-file .env.prod.example -f docker-compose.prod.yml build` | **Built** (`kissansevaai-ml:local` `590MB`, `kissansevaai-backend:local` `380MB`) |
| Local Mac DB backup (`backend/data/db`) | `BACKUP_DIR=/tmp/stage6_local_mac_backup ./infra/scripts/backup-local-mac-db.sh` | **Passed** without altering `backend/data/db` (exported all 14 collections + `local-uploads-*.tar.gz` + `.sha256`) |
| Isolated 4-container stack & E2E verification | Isolated Compose project (`kissansevaai_stage6_verify`, local HTTP Caddyfile on `127.0.0.1:18080`) | **All checks passed** (cleaned up afterward via `down -v`) |

## Container Stack Verification Details

During the isolated local container verification run (`CADDYFILE_PATH=./infra/caddy/Caddyfile.local` on `127.0.0.1:18080` with ephemeral credentials):

1. **Container Health:** `mongodb`, `ml-service`, `backend`, and `caddy` all reached `(healthy)` state.
2. **Host Port Isolation (`docker inspect`):**
   - `mongodb`: `HostConfig.PortBindings={}`
   - `backend`: `HostConfig.PortBindings={}`
   - `ml-service`: `HostConfig.PortBindings={}`
   - `caddy`: bound `127.0.0.1:18080 -> 80/tcp`, `127.0.0.1:18443 -> 443/tcp, 443/udp`
3. **Health Endpoints:**
   - Via Caddy (`GET http://127.0.0.1:18080/health`): HTTP `200`, `{"ok":true,"status":"healthy","service":"kissansevaai-backend","mongodb":"connected","uptimeSeconds":12}`
   - Inside ML container (`GET http://127.0.0.1:8000/health`): HTTP `200`, `active_model: "v1"`, `shadow_mode_enabled: true`, `v3_candidate_status: "ready_candidate_only"`, `embedding_dim: 768`, `classes: 67`, `reference_images: 2144`
4. **MongoDB Auth Enforcement:** Unauthenticated `mongosh` query inside the `mongodb` container was rejected cleanly; backend authenticated using `kissan_app_test` on `kissansevaai`.
5. **End-to-End Inference (`Caddy -> Backend -> ML Service -> MongoDB`):**
   - `POST /api/auth/register` issued JWT cleanly via authenticated MongoDB.
   - `POST /api/crop/analyze` with sample image `1795643035724-wheat_rust_macro.jpg` returned HTTP `200` with `source: "dinov2"`.
   - `ml-service` `/app/logs/shadow_comparison.jsonl` recorded both `active_model: "v1_dinov2_faiss"` (`Wheat/Wheat_Black_Rust`) and `shadow_model_version: "v3_svm_repaired"` (`v3_latency_ms: 16.232`).
6. **Containerized Backup & Restore Round-Trip:**
   - Gzipped `mongodump` archive verified with `gzip -t`.
   - `mongorestore` into `kissansevaai_restore_verify.*` restored `2 document(s) restored successfully. 0 document(s) failed to restore.` (`cropanalyses: 1`, `users: 1`), then dropped only `kissansevaai_restore_verify`.
7. **Observed Memory Usage (`docker stats --no-stream`):**
   - `caddy`: `14.56MiB / 128MiB` (`11.37%`)
   - `backend`: `42.52MiB / 512MiB` (`8.31%`)
   - `mongodb`: `151MiB / 1GiB` (`14.75%`)
   - `ml-service`: `395.7MiB / 1.5GiB` (`25.76%`)
   - **Combined idle/post-inference stack footprint:** `~604 MiB` across all 4 containers.

## Items Explicitly Not Executed

- Live GCP Compute Engine provisioning (`gcloud compute instances create`) — skipped per Stage 6 policy to avoid cloud spend.
- Let's Encrypt ACME HTTP-01 TLS certificate issuance on a public domain (`infra/caddy/Caddyfile`) — requires public DNS `A` record and public ports `80`/`443` on the VM; local Compose stack tested HTTP proxy routing via `infra/caddy/Caddyfile.local` instead.
- Release APK device installation and signing — `assembleRelease` produced `app-release-unsigned.apk` and passed R8 minification/resources shrinking without a production upload keystore.
- Connected Android instrumentation suite (`connectedDebugAndroidTest`) was not re-run in Stage 6 (only JVM unit tests `81/81` + debug/release APK compilation).
