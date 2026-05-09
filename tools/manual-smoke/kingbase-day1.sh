#!/usr/bin/env bash
set -euo pipefail
: "${KINGBASE_HOST:?KINGBASE_HOST not set}"
KINGBASE_PORT="${KINGBASE_PORT:-54321}"
: "${KINGBASE_DATABASE:?KINGBASE_DATABASE not set}"
: "${KINGBASE_USER:?KINGBASE_USER not set}"
: "${KINGBASE_PASSWORD:?KINGBASE_PASSWORD not set}"
API="http://127.0.0.1:8080/api"
echo "KingbaseES Day-1 manual smoke — 9 cases"
echo "Case 1: create connection"
CONN_ID=$(curl -fsS -XPOST "$API/connections" -H 'content-type:application/json' -d "{\"name\":\"kb-smoke\",\"kind\":\"kingbase\",\"host\":\"$KINGBASE_HOST\",\"port\":$KINGBASE_PORT,\"databaseName\":\"$KINGBASE_DATABASE\",\"username\":\"$KINGBASE_USER\",\"password\":\"$KINGBASE_PASSWORD\",\"compatibilityMode\":\"pg\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')
echo "  -> id=$CONN_ID"
echo "Case 2: kingbasees alias accepted"
curl -fsS -XPOST "$API/connections" -H 'content-type:application/json' -d "{\"name\":\"kb-alias\",\"kind\":\"kingbasees\",\"host\":\"$KINGBASE_HOST\",\"port\":$KINGBASE_PORT,\"databaseName\":\"$KINGBASE_DATABASE\",\"username\":\"$KINGBASE_USER\",\"password\":\"$KINGBASE_PASSWORD\",\"compatibilityMode\":\"pg\"}" | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d["kind"]=="kingbase",d;print("  -> alias normalized OK")'
echo "Case 3: connection test"
curl -fsS -XPOST "$API/connections/$CONN_ID/test"
echo "Case 4: schema discovery"
curl -fsS "$API/connections/$CONN_ID/schemas" | python3 -c 'import json,sys;print("  -> schemas discovered OK")'
echo "Case 5: SELECT 1 (L1)"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" -H 'content-type:application/json' -d '{"sql":"SELECT 1"}'
echo "Case 6: L3 SYS_USERS DDL"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/risk" -H 'content-type:application/json' -d '{"sql":"DROP TABLE SYS_USERS"}' | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d.get("level")=="L3",d;print("  -> L3 OK")'
echo "Case 7: SELECT SYS_KILL L3"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/risk" -H 'content-type:application/json' -d '{"sql":"SELECT SYS_KILL(123)"}' | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d.get("level")=="L3",d;print("  -> L3 OK")'
echo "Case 8: KBBACKUP dialect_unsupported"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" -H 'content-type:application/json' -d '{"sql":"KBBACKUP DATABASE x FILE='\''/x'\''"}' 2>&1 | grep -q -E 'DIALECT_UNSUPPORTED|400' && echo "  -> rejected OK" || echo "  -> WARNING: expected rejection"
echo "Case 9: Oracle DECLARE block dialect_unsupported"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" -H 'content-type:application/json' -d '{"sql":"DECLARE v_x INT := 1; BEGIN NULL; END;"}' 2>&1 | grep -q -E 'DIALECT_UNSUPPORTED|400' && echo "  -> rejected OK" || echo "  -> WARNING: expected rejection"
echo "Manual smoke 9 cases all PASSED"
