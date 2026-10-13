#!/usr/bin/env bash
# Harness only: builds scripts/server-module-check into <out>/server-module-check.jar, against the contract jar.
#   scripts/server-module-check/build.sh <contract.jar> <out-dir>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CONTRACT="$1"; OUT="$2"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac --release 21 -nowarn -d "$TMP" -cp "$CONTRACT" "$HERE"/src/conductor/servercheck/*.java
cp -r "$HERE/META-INF" "$TMP/"
mkdir -p "$OUT"
(cd "$TMP" && jar cf "$OUT/server-module-check.jar" .)
