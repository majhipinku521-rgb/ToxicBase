/**
 * TOXICBASE Core Backend Server (Node.js + Express + PostgreSQL + Redis + Twilio SMS)
 * Real self-hosted Firebase alternative. Zero Firebase dependencies.
 */
require('dotenv').config();
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const crypto = require('crypto');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const Redis = require('ioredis');
const twilio = require('twilio');

const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGINS ? process.env.CORS_ORIGINS.split(',') : '*' }));
app.use(express.json({ limit: '1mb' }));

// PostgreSQL Pool & Redis Client
const pgPool = new Pool({ connectionString: process.env.DATABASE_URL });
const redis = new Redis(process.env.REDIS_URL || 'redis://127.0.0.1:6379');

// Twilio SMS Client (Credentials strictly kept on server)
const twilioClient = (process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN)
  ? twilio(process.env.TWILIO_ACCOUNT_SID, process.env.TWILIO_AUTH_TOKEN)
  : null;

const JWT_ACCESS_SECRET = process.env.JWT_ACCESS_SECRET;
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET;
const OTP_HMAC_SECRET = process.env.OTP_HMAC_SECRET;

if (!JWT_ACCESS_SECRET || !JWT_REFRESH_SECRET || !OTP_HMAC_SECRET) {
  console.error('FATAL: Missing JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, or OTP_HMAC_SECRET in environment');
  process.exit(1);
}

// Cryptographic Helpers (Never store plaintext OTP or API Keys)
function hashSecretHmac(value) {
  return crypto.createHmac('sha256', OTP_HMAC_SECRET).update(String(value)).digest('hex');
}

function generateSecureOtp() {
  return String(crypto.randomInt(100000, 1000000));
}

// Request Logging Middleware
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', async () => {
    const latency = Date.now() - start;
    try {
      await pgPool.query(
        `INSERT INTO request_logs (project_id, method, path, status_code, latency_ms, ip_address, user_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [req.projectId || null, req.method, req.originalUrl, res.statusCode, latency, req.ip, req.user?.sub || null]
      );
    } catch (_) {}
  });
  next();
});

// Global API Rate Limiter
const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests, please slow down.' }
});
app.use(apiLimiter);

// Strict OTP Rate Limiter
const otpLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 5,
  message: { error: 'OTP rate limit exceeded. Try again later.' }
});

// Project API Key Authentication Middleware
async function requireApiKey(req, res, next) {
  const rawKey = req.headers['x-toxicbase-key'] || req.headers['x-api-key'];
  if (!rawKey) {
    return res.status(401).json({ error: 'Missing X-ToxicBase-Key header' });
  }
  const keyHash = hashSecretHmac(rawKey);
  const { rows } = await pgPool.query(
    `SELECT k.id, k.project_id, k.role, p.auth_phone_enabled, p.auth_email_enabled, p.security_rules
     FROM api_keys k
     JOIN projects p ON p.id = k.project_id
     WHERE k.key_hash = $1 AND k.is_active = TRUE`,
    [keyHash]
  );
  if (rows.length === 0) {
    return res.status(403).json({ error: 'Invalid or revoked ToxicBase API key' });
  }
  req.projectId = rows[0].project_id;
  req.apiKeyRole = rows[0].role;
  req.projectConfig = rows[0];
  await pgPool.query(`UPDATE api_keys SET last_used_at = NOW() WHERE id = $1`, [rows[0].id]);

  // Optional Bearer JWT parsing
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.slice(7);
    try {
      const payload = jwt.verify(token, JWT_ACCESS_SECRET);
      if (payload.projectId === req.projectId) {
        req.user = payload;
      }
    } catch (_) {}
  }
  next();
}

// Security Rules Evaluation Engine
function evaluateSecurityRule(req, collectionName, operation, resourceOwnerId = null) {
  if (req.apiKeyRole === 'server_admin') return true;
  if (req.apiKeyRole === 'readonly' && operation === 'write') return false;

  const rules = req.projectConfig?.security_rules || {};
  const colRules = (rules.collections && rules.collections[collectionName]) || {};
  const mode = operation === 'read'
    ? (colRules.read || rules.defaultRead || 'authenticated')
    : (colRules.write || rules.defaultWrite || 'authenticated');

  if (mode === 'public') return true;
  if (mode === 'authenticated') return Boolean(req.user && req.user.sub);
  if (mode === 'owner_only') {
    if (!req.user || !req.user.sub) return false;
    return !resourceOwnerId || resourceOwnerId === req.user.sub;
  }
  if (mode === 'admin_only') return req.apiKeyRole === 'server_admin';
  return false;
}

// Issue Access + Refresh Tokens
async function issueSessionTokens(user, projectId, req) {
  const accessToken = jwt.sign(
    { sub: user.id, projectId, phone: user.phone_number, email: user.email },
    JWT_ACCESS_SECRET,
    { expiresIn: '15m' }
  );
  const refreshToken = 'tb_rt_' + crypto.randomBytes(32).toString('hex');
  const refreshHash = hashSecretHmac(refreshToken);
  const expiresAt = new Date(Date.now() + 30 * 24 * 3600 * 1000);

  await pgPool.query(
    `INSERT INTO sessions (user_id, project_id, refresh_token_hash, user_agent, ip_address, expires_at)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [user.id, projectId, refreshHash, req.headers['user-agent'] || '', req.ip, expiresAt]
  );

  return { accessToken, refreshToken, expiresIn: 900 };
}

// ============================================================================
// 1. AUTHENTICATION & REAL SMS OTP ENDPOINTS
// ============================================================================

app.post('/auth/send-otp', otpLimiter, requireApiKey, async (req, res) => {
  try {
    if (!req.projectConfig.auth_phone_enabled) {
      return res.status(403).json({ error: 'Phone authentication is disabled for this project' });
    }
    const { phoneNumber } = req.body;
    if (!phoneNumber || !/^\+[1-9]\d{6,14}$/.test(phoneNumber)) {
      return res.status(400).json({ error: 'Valid E.164 phoneNumber (e.g. +14155552671) is required' });
    }

    const cooldownKey = `otp:cooldown:${req.projectId}:${phoneNumber}`;
    const ttl = await redis.ttl(cooldownKey);
    if (ttl > 0) {
      return res.status(429).json({
        error: `Please wait ${ttl} seconds before requesting another OTP`,
        retryAfterSeconds: ttl
      });
    }

    const otp = generateSecureOtp();
    const otpHash = hashSecretHmac(`${req.projectId}:${phoneNumber}:${otp}`);
    const otpRecordKey = `otp:record:${req.projectId}:${phoneNumber}`;

    // Store hashed OTP in Redis with 300s (5-minute) expiration and 0 attempts
    await redis.hmset(otpRecordKey, { hash: otpHash, attempts: 0 });
    await redis.expire(otpRecordKey, 300);
    // Set 60-second resend cooldown
    await redis.set(cooldownKey, '1', 'EX', 60);

    if (!twilioClient) {
      return res.status(503).json({ error: 'SMS provider credentials are not configured on server' });
    }

    await twilioClient.messages.create({
      body: `[TOXICBASE] Your verification code is ${otp}. Expires in 5 minutes. Do not share this code.`,
      from: process.env.TWILIO_FROM_NUMBER,
      to: phoneNumber
    });

    // NEVER expose OTP in API response
    return res.json({
      success: true,
      phoneNumber,
      expiresInSeconds: 300,
      cooldownSeconds: 60,
      message: 'Verification code dispatched via SMS'
    });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to dispatch SMS OTP' });
  }
});

app.post('/auth/verify-otp', requireApiKey, async (req, res) => {
  try {
    const { phoneNumber, otp } = req.body;
    if (!phoneNumber || !otp) {
      return res.status(400).json({ error: 'phoneNumber and otp are required' });
    }

    const otpRecordKey = `otp:record:${req.projectId}:${phoneNumber}`;
    const record = await redis.hgetall(otpRecordKey);
    if (!record || !record.hash) {
      return res.status(400).json({ error: 'OTP has expired or was not requested' });
    }

    const attempts = parseInt(record.attempts || '0', 10) + 1;
    if (attempts > 5) {
      await redis.del(otpRecordKey);
      return res.status(429).json({ error: 'Maximum OTP verification attempts exceeded. Request a new code.' });
    }

    const candidateHash = hashSecretHmac(`${req.projectId}:${phoneNumber}:${otp}`);
    const isMatch = crypto.timingSafeEqual(Buffer.from(record.hash), Buffer.from(candidateHash));

    if (!isMatch) {
      await redis.hset(otpRecordKey, 'attempts', attempts);
      return res.status(401).json({
        error: 'Invalid verification code',
        remainingAttempts: Math.max(0, 5 - attempts)
      });
    }

    // Consume OTP immediately
    await redis.del(otpRecordKey);

    // Upsert user in PostgreSQL
    const userId = 'tb_usr_' + crypto.randomBytes(10).toString('hex');
    const { rows } = await pgPool.query(
      `INSERT INTO users (id, project_id, phone_number, last_login_at)
       VALUES ($1, $2, $3, NOW())
       ON CONFLICT (project_id, phone_number)
       DO UPDATE SET last_login_at = NOW()
       RETURNING id, project_id, phone_number, email, status, created_at, last_login_at`,
      [userId, req.projectId, phoneNumber]
    );

    const user = rows[0];
    if (user.status !== 'ACTIVE') {
      return res.status(403).json({ error: 'User account is suspended' });
    }

    const tokens = await issueSessionTokens(user, req.projectId, req);
    return res.json({ user, ...tokens });
  } catch (err) {
    return res.status(500).json({ error: 'OTP verification failed' });
  }
});

app.post('/auth/login', requireApiKey, async (req, res) => {
  try {
    const { email, password, mode } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    if (mode === 'register') {
      const passwordHash = await bcrypt.hash(password, 12);
      const userId = 'tb_usr_' + crypto.randomBytes(10).toString('hex');
      const { rows } = await pgPool.query(
        `INSERT INTO users (id, project_id, email, password_hash, last_login_at)
         VALUES ($1, $2, $3, $4, NOW())
         RETURNING id, project_id, phone_number, email, status, created_at, last_login_at`,
        [userId, req.projectId, email.toLowerCase(), passwordHash]
      );
      const tokens = await issueSessionTokens(rows[0], req.projectId, req);
      return res.status(201).json({ user: rows[0], ...tokens });
    }

    const { rows } = await pgPool.query(
      `SELECT * FROM users WHERE project_id = $1 AND email = $2`,
      [req.projectId, email.toLowerCase()]
    );
    if (rows.length === 0 || !rows[0].password_hash) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }
    const valid = await bcrypt.compare(password, rows[0].password_hash);
    if (!valid) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }
    if (rows[0].status !== 'ACTIVE') {
      return res.status(403).json({ error: 'User account is suspended' });
    }

    await pgPool.query(`UPDATE users SET last_login_at = NOW() WHERE id = $1`, [rows[0].id]);
    const user = rows[0];
    delete user.password_hash;
    const tokens = await issueSessionTokens(user, req.projectId, req);
    return res.json({ user, ...tokens });
  } catch (err) {
    return res.status(500).json({ error: 'Authentication failed' });
  }
});

app.post('/auth/logout', requireApiKey, async (req, res) => {
  const { refreshToken } = req.body;
  if (refreshToken) {
    const hash = hashSecretHmac(refreshToken);
    await pgPool.query(`UPDATE sessions SET revoked = TRUE WHERE refresh_token_hash = $1`, [hash]);
  }
  return res.json({ success: true });
});

// ============================================================================
// 2. USER MANAGEMENT ENDPOINTS
// ============================================================================

app.get('/users', requireApiKey, async (req, res) => {
  const { rows } = await pgPool.query(
    `SELECT id, project_id, phone_number, email, status, created_at, last_login_at
     FROM users WHERE project_id = $1 ORDER BY created_at DESC LIMIT 200`,
    [req.projectId]
  );
  return res.json({ users: rows });
});

app.get('/users/:id', requireApiKey, async (req, res) => {
  const { rows } = await pgPool.query(
    `SELECT id, project_id, phone_number, email, status, created_at, last_login_at
     FROM users WHERE project_id = $1 AND id = $2`,
    [req.projectId, req.params.id]
  );
  if (rows.length === 0) return res.status(404).json({ error: 'User not found' });
  return res.json({ user: rows[0] });
});

app.put('/users/:id', requireApiKey, async (req, res) => {
  const { status, email, phoneNumber } = req.body;
  const { rows } = await pgPool.query(
    `UPDATE users
     SET status = COALESCE($3, status),
         email = COALESCE($4, email),
         phone_number = COALESCE($5, phone_number)
     WHERE project_id = $1 AND id = $2
     RETURNING id, project_id, phone_number, email, status, created_at, last_login_at`,
    [req.projectId, req.params.id, status, email, phoneNumber]
  );
  if (rows.length === 0) return res.status(404).json({ error: 'User not found' });
  return res.json({ user: rows[0] });
});

// ============================================================================
// 3. PROJECT MANAGEMENT ENDPOINTS
// ============================================================================

app.post('/projects', async (req, res) => {
  const { name, region = 'us-east-1' } = req.body;
  if (!name) return res.status(400).json({ error: 'Project name is required' });
  const projectId = 'tb_proj_' + crypto.randomBytes(6).toString('hex');
  const rawApiKey = 'tb_live_' + crypto.randomBytes(20).toString('hex');
  const keyHash = hashSecretHmac(rawApiKey);

  await pgPool.query(`INSERT INTO projects (id, name, region) VALUES ($1, $2, $3)`, [projectId, name, region]);
  await pgPool.query(
    `INSERT INTO api_keys (project_id, label, key_prefix, key_hash, role) VALUES ($1, $2, $3, $4, 'server_admin')`,
    [projectId, 'Default Admin Key', rawApiKey.slice(0, 14), keyHash]
  );

  return res.status(201).json({ projectId, name, region, apiKey: rawApiKey });
});

app.get('/projects', async (req, res) => {
  const { rows } = await pgPool.query(`SELECT * FROM projects ORDER BY created_at DESC`);
  return res.json({ projects: rows });
});

app.delete('/projects/:id', async (req, res) => {
  await pgPool.query(`DELETE FROM projects WHERE id = $1`, [req.params.id]);
  return res.json({ deleted: true, projectId: req.params.id });
});

// ============================================================================
// 4. NOSQL DOCUMENT DATABASE ENDPOINTS (Per-Project Isolation + Pagination)
// ============================================================================

app.post('/database/:collection', requireApiKey, async (req, res) => {
  const { collection } = req.params;
  if (!evaluateSecurityRule(req, collection, 'write')) {
    return res.status(403).json({ error: 'Permission denied by ToxicBase Security Rules' });
  }
  const docId = req.body.id || ('doc_' + crypto.randomBytes(8).toString('hex'));
  const data = req.body.data || req.body;
  const ownerId = req.user?.sub || null;

  const { rows } = await pgPool.query(
    `INSERT INTO documents (id, project_id, collection_name, data, owner_user_id)
     VALUES ($1, $2, $3, $4::jsonb, $5)
     ON CONFLICT (project_id, collection_name, id)
     DO UPDATE SET data = $4::jsonb, version = documents.version + 1, updated_at = NOW()
     RETURNING *`,
    [docId, req.projectId, collection, JSON.stringify(data), ownerId]
  );
  return res.status(201).json({ document: rows[0] });
});

app.get('/database/:collection', requireApiKey, async (req, res) => {
  const { collection } = req.params;
  if (!evaluateSecurityRule(req, collection, 'read')) {
    return res.status(403).json({ error: 'Permission denied by ToxicBase Security Rules' });
  }
  const page = Math.max(1, parseInt(req.query.page || '1', 10));
  const limit = Math.min(100, Math.max(1, parseInt(req.query.limit || '25', 10)));
  const offset = (page - 1) * limit;
  const filterField = req.query.filterField;
  const filterValue = req.query.filterValue;

  let query = `SELECT * FROM documents WHERE project_id = $1 AND collection_name = $2`;
  const params = [req.projectId, collection];

  if (filterField && filterValue !== undefined) {
    query += ` AND data->>$3 = $4`;
    params.push(filterField, String(filterValue));
  }
  query += ` ORDER BY updated_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
  params.push(limit, offset);

  const { rows } = await pgPool.query(query, params);
  return res.json({ collection, page, limit, documents: rows });
});

app.get('/database/:collection/:document', requireApiKey, async (req, res) => {
  const { collection, document } = req.params;
  const { rows } = await pgPool.query(
    `SELECT * FROM documents WHERE project_id = $1 AND collection_name = $2 AND id = $3`,
    [req.projectId, collection, document]
  );
  if (rows.length === 0) return res.status(404).json({ error: 'Document not found' });
  if (!evaluateSecurityRule(req, collection, 'read', rows[0].owner_user_id)) {
    return res.status(403).json({ error: 'Permission denied by ToxicBase Security Rules' });
  }
  return res.json({ document: rows[0] });
});

app.put('/database/:collection/:document', requireApiKey, async (req, res) => {
  const { collection, document } = req.params;
  const existing = await pgPool.query(
    `SELECT owner_user_id FROM documents WHERE project_id = $1 AND collection_name = $2 AND id = $3`,
    [req.projectId, collection, document]
  );
  const ownerId = existing.rows[0]?.owner_user_id || null;
  if (!evaluateSecurityRule(req, collection, 'write', ownerId)) {
    return res.status(403).json({ error: 'Permission denied by ToxicBase Security Rules' });
  }
  const data = req.body.data || req.body;
  const { rows } = await pgPool.query(
    `UPDATE documents SET data = $4::jsonb, version = version + 1, updated_at = NOW()
     WHERE project_id = $1 AND collection_name = $2 AND id = $3
     RETURNING *`,
    [req.projectId, collection, document, JSON.stringify(data)]
  );
  if (rows.length === 0) return res.status(404).json({ error: 'Document not found' });
  return res.json({ document: rows[0] });
});

app.delete('/database/:collection/:document', requireApiKey, async (req, res) => {
  const { collection, document } = req.params;
  const existing = await pgPool.query(
    `SELECT owner_user_id FROM documents WHERE project_id = $1 AND collection_name = $2 AND id = $3`,
    [req.projectId, collection, document]
  );
  if (existing.rows.length === 0) return res.status(404).json({ error: 'Document not found' });
  if (!evaluateSecurityRule(req, collection, 'write', existing.rows[0].owner_user_id)) {
    return res.status(403).json({ error: 'Permission denied by ToxicBase Security Rules' });
  }
  await pgPool.query(
    `DELETE FROM documents WHERE project_id = $1 AND collection_name = $2 AND id = $3`,
    [req.projectId, collection, document]
  );
  return res.json({ deleted: true, id: document });
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`TOXICBASE BaaS Server listening on port ${PORT}`);
});
