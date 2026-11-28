# KissanSevaAI — Cloud Infrastructure & Operations

Production deployment configuration for running **KissanSevaAI** 24/7 on a single Ubuntu VM (`docker-compose.prod.yml`, e.g., GCP Compute Engine `e2-medium`) with zero public database/ML port exposure.

---

## Contents

- `caddy/` — Reverse proxy configs (`Caddyfile` for automatic Let's Encrypt TLS on `:80`/`:443`, HSTS & security headers, 15MB upload cap; `Caddyfile.local` for local reverse-proxy testing).
- `mongo/init-app-user.sh` — Initializes least-privilege MongoDB application credentials inside authenticated `--auth` deployments.
- `scripts/`:
  - `deploy.sh` — Builds/starts production Compose stack and checks `/health`.
  - `healthcheck.sh` — Verifies container health across `caddy`, `backend`, `ml-service`, and `mongodb`.
  - `backup-local-mac-db.sh` / `backup-prod-mongo.sh` / `restore-prod-mongo.sh` / `verify-backup-restore.sh` — MongoDB dump, verification, and restore tooling.
  - `monitor-resources.sh`, `logs.sh`, `rollback.sh` — Operational monitoring and rollback helpers.

---

## Full Documentation

See [Stage 6 Cloud Documentation](../docs/stage6/01_CLOUD_ARCHITECTURE.md) and [.env.prod.example](../.env.prod.example).
