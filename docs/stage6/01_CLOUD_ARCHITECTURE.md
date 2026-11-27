# Stage 6 — Production Cloud Architecture Overview

This document describes the production container architecture for running **KissanSevaAI** 24/7 on a single Google Cloud Compute Engine Ubuntu VM using `docker-compose.prod.yml`.

## Architecture Diagram

```
Internet (Farmer Android App / HTTPS Clients)
   │
   │  HTTPS :443 (TLS via Let's Encrypt) / HTTP :80 -> redirect :443
   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Docker Host (GCP Compute Engine Ubuntu 24.04 LTS VM)                    │
│ Firewall & Compose Port Exposure: ONLY 80/tcp, 443/tcp, 443/udp (+ SSH) │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Public Reverse Proxy: caddy (caddy:2-alpine, mem limit 128M)       │  │
│  │ - Automatic HTTPS TLS termination ($KISSAN_API_DOMAIN)            │  │
│  │ - HSTS / X-Content-Type-Options / X-Frame-Options headers          │  │
│  │ - Request body cap: 15MB (crop & livestock multipart uploads)     │  │
│  └───────────────────────────┬───────────────────────────────────────┘  │
│                              │ kissansevaai_internal bridge network     │
│  ┌───────────────────────────▼───────────────────────────────────────┐  │
│  │ API Backend: backend (Node.js 22 Alpine, non-root user "node")    │  │
│  │ - Internal port 5000 only (NOT published on host)                 │  │
│  │ - Healthcheck: GET /health (verifies MongoDB readyState === 1)     │  │
│  │ - Persistent volumes: backend_uploads, backend_logs               │  │
│  │ - Calls external APIs: Gemini API, Open-Meteo, data.gov.in        │  │
│  └───────────────┬───────────────────────────────┬───────────────────┘  │
│                  │                               │                      │
│  ┌───────────────▼─────────────┐   ┌─────────────▼───────────────────┐  │
│  │ Database: mongodb (mongo:8) │   │ ML Inference: ml-service        │  │
│  │ - Internal port 27017 only  │   │ - Python 3.11-slim, non-root    │  │
│  │ - Auth enabled (--auth)     │   │ - Internal port 8000 only       │  │
│  │ - Least-privilege app user  │   │ - V1 DINOv2+FAISS ACTIVE        │  │
│  │   ($MONGO_APP_USER)         │   │ - V3 Hybrid in SHADOW MODE only │  │
│  │ - --wiredTigerCacheSizeGB   │   │ - Read-only model bind mounts   │  │
│  │   0.5 (fits 4GB VM safely)  │   │ - Persistent ml_logs volume     │  │
│  │ - Volume: mongodb_data      │   │ - Single uvicorn worker (1536M) │  │
│  └─────────────────────────────┘   └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Design Guarantees

1. **Zero Public Database or ML Exposure:** Neither `mongodb` (`27017`), `backend` (`5000`), nor `ml-service` (`8000`) defines host `ports:` in [docker-compose.prod.yml](../../docker-compose.prod.yml). Only `caddy` binds host ports `80` and `443`.
2. **Crop Model Policy Preserved:** `ACTIVE_CROP_MODEL=v1` and `ENABLE_V3_SHADOW=true` are enforced by default in [ml-service/Dockerfile](../../ml-service/Dockerfile) and [docker-compose.prod.yml](../../docker-compose.prod.yml). Production farmers continue receiving the verified V1 DINOv2 prediction contract while V3 logs shadow telemetry.
3. **Bounded Disk & Memory Footprint:**
   - Every service uses Docker `json-file` log rotation (`max-size: 10m`, `max-file: 5`, capped at `50MB` per service).
   - Container memory limits total `3200M` (`mongodb` `1024M`, `ml-service` `1536M`, `backend` `512M`, `caddy` `128M`), leaving headroom on an `e2-medium` (`4GB` RAM) VM.
   - `ml-service` mounts model weights read-only (`:ro`) so host weight swaps or rollbacks do not duplicate 250MB+ inside Docker image layers.
4. **Independent Sarvam Telephone Agent:** No backend webhooks or calling integration were added; the Android app continues displaying the configured Sarvam helpline number (`SARVAM_HELPLINE_NUMBER`).
