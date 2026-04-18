# Connection Test Status Persistence Design

## Problem

Data source connection test results (`testConnection`) exist only in memory:
- Backend returns `TestResult(ok, latencyMs, reason)` but doesn't save it
- Frontend stores `testResult` in local `useState`, lost on page refresh / app restart
- Users must re-test every connection after each restart

## Solution

Persist the latest test status in the `connections` table. `list()` returns the saved status alongside connection metadata. Frontend displays it on initial load without re-testing.

## Architecture

### Database

Migration `V6__connection_test_status.sql` — two nullable columns on `connections`:
```sql
ALTER TABLE connections ADD COLUMN last_test_status TEXT;    -- 'ok' | 'fail'
ALTER TABLE connections ADD COLUMN last_test_at INTEGER;      -- epoch millis
```

### Backend

**ConnectionRecord** — add `lastTestStatus` (String) and `lastTestAt` (long) fields.

**ConnectionRepository** — update RowMapper and all INSERT/UPDATE/SELECT SQL to include the two new columns. `testConnection()` writes status back to DB after each test via a targeted `UPDATE connections SET last_test_status=?, last_test_at=? WHERE id=?`.

**ConnectionService.testConnection()** — after computing the result, persist `ok`/`fail` and timestamp to the connection record.

**ConnectionDto** — add `lastTestStatus` (String, nullable) and `lastTestAt` (long, nullable) so the REST response carries persisted state.

### Frontend

**data-sources-page.tsx** — on mount, read `lastTestStatus` and `lastTestAt` from each connection in the list. Show the persisted badge (✓/✗ + time) without requiring a re-test. Manual test button still updates the state.

**api.ts** — no changes needed; types flow through `ConnectionTestResult` / `Connection` from the DTO.

## Behavior

| Event | Result |
|-------|--------|
| Create new connection | No test status (null) |
| Click "测试" → success | `last_test_status='ok'`, `last_test_at=now()` |
| Click "测试" → failure | `last_test_status='fail'`, `last_test_at=now()` |
| Restart app | `list()` returns saved status, displayed immediately |
| Edit connection | Test status preserved (not cleared) |
