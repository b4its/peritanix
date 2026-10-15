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
