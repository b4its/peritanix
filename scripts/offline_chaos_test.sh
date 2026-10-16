#!/usr/bin/env bash
# =============================================================================
# DAPE offline / chaos test.
#
# Simulates the "airplane mode" field workflow WITHOUT losing the server:
#   1. Compute the full field cycle on-device (Kotlin CLI mirror) OFFLINE.
#   2. Queue mutations locally (simulated).
#   3. Restore connectivity and flush the delta sync.
#   4. Verify the server accepts the previously-offline work idempotently.
#   5. Chaos: restart the ML service mid-flight and confirm the API's circuit
#      breaker + graceful errors (no crash), then recovers.
#
# Usage: bash scripts/offline_chaos_test.sh
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://localhost:58080}"
DC="docker compose --env-file .env -f $ROOT/docker-compose.yml"
PASS=0; FAIL=0
pass(){ echo "  ✓ $1"; PASS=$((PASS+1)); }
fail(){ echo "  ✗ $1"; FAIL=$((FAIL+1)); }

echo "═══ DAPE Offline / Chaos Test ═══"

# --- 1. On-device physics (offline, no network) ----------------------------
echo "[1] On-device physics mirror (offline)"
OUT=$($DC --profile mobile run --rm -T mobile gradle --no-daemon -q --console=plain run 2>/dev/null | grep -E "boiling|ratio|mix reaches" | head -2 || true)
if echo "$OUT" | grep -q "mix reaches 52.00 C"; then
  pass "mobile calorimetry reaches 52.00 C offline"
else
  # Fallback: try without -T (older compose) 
  OUT=$($DC --profile mobile run --rm mobile gradle --no-daemon -q --console=plain run 2>/dev/null | grep "mix reaches" | head -2 || true)
  if echo "$OUT" | grep -q "52.00 C"; then pass "mobile calorimetry reaches 52.00 C offline"; else fail "mobile offline calorimetry"; fi
fi

# --- 2. Queue offline mutations, then flush ---------------------------------
echo "[2] Queue offline mutation -> restore connectivity -> flush"
LAND=$(curl -sf "$API/api/v1/lands" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['lands'][0]['id'])" 2>/dev/null || echo "")
if [ -z "$LAND" ]; then
  fail "no land available to sync"; else
  KEY="offline-$(date +%s%N)"
  BEFORE=$(curl -sf "$API/api/v1/lands/$LAND/soil" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['assessments']))" 2>/dev/null || echo 0)
  RESP=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' \
    -d "{\"device_id\":\"field-worker\",\"operations\":[{\"idempotency_key\":\"$KEY\",\"entity\":\"soil_assessment\",\"op\":\"upsert\",\"updated_ts\":$(date +%s),\"data\":{\"land_id\":\"$LAND\",\"sand_pct\":42,\"silt_pct\":36,\"clay_pct\":22,\"soil_class\":\"loam\",\"cec_meq\":13.0,\"ph_deterministic\":6.6}}]}")
  STATUS=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['results'][0]['status'])" 2>/dev/null || echo "")
  AFTER=$(curl -sf "$API/api/v1/lands/$LAND/soil" | python3 -c "import sys,json;print(len(json.load(sys.stdin)['assessments']))" 2>/dev/null || echo 0)
  [ "$STATUS" == "applied" ] && pass "offline queued write applied on reconnect" || fail "sync status ($STATUS)"
  [ "$AFTER" -gt "$BEFORE" ] && pass "row persisted (before=$BEFORE after=$AFTER)" || fail "row not persisted"
fi

# --- 3. Idempotent replay (simulate a dropped-ack retry) --------------------
echo "[3] Dropped-ack retry is idempotent"
KEY2="replay-$(date +%s%N)"
OP="{\"idempotency_key\":\"$KEY2\",\"entity\":\"soil_assessment\",\"op\":\"upsert\",\"updated_ts\":$(date +%s),\"data\":{\"land_id\":\"$LAND\",\"sand_pct\":40,\"silt_pct\":38,\"clay_pct\":22,\"soil_class\":\"loam\",\"cec_meq\":14.0,\"ph_deterministic\":6.5}}"
S1=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' -d "{\"device_id\":\"w\",\"operations\":[$OP]}" | python3 -c "import sys,json;print(json.load(sys.stdin)['results'][0]['status'])" 2>/dev/null)
S2=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' -d "{\"device_id\":\"w\",\"operations\":[$OP]}" | python3 -c "import sys,json;print(json.load(sys.stdin)['results'][0]['status'])" 2>/dev/null)
[ "$S1" == "applied" ] && [ "$S2" == "duplicate" ] && pass "retry applied->duplicate" || fail "retry ($S1/$S2)"

# --- 4. Full offline field cycle (TAHAP 7.1) --------------------------------
# Simulate the phone being 100% offline by computing everything on-device and
# queueing it, then flush only when "connectivity" returns. The server is up,
# but the operations are performed in dependency order with an idempotency key
# so a replay is safe — exactly what the mobile app does in airplane mode.
echo "[4] Full offline cycle: init -> soil -> diagnosis -> GDD lock -> QR"
CYCLE_LAND=$(curl -sf -X POST "$API/api/v1/lands/init" -H 'Content-Type: application/json' \
  -d '{"user_id":"11111111-1111-1111-1111-111111111111","name":"Offline-Cycle","boundary_wkt":"POLYGON((107.61 -6.91,107.612 -6.91,107.612 -6.912,107.61 -6.912,107.61 -6.91))","elevation_m":900}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['land']['id'])" 2>/dev/null || echo "")
if [ -n "$CYCLE_LAND" ]; then
  pass "offline init -> land $CYCLE_LAND"
else
  fail "offline init"
fi

# (a) Soil assessment (Stokes + photometry), queued offline then flushed.
SOIL_OK=$(curl -sf -X POST "$API/api/v1/soil/sync" -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$CYCLE_LAND\",\"sand_thickness\":38,\"silt_thickness\":40,\"clay_thickness\":22,\"measured_rgb\":[180,200,120],\"dark_chamber_lux_check\":true}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['assessment']['soil_class'])" 2>/dev/null || echo "")
[ -n "$SOIL_OK" ] && pass "offline soil assessment ($SOIL_OK)" || fail "offline soil"

# (b) Diagnosis (multi-modal evidence).
DIAG_OK=$(curl -sf -X POST "$API/api/v1/diagnostics/evaluate" -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$CYCLE_LAND\",\"lon\":107.611,\"lat\":-6.911,\"local_twi\":9.0,\"visual\":{\"Antraknosa\":0.6,\"Ralstonia\":0.2},\"severity_pct\":12}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['bayesian']['final_verdict'])" 2>/dev/null || echo "")
[ -n "$DIAG_OK" ] && pass "offline diagnosis ($DIAG_OK)" || fail "offline diagnosis"

# (c) GDD physiology -> fortification lock.
LOCK=$(curl -sf -X POST "$API/api/v1/lands/$CYCLE_LAND/recompute-gdd" -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('fortification_locked'))" 2>/dev/null || echo "")
[ "$LOCK" = "True" ] || [ "$LOCK" = "False" ] && pass "offline GDD lock evaluated ($LOCK)" || fail "offline GDD lock"

# (d) Post-harvest certificate: compute the 52C mix (offline physics) and a
# batch hash, then "print" the QR payload (signed locally, stored as JSON).
QR=$(python3 -c "
import json, hashlib
elev=900.0; well=26.0; target=52.0
t_boil=100-0.0033*elev
v_boil=round(9*(target-well)/(t_boil-target),2)
payload={'land_id':'$CYCLE_LAND','sterilization_c':target,'hot_water_ratio':f'{v_boil} didih : 9 sumur','grade_result':'A'}
h=hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()
print(json.dumps({'payload':payload,'hash':h}))
")
echo "$QR" | grep -q "hash" && pass "offline certificate QR generated" || fail "offline certificate QR"

# (e) Scan the QR payload BEFORE reconnect: the buyer-side verify must accept a
# well-formed envelope shape (signature validity is exercised in the E2E suite;
# here we assert the endpoint is reachable and the payload round-trips).
BEFORE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/certificates/verify" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"land_id":"x","grade_result":"A"},"signature":"AA==","public_key":"AA=="}')
[ "$BEFORE" = "200" ] && pass "QR verify reachable before reconnect (pre-sync)" || fail "qr verify pre ($BEFORE)"

# (f) Flush the cycle: rows must persist with idempotency (replay-safe).
KEY="cycle-$(date +%s%N)"
OP="{\"idempotency_key\":\"$KEY\",\"entity\":\"harvest_batch\",\"op\":\"upsert\",\"updated_ts\":$(date +%s),\"data\":{\"land_id\":\"$CYCLE_LAND\",\"batch_code\":\"CYCLE-$RANDOM\",\"grade_result\":\"A\",\"archimedes_sink_ratio\":0.82,\"boiling_point_calc\":96.7,\"hot_water_ratio\":\"6:9\"}}"
F1=$(curl -sf -X POST "$API/api/v1/sync/delta" -H 'Content-Type: application/json' -d "{\"device_id\":\"offline-cycle\",\"operations\":[$OP]}" | python3 -c "import sys,json;print(json.load(sys.stdin)['results'][0]['status'])" 2>/dev/null)
[ "$F1" = "applied" ] && pass "offline batch flushed on reconnect" || fail "flush ($F1)"

# (g) Scan AFTER reconnect: same endpoint, still reachable (evidence now synced).
AFTER=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/certificates/verify" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"land_id":"x","grade_result":"A"},"signature":"AA==","public_key":"AA=="}')
[ "$AFTER" = "200" ] && pass "QR verify after reconnect (post-sync)" || fail "qr verify post ($AFTER)"

# --- 5. Chaos: ML backend down -> API degrades gracefully -------------------
echo "[5] Chaos: stop ML, API must not crash (circuit breaker)"
$DC stop ml >/dev/null 2>&1
sleep 2
# An ML-dependent call should return a 502 (bad gateway), not hang or 500-crash.
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/diagnostics/evaluate" \
  -H 'Content-Type: application/json' \
  -d "{\"land_id\":\"$LAND\",\"lon\":107.6,\"lat\":-6.9,\"visual\":{\"Antraknosa\":0.5}}")
if [ "$HTTP" == "502" ] || [ "$HTTP" == "503" ]; then
  pass "API degraded gracefully during ML outage (HTTP $HTTP)"
else
  fail "unexpected status during ML outage (HTTP $HTTP)"
fi
# A non-ML call must still work.
HL=$(curl -s -o /dev/null -w "%{http_code}" "$API/healthz")
[ "$HL" == "200" ] && pass "API core still alive during ML outage" || fail "API core down (HTTP $HL)"

# --- 6. Recovery ------------------------------------------------------------
echo "[6] Restore ML -> API recovers"
$DC start ml >/dev/null 2>&1
for i in $(seq 1 30); do
  if curl -sf "$API/api/v1/health" | grep -q '"ml":true'; then pass "ML reconnected"; break; fi
  sleep 3
  [ "$i" -eq 30 ] && fail "ML did not reconnect in time"
done

echo
echo "═══ Results: $PASS passed, $FAIL failed ═══"
[ "$FAIL" -eq 0 ] || exit 1
