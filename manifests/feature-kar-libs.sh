#!/usr/bin/env bash
# Resolve the bundle closure for a Rahla project's features.xml into a local folder,
# so you can scan it with `trivy rootfs` WITHOUT building or extracting the Docker image.
# (Use `rootfs`, not `fs`: `fs` only reads dependency files like pom.xml/lockfiles, while
#  `rootfs` walks the tree and detects the loose .jar bundles — which is what we want.)
#
# It uses the same shared build pom as the KAR (manifests/feature-kar.xml, fetched from codeberg),
# so the jars it writes are exactly the ones the offline KAR would add to Karaf's system/ repo
# (rahla-provided features like fradi are stubbed out, just like in the image).
#
# Usage:
#   feature-kar-libs.sh [features.xml] [output-dir]
#   defaults: ./deploy/features.xml  ->  /tmp/feature-kar-libs
#
# Notes:
#   - needs maven + a JDK (the karaf tooling wants Java >= 22; recent maven enforces it).
#   - if `trivy` is on PATH it scans right away, otherwise it prints the command.
set -euo pipefail

FEATURES="${1:-deploy/features.xml}"
OUT="${2:-/tmp/feature-kar-libs}"
POM_URL="https://codeberg.org/datatactics/rahla/raw/branch/main/manifests/feature-kar.xml"

command -v mvn  >/dev/null 2>&1 || { echo "ERROR: maven not found"; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl not found"; exit 1; }
[ -f "$FEATURES" ] || { echo "ERROR: features.xml not found: $FEATURES"; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/deploy"
cp "$FEATURES" "$WORK/deploy/features.xml"
curl -fsSL "$POM_URL" -o "$WORK/pom.xml"

echo "→ resolving closure for $FEATURES ..."
( cd "$WORK" && mvn -B -q -f pom.xml prepare-package )

rm -rf "$OUT"; mkdir -p "$OUT"
cp -r "$WORK/target/closure/repository/." "$OUT/"

echo "→ $(find "$OUT" -name '*.jar' | wc -l) jars written to $OUT"
if command -v trivy >/dev/null 2>&1; then
    echo "→ trivy rootfs --scanners vuln $OUT"
    trivy rootfs --scanners vuln "$OUT"
else
    echo "→ trivy not on PATH. Scan with:  trivy rootfs --scanners vuln $OUT"
fi
