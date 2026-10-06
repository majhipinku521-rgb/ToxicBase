-- TOXICBASE PostgreSQL Schema (Multi-tenant BaaS with Per-Project Isolation)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Projects Table
CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    region VARCHAR(64) NOT NULL DEFAULT 'us-east-1',
    auth_phone_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_rules JSONB NOT NULL DEFAULT '{"defaultRead": "authenticated", "defaultWrite": "authenticated", "collections": {}}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Project API Keys Table (Project-level API authentication)
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    key_prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(128) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL DEFAULT 'client', -- 'client', 'server_admin', 'readonly'
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_keys_project ON api_keys(project_id);

-- 3. Users Table (Per-project isolated users)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phone_number VARCHAR(32),
    email VARCHAR(255),
    password_hash VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'SUSPENDED'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    UNIQUE(project_id, phone_number),
    UNIQUE(project_id, email)
);

CREATE INDEX IF NOT EXISTS idx_users_project_created ON users(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_project_status ON users(project_id, status);

-- 4. Refresh Sessions Table
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_agent TEXT,
    ip_address VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. NoSQL Collections & Documents Table (Firestore-like Document Store)
CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(128) NOT NULL,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    collection_name VARCHAR(128) NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    owner_user_id VARCHAR(64),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, collection_name, id)
);

-- GIN index for fast JSONB querying & indexing across collections
CREATE INDEX IF NOT EXISTS idx_documents_project_collection ON documents(project_id, collection_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_documents_data_gin ON documents USING GIN (data);

-- 6. Custom Collection Indexes Metadata
CREATE TABLE IF NOT EXISTS collection_indexes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    collection_name VARCHAR(128) NOT NULL,
    field_path VARCHAR(128) NOT NULL,
    sort_order VARCHAR(16) NOT NULL DEFAULT 'ASC',
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, collection_name, field_path, sort_order)
);

-- 7. Request & Security Audit Logs
CREATE TABLE IF NOT EXISTS request_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id VARCHAR(64),
    method VARCHAR(16) NOT NULL,
    path TEXT NOT NULL,
    status_code INTEGER NOT NULL,
    latency_ms INTEGER NOT NULL,
    ip_address VARCHAR(64),
    user_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_logs_project_time ON request_logs(project_id, created_at DESC);
