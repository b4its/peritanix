# DAPE Physics Reference

Every formula below is implemented in three places and must stay numerically
identical:

- **Python** — `web/backend/fastapi/app/services/physics/*.py` (source of truth, server)
- **TypeScript** — `web/frontend/src/lib/physics.ts` (browser, offline)
- **Kotlin** — `mobile/src/main/kotlin/id/peritanix/dape/physics/Physics.kt` (device, offline)

Tests live beside each: `tests/test_*.py`, `tests/physics.test.ts`, `*Test.kt`.

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
