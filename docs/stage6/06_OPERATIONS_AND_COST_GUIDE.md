# Stage 6 — 24/7 Operations, Monitoring, Rollback & Cost Guide

## 1. Operational Commands (`infra/scripts/`)

| Task | Command | Notes |
| --- | --- | --- |
| Deploy / update | `./infra/scripts/deploy.sh` | Verifies `.env.prod` + model weights, records last git commit, builds & starts stack, waits for healthchecks |
| Health verification | `./infra/scripts/healthcheck.sh` | Checks compose status, backend `/health`, ML `/health` (`active_model=v1`, `shadow_mode_enabled=true`), Mongo ping |
| Tail logs | `./infra/scripts/logs.sh` (or pass service name, e.g. `backend`) | Streams last 200 lines + live updates |
| Stop / restart | `docker compose --env-file .env.prod -f docker-compose.prod.yml restart` | `restart: unless-stopped` brings containers back automatically after VM reboot or crash |
| Resource & disk check | `./infra/scripts/monitor-resources.sh` | Shows `df -h /`, RAM, `docker system df`, `docker stats`, and container restart counts |
| Backup MongoDB | `./infra/scripts/backup-prod-mongo.sh` | Compressed `mongodump` + SHA256 + 14-backup rotation |
| Verify restore | `./infra/scripts/verify-backup-restore.sh` | Round-trip test into isolated scratch DB |
| Rollback app code | `./infra/scripts/rollback.sh [<git-ref>]` | Checks out previous recorded commit and rebuilds/redeploys |
| Prune stale images | `docker image prune -f` | Safe periodic cleanup after verified deploys (never run `docker volume prune`!) |

## 2. Monitoring & Alerting Recommendations

- Enable **Google Cloud Ops Agent** or GCP Uptime Check probing `https://$KISSAN_API_DOMAIN/health` every 1–5 minutes with email alert on 2 consecutive non-200 checks.
- Monitor root disk utilization (`df -h /`); keep usage below 75% by running `docker image prune -f` after updates and relying on Compose log rotation (`50 MB` cap per service).
- Inspect `ml_logs` volume (`shadow_comparison.jsonl`) periodically or rotate if request volume exceeds thousands of daily scans.

## 3. Cost & Billing Safety Recommendations

1. **Compute Engine Cost:** `e2-medium` (2 vCPU, 4 GB RAM, `30 GB pd-balanced`) is **not** covered by the `e2-micro` Always Free tier. Set a **Cloud Billing Budget & Alert** at 50%, 90%, and 100% of your expected monthly budget before leaving the VM running continuously.
2. **External IP Cost:** Reserved static external IPv4 addresses remain low-cost while attached to a running VM, or incur idle charge if left detached when VM is deleted — delete unused static IPs when decommissioning.
3. **Egress & Media Uploads:** Farmer crop images stay cached cleanly on VM volumes (`backend_uploads`), avoiding egress amplification; Caddy compresses JSON responses (`encode gzip zstd`).
4. **Gemini Quota / Cost Control:**
   - Free-tier Gemini keys hit daily request limits (`RESOURCE_EXHAUSTED` / 429) once farmer traffic scales beyond demo volume; upgrade to Pay-As-You-Go or set `GEMINI_USER_RPM` / `GEMINI_USER_RPD` per farmer in `.env.prod`.
   - Open-Meteo weather requires **no paid API key**; `data.gov.in` mandi API is optional with curated fallback prices.
5. **Never Lose User Data When Recreating Containers:** Always keep named volumes (`mongodb_data`, `backend_uploads`, `caddy_data`). Never run `docker compose down -v` in production!
