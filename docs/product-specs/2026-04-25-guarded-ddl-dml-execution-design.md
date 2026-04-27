# Guarded DDL / DML Execution Design

## 1. Purpose

Turn DataTalk's existing backend SQL risk classification (L1 / L2 / L3) into a user-visible confirmation flow. L2 and L3 statements must be explicitly acknowledged by the user before execution. The acknowledgment is re-validated on the server, so the SQL the user saw is provably the SQL that runs. Chat tool invocations and the SQL Workbench Query Editor share the same confirmation semantics but use different containers appropriate to their context.

## 2. Current State

**Backend:**

- `CalciteSqlRiskAnalyzer` already classifies statements into L1 / L2 / L3, but `DELETE` is always L3 regardless of `WHERE` presence.
- `SqlExecuteService` throws `SqlRiskBlockedException` for `source=user` L3 and silently allows `source=ai` L3 — neither path supports confirm-then-execute.
- `ExecuteSqlAction` (chat tool) has no `confirmed` input and calls `guard.assertSelectOnly(sql)` to bluntly reject any non-SELECT.
- `PreviewSqlAction` plus the chat renderer `preview-sql.tsx` already exchange a `{confirmed: boolean}` action result, but the backend does not consume it.

**Frontend:**

- Chat: `preview-sql.tsx` renders an inline card with Execute / Cancel buttons; the protocol exists but does not close the loop with the backend.
- Workbench: `runQueryEditorSql` catches `SqlRiskError` and only writes internal state via `setRiskBlocked` — there is no dialog rendered today.
- No risk-aware shared component; chat and Workbench have entirely separate implementations.

## 3. Design Inputs

This feature follows [client/DESIGN.md](../../client/DESIGN.md):

- Destructive confirmation uses `Dialog` / `AlertDialog`; danger surfaces use `status.danger` (red.500) + `status.dangerSurface` (red.100 in light, ~16% red in dark).
- L2 warnings use `accent.warn` (amber.500) — color cannot carry semantics alone, must pair with text and icons.
- The destructive button is the dialog's primary action; Cancel is outline / secondary.
- Focus rings must be visible in both themes; `prefers-reduced-motion` disables non-essential motion.
- Dialogs use the `focused` density and `fast 120ms` / `normal 180ms` motion tokens.
- The "type the table name to confirm" anti-pattern is excluded — DESIGN.md does not endorse it and it conflicts with the "motion confirms state change only" restraint.

## 4. Risk Classification (Final Table)

| Statement | Level | Notes |
|-----------|-------|-------|
| `SELECT` / `EXPLAIN` / `DESCRIBE` / `SHOW` | L1 | Unchanged |
| `WITH ... SELECT` (read-only CTE) | L1 | Unchanged |
| `INSERT` | L2 | Unchanged |
| `UPDATE ... WHERE ...` | L2 | Unchanged |
| **`DELETE ... WHERE ...`** | **L2** | **Changed from L3** |
| `CREATE VIEW` / `CREATE INDEX` | L2 | Unchanged |
| `UPDATE` (no `WHERE`) | L3 | Unchanged |
| `DELETE` (no `WHERE`) | L3 | Unchanged |
| `DROP TABLE` / `DROP DATABASE` / `DROP SCHEMA` / `DROP VIEW` / `DROP INDEX` | L3 | Unchanged |
| `TRUNCATE` | L3 | Unchanged |
| `ALTER` (any) | L3 | Unchanged |
| `GRANT` / `REVOKE` | L3 | Unchanged |
| `WITH ... <mutating DML>` | Escalates to nested DML's level | Unchanged (L2 stays L2, L3 stays L3) |
| Unclassified | L3 | Unchanged safe default |

The only behavioral change is `DELETE WITH WHERE` moving from L3 to L2, mirroring `UPDATE WITH WHERE`.

## 5. Scope

**In scope:**

- Backend `SqlRiskAnalyzer` adjustment for `DELETE` classification.
- Backend `/api/sql/execute` and `ExecuteSqlAction` accept `confirmed` and `riskAck` inputs and emit `requires_confirmation` responses.
- Frontend Query Editor `AlertDialog` confirmation flow.
- Frontend chat `preview-sql.tsx` upgraded to consume the new `requires_confirmation` server response.
- Shared `<SqlConfirmationCard>` visual core (L2 amber / L3 red, SQL preview, affected objects, button hierarchy).
- Simple display of impacted objects derived from the parsed AST; no live row-count probing.

**Out of scope:**

- Server-side confirmation token / nonce caching (unnecessary in the desktop single-user model).
- Dry-run `EXPLAIN` / `SELECT COUNT(*)` impact estimation (deferred to the intelligent operations track).
- Transactional rollback or audit-log surface (separate later plan).
- Per-statement confirmation in multi-statement scripts (entire batch is gated as one — see §8).
- Schema-diff preview / DDL impact analysis (deferred).

## 6. Confirmation Flow

### Server State Machine

Both `/api/sql/execute` (REST) and `ExecuteSqlAction` (chat tool) share the same flow:

```
inbound (sql, connectionId, confirmed?, riskAck?)
   │
   ▼
analyzedRisk = SqlRiskAnalyzer.analyze(sql)
   │
   ├─ analyzedRisk == L1
   │   └─→ execute → status=executed
   │
   ├─ analyzedRisk == L2 || L3
   │   ├─ confirmed != true
   │   │   └─→ no execution → status=requires_confirmation
   │   │       payload: {risk, sqlPreview, affectedObjects}
   │   │
   │   └─ confirmed == true
   │       ├─ riskAck >= analyzedRisk → execute → status=executed
   │       └─ riskAck <  analyzedRisk → no execution → status=confirmation_invalid
   │                                       reason: risk_ack_insufficient
```

### Workbench Client State Machine

`use-sql-execute` is extended:

```
idle ──Run──▶ running ──┬──▶ success
                        ├──▶ error
                        └──▶ requires_confirmation ──Confirm──▶ confirming ──┬──▶ success
                                                  │                          ├──▶ error
                                                  │                          └──▶ confirmation_invalid ──▶ requires_confirmation (with new risk)
                                                  └──Cancel──▶ idle
```

### Chat Flow

`ExecuteSqlAction` is a `SERVER` executor action. The dispatcher's `SERVER` branch resolves the handler's returned future synchronously and never registers a `PendingCallRegistry` entry — there is no pause-resume primitive available for SERVER actions, and `actionResult` for an unregistered call is silently dropped. A naïve "AI sees `requires_confirmation`, user clicks Execute, AI re-invokes with `confirmed=true`" round trip therefore (a) cannot produce a trustworthy confirmation signal — the AI always controls whether to set `confirmed=true` — and (b) is not actually wired end-to-end today.

To eliminate the bypass and align with reality, the chat path **does not run L2 / L3 SQL**:

- AI invokes `executeSql` for L1 → action executes immediately and returns the result.
- AI invokes `executeSql` for L2 / L3 (with or without `confirmed`) → action returns `status: "blocked_in_chat"` with the risk payload and the original `sqlPreview`. The chat renderer surfaces a "Open in SQL Workbench to confirm" card with a one-click action that opens a Workbench tab pre-populated with the SQL.
- The Workbench `AlertDialog` flow remains the single trusted confirmation surface — the user reviews the SQL there and confirms via the REST `/api/sql/execute` two-step flow.
- `confirmed=true` is never honored when it arrives in the chat tool input. The action does not even branch on `confirmed`; the gate fires for every L2 / L3 statement regardless of input flags.

This restricts AI-initiated mutating execution to the auditable Workbench surface and removes the trust ambiguity around the chat tool input.

## 7. API Contract

### `POST /api/sql/execute` Request

```jsonc
{
  "sql": "DELETE FROM users WHERE id = 1",
  "connectionId": "conn-123",
  "database": "app",
  "schema": "public",
  "source": "user",
  "maxRows": 100,
  "confirmed": false,
  "riskAck": null
}
```

`confirmed` defaults to `false`. `riskAck` is one of `"L1"` / `"L2"` / `"L3"` or `null`.

### Response — `status: "requires_confirmation"`

```jsonc
{
  "status": "requires_confirmation",
  "risk": {
    "level": "L2",
    "reason": "DELETE with WHERE clause — bounded mutation",
    "affectedObjects": ["users"]
  },
  "sqlPreview": "DELETE FROM users WHERE id = 1",
  "items": []
}
```

### Response — `status: "executed"`

Existing multi-result `items` payload, unchanged.

### Response — `status: "confirmation_invalid"`

```jsonc
{
  "status": "confirmation_invalid",
  "reason": "risk_ack_insufficient",
  "ackedRisk": "L2",
  "currentRisk": "L3",
  "message": "Acknowledged risk is lower than the current statement risk — please review and confirm again"
}
```

`risk_ack_insufficient` is emitted whenever `riskAck < currentRisk`. It covers two underlying causes that the server cannot distinguish: the SQL was modified between preview and execute (escalating risk), or the original `riskAck` value was simply lower than the statement's actual risk (under-ack). The client treats both the same way: discard the stale acknowledgment, re-render the confirmation with `currentRisk`.

### `ExecuteSqlAction` (Chat Tool) Output

`ExecuteSqlAction` returns one of:

- L1: existing successful result shape (`artifactId`, `columns`, `preview`, `rowCount`, `durationMs`, `metadata`).
- L2 / L3: `{ status: "blocked_in_chat", risk: { level, reason, affectedObjects }, sqlPreview }`.

`ExecuteSqlAction` does not accept `confirmed` or `riskAck` from tool input — those flags exist only on the REST `/api/sql/execute` request used by the Workbench.

## 8. UI Behavior

### Shared Core: `<SqlConfirmationCard>` (New)

Location: `client/src/features/sql-confirmation/sql-confirmation-card.tsx`

Composition:

- Top: risk badge — L2 amber dot with "Bounded mutation"; L3 red dot with "Destructive operation".
- Middle: SQL preview (read-only, same code-window treatment used by `BasicTool` SQL blocks).
- Affected objects list (table / view names, mono font).
- One-line description: "This will modify data in {object} ..." for L2; "This will permanently {drop|truncate|alter} {object} ..." for L3.
- Buttons: left `Cancel` (outline) / right `Execute` (L2 amber-tinted primary, L3 destructive red).
- L3 only: a red sentence above the buttons reading "This action cannot be undone."

### Query Editor Container: `AlertDialog`

- Trigger: `runQueryEditorSql` receives a `requires_confirmation` response → `setConfirmation(tabId, payload)`.
- `<AlertDialog>` wraps `<SqlConfirmationCard>` using DESIGN.md dialog spacing (16 / 24).
- Cancel returns the state machine to `idle`.
- Confirm calls `confirmAndRun(payload.risk.level)`, which re-POSTs `/api/sql/execute` with `confirmed=true, riskAck=...`.
- A `confirmation_invalid` response closes the current dialog and immediately opens a new one carrying the new risk (when the new risk is still L2 / L3).

### Chat Container: "Open in Workbench" Card (`execute-sql.tsx`)

When `status === "blocked_in_chat"`, the chat renderer shows a read-only L2 / L3 card built around the same risk badge + SQL preview vocabulary used by `<SqlConfirmationCard>`, but the action footer collapses to a single primary button: **Open in SQL Workbench**. Clicking it dispatches a stage `query_editor` open with the SQL pre-loaded, where the AlertDialog confirmation flow takes over.

There is no inline Execute / Cancel pair in chat — that affordance only exists in the Workbench `AlertDialog`.

### Multi-Statement Behavior

- Backend splitter splits the script and analyzes each statement.
- If any statement is ≥ L2, the entire batch is gated and the response carries the highest risk level encountered.
- A single user confirmation authorizes the whole batch, reflecting that the user has reviewed the entire script.
- `affectedObjects` is the union across all statements in the batch.

## 9. Error Handling

| Scenario | Behavior |
|----------|----------|
| Network failure before confirmation reaches the server | Toast "Confirmation request failed, please retry"; state returns to `idle` |
| User closes the dialog or switches tabs while in `confirming` | The in-flight request continues; completion surfaces via `streamingBySession`-style toast notification (no cancel — half-committed semantics are worse than slightly-delayed feedback) |
| Backend returns `confirmation_invalid` | Workbench closes the old dialog and opens a new one with `currentRisk`; chat returns the card to `requires_confirmation` with the new payload |
| AI sets `confirmed=true` directly in the `executeSql` tool input | Action ignores the flag entirely; L2 / L3 SQL is gated unconditionally with `blocked_in_chat`. The only surface that honors `confirmed=true` is the REST `/api/sql/execute` endpoint, which the Workbench drives directly from a user click |
| `riskAck` missing or invalid (Workbench / REST) | Treated as `confirmed=false`; server returns `requires_confirmation` |
| AI invokes `executeSql` for L2 / L3 | Returns `blocked_in_chat`; the chat renderer surfaces "Open in SQL Workbench" — the only path to mutating execution from AI-suggested SQL |
| Connection error or SQL syntax error (before risk analysis) | Existing Markdown diagnostic block path is unchanged; the confirmation flow is not entered |

## 10. Testing

**Backend (JUnit 5 + AssertJ):**

- `CalciteSqlRiskAnalyzerTest`: add `DELETE WITH WHERE` → L2 case; confirm `DELETE` (no `WHERE`) remains L3.
- `SqlExecuteServiceTest`:
  - L1 executes directly.
  - L2 without `confirmed` → `requires_confirmation`.
  - L2 with `confirmed=true, riskAck=L2` → executes.
  - L2 with `confirmed=true, riskAck=L1` → `confirmation_invalid` with `reason=risk_ack_insufficient`.
  - L3 mirrors the L2 cases.
  - Multi-statement script containing L3 → entire batch returns `requires_confirmation` with `risk=L3`.
- `ExecuteSqlActionTest`: same matrix dispatched via `ActionDispatcher`.
- `SqlExecuteControllerIT`: HTTP-level end-to-end coverage for L2 two-step, L3 two-step, and `confirmation_invalid`.

**Frontend (vitest):**

- `sql-confirmation-card.test.tsx`: L2 / L3 risk badge, button labels, affected-objects rendering, `Cancel` / `Execute` callbacks.
- `use-sql-execute.test.ts`: state-machine transitions `idle → running → requires_confirmation → confirming → success`; `confirmation_invalid` returns to `requires_confirmation`.
- `sql-workbench-tab.test.tsx`: running an L2 statement opens the AlertDialog; Confirm produces a result tab.
- `preview-sql.test.tsx`: regression — protocol unchanged after `<SqlConfirmationCard>` integration.
- Accessibility: dialog has an `aria-label`; initial focus lands on `Cancel` to avoid accidental destructive activation.

**Manual smoke (recorded in plan, performed by human):**

- Light and dark themes show consistent L2 / L3 card visuals and tokens.
- `prefers-reduced-motion` downgrades dialog entrance motion.

## 11. Acceptance Criteria

- `CalciteSqlRiskAnalyzer` reflects the new classification, with `DELETE WITH WHERE` = L2.
- Query Editor execution of L2 / L3 statements opens an `AlertDialog`; Cancel does not execute, Execute produces a normal result tab.
- Chat AI invocation of `executeSql` for L2 / L3 returns `status: "blocked_in_chat"`; the chat renderer surfaces a card with an "Open in SQL Workbench" CTA. No mutating SQL ever runs from the chat tool path.
- A `riskAck` lower than the current risk yields `confirmation_invalid` (Workbench / REST path only) and the UI guides the user through a refreshed confirmation.
- Workbench `AlertDialog` consumes `<SqlConfirmationCard>`. The chat `blocked_in_chat` card reuses the same risk badge / SQL preview tokens but collapses the footer to a single "Open in SQL Workbench" button.
- `cd server && mvn clean verify` passes.
- `cd client && npx tsc --noEmit` passes.
- All listed JUnit and vitest cases pass.
