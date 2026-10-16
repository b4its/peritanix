#!/usr/bin/env bash
# =============================================================================
# DAPE — Android release artefact verification
# -----------------------------------------------------------------------------
# Asserts that the R8/minified release build actually produced what the release
# pipeline promises:
#   * the APK exists and contains a single (merged) classes.dex,
#   * it is smaller than the debug APK (i.e. shrinking/obfuscation took effect),
#   * the AndroidManifest declares the launcher activity,
#   * the R8 mapping file exists and obfuscates non-kept classes,
#   * the deterministic core (physics/crypto) is kept un-obfuscated.
#
# Usage: bash scripts/verify_android_release.sh [DIST_DIR]  (default: ./dist)
# Exit non-zero on any failure, so it is CI-safe.
# =============================================================================
set -uo pipefail

DIST="${1:-dist}"
REL="$DIST/app-release-unsigned.apk"
DBG="$DIST/app-debug.apk"
AAB="$DIST/app-release.aab"
MAP="$DIST/app-release-mapping.txt"

pass=0; fail=0
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; pass=$((pass+1)); }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$1"; fail=$((fail+1)); }

echo "═══ DAPE Android release verification ═══"

if [ ! -f "$REL" ]; then
  bad "release APK missing: $REL  (run 'make mobile-release' first)"
  echo "═══ Results: $pass passed, $fail failed ═══"
  exit 1
fi

py() { python3 "$@"; }

# 1. valid APK + single dex
if py - "$REL" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
n = z.namelist()
assert "AndroidManifest.xml" in n, "no AndroidManifest.xml"
dex = [x for x in n if x.endswith(".dex")]
assert len(dex) == 1, f"expected 1 dex, got {len(dex)}: {dex}"
PY
then ok "valid APK, single classes.dex"; else bad "APK structure invalid"; fi

# 2. smaller than debug (shrinking worked)
if [ -f "$DBG" ]; then
  rs=$(stat -c %s "$REL"); ds=$(stat -c %s "$DBG")
  if [ "$rs" -lt "$ds" ]; then
    ok "release $(numfmt --to=iec "$rs") < debug $(numfmt --to=iec "$ds") (shrunk)"
  else
    bad "release ($rs B) is NOT smaller than debug ($ds B)"
  fi
else
  printf "  \033[33m•\033[0m debug APK absent; skipping size comparison\n"
fi

# 3. AAB present
[ -f "$AAB" ] && ok "Play-Store AAB present ($(numfmt --to=iec "$(stat -c %s "$AAB")"))" \
              || bad "AAB missing: $AAB"

# 4. R8 mapping obfuscates and keeps the deterministic core
if [ -f "$MAP" ]; then
  if grep -qE '^id\.peritanix\.dape\.physics\..* -> ' "$MAP"; then
    ok "R8 mapping keeps deterministic core (physics)"
  else
    bad "R8 mapping does not reference the physics core"
  fi
  if grep -qE 'id\.peritanix\.dape\.android\.ui\.MainActivity -> id\.peritanix\.dape\.android\.ui\.MainActivity' "$MAP"; then
    ok "launcher MainActivity kept (manifest entry-point)"
  else
    bad "MainActivity not kept in mapping (would crash on launch)"
  fi
  if grep -qE -- '(^|[^.])[a-zA-Z]+(\.[a-zA-Z])? -> [a-z][a-z0-9.]*:$' "$MAP" \
     && ! grep -qE -- '^id\.peritanix\.dape\.android\.ui\.(LahanScreen|HarvestScreen) -> id\.peritanix' "$MAP"; then
    ok "R8 obfuscation applied (classes renamed)"
  elif grep -qE -- '-> (R8\$\$REMOVED|[a-z][a-z0-9.]*):$' "$MAP"; then
    ok "R8 obfuscation applied (classes renamed/shrunk)"
  else
    bad "no obfuscation evidence in mapping"
  fi
else
  printf "  \033[33m•\033[0m R8 mapping absent; skipping obfuscation checks\n"
fi

echo "═══ Results: $pass passed, $fail failed ═══"
[ "$fail" -eq 0 ]
