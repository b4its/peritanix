-- =============================================================================
-- DAPE / Peritanix — Seed data (idempotent)
-- Safe to run repeatedly: guarded by seed_marker + ON CONFLICT.
-- Contains synthetic-but-plausible data for local dev / demo / tests.
-- =============================================================================

DO $$
DECLARE
    v_farmer_id   UUID;
    v_worker_id   UUID;
    v_land_id     UUID;
    v_land2_id    UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM seed_marker WHERE id = 1) THEN
        RAISE NOTICE '[seed] already seeded — skipping.';
        RETURN;
    END IF;

    RAISE NOTICE '[seed] inserting demo data...';

    -- --- Users --------------------------------------------------------------
    INSERT INTO users (id, name, role, phone, ed25519_public_key, white_reference)
    VALUES
        ('11111111-1111-1111-1111-111111111111', 'Pak Budi (Pemilik)', 'farmer',
         '6281200000001',
         'MCowBQYDK2VwAyEAGb9ECWmEzf6FQbrBZ9w7lshQhqowtrbLDFw4rXAxZuE=',
         '[1.0,0.998,1.004,1.001]')
    ON CONFLICT (id) DO NOTHING
    RETURNING id INTO v_farmer_id;

    INSERT INTO users (id, name, role, phone)
    VALUES ('22222222-2222-2222-2222-222222222222', 'Siti (Pekerja Kebun)', 'worker',
            '6281200000002')
    ON CONFLICT (id) DO NOTHING
    RETURNING id INTO v_worker_id;

    -- --- Lands (two plots in a hilly area, ~0.25 ha & ~1.2 ha) --------------
    -- Lahan 1: mild slope, elevation ~850 m
    INSERT INTO lands (id, user_id, name, boundary, elevation_m, planting_date,
                       accumulated_gdd, current_phase)
    VALUES (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        '11111111-1111-1111-1111-111111111111',
        'Bedengan Utara',
        ST_GeomFromText('POLYGON((
            107.6010 -6.9020,
            107.6030 -6.9020,
            107.6032 -6.9040,
            107.6012 -6.9041,
            107.6010 -6.9020
        ))', 4326),
        850.0,
        CURRENT_DATE - INTERVAL '48 days',
        480.0,
        'FLOWERING'
    )
    ON CONFLICT (id) DO NOTHING
    RETURNING id INTO v_land_id;

    -- Lahan 2: steeper slope -> higher TWI risk, elevation ~980 m
    INSERT INTO lands (id, user_id, name, boundary, elevation_m, planting_date,
                       accumulated_gdd, current_phase)
    VALUES (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        '11111111-1111-1111-1111-111111111111',
        'Lereng Selatan',
        ST_GeomFromText('POLYGON((
            107.6050 -6.9050,
            107.6085 -6.9050,
            107.6085 -6.9085,
            107.6050 -6.9085,
            107.6050 -6.9050
        ))', 4326),
        980.0,
        CURRENT_DATE - INTERVAL '25 days',
        210.0,
        'VEGETATIVE'
    )
    ON CONFLICT (id) DO NOTHING
    RETURNING id INTO v_land2_id;

    -- --- Soil assessments ---------------------------------------------------
    INSERT INTO soil_assessments (
        id, land_id, sand_pct, silt_pct, clay_pct, soil_class,
        cec_meq, ph_deterministic, nitrate_ppm, dark_chamber_lux_check, stokes_meta
    ) VALUES
        ('c0000001-0000-0000-0000-000000000001',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         38.0, 40.0, 22.0, 'loam', 18.5, 6.3, 24.0, true,
         '{"sand_settle_s":40,"silt_settle_s":7200,"dispersant":"Na-HMP"}'::jsonb),
        ('c0000002-0000-0000-0000-000000000002',
         'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
         22.0, 34.0, 44.0, 'clay', 27.1, 5.4, 12.0, true,
         '{"sand_settle_s":40,"silt_settle_s":7200,"dispersant":"Na-HMP"}'::jsonb)
    ON CONFLICT (id) DO NOTHING;

    -- --- Disease logs (a drainage-trough cluster on land 2) ------------------
    INSERT INTO disease_logs (
        id, land_id, point_location, local_twi,
        posterior_probabilities, confirmed_disease, severity_pct,
        confidence_level, protocol_code
    ) VALUES
        ('d0000001-0000-0000-0000-000000000001',
         'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
         ST_SetSRID(ST_MakePoint(107.6055, -6.9080), 4326), 9.4,
         '{"Antraknosa":0.71,"Ralstonia":0.18,"Defisiensi":0.11}'::jsonb,
         'Antraknosa (Colletotrichum spp.)', 14.0, 'HIGH', 'FUNG_MANKOZEB_AZOXY'),
        ('d0000002-0000-0000-0000-000000000002',
         'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
         ST_SetSRID(ST_MakePoint(107.6060, -6.9082), 4326), 8.9,
         '{"Antraknosa":0.63,"Ralstonia":0.25,"Defisiensi":0.12}'::jsonb,
         'Antraknosa (Colletotrichum spp.)', 9.5, 'MEDIUM', 'FUNG_MANKOZEB_AZOXY'),
        ('d0000003-0000-0000-0000-000000000003',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         ST_SetSRID(ST_MakePoint(107.6020, -6.9030), 4326), 6.1,
         '{"Keriting_Thrips":0.55,"Keriting_Mites":0.30,"Begomovirus":0.15}'::jsonb,
         'Keriting daun — Thrips', 20.0, 'MEDIUM', 'INSEK_ABAMEKTIN')
    ON CONFLICT (id) DO NOTHING;

    -- --- GDD history (last 14 days) -----------------------------------------
    INSERT INTO gdd_daily (land_id, day, t_min, t_max, gdd_day, gdd_cum)
    SELECT
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        (CURRENT_DATE - g)::date,
        20.0 + (random()*3)::numeric,
        30.0 + (random()*4)::numeric,
        GREATEST(0, (((20.0 + 30.0)/2) + (random()*3)) - 15.0),
        (14 - g) * 10.0
    FROM generate_series(0, 13) AS g
    ON CONFLICT DO NOTHING;

    -- --- Harvest batch ------------------------------------------------------
    INSERT INTO harvest_batches (
        id, land_id, batch_code, boiling_point_calc, hot_water_ratio,
        archimedes_sink_ratio, grade_result, payload_hash, crypto_signature,
        signer_public_key
    ) VALUES (
        'e0000001-0000-0000-0000-000000000001',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'BATCH-2024-DEMO-001',
        96.70, '5 gayung didih : 9 gayung sumur',
        0.82, 'A',
        'demo-payload-hash',
        'demo-ed25519-signature-not-verified',
        'MCowBQYDK2VwAyEAGb9ECWmEzf6FQbrBZ9w7lshQhqowtrbLDFw4rXAxZuE='
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO seed_marker (id, version) VALUES (1, 'v1.0.0')
    ON CONFLICT (id) DO NOTHING;

    RAISE NOTICE '[seed] done.';
END $$;
