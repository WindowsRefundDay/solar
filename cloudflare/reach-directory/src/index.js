/**
 * Reach directory — registers Soulseek usernames from Reach clients on Y1/Y2.
 * Stale entries (>24h without heartbeat) are pruned on read/write.
 */

const TTL_MS = 24 * 60 * 60 * 1000;
const INDEX_KEY = "reach:index";
const USER_PREFIX = "reach:user:";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }
    if (!authorized(request, env)) {
      return cors(json({ error: "unauthorized" }, 401));
    }
    try {
      if (url.pathname === "/v1/register" && request.method === "POST") {
        return cors(await handleRegister(request, env));
      }
      if (url.pathname === "/v1/users" && request.method === "GET") {
        return cors(await handleList(request, env));
      }
      if (url.pathname.startsWith("/v1/users/") && request.method === "GET") {
        const name = decodeURIComponent(url.pathname.slice("/v1/users/".length));
        return cors(await handleGet(name, env));
      }
      return cors(json({ error: "not_found" }, 404));
    } catch (e) {
      return cors(json({ error: "server_error", message: String(e) }, 500));
    }
  },
};

function authorized(request, env) {
  const token = env.REGISTRY_TOKEN;
  if (!token) return true;
  return request.headers.get("X-Reach-Token") === token;
}

async function handleRegister(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }
  const username = normalizeUsername(body && body.username);
  if (!username) return json({ error: "invalid_username" }, 400);
  const device = normalizeDevice(body && body.device);
  const now = Date.now();
  const key = userKey(username);
  const existing = await env.REACH_USERS.get(key, "json");
  const record = {
    username,
    device,
    lastSeen: now,
    registeredAt: existing && existing.registeredAt ? existing.registeredAt : now,
  };
  await env.REACH_USERS.put(key, JSON.stringify(record));
  const index = await loadIndex(env);
  const lower = username.toLowerCase();
  if (!index.includes(lower)) {
    index.push(lower);
    await saveIndex(env, index);
  }
  await pruneStale(env);
  return json({ ok: true, username, lastSeen: now });
}

async function handleList(request, env) {
  const url = new URL(request.url);
  const exclude = normalizeUsername(url.searchParams.get("exclude") || "");
  const users = await listActiveUsers(env, exclude);
  return json({ users, ttlHours: 24, now: Date.now() });
}

async function handleGet(rawName, env) {
  const username = normalizeUsername(rawName);
  if (!username) return json({ error: "invalid_username" }, 400);
  const record = await env.REACH_USERS.get(userKey(username), "json");
  if (!record || isStale(record)) {
    return json({ found: false, username }, 404);
  }
  return json({ found: true, user: publicUser(record) });
}

async function listActiveUsers(env, excludeLower) {
  await pruneStale(env);
  const index = await loadIndex(env);
  const users = [];
  for (const lower of index) {
    if (excludeLower && lower === excludeLower.toLowerCase()) continue;
    const record = await env.REACH_USERS.get(USER_PREFIX + lower, "json");
    if (!record || isStale(record)) continue;
    users.push(publicUser(record));
  }
  users.sort((a, b) => b.lastSeen - a.lastSeen);
  return users;
}

async function pruneStale(env) {
  const index = await loadIndex(env);
  const kept = [];
  for (const lower of index) {
    const record = await env.REACH_USERS.get(USER_PREFIX + lower, "json");
    if (!record || isStale(record)) {
      await env.REACH_USERS.delete(USER_PREFIX + lower);
      continue;
    }
    kept.push(lower);
  }
  if (kept.length !== index.length) {
    await saveIndex(env, kept);
  }
}

function isStale(record) {
  return !record || !record.lastSeen || Date.now() - record.lastSeen > TTL_MS;
}

function publicUser(record) {
  return {
    username: record.username,
    device: record.device || "Y1",
    lastSeen: record.lastSeen,
    registeredAt: record.registeredAt || record.lastSeen,
  };
}

async function loadIndex(env) {
  const raw = await env.REACH_USERS.get(INDEX_KEY, "json");
  return Array.isArray(raw) ? raw : [];
}

async function saveIndex(env, index) {
  await env.REACH_USERS.put(INDEX_KEY, JSON.stringify(index));
}

function userKey(username) {
  return USER_PREFIX + username.toLowerCase();
}

function normalizeUsername(value) {
  if (!value || typeof value !== "string") return "";
  const t = value.trim();
  if (t.length < 2 || t.length > 24) return "";
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(t)) return "";
  return t;
}

function normalizeDevice(value) {
  if (!value || typeof value !== "string") return "Y1";
  const d = value.trim().toUpperCase();
  if (d === "Y2") return "Y2";
  return "Y1";
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function cors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, X-Reach-Token");
  return new Response(response.body, { status: response.status, headers });
}
