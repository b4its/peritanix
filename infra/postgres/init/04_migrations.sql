-- =============================================================================
-- DAPE / Peritanix — Idempotent migrations
-- Safe to run repeatedly on an EXISTING volume (adds columns/indexes that were
-- introduced after the volume was first initialised). `make web-migrate` runs
-- this file. New volumes get everything from 01_schema.sql anyway.
-- =============================================================================

-- TAHAP 0.1: JWT auth password hash
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;

-- Ensure the seed marker exists (older volumes).
CREATE TABLE IF NOT EXISTS seed_marker (
    id          INT PRIMARY KEY DEFAULT 1,
    seeded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     VARCHAR(32) NOT NULL,
    CONSTRAINT seed_single_row CHECK (id = 1)
);

-- Helpful index for login lookups by phone.
CREATE INDEX IF NOT EXISTS idx_users_phone ON users (phone);

-- Performance index for media/time queries on disease logs.
CREATE INDEX IF NOT EXISTS idx_disease_created ON disease_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_harvest_created ON harvest_batches (created_at DESC);

-- TAHAP 3.2: spatial-context functions are defined in 02_functions.sql with
-- CREATE OR REPLACE. On an existing volume, re-applying 02_functions.sql picks
-- them up. `make web-migrate` runs it explicitly:
--   psql -f /docker-entrypoint-initdb.d/02_functions.sql

-- Continuous-learning feedback store (ground-truth corrections for retraining).
CREATE TABLE IF NOT EXISTS ml_feedback (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    model_name      VARCHAR(64) NOT NULL,
    inputs          JSONB NOT NULL DEFAULT '{}'::jsonb,
    predicted       JSONB NOT NULL DEFAULT '{}'::jsonb,
    corrected       JSONB NOT NULL DEFAULT '{}'::jsonb,
    land_id         UUID REFERENCES lands(id) ON DELETE SET NULL,
    diagnosis_id    UUID REFERENCES disease_logs(id) ON DELETE SET NULL,
    source          VARCHAR(32) NOT NULL DEFAULT 'field',
    confidence      FLOAT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_feedback_model ON ml_feedback (model_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON ml_feedback (created_at DESC);

CREATE OR REPLACE FUNCTION feedback_counts()
RETURNS TABLE(model_name VARCHAR, n BIGINT) AS $$
    SELECT model_name, COUNT(*)::BIGINT AS n
    FROM ml_feedback
    GROUP BY model_name
    ORDER BY COUNT(*) DESC;
$$ LANGUAGE sql STABLE;
