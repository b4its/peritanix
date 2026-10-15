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

# --- 4. Chaos: ML backend down -> API degrades gracefully -------------------
echo "[4] Chaos: stop ML, API must not crash (circuit breaker)"
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

# --- 5. Recovery ------------------------------------------------------------
echo "[5] Restore ML -> API recovers"
$DC start ml >/dev/null 2>&1
for i in $(seq 1 30); do
  if curl -sf "$API/api/v1/health" | grep -q '"ml":true'; then pass "ML reconnected"; break; fi
  sleep 3
  [ "$i" -eq 30 ] && fail "ML did not reconnect in time"
done

echo
echo "═══ Results: $PASS passed, $FAIL failed ═══"
[ "$FAIL" -eq 0 ] || exit 1
