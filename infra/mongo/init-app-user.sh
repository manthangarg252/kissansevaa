#!/usr/bin/env bash
set -euo pipefail

# Runs once during MongoDB first initialization when /data/db is empty.
# Creates a least-privilege readWrite user for the KissanSevaAI application database.
if [[ -z "${MONGO_APP_PASSWORD:-}" ]]; then
  echo "ERROR: MONGO_APP_PASSWORD is not set; refusing to initialize application MongoDB user." >&2
  exit 1
fi

mongosh --quiet \
  --username "${MONGO_INITDB_ROOT_USERNAME}" \
  --password "${MONGO_INITDB_ROOT_PASSWORD}" \
  --authenticationDatabase admin <<'EOF'
const appDbName = process.env.MONGO_APP_DB || 'kissansevaai';
const appUser = process.env.MONGO_APP_USER || 'kissan_app';
const appPass = process.env.MONGO_APP_PASSWORD;
const targetDb = db.getSiblingDB(appDbName);

if (!targetDb.getUser(appUser)) {
  targetDb.createUser({
    user: appUser,
    pwd: appPass,
    roles: [{ role: 'readWrite', db: appDbName }],
  });
  print(`Created MongoDB app user "${appUser}" on database "${appDbName}"`);
} else {
  print(`MongoDB app user "${appUser}" already exists on "${appDbName}"`);
}
EOF
