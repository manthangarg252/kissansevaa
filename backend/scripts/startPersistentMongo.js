import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const BACKEND_ROOT = path.resolve(__dirname, '..');

function isPortOpen(host, port, timeoutMs = 600) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let settled = false;
    const finish = (ok) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
    socket.connect(port, host);
  });
}

function findCachedMongodBinary() {
  const dirs = [
    path.join(os.homedir(), '.cache', 'mongodb-binaries'),
    path.join(os.homedir(), 'Library', 'Caches', 'mongodb-binaries'),
    path.join(BACKEND_ROOT, 'node_modules', '.cache', 'mongodb-memory-server'),
  ];
  for (const dir of dirs) {
    if (!fs.existsSync(dir)) continue;
    const matches = fs
      .readdirSync(dir)
      .filter((f) => /^mongod-/.test(f) && !f.endsWith('.md5'))
      .map((f) => path.join(dir, f))
      .filter((p) => {
        try {
          fs.accessSync(p, fs.constants.X_OK);
          return true;
        } catch {
          return false;
        }
      })
      .sort()
      .reverse();
    if (matches.length > 0) return matches[0];
  }
  return null;
}

export function resolveMongodExecutable() {
  if (process.env.MONGOD_BIN && fs.existsSync(process.env.MONGOD_BIN)) {
    return { path: process.env.MONGOD_BIN, source: 'MONGOD_BIN' };
  }

  for (const candidate of ['/opt/homebrew/bin/mongod', '/usr/local/bin/mongod']) {
    if (fs.existsSync(candidate)) {
      return { path: candidate, source: 'system_install' };
    }
  }

  try {
    const resolved = execFileSync('which', ['mongod'], { encoding: 'utf8' }).trim();
    if (resolved && fs.existsSync(resolved)) {
      return { path: resolved, source: 'PATH' };
    }
  } catch {
    // mongod not on PATH
  }

  const cached = findCachedMongodBinary();
  if (cached) {
    return { path: cached, source: 'cached_mongodb_binary' };
  }

  return null;
}

export function parseLocalMongoTarget(rawUri = '') {
  const fallbackUri = 'mongodb://127.0.0.1:27017/kissansevaai';
  const uri = String(rawUri || '').trim() || fallbackUri;
  try {
    const parsed = new URL(uri);
    const isLocalHost = ['127.0.0.1', 'localhost', '::1'].includes(parsed.hostname);
    return {
      uri,
      host: parsed.hostname || '127.0.0.1',
      port: Number(parsed.port || 27017),
      isLocalHost,
    };
  } catch {
    return {
      uri: fallbackUri,
      host: '127.0.0.1',
      port: 27017,
      isLocalHost: true,
    };
  }
}

export async function ensurePersistentMongo({
  mongoUri = process.env.MONGO_URI,
  dataDir = process.env.MONGO_DATA_DIR || path.join(BACKEND_ROOT, 'data', 'db'),
  logDir = path.join(BACKEND_ROOT, 'logs'),
} = {}) {
  const target = parseLocalMongoTarget(mongoUri);
  if (!target.isLocalHost) {
    return { mode: 'external_uri', uri: target.uri, started: false };
  }

  if (await isPortOpen(target.host, target.port)) {
    return {
      mode: 'existing_local_mongod',
      uri: target.uri,
      host: target.host,
      port: target.port,
      dataDir,
      started: false,
    };
  }

  const bin = resolveMongodExecutable();
  if (!bin) {
    throw new Error(
      'No local mongod binary found and port 27017 is closed. Install MongoDB (`brew install mongodb-community`), run `docker compose -f backend/docker-compose.yml up -d`, or set MONGOD_BIN.',
    );
  }

  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(logDir, { recursive: true });
  const logPath = path.join(logDir, 'mongod.log');
  const pidPath = path.join(path.dirname(dataDir), 'mongod.pid');

  const logFd = fs.openSync(logPath, 'a');
  const child = spawn(
    bin.path,
    [
      '--dbpath',
      dataDir,
      '--bind_ip',
      target.host,
      '--port',
      String(target.port),
    ],
    {
      detached: true,
      stdio: ['ignore', logFd, logFd],
    },
  );
  child.unref();
  fs.closeSync(logFd);

  if (child.pid) {
    fs.writeFileSync(pidPath, `${child.pid}\n`, 'utf8');
  }

  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (await isPortOpen(target.host, target.port, 500)) {
      return {
        mode: 'started_local_mongod',
        binarySource: bin.source,
        binaryPath: bin.path,
        pid: child.pid || null,
        pidPath,
        dataDir,
        logPath,
        uri: target.uri,
        host: target.host,
        port: target.port,
        started: true,
      };
    }
    await new Promise((r) => setTimeout(r, 250));
  }

  throw new Error(`Timed out waiting for persistent mongod on ${target.host}:${target.port}. Check ${logPath}`);
}

const isCliRun = process.argv[1] && path.resolve(process.argv[1]) === __filename;
if (isCliRun) {
  import('dotenv')
    .then((dotenv) => {
      dotenv.default.config({ path: path.resolve(BACKEND_ROOT, '.env') });
      return ensurePersistentMongo();
    })
    .then((info) => {
      console.log(JSON.stringify({ ok: true, ...info }, null, 2));
    })
    .catch((err) => {
      console.error('[Persistent Mongo] Startup failed:', err.message);
      process.exit(1);
    });
}
