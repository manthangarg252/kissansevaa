# KissanSevaAI — Stage 3 Persistent MongoDB Architecture & Verification

**Status:** **`VERIFIED LIVE`** across full `mongod` (`27017`) and Node.js (`5050`) shutdown & restart  
**Live Storage Path:** `backend/data/db` (`67` WiredTiger database files on disk; excluded from Git via `.gitignore`)

---

## 1. Stage 3 Persistence Changes Implemented

Stage 2's dev startup ran against ephemeral `MongoMemoryServer`, wiping farmer edits when the Node process restarted. Stage 3 resolves this cleanly while keeping automated test isolation intact:

1. **Persistent Local MongoDB Launcher ([`backend/scripts/startPersistentMongo.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/scripts/startPersistentMongo.js)):**
   - Parses `MONGO_URI` (`mongodb://127.0.0.1:27017/kissansevaai` default) via `parseLocalMongoTarget()`.
   - Checks if `127.0.0.1:27017` is already accepting TCP connections (reuses existing daemon with `mode: "existing_local_mongod"`).
   - Otherwise resolves a local `mongod` executable in order (`MONGOD_BIN` env -> `/opt/homebrew/bin/mongod` -> `/usr/local/bin/mongod` -> `PATH` -> cached `~/.cache/mongodb-binaries/mongod-*`) and launches detached `mongod --dbpath backend/data/db --bind_ip 127.0.0.1 --port 27017`, recording PID in `backend/data/mongod.pid` and logs in `backend/logs/mongod.log`.
   - Verified runtime binary on host: `/Users/manthangarg/.cache/mongodb-binaries/mongod-arm64-darwin-8.2.6`.
2. **Idempotent Dev Server Startup ([`backend/scripts/startDevServer.js`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/scripts/startDevServer.js)):**
   - Defaults to `ensurePersistentMongo()` (`DEV_PORT || 5050` to avoid macOS ControlCenter `:5000` conflict).
   - Preserves ephemeral `MongoMemoryServer` when `USE_IN_MEMORY_DB === 'true'`, `--memory`, or `NODE_ENV === 'test'`.
   - `seedIfEmpty()` uses `findOne({ email: 'farmer@kissan.com' })` and per-collection `countDocuments() === 0` checks so existing farmer records, scans, plans, favorites, and bookmarks are **never dropped or reset** on startup.
3. **Optional Docker Volume Definition ([`backend/docker-compose.yml`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/docker-compose.yml)):**
   - Defines `mongo:8` container `kissansevaai-mongodb` bound to `127.0.0.1:27017` with named persistent volume `kissansevaai_mongodb_data:/data/db`.
4. **NPM Scripts & Git Hygiene:**
   - [`backend/package.json`](file:///Users/manthangarg/Desktop/kissansevaai-main/backend/package.json): added `"dev:persistent"`, `"dev:memory"`, and `"mongo:start"`.
   - [`.gitignore`](file:///Users/manthangarg/Desktop/kissansevaai-main/.gitignore#L43): ignores `backend/data/` and `backend/.mongodb-data/` so local database files stay uncommitted.

---

## 2. Live Restart-Survival Verification Evidence

Both the detached `mongod-arm64-darwin-8.2.6` daemon (`:27017`) and the Node.js server (`:5050`) were completely stopped and restarted cleanly in Stage 3. Post-restart API checks confirmed survival of all 7 required categories under farmer account `farmer@kissan.com` (`id: 6b07cf47d612ab7a47ac6d30`):

| Persistence Category | Post-Restart Observed Result |
|---|---|
| **1. Farmer Login & Profile** | `POST_RESTART_LOGIN: 200` (`id: 6b07cf47d612ab7a47ac6d30`, `Nagpur Maharashtra`) |
| **2. Crop Scan History** | `POST_RESTART_CROP_HISTORY: 2` (`Wheat Black Rust` `reconciliationStatus='ai_unavailable'`; `Yellow Rust` `'primary_only'`) |
| **3. Livestock Clinical History** | `POST_RESTART_LIVESTOCK: 1` (`Cattle`, `urgentVetEscalation=True`, `source='service_unavailable'`) |
| **4. Crop Planner Plans** | `POST_RESTART_PLANS: 3` (`['Gram', 'Barley', 'Wheat']`) — subsequently incremented to `4` (`['Millet', 'Gram', 'Barley', 'Wheat']`) via Galaxy A35 offline sync |
| **5. Completed Planner Tasks** | `POST_RESTART_COMPLETED_TASKS: 2` (`Barley` and `Gram` `dayOffset=0`) |
| **6. Bookmarked Schemes & Market Favorites** | `POST_RESTART_SCHEMES: ['pm-kisan', 'pmfby']`, `POST_RESTART_FAVORITES: ['Soybean']` |
| **7. Offline Sync Idempotency / Replay Guard** | `POST_RESTART_SYNC_REPLAY: 200` (`applied=0`, `alreadyApplied=4`) — replaying already-applied `clientMutationId`s created zero duplicates |

All `10/10` Node tests (`NODE_ENV=test node --test tests/*.test.js`) also continue passing in isolated ephemeral `MongoMemoryServer` mode without touching `backend/data/db`.
