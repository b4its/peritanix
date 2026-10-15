-- =============================================================================
-- DAPE / Peritanix — Core Schema (PostgreSQL + PostGIS)
-- Runs automatically on first init of an empty postgres volume.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- --- Enums ------------------------------------------------------------------
DO $$ BEGIN
    CREATE TYPE land_phase AS ENUM (
        'VEGETATIVE', 'FLOWERING', 'FRUITING', 'HARVEST', 'FORTIFICATION_LOCKED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('farmer', 'worker', 'agronomist', 'koperasi', 'offtaker');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- --- users ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(160) NOT NULL,
    role                user_role NOT NULL DEFAULT 'farmer',
    phone               VARCHAR(32),
    -- Ed25519 public key (base64 / hex). Used to verify offline-signed certs.
    ed25519_public_key  TEXT,
    -- bcrypt password hash for JWT auth (nullable: device-only users may skip).
    password_hash       TEXT,
    -- Per-device white reference matrix for dark-chamber photometry (JSON array).
    white_reference     JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- CRDT / LWW sync bookkeeping
    rev                 BIGINT NOT NULL DEFAULT 1,
    updated_ts          BIGINT NOT NULL DEFAULT 0
);

-- --- lands ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lands (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(160) NOT NULL,
    boundary            GEOMETRY(Polygon, 4326) NOT NULL,
    area_ha             FLOAT GENERATED ALWAYS AS (ST_Area(boundary::geography) / 10000.0) STORED,
    centroid            GEOMETRY(Point, 4326) GENERATED ALWAYS AS (ST_Centroid(boundary)) STORED,
    elevation_m         FLOAT,
    twi_raster_url      TEXT,
    planting_date       DATE,
    accumulated_gdd     FLOAT NOT NULL DEFAULT 0,
    current_phase       land_phase NOT NULL DEFAULT 'VEGETATIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    rev                 BIGINT NOT NULL DEFAULT 1,
    updated_ts          BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_lands_boundary ON lands USING GIST (boundary);
CREATE INDEX IF NOT EXISTS idx_lands_user ON lands (user_id);

-- --- soil_assessments -------------------------------------------------------
CREATE TABLE IF NOT EXISTS soil_assessments (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    land_id                 UUID NOT NULL REFERENCES lands(id) ON DELETE CASCADE,
    sand_pct                FLOAT,
    silt_pct                FLOAT,
    clay_pct                FLOAT,
    soil_class              VARCHAR(48),
    cec_meq                 FLOAT,
    ph_deterministic        FLOAT,
    nitrate_ppm             FLOAT,
    dark_chamber_lux_check  BOOLEAN NOT NULL DEFAULT false,
    stokes_meta             JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    rev                     BIGINT NOT NULL DEFAULT 1,
    updated_ts              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_soil_land ON soil_assessments (land_id);

-- --- disease_logs -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS disease_logs (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    land_id                 UUID NOT NULL REFERENCES lands(id) ON DELETE CASCADE,
    point_location          GEOMETRY(Point, 4326),
    local_twi               FLOAT,
    mfsr_image_url          TEXT,
    trans_illum_image_url   TEXT,
    ooze_video_url          TEXT,
    posterior_probabilities JSONB,
    confirmed_disease       VARCHAR(80),
    severity_pct            FLOAT,
    confidence_level        VARCHAR(24),
    protocol_code           VARCHAR(48),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    rev                     BIGINT NOT NULL DEFAULT 1,
    updated_ts              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_disease_point ON disease_logs USING GIST (point_location);
CREATE INDEX IF NOT EXISTS idx_disease_land ON disease_logs (land_id);

-- --- harvest_batches --------------------------------------------------------
CREATE TABLE IF NOT EXISTS harvest_batches (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    land_id                 UUID NOT NULL REFERENCES lands(id) ON DELETE CASCADE,
    batch_code              VARCHAR(48) UNIQUE NOT NULL,
    boiling_point_calc      FLOAT,
    hot_water_ratio         VARCHAR(64),
    archimedes_sink_ratio   FLOAT,
    grade_result            VARCHAR(16),
    payload_hash            TEXT,
    crypto_signature        TEXT,
    signer_public_key       TEXT,
    gyro_trace              JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    rev                     BIGINT NOT NULL DEFAULT 1,
    updated_ts              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_harvest_land ON harvest_batches (land_id);

-- --- idempotency (server-side duplicate suppression) ------------------------
CREATE TABLE IF NOT EXISTS sync_idempotency (
    idempotency_key     TEXT PRIMARY KEY,
    endpoint            VARCHAR(160) NOT NULL,
    response_hash       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --- gdd history (time series for dashboard) --------------------------------
CREATE TABLE IF NOT EXISTS gdd_daily (
    land_id     UUID NOT NULL REFERENCES lands(id) ON DELETE CASCADE,
    day         DATE NOT NULL,
    t_min       FLOAT,
    t_max       FLOAT,
    gdd_day     FLOAT NOT NULL DEFAULT 0,
    gdd_cum     FLOAT NOT NULL DEFAULT 0,
    PRIMARY KEY (land_id, day)
);

-- --- updated_at trigger -----------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users','lands','soil_assessments','disease_logs','harvest_batches']
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_touch_%1$s ON %1$s;
             CREATE TRIGGER trg_touch_%1$s BEFORE UPDATE ON %1$s
             FOR EACH ROW EXECUTE FUNCTION touch_updated_at();', t);
    END LOOP;
END $$;

-- --- seed marker ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seed_marker (
    id          INT PRIMARY KEY DEFAULT 1,
    seeded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     VARCHAR(32) NOT NULL,
    CONSTRAINT seed_single_row CHECK (id = 1)
);
