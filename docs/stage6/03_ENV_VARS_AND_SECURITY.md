# Stage 6 — Environment Variables & Security Hardening

## 1. Environment Configuration Template

All required variables are templated in [.env.prod.example](../../.env.prod.example). **Never commit `.env.prod` or real credentials to Git.**

| Variable | Service(s) | Required | Default / Notes |
| --- | --- | --- | --- |
| `KISSAN_API_DOMAIN` | Caddy | Yes (Prod) | Public HTTPS hostname, e.g. `api.yourdomain.com` |
| `CADDY_ACME_EMAIL` | Caddy | Yes (Prod) | Email for Let's Encrypt expiry/TLS notices |
| `CADDYFILE_PATH` | Caddy | No | Defaults to `./infra/caddy/Caddyfile` |
| `CADDY_HTTP_PORT` / `CADDY_HTTPS_PORT` | Caddy | No | `80` / `443` |
| `MONGO_INITDB_ROOT_USERNAME` | MongoDB, scripts | Yes | Mongo admin root username (e.g. `kissan_root`) |
| `MONGO_INITDB_ROOT_PASSWORD` | MongoDB, scripts | Yes | Generate via `openssl rand -hex 24` |
| `MONGO_APP_DB` | MongoDB, Backend | No | `kissansevaai` |
| `MONGO_APP_USER` | MongoDB, Backend | No | `kissan_app` (least-privilege `readWrite` on app DB) |
| `MONGO_APP_PASSWORD` | MongoDB, Backend | Yes | Different `openssl rand -hex 24` secret (avoid `@:/#?%`) |
| `NODE_ENV` | Backend | Yes | `production` (disables dev JSONL request logging) |
| `PORT` | Backend | No | `5000` (internal container port) |
| `JWT_SECRET` | Backend | Yes | Generate via `openssl rand -hex 32`; backend exits if absent |
| `ML_SERVICE_URL` | Backend | Yes | `http://ml-service:8000` |
| `GEMINI_API_KEY` | Backend | Recommended | Gemini Developer API key for second opinion, chatbot, voice extraction, livestock AI, planner |
| `GEMINI_MODEL` | Backend | No | `gemini-3-flash-preview` |
| `GEMINI_FALLBACK_MODELS` | Backend | No | Comma-separated fallback models if needed |
| `GEMINI_TIMEOUT_MS` | Backend | No | `25000` |
| `GEMINI_MAX_RETRIES` | Backend | No | `2` |
| `GEMINI_USER_RPM` / `GEMINI_USER_RPD` | Backend | No | `20` / `200` |
| `DATA_GOV_IN_API_KEY` | Backend | Optional | AGMARKNET mandi API key (falls back to curated market prices when omitted or rate-limited) |
| `WEATHER_CACHE_TTL_MINUTES` | Backend | No | `30` (Open-Meteo requires no key) |
| `ACTIVE_CROP_MODEL` | ML Service | Yes | Must remain `v1` |
| `ENABLE_V3_SHADOW` | ML Service | No | `true` (V3 logs shadow metrics only) |
| `V3_LOAD_FULL_FAISS` | ML Service | No | `true` |

## 2. Security Controls Implemented

1. **Secrets Hygiene:** `.gitignore` ignores `.env`, `.env.*`, `backups/`, `*.archive.gz`, and keystores while explicitly allowing `!.env.prod.example`.
2. **Non-Root Containers:**
   - [backend/Dockerfile](../../backend/Dockerfile) runs under the unprivileged `node` user with `/sbin/tini` signal handling and graceful `SIGTERM`/`SIGINT` MongoDB shutdown in [backend/server.js](../../backend/server.js).
   - [ml-service/Dockerfile](../../ml-service/Dockerfile) runs under system user `mluser` (`UID 10001`) with `/usr/bin/tini` and read-only host mounts for model weights.
3. **Unauthenticated Secret-Free Health Probe:** `GET /health` in [backend/app.js](../../backend/app.js) checks `mongoose.connection.readyState === 1`, returning HTTP `200` (`healthy`) or `503` (`degraded`) with uptime and service name only — never exposing `MONGO_URI`, `JWT_SECRET`, or API keys. `app.set('trust proxy', 1)` ensures forwarded headers from Caddy are honored safely.
4. **MongoDB Least Privilege:** [infra/mongo/init-app-user.sh](../../infra/mongo/init-app-user.sh) creates `kissan_app` scoped exclusively with `readWrite` on the `kissansevaai` database; root credentials are kept out of Node's `MONGO_URI`.
5. **Android Transport & Hardening:** Release builds lock `cleartextTrafficPermitted="false"`, ignore stored DataStore URL overrides, remove dev fallback hosts (`127.0.0.1`, `10.0.2.2`, `192.168.1.7`), and reject non-HTTPS or private hosts at both Gradle build time and runtime via `CloudUrlValidator`.
