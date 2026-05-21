#!/usr/bin/env bash
# bundle-backend.sh — Assemble the Spring Boot sidecar into the Tauri resources dir.
#
# Produces:
#   client/src-tauri/backend/app.jar    (Spring Boot executable fat jar)
#   client/src-tauri/backend/runtime/   (full-module JRE via jlink ALL-MODULE-PATH)
#
# These are git-ignored build artifacts consumed by `tauri build` (bundle.resources).
# Used by both local release verification and CI (.github/workflows/release.yml).
#
# Requires: JAVA_HOME pointing at a JDK 21 (provides mvn-built jar + jlink).
# Usage: ./scripts/bundle-backend.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_DIR="$ROOT_DIR/server"
BACKEND_DIR="$ROOT_DIR/client/src-tauri/backend"

if [ -z "${JAVA_HOME:-}" ]; then
  echo "ERROR: JAVA_HOME is not set (need a JDK 21 with jlink)." >&2
  exit 1
fi

JLINK="$JAVA_HOME/bin/jlink"
if [ ! -x "$JLINK" ] && [ ! -x "$JLINK.exe" ]; then
  echo "ERROR: jlink not found at $JLINK — is JAVA_HOME a full JDK?" >&2
  exit 1
fi

echo "==> Building backend fat jar"
( cd "$SERVER_DIR" && mvn -q -pl data-talk-adapter -am package -DskipTests )

JAR_SRC="$(find "$SERVER_DIR/data-talk-adapter/target" -maxdepth 1 -name 'data-talk-adapter-*.jar' ! -name '*.original' | head -n1)"
if [ -z "$JAR_SRC" ]; then
  echo "ERROR: built fat jar not found under data-talk-adapter/target" >&2
  exit 1
fi
echo "    jar: $JAR_SRC"

echo "==> Resetting $BACKEND_DIR"
rm -rf "$BACKEND_DIR/runtime" "$BACKEND_DIR/app.jar"
mkdir -p "$BACKEND_DIR"
cp "$JAR_SRC" "$BACKEND_DIR/app.jar"

echo "==> Building full-module runtime with jlink"
"$JLINK" \
  --add-modules ALL-MODULE-PATH \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --output "$BACKEND_DIR/runtime"

echo "==> Done"
echo "    app.jar  : $BACKEND_DIR/app.jar"
echo "    runtime  : $BACKEND_DIR/runtime"
du -sh "$BACKEND_DIR/runtime" "$BACKEND_DIR/app.jar" 2>/dev/null || true
