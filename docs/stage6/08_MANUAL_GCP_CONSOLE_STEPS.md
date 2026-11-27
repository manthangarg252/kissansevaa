# Stage 6 — Exact Manual Steps After Handoff (Google Cloud Console & Release Signing)

No paid cloud resources were provisioned by this autonomous agent. Follow these exact steps when you are ready to launch KissanSevaAI 24/7 on Google Cloud Platform.

## Step 1 — Set GCP Billing Budget Alert

1. Open **Google Cloud Console -> Billing -> Budgets & alerts -> Create budget**.
2. Choose your target project and set an alert threshold (e.g., 50%, 90%, 100%) to your email before creating the VM.

## Step 2 — Reserve a Static External IPv4 Address

1. Open **VPC network -> IP addresses -> Reserve external static IP address**.
2. Name: `kissansevaai-prod-ip`, Region: `asia-south1` (Mumbai) or `asia-south2` (Delhi), Tier: Standard.
3. Note the allocated IPv4 address (denoted `<STATIC_VM_IP>` below).

## Step 3 — Configure Firewall Rules (Least Privilege)

1. Open **VPC network -> Firewall**.
2. Ensure default or custom rules applied to tag `kissansevaai-prod` allow ONLY:
   - `TCP:80` from `0.0.0.0/0` (Let's Encrypt ACME HTTP-01 & redirect to HTTPS)
   - `TCP:443` and `UDP:443` from `0.0.0.0/0` (HTTPS & HTTP/3 API traffic)
   - `TCP:22` from `<YOUR_PUBLIC_IP>/32` (or omit `0.0.0.0/0` SSH and use GCP Console Browser SSH / IAP)
3. Verify ports `27017`, `5000`, `5050`, and `8000` are **not** opened externally.

## Step 4 — Create Compute Engine Ubuntu VM

1. Open **Compute Engine -> VM instances -> Create instance**.
2. Configure:
   - **Name:** `kissansevaai-vm`
   - **Region / Zone:** `asia-south1` / `asia-south1-a` (matching your static IP region)
   - **Machine configuration:** `E2` -> `e2-medium` (2 vCPU, 4 GB RAM)
   - **Boot disk:** OS `Ubuntu`, Version `Ubuntu 24.04 LTS`, Type `Balanced persistent disk`, Size `30 GB`
   - **Networking -> Network interfaces -> External IPv4:** Select `kissansevaai-prod-ip`
   - **Firewall checkboxes / Network tags:** Apply tags matching your `80` & `443` firewall rules.

## Step 5 — Point DNS Domain to VM Static IP

1. At your domain registrar / DNS provider, add an `A` record:
   - Host/Name: `api` (giving e.g. `api.yourdomain.com`)
   - Value: `<STATIC_VM_IP>`
   - TTL: `300` seconds
2. Verify resolution from terminal: `dig +short api.yourdomain.com`.

## Step 6 — Bootstrap VM, Transfer Assets & Migrate Local MongoDB

Follow sections 3–6 in [02_GCP_VM_AND_DOCKER_DEPLOYMENT.md](./02_GCP_VM_AND_DOCKER_DEPLOYMENT.md) and [04_MONGODB_MIGRATION_AND_BACKUPS.md](./04_MONGODB_MIGRATION_AND_BACKUPS.md):
1. Install Docker + `ufw` + `2 GB` swap on the VM.
2. Clone the repository on the VM (`~/kissansevaai`).
3. Transfer `ml-service/model_assets/` and `ml-service/model_candidates/v3_svm_repaired/model_assets/` via `gcloud compute scp`.
4. Copy `.env.prod.example` to `.env.prod` (`chmod 600 .env.prod`) and populate real secrets (`openssl rand -hex 24` / `32`), `KISSAN_API_DOMAIN`, `CADDY_ACME_EMAIL`, `GEMINI_API_KEY`.
5. Run `./infra/scripts/deploy.sh`.
6. Export your local Mac database non-destructively via `./infra/scripts/backup-local-mac-db.sh`, upload it with `gcloud compute scp`, and import via `./infra/scripts/restore-prod-mongo.sh <archive> --drop`.
7. Add the daily `cron` entry for `./infra/scripts/backup-prod-mongo.sh` and run `./infra/scripts/verify-backup-restore.sh` once on the VM.

## Step 7 — Build & Sign Production Android Release APK

Once `https://api.yourdomain.com/health` returns HTTP `200`:

```bash
export JAVA_HOME=/Users/manthangarg/.jdks/jdk-21.0.12.1+1/Contents/Home
./android/gradlew -p android assembleRelease -PKISSAN_PROD_API_URL=https://api.yourdomain.com
```

Sign the generated APK (`android/app/build/outputs/apk/release/app-release-unsigned.apk`) with your Android upload/release keystore (`apksigner`) before distributing to farmers or Play Console.
