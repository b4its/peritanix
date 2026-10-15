#!/usr/bin/env bash
# =============================================================================
# DAPE — PostgreSQL restore from a custom-format dump.
#
# Usage: bash scripts/db_restore.sh backups/dape-<ts>.dump [--clean]
#
# Verifies the checksum (if a .sha256 sidecar exists) before restoring.
# --clean drops existing objects first (destructive) — use for a full reset.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -f .env ]; then
  set -a; . ./.env; set +a
fi
PGUSER="${POSTGRES_USER:-dape}"
PGDB="${POSTGRES_DB:-dape}"
DC="docker compose --env-file .env -f docker-compose.yml"

DUMP="${1:-}"
CLEAN="${2:-}"
if [ -z "$DUMP" ] || [ ! -f "$DUMP" ]; then
  echo "usage: bash scripts/db_restore.sh <dump-file> [--clean]" >&2
  exit 1
fi

# Integrity check.
if [ -f "$DUMP.sha256" ]; then
  echo "[restore] verifying checksum..."
  sha256sum -c "$DUMP.sha256"
fi

EXTRA=""
if [ "$CLEAN" = "--clean" ]; then
  EXTRA="--clean --if-exists"
  echo "[restore] WARNING: --clean will drop existing objects in $PGDB"
fi

echo "[restore] restoring $DUMP -> $PGDB"
$DC exec -T postgres pg_restore -U "$PGUSER" -d "$PGDB" $EXTRA --no-owner --no-privileges < "$DUMP"
echo "[restore] done."
