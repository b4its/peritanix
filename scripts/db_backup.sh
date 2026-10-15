#!/usr/bin/env bash
# =============================================================================
# DAPE — PostgreSQL backup / restore (custom-format pg_dump, portable).
#
# Usage:
#   bash scripts/db_backup.sh              # write backups/dape-<ts>.dump
#   bash scripts/db_backup.sh out.dump     # explicit path
#   bash scripts/db_restore.sh backups/x.dump
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Load DB settings from .env.
if [ -f .env ]; then
  set -a; . ./.env; set +a
fi
PGUSER="${POSTGRES_USER:-dape}"
PGDB="${POSTGRES_DB:-dape}"
DC="docker compose --env-file .env -f docker-compose.yml"

TS="$(date +%Y%m%d-%H%M%S)"
OUT="${1:-backups/dape-${TS}.dump}"
mkdir -p "$(dirname "$OUT")"

echo "[backup] dumping $PGDB -> $OUT"
# -Fc = custom format (compressed, restorable with pg_restore).
$DC exec -T postgres pg_dump -U "$PGUSER" -d "$PGDB" -Fc > "$OUT"

SIZE="$(wc -c < "$OUT" | tr -d ' ')"
echo "[backup] done: $OUT (${SIZE} bytes)"
# Also write a checksum for integrity verification.
sha256sum "$OUT" > "$OUT.sha256"
echo "[backup] checksum: $OUT.sha256"
