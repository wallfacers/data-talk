#!/usr/bin/env bash
# measure-startup-e2e.sh — End-to-end wallclock from JVM spawn to /api/health 200.
#
# Mimics what the Tauri sidecar measures: Instant::now() at ensure_started()
# entry to the moment health_ok() first returns true.  Use this to validate the
# desktop SLA (Linux p95 ≤ 10s).
#
# Usage:
#   ./scripts/measure-startup-e2e.sh [N]
#
# Reads the same jar + JVM args the Tauri sidecar uses (AOT+CDS, no JMX, no
# backgroundPreinitializer).

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_DIR="$ROOT_DIR/tmp"
BACKEND_DIR="$ROOT_DIR/client/src-tauri/backend"
JAVA="$BACKEND_DIR/runtime/bin/java"
JAR="$BACKEND_DIR/app.jar"
JSA="$BACKEND_DIR/app.jsa"

N="${1:-3}"
mkdir -p "$TMP_DIR"

if [ ! -x "$JAVA" ] || [ ! -f "$JAR" ]; then
  echo "ERROR: bundled backend not found. Run ./scripts/bundle-backend.sh first." >&2
  exit 1
fi

samples=()
for i in $(seq 1 "$N"); do
  WORK_DIR="$(mktemp -d -p "$TMP_DIR" e2e-work.XXXXXX)"
  mkdir -p "$WORK_DIR/data"
  # Pick a random port
  PORT=$(python3 -c "import socket; s=socket.socket(); s.bind(('127.0.0.1', 0)); print(s.getsockname()[1]); s.close()")
  LOG="$TMP_DIR/measure-startup-e2e-run-$i.log"

  T0=$(date +%s.%N)
  ( cd "$WORK_DIR" && "$JAVA" \
    -XX:+AutoCreateSharedArchive \
    -XX:SharedArchiveFile="$JSA" \
    -Dspring.aot.enabled=true \
    -Dspring.jmx.enabled=false \
    -Dspring.backgroundpreinitializer.ignore=true \
    -jar "$JAR" \
    --server.port="$PORT" ) > "$LOG" 2>&1 &
  PID=$!

  # Poll /api/health every 200ms for up to 60s
  DEADLINE=$(awk -v t="$T0" 'BEGIN{print t + 60}')
  HEALTH_AT=""
  while :; do
    NOW=$(date +%s.%N)
    if awk -v n="$NOW" -v d="$DEADLINE" 'BEGIN{exit !(n > d)}'; then
      break
    fi
    if curl -sS -m 2 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/health" 2>/dev/null | grep -q '^200$'; then
      HEALTH_AT=$(date +%s.%N)
      break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      break
    fi
    sleep 0.2
  done

  kill -TERM "$PID" 2>/dev/null || true
  for _ in 1 2 3 4 5; do kill -0 "$PID" 2>/dev/null || break; sleep 0.5; done
  kill -KILL "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true

  if [ -z "$HEALTH_AT" ]; then
    echo "run $i: FAILED — /api/health did not become 200 within 60s (log: $LOG)"
    rm -rf "$WORK_DIR"
    continue
  fi
  DELTA=$(awk -v a="$HEALTH_AT" -v b="$T0" 'BEGIN{printf "%.3f", a - b}')
  echo "run $i: e2e ready in ${DELTA}s (log: $LOG)"
  samples+=("$DELTA")
  rm -rf "$WORK_DIR"
done

if [ "${#samples[@]}" -eq 0 ]; then
  echo "ERROR: zero successful runs" >&2
  exit 1
fi

sorted=($(printf '%s\n' "${samples[@]}" | sort -g))
sum=0
for s in "${sorted[@]}"; do sum="$(awk -v a="$sum" -v b="$s" 'BEGIN{printf "%.3f", a + b}')"; done
mean="$(awk -v s="$sum" -v n="${#sorted[@]}" 'BEGIN{printf "%.3f", s / n}')"
p50_idx=$(( (${#sorted[@]} - 1) / 2 ))
p95_idx=$(awk -v n="${#sorted[@]}" 'BEGIN{i=int(0.95*n + 0.999999) - 1; if (i < 0) i = 0; print i}')

echo
echo "==> e2e Summary"
echo "    samples : ${sorted[*]}"
echo "    mean    : ${mean}s"
echo "    p50     : ${sorted[$p50_idx]}s"
echo "    p95     : ${sorted[$p95_idx]}s"
echo "    sla 10s : $(awk -v p="${sorted[$p95_idx]}" 'BEGIN{print (p <= 10.0) ? "PASS" : "FAIL"}')"
