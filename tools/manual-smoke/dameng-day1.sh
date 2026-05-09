#!/usr/bin/env bash
# tools/manual-smoke/dameng-day1.sh
#
# T2 fixture manual smoke for Dameng Day-1.
# CI does NOT run this script. Local QA acceptance only.
#
# Prerequisites:
# 1. Reachable DM 8 server (trial license or development edition).
#    Vendor portal: https://eco.dameng.com/
# 2. Environment variables:
#       export DAMENG_HOST=127.0.0.1
#       export DAMENG_PORT=5236
#       export DAMENG_USER=SYSDBA
#       export DAMENG_PASSWORD=...
#       export DAMENG_SCHEMA=SCOTT          # optional initial schema
#       export DATATALK_API=http://localhost:8080
# 3. DataTalk backend running locally on port 8080.
# 4. DataTalk frontend running locally on port 5173 (for UI cases).
#
# Common connection-failure causes:
#   - case-sensitive username (DM IDENTITY_CASE_SENSITIVE=1)
#   - port firewall (5236 default; verify reachability)
#   - schema permission (initial schema requires SELECT on user objects)

set -euo pipefail

: "${DAMENG_HOST:?required}"
: "${DAMENG_PORT:=5236}"
: "${DAMENG_USER:?required}"
: "${DAMENG_PASSWORD:?required}"
: "${DAMENG_SCHEMA:=}"
: "${DATATALK_API:=http://localhost:8080}"

LOG_DIR=tmp/dameng-smoke
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/run-$(date +%s).log"
exec > >(tee -a "$LOG") 2>&1

echo "==> Dameng Day-1 manual smoke (run log: $LOG)"

# ====== Case 1: Connection success + failure ======

echo "[Case 1] Create dameng connection — success path"
CONN_JSON=$(curl -sf -X POST "$DATATALK_API/api/connections" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"dameng-smoke\",\"kind\":\"dameng\",\"host\":\"$DAMENG_HOST\",\"port\":$DAMENG_PORT,\"username\":\"$DAMENG_USER\",\"password\":\"$DAMENG_PASSWORD\",\"databaseName\":\"$DAMENG_SCHEMA\"}")
echo "    raw: $CONN_JSON"
CONN_ID=$(echo "$CONN_JSON" | jq -r '.id')
echo "    created connectionId=$CONN_ID"

echo "[Case 1] Test connection — expect ok"
curl -sf -X POST "$DATATALK_API/api/connections/$CONN_ID/test" | jq '.'

echo "[Case 1] Connection failure — wrong password"
curl -s -X POST "$DATATALK_API/api/connections" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"dameng-bad\",\"kind\":\"dameng\",\"host\":\"$DAMENG_HOST\",\"port\":$DAMENG_PORT,\"username\":\"$DAMENG_USER\",\"password\":\"WRONG\"}" \
    | jq '.code'

echo "[Case 1] Alias rejection — kind=dm must be rejected"
curl -s -X POST "$DATATALK_API/api/connections" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"dameng-alias\",\"kind\":\"dm\",\"host\":\"$DAMENG_HOST\",\"port\":$DAMENG_PORT,\"username\":\"$DAMENG_USER\",\"password\":\"$DAMENG_PASSWORD\"}" \
    | jq '.code'

# ====== Case 2-4: Schema / table / column discovery ======

echo "[Case 2] Schema discovery — expect SYS/SYSDBA/SYSAUDITOR/SYSSSO/CTISYS filtered out"
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas" | jq '.[].name'

echo "[Case 3] Table discovery — pick first schema"
SCHEMA=$(curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas" | jq -r '.[0].name')
echo "    using schema=$SCHEMA"
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables" | jq '.[].name' | head -5

echo "[Case 4] Column discovery — pick first table"
TABLE=$(curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables" | jq -r '.[0].name')
echo "    using table=$TABLE"
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables/$TABLE/columns" | jq '.[].name'

# ====== Case 5: L1 SELECT execution ======

echo "[Case 5] L1 SELECT execution"
curl -sf -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"SELECT 1 FROM DUAL\"}" \
    | jq '.results[0].rowCount'

# ====== Case 6: L2 INSERT/UPDATE ======

echo "[Case 6] L2 INSERT/UPDATE — expect L2 confirmation envelope"
if [ -n "$TABLE" ]; then
    curl -s -X POST "$DATATALK_API/api/sql/execute" \
        -H "Content-Type: application/json" \
        -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"UPDATE $SCHEMA.$TABLE SET dummy = dummy\"}" \
        | jq '{level: .level, reason: .reason}' 2>/dev/null || echo "    (no table with updatable column found)"
else
    echo "    (skipped: no table discovered)"
fi

# ====== Case 7: L3 DROP (dameng_admin_command label) ======

echo "[Case 7] L3 DROP TABLE — expect L3 with dameng_admin_command label"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DROP TABLE smoke_temp_table\"}" \
    | jq '{level: .level, reason: .reason}'

echo "[Case 7] L3 DROP USER — expect dameng_admin_command label"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DROP USER fake_user CASCADE\"}" \
    | jq '{level: .level, reason: .reason}'

# ====== Case 8: dialect_unsupported rejection ======

echo "[Case 8] PL/SQL block — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DECLARE v INT; BEGIN v := 1; END;\"}" \
    | jq '{code: .code, message: .message}'

echo "[Case 8] CREATE PROCEDURE — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"CREATE PROCEDURE p1 AS BEGIN NULL; END;\"}" \
    | jq '{code: .code, message: .message}'

echo "[Case 8] EXP utility — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"EXP scott/tiger@dm FILE=demo.dmp\"}" \
    | jq '{code: .code, message: .message}'

# ====== Case 9: i18n key resolution ======

echo "[Case 9] i18n key resolution — verify en + zh"
for LANG in en-US zh-CN; do
    echo "  language=$LANG"
    curl -sf -X POST "$DATATALK_API/api/sql/execute" \
        -H "Content-Type: application/json" \
        -H "Accept-Language: $LANG" \
        -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"BEGIN NULL; END;\"}" \
        | jq '.message'
done

echo "==> Dameng Day-1 smoke COMPLETE"
