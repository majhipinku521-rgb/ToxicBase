# TOXICBASE Production Deployment & Operations Guide

TOXICBASE is a complete, self-hosted Backend-as-a-Service (BaaS) platform independent from Firebase.

---

## 1. Local Development Setup

### Prerequisites
- **Node.js** v20+ LTS
- **PostgreSQL** 16+
- **Redis** 7+
- **Android Studio** (for the ToxicBase Android Console & Android SDK)

### Start Backend Locally
```bash
cd backend
npm install
cp .env.example .env
psql "$DATABASE_URL" -f sql/schema.sql
npm run dev
```

---

## 2. Database Setup (PostgreSQL)

Create a dedicated user and database with TLS enabled:
```sql
CREATE USER toxicbase_admin WITH ENCRYPTED PASSWORD 'STRONG_DB_PASSWORD';
CREATE DATABASE toxicbase_prod OWNER toxicbase_admin;
\c toxicbase_prod
\i backend/sql/schema.sql
```
- **JSONB Indexing**: `documents.data` uses a PostgreSQL `GIN` index (`idx_documents_data_gin`) for sub-millisecond document filtering and pagination.
- **Per-Project Isolation**: Every table (`users`, `sessions`, `documents`, `api_keys`, `collection_indexes`, `request_logs`) enforces strict `project_id` foreign-key isolation.

---

## 3. Redis Setup (OTP & Rate Limiting)

Configure Redis 7 with password authentication and memory policies:
```conf
requirepass YOUR_STRONG_REDIS_PASSWORD
maxmemory 512mb
maxmemory-policy volatile-ttl
appendonly no
```
- OTP hashes (`otp:record:{projectId}:{phone}`) automatically expire in `300` seconds (5 minutes).
- OTP cooldown keys (`otp:cooldown:{projectId}:{phone}`) automatically expire in `60` seconds.

---

## 4. SMS Provider Setup (Twilio)

1. Create a Twilio account at `https://console.twilio.com`.
2. Provision an SMS-capable phone number in E.164 format (e.g., `+15005550006`).
3. Copy `TWILIO_ACCOUNT_SID` and `TWILIO_AUTH_TOKEN` to the backend `.env` (or AI Studio Secrets panel for the Android embedded BaaS server).
4. **Security Rule**: Never expose Twilio credentials to client SDKs; only the backend server calls Twilio's REST API, and OTP codes are hashed with `HMAC-SHA256` immediately upon generation.

---

## 5. Production Environment Variables (`backend/.env`)

```ini
NODE_ENV=production
PORT=8080
DATABASE_URL=postgresql://toxicbase_admin:STRONG_PASS@db.internal:5432/toxicbase_prod?sslmode=require
REDIS_URL=redis://:STRONG_REDIS_PASS@redis.internal:6379/0
JWT_ACCESS_SECRET=64_BYTE_HEX_CRYPTOGRAPHIC_SECRET_1
JWT_REFRESH_SECRET=64_BYTE_HEX_CRYPTOGRAPHIC_SECRET_2
OTP_HMAC_SECRET=64_BYTE_HEX_CRYPTOGRAPHIC_SECRET_3
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_FROM_NUMBER=+15005550006
CORS_ORIGINS=https://console.toxicbase.yourdomain.com
```

---

## 6. HTTPS, Domain Configuration & Reverse Proxy (Nginx + Certbot)

1. Point DNS `A` records for `api.toxicbase.yourdomain.com` and `console.toxicbase.yourdomain.com` to your server IP.
2. Install Nginx and Let's Encrypt Certbot:
```bash
sudo apt update && sudo apt install -y nginx certbot python3-certbot-nginx
sudo certbot --nginx -d api.toxicbase.yourdomain.com -d console.toxicbase.yourdomain.com
```
3. Configure `/etc/nginx/sites-available/toxicbase`:
```nginx
server {
    listen 443 ssl http2;
    server_name api.toxicbase.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/api.toxicbase.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.toxicbase.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 7. Automated Database Backups

Create `/etc/cron.daily/toxicbase-backup`:
```bash
#!/usr/bin/env bash
set -euo pipefail
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
pg_dump "$DATABASE_URL" -Fc -f "/var/backups/toxicbase/tb_${TIMESTAMP}.dump"
find /var/backups/toxicbase -type f -mtime +14 -delete
```
