#!/usr/bin/env bash
# measure-startup.sh — Cold-start wallclock benchmark for the Spring Boot backend.
#
# Launches the packaged jar N times, parses Spring's own "Started ... in X.XXX
# seconds" line from each run's stdout, then reports mean / p50 / p95 over the
# samples.
#
# Usage:
#   ./scripts/measure-startup.sh [N] [jar-path] [extra-java-args...]
#
# Defaults:
#   N         = 3
#   jar-path  = client/src-tauri/backend/app.jar (falls back to
#               server/data-talk-adapter/target/data-talk-adapter-*.jar)
#
# Examples:
#   # baseline — current jar, no AOT, no CDS:
#   ./scripts/measure-startup.sh 3 server/data-talk-adapter/target/data-talk-adapter-0.0.1-SNAPSHOT.jar
#
#   # AOT only:
#   ./scripts/measure-startup.sh 3 client/src-tauri/backend/app.jar -Dspring.aot.enabled=true
#
#   # AOT + CDS:
#   ./scripts/measure-startup.sh 3 client/src-tauri/backend/app.jar \
#     -Dspring.aot.enabled=true \
#     -XX:+AutoCreateSharedArchive \
#     -XX:SharedArchiveFile=client/src-tauri/backend/app.jsa
#
# The script writes each run's full stdout to tmp/measure-startup-run-<i>.log
# for diagnostics, and a one-line summary to stdout suitable for piping into
# tmp/startup-baseline-*.txt.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_DIR="$ROOT_DIR/tmp"
mkdir -p "$TMP_DIR"

N="${1:-3}"
JAR="${2:-}"
shift || true
shift || true
EXTRA_ARGS=("$@")

# Resolve jar path
if [ -z "$JAR" ]; then
  JAR="$ROOT_DIR/client/src-tauri/backend/app.jar"
  if [ ! -f "$JAR" ]; then
    # Fallback to target dir build artifact
    JAR="$(find "$ROOT_DIR/server/data-talk-adapter/target" -maxdepth 1 -name 'data-talk-adapter-*.jar' ! -name '*.original' 2>/dev/null | head -n1)"
  fi
fi

if [ ! -f "$JAR" ]; then
  echo "ERROR: jar not found. Tried:" >&2
  echo "  client/src-tauri/backend/app.jar" >&2
  echo "  server/data-talk-adapter/target/data-talk-adapter-*.jar" >&2
  echo "Pass jar path as 2nd arg." >&2
  exit 1
fi

# Resolve to absolute path — the runner cd's into a temp work dir, so any
# relative path passed in would otherwise become invalid mid-run.
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

# Resolve java: prefer bundled runtime alongside the jar if it exists, else PATH
JAVA_BIN=""
JAR_DIR="$(dirname "$JAR")"
if [ -x "$JAR_DIR/runtime/bin/java" ]; then
  JAVA_BIN="$JAR_DIR/runtime/bin/java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="$(command -v java)"
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "ERROR: java not found. Set JAVA_HOME or bundle runtime alongside the jar." >&2
  exit 1
fi

echo "==> measure-startup.sh"
echo "    java       : $JAVA_BIN"
echo "    jar        : $JAR"
echo "    runs       : $N"
echo "    extra args : ${EXTRA_ARGS[*]:-(none)}"
echo

# Use a temp working dir to avoid contaminating the repo (./data/*.db etc.)
WORK_DIR="$(mktemp -d -p "$TMP_DIR" startup-work.XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

samples=()
for i in $(seq 1 "$N"); do
  LOG="$TMP_DIR/measure-startup-run-$i.log"
  echo "--- run $i/$N ---"
  # --server.port=0 → ephemeral port, no collision risk between runs
  ( cd "$WORK_DIR" && "$JAVA_BIN" "${EXTRA_ARGS[@]}" -jar "$JAR" --server.port=0 ) >"$LOG" 2>&1 &
  PID=$!

  # Wait for the "Started ... in X.XXX seconds" line or process exit, max 90s
  DEADLINE=$((SECONDS + 90))
  SECS=""
  while [ "$SECONDS" -lt "$DEADLINE" ]; do
    if ! kill -0 "$PID" 2>/dev/null; then
      break
    fi
    # Spring's startup-complete line — match either form Boot emits
    SECS="$(grep -oE 'Started [A-Za-z0-9_]+ in [0-9]+\.[0-9]+ seconds' "$LOG" 2>/dev/null | head -n1 | grep -oE '[0-9]+\.[0-9]+' | head -n1 || true)"
    if [ -n "$SECS" ]; then
      break
    fi
    sleep 0.2
  done

  # Graceful shutdown
  if kill -0 "$PID" 2>/dev/null; then
    kill -TERM "$PID" 2>/dev/null || true
    # Give Spring's shutdown hook a brief moment, then SIGKILL
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$PID" 2>/dev/null || break
      sleep 0.5
    done
    kill -KILL "$PID" 2>/dev/null || true
  fi
  wait "$PID" 2>/dev/null || true

  if [ -z "$SECS" ]; then
    echo "    FAILED: no 'Started ... in X.XXX seconds' line in $LOG" >&2
    continue
  fi
  echo "    Started in ${SECS}s  (log: $LOG)"
  samples+=("$SECS")
done

echo
if [ "${#samples[@]}" -eq 0 ]; then
  echo "ERROR: zero successful runs — see logs under $TMP_DIR/measure-startup-run-*.log" >&2
  exit 1
fi

# Sort numerically for percentile math
sorted=($(printf '%s\n' "${samples[@]}" | sort -g))

# Mean
sum=0
for s in "${sorted[@]}"; do
  sum="$(awk -v a="$sum" -v b="$s" 'BEGIN{printf "%.3f", a + b}')"
done
mean="$(awk -v s="$sum" -v n="${#sorted[@]}" 'BEGIN{printf "%.3f", s / n}')"

# p50 (median): for even N take the lower-mid; we keep it simple — index = floor((N-1)/2)
p50_idx=$(( (${#sorted[@]} - 1) / 2 ))
p50="${sorted[$p50_idx]}"

# p95: index = ceil(0.95 * N) - 1, but for small N (<20) p95 is dominated by the max
p95_idx=$(awk -v n="${#sorted[@]}" 'BEGIN{i=int(0.95*n + 0.999999) - 1; if (i < 0) i = 0; print i}')
p95="${sorted[$p95_idx]}"

echo "==> Summary (seconds)"
echo "    samples : ${sorted[*]}"
echo "    mean    : $mean"
echo "    p50     : $p50"
echo "    p95     : $p95"
echo "    sla 10s : $(awk -v p="$p95" 'BEGIN{print (p <= 10.0) ? "PASS" : "FAIL"}')"
