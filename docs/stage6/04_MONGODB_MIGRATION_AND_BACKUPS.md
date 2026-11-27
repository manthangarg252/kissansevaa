# Stage 6 — MongoDB Migration, Backups, and Verified Restore

Existing local development data in `backend/data/db` is preserved intact. Use the scripts under `infra/scripts/` to export local data safely and maintain automated backups in production.

## 1. Local Mac Database Export (Non-Destructive)

Run [infra/scripts/backup-local-mac-db.sh](../../infra/scripts/backup-local-mac-db.sh) with Docker Desktop running:

```bash
./infra/scripts/backup-local-mac-db.sh
```

How it works safely:
- **If local `mongod` is running on port `27017`:** streams `mongodump --db kissansevaai --archive --gzip` through an ephemeral `mongo:8.0` container to `backups/local-kissansevaai-<UTC>.archive.gz` + `.sha256` without interrupting or modifying `backend/data/db`.
- **If local `mongod` is stopped:** copies `backend/data/db` into a temporary directory first, runs an ephemeral `mongo:8.0` container against that copy, dumps `kissansevaai`, and removes only the temporary copy — original `backend/data/db` is never mutated.
- Also archives `backend/uploads/` into `backups/local-uploads-<UTC>.tar.gz` if non-empty.

## 2. Migrate Local Data to the Cloud VM

1. Transfer the archive (and uploads tar if needed) to the GCP VM:
   ```bash
   gcloud compute scp backups/local-kissansevaai-*.archive.gz \
     kissansevaai-vm:~/kissansevaai/backups/ --zone=asia-south1-a
   ```
2. On the VM, ensure the authenticated `mongodb` container is running and healthy (`docker compose --env-file .env.prod -f docker-compose.prod.yml up -d mongodb`).
3. Restore ONLY the application namespace (`kissansevaai.*`) so `admin` users and `kissan_app` remain intact:
   ```bash
   ./infra/scripts/restore-prod-mongo.sh backups/local-kissansevaai-<TIMESTAMP>.archive.gz --drop
   ```
4. If migrating local uploaded images:
   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d backend
   docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T backend \
     tar -xzf - -C /app/uploads < backups/local-uploads-<TIMESTAMP>.tar.gz
   ```

## 3. Automated Production Backups (Daily Cron)

`infra/scripts/backup-prod-mongo.sh` runs an authenticated `mongodump --archive --gzip`, validates the gzip stream (`gzip -t`), writes a SHA256 checksum, locks permissions (`chmod 600`), and retains the newest `14` archives (`RETENTION_COUNT=14`).

Install a daily 02:30 UTC cron job on the GCP VM (`crontab -e`):
```cron
30 2 * * * cd /home/$USER/kissansevaai && ENV_FILE=/home/$USER/kissansevaai/.env.prod ./infra/scripts/backup-prod-mongo.sh >> /home/$USER/kissansevaai/backups/cron.log 2>&1
```

For off-VM disaster recovery, sync `~/kissansevaai/backups/` periodically to a private Cloud Storage bucket (with versioning/lifecycle retention enabled).

## 4. Non-Destructive Backup & Restore Verification

Run [infra/scripts/verify-backup-restore.sh](../../infra/scripts/verify-backup-restore.sh):
```bash
./infra/scripts/verify-backup-restore.sh
```
This script:
1. Creates a fresh gzipped production backup (or tests a specified archive path).
2. Restores `${MONGO_APP_DB}.*` into an isolated scratch database `${MONGO_APP_DB}_restore_verify_<pid>`.
3. Prints every restored collection name and document count.
4. Drops only the temporary scratch verification database cleanly in a shell `trap` without touching live production data.
