# DAPE Physics Reference

Every formula below is implemented in three places and must stay numerically
identical:

- **Python** — `web/backend/fastapi/app/services/physics/*.py` (source of truth, server)
- **TypeScript** — `web/frontend/src/lib/physics.ts` (browser, offline)
- **Kotlin** — `mobile/src/main/kotlin/id/peritanix/dape/physics/Physics.kt` (device, offline)

Tests live beside each: `tests/test_*.py`, `tests/physics.test.ts`, `*Test.kt`.

> **Scope note (honesty).** Where the design doc names learned models
> ("trans-illumination CNN", "Deep Burst Super-Resolution"), this codebase
> implements the **deterministic signal-processing core** those models consume
> (optical-density maps, phase-correlation alignment, Lucas–Kanade flow,
> Otsu segmentation). The learned weights are a drop-in replacement at the same
> interface — see each module's docstring. This keeps the engine reproducible,
> auditable and offline-capable today while leaving a clean ML upgrade path.

---

## 1. Topographic Wetness Index (TWI)

```
TWI = ln( a / tan β )
```
- `a` = upslope contributing area per unit contour width (m²/m)
- `β` = local slope angle (radians)

Slope is floored at 0.1° so a flat cell does not send TWI → +∞. Risk bands and
the prescriptive bed height:

| TWI | Risk | Bed height |
|---|---|---|
| ≥ 10.0 | CRITICAL | 50 cm |
| ≥ 8.0 | HIGH | 40 cm |
| ≥ 6.0 | MODERATE | 30 cm |
| < 6.0 | LOW | 20 cm |

High TWI ⇒ concave wet cells ⇒ higher *Phytophthora* / *Ralstonia* risk.

**D8 flow accumulation** routes each cell to its steepest-descent neighbour,
processing cells in descending elevation with a flat-index tie-break for full
determinism.

## 2. Stokes sedimentation → USDA texture → CEC

Terminal velocity of a settling sphere:

```
v = (2/9) · (ρ_p − ρ_f) · g · r² / μ
```
with ρ_p = 2650 kg/m³, ρ_f = 998 kg/m³, μ = 1.002e-3 Pa·s, g = 9.80665 m/s².

The protocol photographs the suspension at **t = 40 s** (sand settled) and
**t = 7200 s** (silt settled). Layer thickness fractions → (sand, silt, clay) %
→ USDA texture class → CEC (meq/100 g) via class reference values.

> A dispersant (Na-hexametaphosphate) is mandatory; without it the fractions are
> meaningless.

## 3. CIELAB photometry (dark chamber)

sRGB → linear → CIE XYZ (D65) → CIELAB. Per-channel white-reference gain
cancels LED-flash spectral bias between phone brands:

```
corrected_c = c / white_c · 255
```

Reagent-patch matching uses CIE76 ΔE with inverse-distance interpolation over
the calibration curve. Target: deterministic pH ± 0.1.

## 4. Black's Law calorimetry (52 °C sterilization)

Local boiling point vs elevation:

```
T_boil(h) = 100 − 0.0033 · h      (h in metres)
```

Mixing boiling water (V_boil) with well water (V_well) to reach exactly 52 °C:

```
V_boil · (T_boil − 52) = V_well · (52 − T_well)
```

The app emits a human ratio, e.g. `5.0 gayung didih : 9 gayung sumur`, followed
by a 180 s immersion countdown. At 1000 m, T_boil = 96.7 °C.

## 5. Growing Degree Days & phases

```
GDD_day = max(0, (T_max + T_min)/2 − 15 °C)
```

| Accumulated GDD | Phase |
|---|---|
| < 350 | VEGETATIVE |
| 350 – 699 | FLOWERING (**Fortification Lock**) |
| 700 – 1049 | FRUITING |
| ≥ 1050 | HARVEST |

During FLOWERING the Ca+B+Si cell-wall fortification is mandatory and high-N
(urea) is forbidden — enforced by the backend and the mobile UI.

## 6. Bayesian multi-modal diagnosis

```
                P(V|D)·P(S|D)·P(T|D)·P(D)
P(D|V,S,T) = ───────────────────────────────
             Σ_j P(V|D_j)·P(S|D_j)·P(T|D_j)·P(D_j)
```
- `V` visual / trans-illumination posterior
- `S` spatial pattern (Moran's I classified into EDGE_WIND_GRADIENT /
  DRAINAGE_TROUGH_CLUSTER / UNIFORM_BLOCK)
- `T` local TWI likelihood (water-borne pathogens up-weighted when wet)

Missing channels default to 1.0 (uninformative); missing priors to uniform.

## 7. Archimedes density assay

```
sink_ratio = (sink + 0.5 · neutral) / (sink + neutral + float)
```
| sink_ratio | Physics grade |
|---|---|
| ≥ 0.80 | A+ |
| ≥ 0.65 | A |
| ≥ 0.45 | B |
| < 0.45 | BS |

Brine relative density ≈ `1 + 0.0007 · (g salt per mL) · 100`.

## 8. Canonical JSON (offline certificate signatures)

Sorted keys, no insignificant whitespace. **Float rule** (critical for
cross-platform signatures): integral values keep a single decimal place
(`52` → `52.0`); non-integral use the shortest round-trip form (`0.82` → `0.82`).

The Go verifier, the Kotlin signer, and the TypeScript encoder must all agree.

## 9. Multi-Frame Super-Resolution (MFSR)

Align a burst by **phase correlation** (peak of the normalised cross-power
spectrum) then shift-and-add, rejecting frames whose **Laplacian variance** is
below a fraction of the sharpest frame:

```
mfsr_sharpness_gain = VarLaplacian(stacked) / mean_i VarLaplacian(frame_i)
```

## 10. Trans-illumination mesophyll attenuation

Optical density of transmitted light, with `I0` a bright reference percentile:

```
OD = -log10(I / I0)
attenuation_pct = area(OD > baseline + k·σ) / leaf_area × 100
```

A dark (dense) patch ⇒ higher OD ⇒ latent fungal colonisation suspicion when
`3 % ≤ attenuation ≤ 60 %`. Otsu's method gives a parameter-free lesion split.

## 11. Ooze optical flow (Ralstonia vs Fusarium)

Dense **Lucas–Kanade** flow between consecutive frames. Descriptors:

```
mean_speed      = mean(|v|)               over moving pixels
coherence       = |mean(v)| / mean(|v|)   (1 = perfectly aligned)
directionality  = |mean(e^{iθ})|          (resultant length of flow angles)
streaming_score = 0.5·coherence + 0.5·directionality
```

`streaming_score ≥ 0.6` ⇒ bacterial streaming ⇒ **Ralstonia**; else **Fusarium**.

## 12. Post-harvest grading

```
curvature_ratio = arc_length(spine) / chord_length(spine)   (≈1 = straight)
red_pct         = clamp((a* − 25)/(45 − 25), 0, 1) × 100
defect_pct      = lesion_area / total_area × 100
```

Grade table (defects disqualify first — one patek fruit spoils a crate):
`defect ≥ 15 %` → BS; else `red ≥ 90 & defect < 3 & curv ≤ 1.12` → A+;
`red ≥ 75 & defect < 5 & curv ≤ 1.25` → A; `red ≥ 50 & defect < 10` → B; else BS.

## 13. Microclimate EWS (72 h fungal blast)

Per-hour risk = RH membership × temperature membership:

```
rh_m = ramp(RH; 80, 90)                    # saturates quickly ≥ 90 %
t_m  = ramp(T; 15, 27) · (1 − ramp(T; 33, 38))
risk_h = rh_m · t_m
```

Extrapolate RH/T with a damped linear trend, take the peak over the horizon.
`peak ≥ 0.7` ⇒ high risk, withhold irrigation; else normal schedule.

## 14. Image forensics (anti-fraud)

```
ELA: recompress at quality Q, ela_variance = Var(|original − recompressed|)
aHash: 64-bit average hash of the 8×8 grayscale; duplicate if
       Hamming(hash, known) ≤ threshold
EXIF:  camera Model/Make + DateTimeOriginal required for provenance
```

Verdicts: `REJECT` (duplicate), `SUSPECT` (high ELA + ≥2 flags),
`REVIEW` (weak provenance), `PASS`.

## 15. CRDT merge (offline multi-worker)

Each field carries an LWW timestamp `(updated_ts, rev, device_id)` ordered
lexicographically with `device_id` as the deterministic tie-break. A
`VersionVector` (device → count) detects concurrency:
`Concurrent(a,b) = ¬a.Dominates(b) ∧ ¬b.Dominates(a)`.
`Merge` is **commutative, associative, idempotent** — proven by Go tests — so
replicas converge regardless of sync order.

## 16. Yield projection (Opsi 3)

Counts of three ripeness classes are scaled to the bed
(`scale = trees_per_bed / sampled_trees`) and advanced with a GDD-driven
phenology transfer matrix:

```
r_gb = min(1, gdd_per_day / gdd_green_to_blush)   # green -> blush rate
r_bf = min(1, gdd_per_day / gdd_blush_to_full)    # blush -> full rate
green_dark' = green_dark·(1−r_gb)
red_blush'  = red_blush·(1−r_bf) + green_dark·r_gb
red_full'   = red_full           + red_blush·r_bf   (picked daily)
day_mass_kg = red_full · red_full_g / 1000
```

Confidence heuristic grows with the sampled fraction:
`confidence = min(97, 60 + 200 · sampled/total)`.

## 17. Prescriptive protocols (Opsi 1D)

Severity scales the dose linearly within the labelled range — **never
exceeding it**:

```
dose = dose_min + (dose_max − dose_min) · clamp(severity/100, 0, 1)
```

A transparent rule engine (not an LLM — doses must not be hallucinated). 9
protocols incl. Antraknosa (Mankozeb+Azoksistrobin), Ralstonia (eradication),
Thrips/Mites/Begomovirus, deficiency. Each carries safety + organic fallback.

## 18. Immersion trace anti-cheat

```
duration = max(t) − min(t)                     must be ≈ 180 s (±20)
motion_variance = Var(|gyro|)                  must exceed a floor
reject if the gyro signal is constant (all-equal) -> timer skipped
score = 0.5·duration_closeness + 0.5·motion_norm
```

A heuristic deterrent combined with the SHA-256 image hash and Ed25519
signature in the certificate.

## 19. Spatial epidemiology enrichment (TAHAP 3.2)

Beyond Moran's I (§6), each diagnosis point is enriched with PostGIS context
that feeds the `P(S | D_i)` channel:

```
distance_to_land_edge_m   = ST_Distance(point, ST_Boundary(land))
bearing_deg               = (90 − azimuth(centroid, point)) mod 360
```

- **Edge proximity** favours airborne/vector entry:
  `edge_mult = 1 − 0.7·min(1, dist/band)`
- **Wind alignment** favours downwind spread:
  `wind_mult = 1.5` if |Δbearing| ≤ 60°, `0.7` if ≥ 120°, linear between.
- **Pattern classification** (§6) maps to per-disease odds:
  EDGE_WIND_GRADIENT → Begomovirus/Thrips/Mites up-weighted;
  DRAINAGE_TROUGH_CLUSTER → Ralstonia/Phytophthora/Fusarium;
  UNIFORM_BLOCK → deficiency.

## 20. Model registry & OTA (Continuous Learning)

Each ONNX artefact is described by a *model card* (name, version, sha256,
size, quantisation). The registry version is a deterministic digest of
`(name, version)` pairs. A client's OTA manifest flags only the models whose
version changed, so a farmer on metered data downloads the minimum. Integrity
is verified by re-hashing the artefact and comparing to the card's sha256.

## 21. Harvest decision engine

A transparent rule engine over the post-harvest physics (not a black box):

```
quality_score = 0.35·sink_ratio + 0.30·(red/100) + 0.25·(1−defect/100)
                       + 0.10·(1 − (curv−1)/0.5)      [each term clamped 0..1]

defect ≥ 15 %            -> PROCESS_OR_SORT   (patek dominates)
red < 50 % (immature)    -> HOLD_14D          (ripen via GDD)
uptrend ≥ 12 % & grade∈{A+,A} & risk≤MED
                         -> HOLD_14D          (sell into strength)
grade = A+ & risk = LOW  -> SELL_NOW          (peak, lock contract)
otherwise                -> SELL_NOW          (time-sensitive)
```

A GDD-phase gate overrides `SELL_NOW` when the land is still VEGETATIVE /
FLOWERING (thermal time has not reached harvest), so a few red samples cannot
trigger a premature harvest.
