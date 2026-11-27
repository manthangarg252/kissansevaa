# Stage 6 — GCP Compute Engine VM & Docker Deployment

## 1. Recommended Compute Engine VM Specification

| Parameter | Recommended Setting | Rationale |
| --- | --- | --- |
| Machine type | `e2-medium` (2 vCPU, 4 GB RAM) | ML service holds ONNX runtime + V1 FAISS (`~6.6 MB`) + V3 repaired FAISS (`~161 MB`) in RAM (~700–900 MB total with Python). `e2-micro` (1 GB RAM) will OOM unless V3 shadow is disabled. |
| OS image | Ubuntu `24.04 LTS` (x86_64) | Long-term security updates through 2029, systemd cgroups v2, first-class Docker apt repo support. |
| Boot disk | `30 GB` `pd-balanced` SSD | OS (~4 GB) + Docker images/layers (~3 GB) + model assets (~260 MB) + MongoDB data/uploads + 14 rotating backups with safe free space. |
| Region | `asia-south1` (Mumbai) or `asia-south2` (Delhi) | Lowest RTT for Indian farmers using Gemini, weather, and upload endpoints. |
| External IP | Reserved **Static External IPv4** | Required so DNS `A` record remains stable across VM restarts. |
| Swap (recommended) | `2 GB` `/swapfile` on VM | Safety cushion against transient Python/MongoDB memory spikes during concurrent backups or image requests. |

## 2. Firewall Rules (Least Privilege)

In **VPC Network -> Firewall** (attached via VM network tags `http-server`, `https-server`):
- Allow **TCP `80`** from `0.0.0.0/0` (ACME HTTP-01 challenge + HTTP-to-HTTPS redirect)
- Allow **TCP `443`** and **UDP `443`** from `0.0.0.0/0` (HTTPS API traffic + HTTP/3)
- Restrict **TCP `22` (SSH)** to your operator IP CIDR (or use Identity-Aware Proxy / GCP Console SSH instead of leaving `22` open to `0.0.0.0/0`).
- **Never** open ports `27017`, `5000`, `5050`, or `8000` in GCP Firewall or `ufw`.

## 3. Initial Ubuntu VM Bootstrap (Run on VM via SSH)

```bash
# 1. Update packages & add 2GB safety swap
sudo apt-get update && sudo apt-get upgrade -y
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 2. Install Docker Engine + Docker Compose v2 plugin (official convenience or apt repo)
sudo apt-get install -y ca-certificates curl gnupg ufw fail2ban
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
sudo systemctl enable docker

# 3. Host UFW firewall matching GCP firewall
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw --force enable
```

Log out and back in so Docker group membership takes effect.

## 4. Application Checkout & Model Asset Transfer

Git excludes large binary model weights (`*.onnx`, `*.faiss`, `*.pkl`, `ml-service/model_candidates/`). After cloning the repository onto the VM (`~/kissansevaai`):

From your MacBook (only required weights — do NOT copy `.venv*`, CSV splits, or datasets):
```bash
# Create destination folders on VM first
gcloud compute ssh kissansevaai-vm --zone=asia-south1-a --command \
  "mkdir -p ~/kissansevaai/ml-service/model_assets ~/kissansevaai/ml-service/model_candidates/v3_svm_repaired/model_assets"

# Copy active V1 DINOv2 + FAISS assets (~95 MB)
gcloud compute scp --recurse ./ml-service/model_assets/* \
  kissansevaai-vm:~/kissansevaai/ml-service/model_assets/ --zone=asia-south1-a

# Copy V3 shadow assets (~165 MB) + SHA256 manifest
gcloud compute scp --recurse ./ml-service/model_candidates/v3_svm_repaired/model_assets/* \
  kissansevaai-vm:~/kissansevaai/ml-service/model_candidates/v3_svm_repaired/model_assets/ --zone=asia-south1-a
gcloud compute scp ./ml-service/model_candidates/v3_svm_repaired/SHA256_MANIFEST.json \
  kissansevaai-vm:~/kissansevaai/ml-service/model_candidates/v3_svm_repaired/ --zone=asia-south1-a
```

## 5. Production Environment & Launch

```bash
cd ~/kissansevaai
cp .env.prod.example .env.prod
chmod 600 .env.prod
# Edit .env.prod with real KISSAN_API_DOMAIN, CADDY_ACME_EMAIL, generated DB/JWT secrets, GEMINI_API_KEY
nano .env.prod

# Build containers, start stack, verify /health across backend, ml-service, mongodb
./infra/scripts/deploy.sh
```

## 6. Caddy HTTPS Domain Setup

1. Create a DNS `A` record for your API subdomain (for example `api.yourdomain.com`) pointing to the VM's reserved static IPv4 address.
2. Confirm `dig +short api.yourdomain.com` resolves to that IP.
3. Ensure `KISSAN_API_DOMAIN=api.yourdomain.com` and `CADDY_ACME_EMAIL=<you@domain>` are set in `.env.prod`.
4. Caddy automatically provisions and renews Let's Encrypt TLS certificates in the persistent `caddy_data` volume, redirects HTTP `:80` to HTTPS `:443`, enforces HSTS, and restricts upload bodies to `15MB` via [infra/caddy/Caddyfile](../../infra/caddy/Caddyfile).
