# DAPE API Contract

Base URL (dev): `http://localhost:58080`

Two surfaces:

- **Public** — `/api/v1/*` (this document)
- **Internal** — `/internal/*` on the ML service (`:58000`), guarded by the
  `X-Internal-Token` header. Only the Go backend calls these.

---

## Health

### `GET /healthz`
Liveness for the Go process.
```json
{ "status": "ok" }
```

### `GET /api/v1/health`
```json
{ "status": "ok", "service": "dape-api", "env": "development", "ml": true, "time": "..." }
```

### `GET /api/v1/readyz`
Deep readiness: pings Postgres + Redis + ML; `503` with `status: "not_ready"` if
any critical dependency is down (used by Kubernetes/ingress to gate traffic).
```json
{ "status": "ready", "checks": { "postgres": true, "redis": true, "ml": true } }
```

### `GET /api/v1/version`
Build/runtime info for ops.
→ `{ "service": "dape-api", "version": "1.0.0-dev", "commit": "unknown", "env": "development", "go_version": "go1.23.12", "auth_required": false }`

### `GET /api/v1/models/runtime`
Proxy of the ML ONNX runtime status (availability + which models are loadable).
→ `{ "onnxruntime_available": true, "models_dir": "models", "models": [ { "name": "...", "version": "1.0.0", "loadable": false, "fallback": "deterministic rule engine" } ] }`

Every response carries an `X-Request-ID` header (echoed or generated), and
`/api/v1/lands/:id*` routes reject non-UUID ids with `400`. `/auth/*` is
rate-limited (20/min per IP → `429`).

---

## Auth (JWT)

Public: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`.
When `AUTH_REQUIRED=true`, all mutating routes require `Authorization: Bearer <token>`.

### `POST /api/v1/auth/register`
```json
{ "name": "Pak Budi", "phone": "6281200000001", "role": "farmer", "password": "secret123" }
```
→ `201 { "token": "<jwt>", "user": { "id": "...", "name": "...", "role": "farmer" } }`
(409 if the phone already exists; password must be ≥ 6 chars.)

### `POST /api/v1/auth/login`
```json
{ "phone": "6281200000001", "password": "secret123" }
```
→ `{ "token": "<jwt>", "user": { ... } }` or `401`.

### `GET /api/v1/auth/me`  *(requires Bearer)*
→ the authenticated user.

---

## Lands

### `POST /api/v1/lands/init`
Create a plot, register the farmer's Ed25519 public key, and fetch the TWI /
flow-accumulation cache.

```json
{
  "user_id": "11111111-1111-1111-1111-111111111111",
  "name": "Bedengan Utara",
  "boundary_wkt": "POLYGON((107.601 -6.902,...))",
  "elevation_m": 850,
  "planting_date": "2026-08-28",
  "ed25519_public_key": "MCowBQ...",
  "white_reference": [1.0, 0.998, 1.004, 1.001]
}
```
Response `201`:
```json
{
  "land": { "id": "...", "area_ha": 0.25, "accumulated_gdd": 0, "current_phase": "VEGETATIVE" },
  "twi_cache": { "grid_rows": 24, "grid_cols": 24, "cells": [ ... ], "max_risk": "CRITICAL" },
  "cache_kb": 50.6
}
```
`cache_kb` is kept < 500 KB by design.

### `GET /api/v1/lands`
`{ "lands": [ ... ] }`

### `GET /api/v1/lands/geojson`
`application/geo+json` FeatureCollection of all land boundaries.

### `GET /api/v1/lands/:id`
Single land.

### `POST /api/v1/lands/:id/recompute-gdd`
Body (optional): `{ "series": [["d0", 20, 30], ...], "start_cum": 0 }`.
If omitted, a synthetic warm series is used. Returns the ML GDD result and
persists `accumulated_gdd` + `current_phase`.

---

## Soil

### `POST /api/v1/soil/sync`
Runs Stokes (texture → CEC) + dark-chamber photometry (pH) + fertilizer plan.

```json
{
  "land_id": "aaaa...",
  "sand_thickness": 38, "silt_thickness": 40, "clay_thickness": 22,
  "measured_rgb": [180, 200, 120],
  "white_ref_rgb": [255, 255, 255],
  "dark_chamber_lux_check": true
}
```
Response `201`:
```json
{
  "assessment": { "soil_class": "loam", "cec_meq": 12.3, "ph_deterministic": 6.4 },
  "fertilizer": { "npk_16_16_16_g_per_l": 3.0, "recommendation": "..." }
}
```

### `GET /api/v1/lands/:id/soil`
`{ "assessments": [ ... ] }` (newest first).

### `GET /api/v1/soil/fertility/geojson`
`application/geo+json` — one point per land carrying its latest soil assessment
(`soil_class`, `ph`, `ph_class` ∈ optimal|acid|alkaline, `cec_meq`, `nitrate_ppm`).

### `GET /api/v1/lands/:id/gdd?days=30`
GDD daily time series for the accumulation chart.
→ `{ "land_id": "...", "series": [ { "day": "2026-10-15", "t_min": 21, "t_max": 32, "gdd_day": 11.5, "gdd_cum": 480 } ] }`

---

## Microclimate sensors (TAHAP 4 / Opsi 4)

### `POST /api/v1/climate/ingest`
Batch of sensor readings pulled from an ESP32/BLE logger by the phone; returns
the current 72h EWS inline. Idempotent on `(land_id, observed_at)`.
```json
{ "land_id": "aaaa...", "device_id": "esp32-01",
  "readings": [ { "observed_at": "2026-10-15T10:00:00Z", "rh": 92, "temp_c": 26 } ] }
```
→ `201 { "ingested": 6, "land_id": "...", "ews": { "peak_risk": 0.92, "action": "RISIKO TINGGI...", "irrigation_minutes_per_bed": 0 } }`

### `GET /api/v1/lands/:id/ews`
EWS computed from the land's stored readings (last 72h).
→ the EWS object, or `{ "insufficient_data": true, "readings": N }` when < 3 rows.

---

## Diagnostics

### `POST /api/v1/diagnostics/evaluate`
Multi-modal Bayesian fusion. Go enriches with PostGIS spatial context
(`nearby_infected_count` within 50 m) before calling the ML engine.

```json
{
  "land_id": "bbbb...",
  "lon": 107.6055, "lat": -6.908,
  "local_twi": 9.4,
  "visual": { "Antraknosa": 0.7, "Ralstonia": 0.2, "Fusarium": 0.1 },
  "spatial": { "Antraknosa": 1.6, "Ralstonia": 0.8 },
  "trans_illumination_attenuation_pct": 65,
  "severity_pct": 14
}
```
Response:
```json
{
  "diagnosis": { "id": "...", "confirmed_disease": "Antraknosa", "protocol_code": "FUNG_MANKOZEB_AZOXY" },
  "bayesian": { "posterior_probabilities": { ... }, "confidence_level": "MEDIUM" },
  "nearby_infected": 1
}
```

### `GET /api/v1/lands/:id/diseases`
`{ "diseases": [ ... ] }`

### `GET /api/v1/lands/:id/diseases/geojson`
GeoJSON for the spatiotemporal heatmap.

---

## Post-harvest

### `POST /api/v1/postharvest/calorimetry`
```json
{ "elevation_m": 1000, "well_temp_c": 22, "target_c": 52, "scale": 9 }
```
→ `{ "boiling_point_c": 96.7, "v_boil_parts": 6.04, "ratio_label": "6.0 gayung didih : 9 gayung sumur", "tts_text": "...", "countdown_s": 180 }`

### `POST /api/v1/postharvest/archimedes`
```json
{ "sink_count": 16, "neutral_count": 3, "float_count": 1, "salt_g": 150, "water_ml": 1000 }
```
→ `{ "sink_ratio": 0.875, "physics_grade": "A+", "brine_relative_density": 1.0105 }`

### `POST /api/v1/harvest/batches`
Persist a signed batch (from mobile/web).
```json
{
  "land_id": "...", "batch_code": "BATCH-001",
  "boiling_point_calc": 96.7, "hot_water_ratio": "6 gayung : 9 gayung",
  "archimedes_sink_ratio": 0.82, "grade_result": "A",
  "payload_hash": "...", "crypto_signature": "...", "signer_public_key": "..."
}
```

### `GET /api/v1/harvest/batches/:code`
Single batch by code.

---

## Multi-modal optics (TAHAP 3.2)

### `POST /api/v1/optics/mfsr`
Multi-frame super-resolution burst stacking.
```json
{ "frames": [[[..rows..]], ...], "sharpness_reject_ratio": 0.5 }
```
→ `{ "frames_used": 11, "frames_rejected": 1, "shifts": [[dx,dy],...], "sharpness_gain": 1.42, "stacked_sharpness": ... }`

### `POST /api/v1/optics/trans-illumination`
Mesophyll attenuation from a contact-flash leaf image.
```json
{ "intensity": [[..]], "sigma_k": 2.0 }
```
→ `{ "attenuation_pct": 5.2, "lesion_fraction": 4.1, "max_od": 0.38, "latent_fungal_suspected": true }`

### `POST /api/v1/optics/ooze`
Stem-dip ooze test — distinguishes Ralstonia vs Fusarium.
```json
{ "frames": [[[..]], ...] }
```
→ `{ "streaming_score": 0.71, "coherence": 0.66, "directionality": 0.76, "verdict": "Ralstonia (bacterial streaming)", "confidence": 0.86 }`

---

## Grading & EWS (TAHAP 4/5)

### `POST /api/v1/postharvest/grade`
```json
{ "contour": [[x,y],...], "mean_a_channel": 45, "lesion_area": 1, "total_area": 100 }
```
→ `{ "curvature_ratio": 1.02, "red_pct": 100, "defect_pct": 1, "grade": "A+", "reasons": [...] }`

### `POST /api/v1/postharvest/decision`
Fuses GDD phase + archimedes + grading + market trend into one recommendation.
```json
{ "accumulated_gdd": 1150, "current_phase": "HARVEST", "archimedes_sink_ratio": 0.85,
  "curvature_ratio": 1.05, "red_pct": 95, "defect_pct": 1, "price_trend_14d": 0.0 }
```
→ `{ "recommended_action": "SELL_NOW", "best_window_days": 5, "expected_grade": "A+", "quality_score": 0.92, "spoilage_risk": "LOW", "rationale": ["GDD 1150 (fase HARVEST)", ...] }`

Actions: `SELL_NOW` | `HOLD_14D` | `PROCESS_OR_SORT` | `REJECT_LOT`.

### `POST /api/v1/agronomy/ews`
```json
{ "rh_series": [86,88,90,92,94,93,91], "t_series": [25,26,27,26,25,26,27], "horizon_h": 72 }
```
→ `{ "peak_risk": 0.92, "peak_hour_offset": 12, "action": "RISIKO TINGGI...", "irrigation_minutes_per_bed": 0 }`

---

## Anti-fraud forensics (TAHAP 6.3)

### `POST /api/v1/forensics/image-audit`
```json
{ "image_base64": "....", "known_hashes": ["ab12..."], "ela_threshold": 120, "duplicate_hamming_max": 4 }
```
→ `{ "exif_ok": true, "ela_variance": 43.2, "ela_suspicious": false, "perceptual_hash": "9f3c...", "duplicate_of": null, "verdict": "PASS", "flags": [] }`

---

## CRDT merge (TAHAP 6.2)

### `POST /api/v1/sync/merge`
Field-level Last-Write-Wins with version-vector concurrency detection. The
operation is commutative/associative/idempotent, so clients retry freely.

```json
{
  "local":  { "id": "L1", "vv": {"A": 2}, "fields": { "ph": { "value": 6.5, "ts": {"updated_ts":1000,"rev":3,"device_id":"A"} } } },
  "remote": { "id": "L1", "vv": {"B": 2}, "fields": { "ph": { "value": 5.8, "ts": {"updated_ts":1100,"rev":1,"device_id":"B"} } } }
}
```
→ `{ "merged": { "vv": {"A":2,"B":2}, "fields": { "ph": {"value":5.8,...} } }, "concurrent": true }`

---

## Decision support (Opsi 1D & 3)

### `POST /api/v1/yield/projection`
Fruit-count sampling → daily harvest curve + 14-day tonnage.
```json
{ "green_dark": 30, "red_blush": 15, "red_full": 10, "trees_per_bed": 500, "sampled_trees": 50, "gdd_per_day": 14 }
```
→ `{ "projected_14d_kg": 8.15, "cumulative_kg": [...], "day_mass_kg": [...], "confidence_pct": 80, "scale_factor": 10 }`

### `POST /api/v1/protocols/prescribe`
```json
{ "verdict_key": "FUNG_MANKOZEB_AZOXY", "severity_pct": 14 }
```
→ `{ "action_line": "...dosis 2.07 g/L...", "dose_g_per_l": 2.07, "steps": [...], "organic_fallback": "..." }`

### `GET /api/v1/protocols`
Full pesticide-protocol catalogue (public).

### `POST /api/v1/certificates/validate-trace`
Immersion gyroscope-trace anti-cheat.
```json
{ "samples": [ { "t": 0, "gx": 0.05, "gy": 0.02, "gz": -0.01 }, ... ] }
```
→ `{ "valid": true, "duration_s": 180, "motion_variance": 1.2e-3, "score": 0.9 }`

---

## Media (TAHAP 3.2)

### `POST /api/v1/media/upload`
Base64 media → object storage under `dape-raw`.
```json
{ "land_id": "aaaa...", "kind": "trans_illum", "filename": "leaf.jpg", "content_type": "image/jpeg", "content_base64": "..." }
```
→ `{ "key": "lands/<id>/trans_illum/leaf.jpg", "url": "http://files:9000/dape-raw/...", "bytes": 1234 }`
kinds: `burst | trans_illum | ooze | twi_raster | archimedes | photometry`; 10 MB cap.

---

## Model OTA (Continuous Learning)

### `POST /api/v1/models/manifest`
Returns the OTA manifest; only models whose version differs from the client's are
flagged `update_available`.
```json
{ "client_versions": { "detector_yolov8n": "1.0.0" } }
```
→ `{ "registry_version": "…", "update_count": 0, "models": [ { "name": "...", "version": "1.0.0", "sha256": "...", "size_mb": 6, "quantisation": "int8", "url": "..." } ] }`

### `GET /api/v1/models`
Full registry listing (public).

### `POST /api/v1/ml/feedback`
Capture a ground-truth correction (lab/expert) for the next retraining cycle.
```json
{ "model_name": "classifier_mobilenetv3", "inputs": {"leaf_lesion_pct": 12},
  "predicted": {"disease": "Antraknosa"}, "corrected": {"disease": "Keriting_Thrips"},
  "source": "lab", "confidence": 0.95 }
```
→ `201 { "id": "...", "model_name": "classifier_mobilenetv3" }`

### `GET /api/v1/ml/retrain-readiness`
Per-model labelled-example counts vs the retraining threshold.
→ `{ "models": [ { "model_name": "...", "examples": 12, "threshold": 150, "ready_to_retrain": false } ] }`

---

## Certificates (public, no auth)

### `POST /api/v1/certificates/verify`
Verify an offline-signed quality certificate. The public key travels inside the
scanned payload, so verification works even if the farmer never synced.

```json
{
  "payload": { "land_id": "...", "grade_result": "A", "sterilization_c": 52.0 },
  "signature": "base64-or-hex",
  "public_key": "base64-or-hex",
  "user_id": "optional uuid"
}
```
→ `{ "valid": true, "payload": { ... }, "public_key": "..." }`

If `public_key` is omitted and `user_id` is given, the farmer's **stored** key is
used. If both are present and disagree, verification returns `valid: false`
(prevents swapping in a forged key).

---

## Offline Sync

### `POST /api/v1/sync/delta`
Apply a batch of offline mutations with per-op idempotency (Redis `SET NX`,
24 h TTL) and Last-Write-Wins.

```json
{
  "device_id": "web-ab12cd34",
  "operations": [
    {
      "idempotency_key": "web-ab12cd34:uuid",
      "entity": "soil_assessment",
      "op": "upsert",
      "updated_ts": 1792090000,
      "rev": 5,
      "data": { "land_id": "...", "sand_pct": 40, "silt_pct": 38, "clay_pct": 22, "soil_class": "loam", "cec_meq": 14.5, "ph_deterministic": 6.5 }
    }
  ]
}
```
→ `{ "results": [ { "status": "applied" } ], "server_ts": ... }`

Re-sending the same `idempotency_key` returns `"duplicate"` and does not
re-insert. Supported entities: `soil_assessment`, `disease_log`, `harvest_batch`.

---

## Internal ML endpoints (Go → FastAPI)

All require `X-Internal-Token`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/gis/extract-dem` | DEM crop + flow accumulation + TWI grid |
| POST | `/internal/physics/soil-calibrate` | Stokes → USDA → CEC |
| POST | `/internal/physics/photometry` | CIELAB + white-ref + reagent match |
| POST | `/internal/physics/fertilizer-plan` | Prescriptive NPK/KNO3/Ca/dolomite |
| POST | `/internal/ml/bayesian-diagnosis` | V,S,T Bayes fusion |
| POST | `/internal/agronomy/gdd` | GDD accumulation + phase |
| POST | `/internal/agronomy/grade-fruit` | Curvature/colour/defect grading |
| POST | `/internal/agronomy/ews-microclimate` | 72h fungal-blast EWS |
| POST | `/internal/optics/mfsr-stack` | Burst alignment + stacking |
| POST | `/internal/optics/trans-illumination` | Mesophyll attenuation |
| POST | `/internal/optics/ooze-optical-flow` | Ralstonia vs Fusarium |
| POST | `/internal/forensics/image-audit` | EXIF + ELA + aHash duplicate |
| POST | `/internal/decision/yield-projection` | 14-day harvest tonnage projection |
| POST | `/internal/decision/protocol` | Prescriptive pesticide protocol |
| GET  | `/internal/decision/protocols` | Full protocol catalogue |
| POST | `/internal/decision/validate-trace` | Immersion trace anti-cheat |
| POST | `/internal/decision/harvest-decision` | Sell/hold/sort fusion |
| POST | `/internal/models/manifest` | OTA model manifest |
| GET  | `/internal/models/list` | All registered models |
| GET  | `/internal/models/runtime` | ONNX runtime + loadable-model status |
| POST | `/internal/postharvest/calorimetry` | Black's-law hot-water ratio |
| POST | `/internal/postharvest/archimedes` | Sink ratio + grade |
