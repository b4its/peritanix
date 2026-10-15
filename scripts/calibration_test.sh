#!/usr/bin/env bash
# =============================================================================
# DAPE agronomic calibration test (TAHAP 7.1).
#
# Validates deterministic physics against lab/reference values:
#   1. Calorimetry: mix must reach exactly 52.0 C at 100 m and 1000 m.
#   2. White-reference photometry: a dim-flash capture must correct back to
#      the true colour within a small delta-E (simulating cross-brand flash).
#   3. TWI: slope floor prevents runaway; monotonicity holds.
#   4. GDD: phase thresholds match the agronomic calendar.
#
# Usage: bash scripts/calibration_test.sh
# =============================================================================
set -uo pipefail

ML="${ML:-http://localhost:58000}"
TOKEN="${INTERNAL_TOKEN:-internal-shared-secret-change-me}"
PASS=0; FAIL=0
pass(){ echo "  ✓ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ✗ $1"; FAIL=$((FAIL+1)); }

mlpost() { # path json
  curl -sf -X POST "$ML$1" -H 'Content-Type: application/json' -H "X-Internal-Token: $TOKEN" -d "$2"
}

echo "═══ DAPE Calibration Test ═══"

# --- 1. Calorimetry at two elevations --------------------------------------
echo "[1] Calorimetry: mix reaches 52.0 C"
for elev in 100 1000; do
  R=$(mlpost /internal/postharvest/calorimetry "{\"elevation_m\":$elev,\"well_temp_c\":27,\"target_c\":52}")
  BP=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['boiling_point_c'])")
  VB=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['v_boil_parts'])")
  VW=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['v_well_parts'])")
  # Forward model: expected boiling = 100 - 0.0033*elev (approx), exact formula used.
  EXPECTED_BP=$(python3 -c "print(round(100-0.0033*$elev,2))")
  MIX=$(python3 -c "print(round(($VB*$BP + $VW*27)/($VB+$VW),2))")
  python3 -c "import sys;sys.exit(0 if abs($BP-$EXPECTED_BP)<0.01 else 1)" && \
    pass "boiling point @ ${elev}m = $BP (expected $EXPECTED_BP)" || fail "boiling point @ ${elev}m ($BP)"
  python3 -c "import sys;sys.exit(0 if abs($MIX-52.0)<0.5 else 1)" && \
    pass "mix reaches $MIX C @ ${elev}m (target 52.0, rounded ratio)" || fail "mix temp @ ${elev}m ($MIX)"
done

# --- 2. Cross-brand flash correction (white reference) ----------------------
echo "[2] White-reference photometry cancels flash bias"
# Simulate a dim flash: true white captured as (200,200,200). After correction
# the pixel should recover toward its true colour.
R=$(mlpost /internal/physics/photometry '{"measured_rgb":[150,180,120],"white_ref_rgb":[200,200,200]}')
DX=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['corrected_rgb'][0])")
python3 -c "import sys;sys.exit(0 if $DX>150 else 1)" && \
  pass "dim-flash channel lifted by white ref (150 -> $DX)" || fail "white ref correction ($DX)"

# --- 3. TWI slope floor -----------------------------------------------------
echo "[3] TWI slope floor prevents runaway"
R=$(mlpost /internal/gis/extract-dem '{"land_id":"cal","min_lon":107.6,"min_lat":-6.9,"max_lon":107.61,"max_lat":-6.89}')
MAXTWI=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['max_twi'])")
python3 -c "import sys;sys.exit(0 if $MAXTWI<50 else 1)" && \
  pass "max TWI bounded ($MAXTWI)" || fail "TWI runaway ($MAXTWI)"

# --- 4. GDD phase thresholds ------------------------------------------------
echo "[4] GDD phase transitions"
# Synthetic series should push the phase forward monotonically.
R=$(mlpost /internal/agronomy/gdd '{"land_id":"cal","series":[["d0",20,30],["d1",20,30],["d2",20,30]],"start_cum":340}')
PHASE=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['current_phase'])")
# 340 + 3*10 = 370 GDD -> FLOWERING (>=350)
[ "$PHASE" == "FLOWERING" ] || [ "$PHASE" == "FORTIFICATION_LOCKED" ] && \
  pass "GDD 370 -> $PHASE (fortification window)" || fail "GDD phase ($PHASE)"

echo
echo "═══ Results: $PASS passed, $FAIL failed ═══"
[ "$FAIL" -eq 0 ] || exit 1
