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

## Certificates (public, no auth)

### `POST /api/v1/certificates/verify`
Verify an offline-signed quality certificate. The public key travels inside the
scanned payload, so verification works even if the farmer never synced.

```json
{
  "payload": { "land_id": "...", "grade_result": "A", "sterilization_c": 52.0 },
  "signature": "base64-or-hex",
  "public_key": "base64-or-hex"
}
```
→ `{ "valid": true, "payload": { ... }, "public_key": "..." }`

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
| POST | `/internal/postharvest/calorimetry` | Black's-law hot-water ratio |
| POST | `/internal/postharvest/archimedes` | Sink ratio + grade |
