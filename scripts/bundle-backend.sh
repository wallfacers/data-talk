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

# The fat jar is platform-independent. CI builds it once on Linux and passes it
# via DATATALK_PREBUILT_JAR so Windows/macOS runners skip the Unix-only
# opencode-deps maven step and only build their platform JRE below.
if [ -n "${DATATALK_PREBUILT_JAR:-}" ]; then
  echo "==> Using prebuilt backend jar: $DATATALK_PREBUILT_JAR"
  if [ ! -f "$DATATALK_PREBUILT_JAR" ]; then
    echo "ERROR: DATATALK_PREBUILT_JAR does not exist: $DATATALK_PREBUILT_JAR" >&2
    exit 1
  fi
  JAR_SRC="$DATATALK_PREBUILT_JAR"
else
  echo "==> Building backend fat jar"
  ( cd "$SERVER_DIR" && mvn -q -pl data-talk-adapter -am package -DskipTests )
  JAR_SRC="$(find "$SERVER_DIR/data-talk-adapter/target" -maxdepth 1 -name 'data-talk-adapter-*.jar' ! -name '*.original' | head -n1)"
  if [ -z "$JAR_SRC" ]; then
    echo "ERROR: built fat jar not found under data-talk-adapter/target" >&2
    exit 1
  fi
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

# ── Bundle the platform OpenCode binary ──────────────────────────────────────
# Embeds opencode into backend/opencode/ so the packaged app never downloads it on
# first launch (backend reads DATATALK_OPENCODE_SERVE_BINARY_PATH set by backend.rs).
# Version is the single source of truth in application.yml; override via OPENCODE_VERSION.
APP_YML="$SERVER_DIR/data-talk-adapter/src/main/resources/application.yml"
OPENCODE_VERSION="${OPENCODE_VERSION:-$(grep -E '^\s+version:\s*[0-9]' "$APP_YML" | head -n1 | sed -E 's/.*version:[[:space:]]*//' | tr -d '[:space:]')}"
if [ -z "$OPENCODE_VERSION" ]; then
  echo "ERROR: could not determine OpenCode version (set OPENCODE_VERSION or check $APP_YML)" >&2
  exit 1
fi

case "$(uname -s)" in
  Linux*)               OC_OS=linux;   OC_EXT=tar.gz; OC_BIN=opencode ;;
  Darwin*)              OC_OS=darwin;  OC_EXT=zip;    OC_BIN=opencode ;;
  MINGW*|MSYS*|CYGWIN*) OC_OS=windows; OC_EXT=zip;    OC_BIN=opencode.exe ;;
  *) echo "ERROR: unsupported OS for OpenCode bundling: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64)  OC_ARCH=x64 ;;
  arm64|aarch64) OC_ARCH=arm64 ;;
  *) echo "ERROR: unsupported arch for OpenCode bundling: $(uname -m)" >&2; exit 1 ;;
esac

OC_PLATFORM="$OC_OS-$OC_ARCH"
OC_ASSET="opencode-$OC_PLATFORM.$OC_EXT"
OC_URL="https://github.com/anomalyco/opencode/releases/download/v$OPENCODE_VERSION/$OC_ASSET"
OC_DIR="$BACKEND_DIR/opencode"

echo "==> Bundling OpenCode v$OPENCODE_VERSION ($OC_PLATFORM)"
echo "    url: $OC_URL"
rm -rf "$OC_DIR"
mkdir -p "$OC_DIR"

OC_TMP="$(mktemp -d)"
trap 'rm -rf "$OC_TMP"' EXIT
curl -fsSL "$OC_URL" -o "$OC_TMP/$OC_ASSET"

if [ "$OC_EXT" = "tar.gz" ]; then
  tar -xzf "$OC_TMP/$OC_ASSET" -C "$OC_TMP"
else
  ( cd "$OC_TMP" && { unzip -oq "$OC_ASSET" || tar -xf "$OC_ASSET"; } )
fi

EXTRACTED="$(find "$OC_TMP" -type f -name "$OC_BIN" | head -n1)"
if [ -z "$EXTRACTED" ]; then
  echo "ERROR: '$OC_BIN' not found in extracted archive $OC_ASSET" >&2
  exit 1
fi
cp "$EXTRACTED" "$OC_DIR/$OC_BIN"
chmod +x "$OC_DIR/$OC_BIN" 2>/dev/null || true

echo "==> Done"
echo "    app.jar  : $BACKEND_DIR/app.jar"
echo "    runtime  : $BACKEND_DIR/runtime"
echo "    opencode : $OC_DIR/$OC_BIN"
du -sh "$BACKEND_DIR/runtime" "$BACKEND_DIR/app.jar" "$OC_DIR/$OC_BIN" 2>/dev/null || true
