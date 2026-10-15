#!/usr/bin/env bash
# =============================================================================
# DAPE end-to-end smoke test.
# Exercises every layer: TWI cache, soil (Stokes+CIELAB), Bayesian diagnosis,
# microclimate EWS, grading, calorimetry, Archimedes, CRDT merge, idempotent
# delta sync, and Ed25519 certificate verification.
#
# Usage: API=http://localhost:58080 bash scripts/e2e_smoke.sh
# Exit non-zero on the first failed assertion.
# =============================================================================
set -euo pipefail

API="${API:-http://localhost:58080}"
ML="${ML:-http://localhost:58000}"
TOKEN="${INTERNAL_TOKEN:-internal-shared-secret-change-me}"
USER_ID="11111111-1111-1111-1111-111111111111"
PASS=0
FAIL=0

pass() { echo "  ✓ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ $1"; FAIL=$((FAIL + 1)); }

jqv() { python3 -c "
import sys, json
d = json.load(sys.stdin)
path = '''$1'''
for k in [p for p in path.strip('[]').split('][') if p]:
    k = k.strip().strip(\"'\").strip('\"')
    d = d[int(k)] if k.isdigit() else d[k]
print(d)
" 2>/dev/null; }

assert_eq() { # actual expected label
  if [ "$1" == "$2" ]; then pass "$3 ($1)"; else fail "$3 (got '$1', want '$2')"; fi
}
assert_gt0() { # value label
  if python3 -c "import sys;sys.exit(0 if float('$1')>0 else 1)" 2>/dev/null; then pass "$2 ($1)"; else fail "$2 (got '$1')"; fi
}

echo "═══ DAPE E2E Smoke ═══"
echo "API=$API ML=$ML"

# --- 1. Health --------------------------------------------------------------
echo "[1] Health"
H=$(curl -sf "$API/api/v1/health")
assert_eq "$(echo "$H" | jqv "['status']")" "ok" "api status"
assert_eq "$(echo "$H" | jqv "['ml']")" "True" "ml reachable"

# --- 2. Land init + TWI cache ----------------------------------------------
echo "[2] Land init + TWI cache"
BOX="POLYGON((107.610 -6.910,107.613 -6.910,107.613 -6.913,107.610 -6.913,107.610 -6.910))"
LAND=$(curl -sf -X POST "$API/api/v1/lands/init" -H 'Content-Type: application/json' \
  -d "{\"user_id\":\"$USER_ID\",\"name\":\"E2E-$(date +%s)\",\"boundary_wkt\":\"$BOX\",\"elevation_m\":880}")
LAND_ID=$(echo "$LAND" | jqv "['land']['id']")
CELLS=$(echo "$LAND" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['twi_cache']['cells']))")
KB=$(echo "$LAND" | jqv "['cache_kb']")
assert_gt0 "$CELLS" "twi cells present"
if python3 -c "import sys;sys.exit(0 if float('$KB')<500 else 1)"; then pass "twi cache < 500KB ($KB)"; else fail "twi cache size ($KB)"; fi

# --- 3. Soil: Stokes + photometry + fertilizer ------------------------------
echo "[3] Soil Stokes + CIELAB + fertilizer"
SOIL=$(curl -sf -X POST "$API/api/v1/soil/sync" -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$LAND_ID\",\"sand_thickness\":38,\"silt_thickness\":40,\"clay_thickness\":22,\"measured_rgb\":[180,200,120],\"dark_chamber_lux_check\":true}")
SC=$(echo "$SOIL" | jqv "['assessment']['soil_class']")
assert_eq "$SC" "loam" "USDA texture class"
assert_gt0 "$(echo "$SOIL" | jqv "['assessment']['cec_meq']")" "CEC computed"

# --- 4. Bayesian diagnosis with spatial evidence ----------------------------
echo "[4] Bayesian multi-modal diagnosis"
DIAG=$(curl -sf -X POST "$API/api/v1/diagnostics/evaluate" -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$LAND_ID\",\"lon\":107.6115,\"lat\":-6.9115,\"local_twi\":9.2,\"visual\":{\"Antraknosa\":0.7,\"Ralstonia\":0.2,\"Fusarium\":0.1},\"spatial\":{\"Antraknosa\":1.5},\"trans_illumination_attenuation_pct\":60,\"severity_pct\":12,\"prevailing_wind_deg\":90}")
VERDICT=$(echo "$DIAG" | jqv "['bayesian']['final_verdict']")
assert_gt0 "$(echo "$DIAG" | jqv "['bayesian']['posterior_probabilities']['Antraknosa']")" "posterior computed"
PATTERN=$(echo "$DIAG" | jqv "['spatial_context']['pattern']")
echo "    verdict: $VERDICT | spatial pattern: $PATTERN"
DEDGE=$(echo "$DIAG" | jqv "['spatial_context']['distance_to_edge_m']")
python3 -c "import sys;sys.exit(0 if float('$DEDGE')>=0 else 1)" && \
  pass "distance-to-edge computed ($DEDGE m)" || fail "distance-to-edge ($DEDGE)"

# --- 5. Microclimate EWS ----------------------------------------------------
echo "[5] Microclimate EWS (72h blast risk)"
EWS=$(curl -sf -X POST "$API/api/v1/agronomy/ews" -H 'Content-Type: application/json' \
  -d '{"land_id":"'"$LAND_ID"'","rh_series":[88,90,92,94,93,91],"t_series":[25,26,27,26,25,26]}')
assert_gt0 "$(echo "$EWS" | jqv "['peak_risk']")" "blast risk computed"
echo "    action: $(echo "$EWS" | jqv "['irrigation_minutes_per_bed']") min/bed"

# --- 6. Post-harvest grading ------------------------------------------------
echo "[6] Post-harvest grading"
GRADE=$(curl -sf -X POST "$API/api/v1/postharvest/grade" -H 'Content-Type: application/json' \
  -d '{"mean_a_channel":45,"lesion_area":1,"total_area":100,"contour":[[0,0],[10,0],[20,0],[30,0],[40,0],[50,0],[50,10],[40,10],[30,10],[20,10],[10,10],[0,10]]}')
G=$(echo "$GRADE" | jqv "['grade']")
echo "    grade: $G"
case "$G" in A+|A|B|BS) pass "grade in {A+,A,B,BS}" ;; *) fail "unexpected grade $G" ;; esac

# --- 7. Calorimetry ---------------------------------------------------------
echo "[7] Black's-law calorimetry (1000m)"
CAL=$(curl -sf -X POST "$API/api/v1/postharvest/calorimetry" -H 'Content-Type: application/json' \
  -d '{"elevation_m":1000,"well_temp_c":22}')
BP=$(echo "$CAL" | jqv "['boiling_point_c']")
if python3 -c "import sys;sys.exit(0 if abs(float('$BP')-96.7)<0.01 else 1)"; then pass "boiling point 96.7 at 1000m"; else fail "boiling point ($BP)"; fi

# --- 8. Archimedes ----------------------------------------------------------
echo "[8] Archimedes grading"
ARCH=$(curl -sf -X POST "$API/api/v1/postharvest/archimedes" -H 'Content-Type: application/json' \
  -d '{"sink_count":16,"neutral_count":3,"float_count":1}')
assert_eq "$(echo "$ARCH" | jqv "['physics_grade']")" "A+" "archimedes grade"

# --- 9. CRDT merge ----------------------------------------------------------
echo "[9] CRDT field-level merge"
MERGE=$(curl -sf -X POST "$API/api/v1/sync/merge" -H 'Content-Type: application/json' \
  -d '{
    "local":{"id":"L1","vv":{"A":2,"B":0},"fields":{"ph":{"value":6.5,"ts":{"updated_ts":1000,"rev":3,"device_id":"A"}},"n":{"value":20,"ts":{"updated_ts":1000,"rev":3,"device_id":"A"}}}},
    "remote":{"id":"L1","vv":{"A":0,"B":2},"fields":{"ph":{"value":5.8,"ts":{"updated_ts":1100,"rev":1,"device_id":"B"}},"k":{"value":30,"ts":{"updated_ts":1050,"rev":1,"device_id":"B"}}}}
  }')
PH=$(echo "$MERGE" | jqv "['merged']['fields']['ph']['value']")
if python3 -c "import sys;sys.exit(0 if abs(float('$PH')-5.8)<1e-9 else 1)"; then pass "newer ph wins (5.8)"; else fail "CRDT ph ($PH)"; fi
assert_eq "$(echo "$MERGE" | jqv "['concurrent']")" "True" "concurrency detected"

# --- 10. Idempotent delta sync ---------------------------------------------
echo "[10] Idempotent delta sync"
OP='{"idempotency_key":"e2e-'$(date +%s%N)'","entity":"soil_assessment","op":"upsert","updated_ts":1792090000,"data":{"land_id":"'"$LAND_ID"'","sand_pct":40,"silt_pct":38,"clay_pct":22,"soil_class":"loam","cec_meq":14.5,"ph_deterministic":6.5}}'
R1=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' -d "{\"device_id\":\"e2e\",\"operations\":[$OP]}")
R2=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' -d "{\"device_id\":\"e2e\",\"operations\":[$OP]}")
assert_eq "$(echo "$R1" | jqv "['results'][0]['status']")" "applied" "first sync applied"
assert_eq "$(echo "$R2" | jqv "['results'][0]['status']")" "duplicate" "second sync duplicate"

# --- 11. Ed25519 certificate verification ----------------------------------
echo "[11] Ed25519 certificate (fixed test vector)"
CERT=$(curl -sf -X POST "$API/api/v1/certificates/verify" -H 'Content-Type: application/json' \
  -d '{"payload":{"land_id":"e2e","grade_result":"A"},"signature":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}')
# A zero signature must NOT verify (it is not a valid signature for the payload).
assert_eq "$(echo "$CERT" | jqv "['valid']")" "False" "invalid signature rejected"

# --- 12. Yield projection ---------------------------------------------------
echo "[12] Yield projection"
YIELD=$(curl -sf -X POST "$API/api/v1/yield/projection" -H 'Content-Type: application/json' \
  -d '{"green_dark":30,"red_blush":15,"red_full":10,"trees_per_bed":500,"sampled_trees":50,"gdd_per_day":14}')
assert_gt0 "$(echo "$YIELD" | jqv "['projected_14d_kg']")" "14d tonnage projected"
SF=$(echo "$YIELD" | jqv "['scale_factor']")
python3 -c "import sys;sys.exit(0 if abs(float('$SF')-10.0)<1e-9 else 1)" && \
  pass "sampling scale factor ($SF)" || fail "scale factor ($SF)"

# --- 13. Protocol prescription ----------------------------------------------
echo "[13] Prescriptive protocol"
PROTO=$(curl -sf -X POST "$API/api/v1/protocols/prescribe" -H 'Content-Type: application/json' \
  -d '{"verdict_key":"FUNG_MANKOZEB_AZOXY","severity_pct":14}')
assert_gt0 "$(echo "$PROTO" | jqv "['dose_g_per_l']")" "dose prescribed"
CAT=$(curl -sf "$API/api/v1/protocols" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['protocols']))")
assert_gt0 "$CAT" "protocol catalogue size"

# --- 14. Trace validation (anti-cheat) --------------------------------------
echo "[14] Immersion trace validation"
python3 -c "
import json,math
samples=[{'t':180*i/59,'gx':0.05*math.sin(i),'gy':0.05*math.cos(i*1.3),'gz':0.02*math.sin(i*0.7)} for i in range(60)]
json.dump({'land_id':'x','samples':samples}, open('/tmp/good_trace.json','w'))
samples2=[{'t':180*i/59,'gx':0,'gy':0,'gz':0} for i in range(60)]
json.dump({'land_id':'x','samples':samples2}, open('/tmp/const_trace.json','w'))
"
GOOD=$(curl -sf -X POST "$API/api/v1/certificates/validate-trace" -H 'Content-Type: application/json' -d @/tmp/good_trace.json | jqv "['valid']")
BAD=$(curl -sf -X POST "$API/api/v1/certificates/validate-trace" -H 'Content-Type: application/json' -d @/tmp/const_trace.json | jqv "['valid']")
assert_eq "$GOOD" "True" "real immersion trace accepted"
assert_eq "$BAD" "False" "constant trace (cheat) rejected"

# --- 15. Media upload -------------------------------------------------------
echo "[15] Media upload to object storage"
B64=$(printf 'DAPE-e2e-media' | base64)
UP=$(curl -sf -X POST "$API/api/v1/media/upload" -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$LAND_ID\",\"kind\":\"trans_illum\",\"filename\":\"leaf.jpg\",\"content_type\":\"image/jpeg\",\"content_base64\":\"$B64\"}")
UKEY=$(echo "$UP" | jqv "['key']")
assert_gt0 "$(echo "$UP" | jqv "['bytes']")" "media stored"
case "$UKEY" in lands/*) pass "storage key namespaced ($UKEY)" ;; *) fail "key ($UKEY)" ;; esac

# --- 16. JWT auth -----------------------------------------------------------
echo "[16] JWT auth register + login"
PHONE="628$(date +%s%N | tail -c 12)"
REG=$(curl -sf -X POST "$API/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"name\":\"E2E Tester\",\"phone\":\"$PHONE\",\"role\":\"farmer\",\"password\":\"secret123\"}")
TOKEN=$(echo "$REG" | jqv "['token']")
assert_gt0 "${#TOKEN}" "register returns token"
ME=$(curl -sf "$API/api/v1/auth/me" -H "Authorization: Bearer $TOKEN" | jqv "['role']")
assert_eq "$ME" "farmer" "authenticated /auth/me"
# wrong password must fail (expect non-zero exit / 401)
if curl -sf -o /dev/null -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
   -d "{\"phone\":\"$PHONE\",\"password\":\"wrong\"}"; then
  fail "wrong password accepted"
else
  pass "wrong password rejected"
fi

echo
echo "═══ Results: $PASS passed, $FAIL failed ═══"
[ "$FAIL" -eq 0 ] || exit 1
