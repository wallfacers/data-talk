# SQL Editor Toolbar Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the SQL editor context popover with inline toolbar controls for session-following, connection, database, schema, and result limit, while exposing the same controls through the AI UI object contract.

**Architecture:** Keep SQL execution on the existing `/api/sql/execute` path and move only client-side context selection, persistence, and UI object action semantics. The query editor will resolve effective context from either the latest session data context or the current tab override, and the toolbar will render that single resolved value. Server changes are limited to UI action schema, runtime prompt, and contract tests so OpenCode knows the new linked parameters.

**Tech Stack:** React 19, shadcn/ui `Switch` and `Select`, Zustand Stage stores, TanStack Query-adjacent session data context APIs, Vitest, Spring Boot UI action schema tests.

---

**Status:** Active

**Spec:** [SQL Editor Toolbar Context Design](../product-specs/2026-04-30-sql-editor-toolbar-context-design.md)

**Branch target:** `develop`

## Design Inputs

- `client/DESIGN.md`: SQL editor belongs to the Stage instrument lane, so controls must be compact, semantic, keyboard accessible, and visually aligned with existing toolbar density. Use semantic tokens, shadcn/ui primitives, stable control widths, i18n keys, and avoid explanatory cards inside the workbench.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: This plan changes Stage Query Editor context and data source pickers, so the gate applies. No new database kind, JDBC driver, SQL splitter, risk analyzer, or backend SQL execution API is introduced. Target discovery reuses existing connection target APIs; if implementation discovers a new database-specific branch, update the compatibility document in the same change.
- Product spec sections covered: toolbar layout (§5), state model (§6), context resolution (§7), dropdown data flow (§8), failure handling (§9), AI action contract (§10), AI read state (§11), testing (§12), compatibility checklist (§13).

## Scope

In scope:

- Inline toolbar controls in the SQL editor: session context switch, connection select, database select, schema select, and final limit select.
- Immediate tab override writes when session-following is off.
- Readonly display and live session-context synchronization when session-following is on.
- Fresh connection and target fetch on every dropdown open, with global toast failure feedback.
- `query_editor.set_context`, `query_editor.read('state')`, and `workspace.read('state')` updates for `useSessionContext`, `limit`, and strict linked context parameters.
- Runtime prompt and UI object reference updates for AI-operable controls.

Out of scope:

- Backend SQL execution behavior, SQL result payloads, risk analysis, SQL splitting, JDBC drivers, and new database kinds.
- Writing tab overrides back to session data context.
- Changing `resolvedContext`; it remains the last backend execution target.

## File Structure

Create:

- `client/src/features/stage/components/sql-context-toolbar-controls.tsx` - compact toolbar controls and dropdown refresh orchestration.
- `client/src/features/stage/components/sql-context-toolbar-controls.test.tsx` - rendering, ordering, readonly/manual mode, immediate write, and toast tests.

Modify:

- `client/src/features/stage/components/sql-editor-toolbar.tsx` - replace `contextChip` prop with `contextControls`, keep limit last.
- `client/src/features/stage/components/sql-editor-toolbar.test.tsx` - assert context controls render before limit.
- `client/src/features/stage/components/sql-workbench-tab.tsx` - compute effective context mode, wire dropdown callbacks, and remove popover usage.
- `client/src/features/stage/components/sql-workbench-tab.test.tsx` - session sync, manual override, refresh, and failure coverage.
- `client/src/features/stage/components/sql-limit-select.tsx` - add compact label/class support only if the new toolbar needs it.
- `client/src/features/stage/components/sql-context-chip.tsx` and `client/src/features/stage/components/sql-context-chip.test.tsx` - delete when no remaining imports exist.
- `client/src/features/stage/utils/normalize-query-editor-payload.ts` and `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts` - persist and migrate `useSessionContext`.
- `client/src/features/stage/utils/resolve-tab-data-context.ts` and `client/src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts` - resolve session vs override context from one contract.
- `client/src/features/stage/utils/query-editor-actions.ts` and `client/src/features/stage/utils/query-editor-actions.test.ts` - update run/default context behavior and action helpers.
- `client/src/features/stage/adapters/QueryEditorAdapter.ts` and `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` - update AI action schema, validation, exec, and read state.
- `client/src/features/stage/adapters/WorkspaceAdapter.ts` and `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts` - include query editor effective context summary.
- `client/src/stores/stage-store.ts` and `client/src/stores/stage-store.test.ts` - default new query editors to session-following and migrate restored payloads.
- `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` and `client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts` - persist mode changes and tab override changes.
- `client/src/i18n/messages.ts` - add zh/en labels and toast strings.
- `docs/references/ui-objects-reference.md` - document `set_context` linked parameters and read state.
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` - update server-exposed `ui_exec` schema/required-parameter hints for `query_editor.set_context`.
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` - update runtime agent instructions for query editor context controls.
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` - lock prompt guidance.
- `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpActionBridgeTest.java` - lock UI action schema behavior if existing assertions cover `set_context`.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` - update only if implementation discovers new database-specific context behavior.

## Compatibility Gate

- Domain Layer: N/A, no domain sealed interfaces or records change.
- Frontend Connection UI: Applicable, the SQL toolbar becomes a new connection/database/schema selection surface for existing connection kinds.
- Application Connection Layer: N/A, no backend connection parsing or JDBC URL semantics change.
- Persistence And Metadata DB: N/A, existing Stage tab JSON payload stores the new lightweight mode field without schema migration.
- JDBC Driver And Runtime Packaging: N/A, no driver dependency change.
- Dynamic SQL Execution Repository: N/A, SQL execution still calls the existing endpoint.
- Schema Discovery And Target Resolution: Applicable, toolbar reuses current `listConnectionTargets` behavior and must not introduce cross-connection database/schema combinations.
- SQL Statement Splitting / Risk Analysis: N/A, SQL text analysis is untouched.
- Adapter Actions And Ontology: Applicable, `query_editor.set_context` schema and state summaries change.
- Runtime Agent Prompt / Tool Docs: Applicable, update the runtime prompt and UI object reference with linked-parameter rules.

## Tasks

### Task 1: Normalize Payload And Resolve Context Mode

**Files:**
- Modify: `client/src/features/stage/utils/normalize-query-editor-payload.ts`
- Modify: `client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts`
- Modify: `client/src/features/stage/utils/resolve-tab-data-context.ts`
- Modify: `client/src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts`

- [ ] **Step 1: Add failing tests for mode migration**

Add cases equivalent to:

```ts
expect(normalizeQueryEditorPayload({ contextOverride: { connectionId: 'c1', database: 'db1', schema: null } }).useSessionContext).toBe(false)
expect(normalizeQueryEditorPayload({ contextOverride: null, contextPinMode: 'session' }).useSessionContext).toBe(true)
expect(normalizeQueryEditorPayload({ useSessionContext: false, contextOverride: null }).useSessionContext).toBe(false)
```

Run: `cd client && npm test -- --run src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts`

Expected before implementation: at least one assertion fails because `useSessionContext` is not normalized.

- [ ] **Step 2: Implement the payload contract**

Use this shape in the normalizer and resolver:

```ts
export type QueryEditorContextSource = 'session' | 'override' | 'tab'

export type QueryEditorEffectiveContext = {
  useSessionContext: boolean
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  contextSource: QueryEditorContextSource
}
```

Normalization rules:

```ts
const explicitUseSession = typeof record.useSessionContext === 'boolean'
  ? record.useSessionContext
  : record.contextOverride == null
```

Keep `contextPinMode` only as a legacy input. New persisted payloads should write `useSessionContext`.

- [ ] **Step 3: Verify focused utility tests**

Run: `cd client && npm test -- --run src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts`

Expected after implementation: all tests pass.

### Task 2: Update Stores And Persistence

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.test.ts`
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`
- Modify: `client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts`

- [ ] **Step 1: Add failing tests for defaults and persistence**

Add cases equivalent to:

```ts
expect(normalizeQueryEditorPayload(useStageStore.getState().openQueryEditor({}).payload).useSessionContext).toBe(true)

useSqlWorkbenchStore.getState().setContextOverride('tab-1', {
  connectionId: 'c2',
  connectionName: 'Local database',
  database: 'analytics',
  schema: 'public',
})
expect(useSqlWorkbenchStore.getState().tabs['tab-1']?.useSessionContext).toBe(false)
```

Run: `cd client && npm test -- --run src/stores/stage-store.test.ts src/features/stage/stores/sql-workbench-store.test.ts src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts`

Expected before implementation: defaults or persistence assertions fail.

- [ ] **Step 2: Implement mode-aware tab state**

Persist these fields in query editor tab payload snapshots:

```ts
{
  initialSql,
  sqlText,
  contextOverride,
  useSessionContext,
}
```

When `useSessionContext` changes to `true`, clear runtime tab override and persist `contextOverride: null`. When dropdowns write a tab override, persist `useSessionContext: false`.

- [ ] **Step 3: Verify focused store tests**

Run: `cd client && npm test -- --run src/stores/stage-store.test.ts src/features/stage/stores/sql-workbench-store.test.ts src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts`

Expected after implementation: all tests pass.

### Task 3: Update AI Query Editor Contract

**Files:**
- Modify: `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`
- Modify: `client/src/features/stage/utils/query-editor-actions.ts`
- Modify: `client/src/features/stage/utils/query-editor-actions.test.ts`

- [ ] **Step 1: Add failing adapter tests**

Add cases equivalent to:

```ts
await expect(adapter.exec('set_context', { useSessionContext: true })).resolves.toMatchObject({ ok: true })
await expect(adapter.exec('set_context', { limit: 100 })).resolves.toMatchObject({ ok: true })
await expect(adapter.exec('set_context', { schema: 'public' })).resolves.toMatchObject({ ok: false })
await expect(adapter.exec('set_context', { database: 'app' })).resolves.toMatchObject({ ok: false })
await expect(adapter.exec('set_context', { useSessionContext: true, connectionId: 'c1' })).resolves.toMatchObject({ ok: false })
```

Run: `cd client && npm test -- --run src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/utils/query-editor-actions.test.ts`

Expected before implementation: schema and validation assertions fail.

- [ ] **Step 2: Implement strict linked parameters**

Use this params contract:

```ts
type QueryEditorSetContextParams = {
  useSessionContext?: boolean
  connectionId?: string | null
  database?: string | null
  schema?: string | null
  limit?: 10 | 100 | 1000 | null
}
```

Validation:

```ts
if (params.useSessionContext === true && (params.connectionId || params.database || params.schema)) {
  return execError('useSessionContext=true cannot be combined with connectionId, database, or schema')
}
if (params.schema && (!params.connectionId || !params.database)) {
  return execError('schema requires connectionId and database')
}
if (params.database && !params.connectionId) {
  return execError('database requires connectionId')
}
```

Expose read state with `useSessionContext`, `contextSource`, `connectionId`, `connectionName`, `database`, `schema`, `contextOverride`, `limit`, `availableDatabases`, and `availableSchemas`.

- [ ] **Step 3: Verify adapter/action tests**

Run: `cd client && npm test -- --run src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/utils/query-editor-actions.test.ts`

Expected after implementation: all tests pass.

### Task 4: Build Toolbar Context Controls

**Files:**
- Create: `client/src/features/stage/components/sql-context-toolbar-controls.tsx`
- Create: `client/src/features/stage/components/sql-context-toolbar-controls.test.tsx`
- Modify: `client/src/features/stage/components/sql-limit-select.tsx`
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 1: Add failing component tests**

Cover these visible labels and ordering:

```ts
expect(screen.getByLabelText('固定 session 上下文')).toBeInTheDocument()
expect(screen.getByLabelText('连接')).toBeInTheDocument()
expect(screen.getByLabelText('数据库')).toBeInTheDocument()
expect(screen.getByLabelText('Schema')).toBeInTheDocument()
expect(screen.getByLabelText('分页限制')).toBeInTheDocument()
expect(toolbar.textContent).toMatch(/固定 session 上下文.*连接.*数据库.*Schema.*分页限制/)
```

Run: `cd client && npm test -- --run src/features/stage/components/sql-context-toolbar-controls.test.tsx`

Expected before implementation: test file fails because the component does not exist.

- [ ] **Step 2: Implement compact controls**

Props should include:

```ts
type SqlContextToolbarControlsProps = {
  useSessionContext: boolean
  context: SqlContextValue | null
  connections: SqlContextConnectionOption[]
  targets: SqlContextConnectionTargets | null
  limit: SqlLimitValue
  onUseSessionContextChange: (value: boolean) => void
  onConnectionChange: (connectionId: string) => void
  onDatabaseChange: (database: string | null) => void
  onSchemaChange: (schema: string | null) => void
  onLimitChange: (value: SqlLimitValue) => void
  onOpenConnections: () => Promise<void>
  onOpenTargets: () => Promise<void>
}
```

Use shadcn/ui `Switch` for the mode, shadcn/ui `Select` for dropdowns, and `toast.error(...)` from `sonner` when refresh callbacks reject. Disabled session-following dropdowns still show the latest session values.

- [ ] **Step 3: Verify component tests**

Run: `cd client && npm test -- --run src/features/stage/components/sql-context-toolbar-controls.test.tsx`

Expected after implementation: all tests pass.

### Task 5: Wire SQL Workbench Toolbar

**Files:**
- Modify: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Delete: `client/src/features/stage/components/sql-context-chip.tsx`
- Delete: `client/src/features/stage/components/sql-context-chip.test.tsx`

- [ ] **Step 1: Add failing integration tests**

Add cases equivalent to:

```ts
expect(screen.queryByText('SQL 执行上下文')).not.toBeInTheDocument()
expect(screen.getByLabelText('分页限制')).toBeInTheDocument()
expect(screen.getByLabelText('分页限制').compareDocumentPosition(screen.getByLabelText('Schema'))).toBe(Node.DOCUMENT_POSITION_PRECEDING)
```

Also assert that changing session data context updates visible values while `useSessionContext` is enabled.

Run: `cd client && npm test -- --run src/features/stage/components/sql-editor-toolbar.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`

Expected before implementation: existing popover behavior causes assertions to fail.

- [ ] **Step 2: Replace popover wiring**

Change the toolbar prop from:

```tsx
<SqlEditorToolbar contextChip={<SqlContextChip ... />} limit={limit} onLimitChange={setLimit} />
```

to:

```tsx
<SqlEditorToolbar
  contextControls={<SqlContextToolbarControls ... />}
  canRun={canRun}
  isRunning={isRunning}
  onRun={runSql}
  onCancel={cancelRun}
  onFormat={formatSql}
/>
```

Keep `SqlLimitSelect` inside `SqlContextToolbarControls` so pagination remains the final control.

- [ ] **Step 3: Verify toolbar/workbench tests and remove dead imports**

Run: `cd client && npm test -- --run src/features/stage/components/sql-editor-toolbar.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`
Run: `cd client && rg -n "SqlContextChip|sql-context-chip" src`

Expected after implementation: tests pass and `rg` returns no matches.

### Task 6: Enforce Fresh Dropdown Data And Failure Toasts

**Files:**
- Modify: `client/src/features/stage/components/sql-context-toolbar-controls.tsx`
- Modify: `client/src/features/stage/components/sql-context-toolbar-controls.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [ ] **Step 1: Add failing refresh and failure tests**

Use mocked callbacks to assert each dropdown open refreshes data:

```ts
await user.click(screen.getByLabelText('连接'))
await user.click(screen.getByLabelText('连接'))
expect(onOpenConnections).toHaveBeenCalledTimes(2)

onOpenTargets.mockRejectedValueOnce(new Error('connection refused'))
await user.click(screen.getByLabelText('数据库'))
expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('数据库上下文刷新失败'))
expect(onDatabaseChange).not.toHaveBeenCalled()
```

Run: `cd client && npm test -- --run src/features/stage/components/sql-context-toolbar-controls.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`

Expected before implementation: refresh count or toast assertions fail.

- [ ] **Step 2: Implement linked reset semantics**

On manual connection change, immediately write:

```ts
{
  connectionId: nextConnectionId,
  connectionName: nextConnectionName,
  database: null,
  schema: null,
}
```

On manual database change, immediately write:

```ts
{
  connectionId: currentConnectionId,
  connectionName: currentConnectionName,
  database: nextDatabase,
  schema: null,
}
```

Never write `__empty__` into payload, store, or API-facing params. On refresh failure, keep the previous effective context and SQL text unchanged.

- [ ] **Step 3: Verify refresh and failure tests**

Run: `cd client && npm test -- --run src/features/stage/components/sql-context-toolbar-controls.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`

Expected after implementation: all tests pass.

### Task 7: Update Server UI Schema And Agent Docs

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpActionBridgeTest.java`
- Modify: `docs/references/ui-objects-reference.md`

- [ ] **Step 1: Add failing backend contract assertions**

Assert the runtime prompt contains these rules:

```java
assertThat(prompt).contains("set_context({ useSessionContext: true })");
assertThat(prompt).contains("database requires connectionId");
assertThat(prompt).contains("schema requires connectionId and database");
assertThat(prompt).contains("limit");
```

Run: `cd server && mvn -q -pl data-talk-adapter -am -Dtest=AgentPromptContractTest test`

Expected before implementation: prompt assertion fails.

- [ ] **Step 2: Implement schema and docs**

Update `query_editor.set_context` server schema to advertise:

```json
{
  "useSessionContext": { "type": "boolean" },
  "connectionId": { "type": ["string", "null"] },
  "database": { "type": ["string", "null"] },
  "schema": { "type": ["string", "null"] },
  "limit": { "type": ["integer", "null"], "enum": [10, 100, 1000, null] }
}
```

Update docs to state that `useSessionContext=true` cannot be combined with connection fields, `database` requires `connectionId`, and `schema` requires `connectionId + database`.

- [ ] **Step 3: Verify backend contract tests**

Run: `cd server && mvn -q -pl data-talk-adapter -am -Dtest=AgentPromptContractTest test`
Run if schema assertions are touched: `cd server && mvn -q -pl data-talk-application -Dtest=McpActionBridgeTest test`

Expected after implementation: selected tests pass.

### Task 8: Final Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-30-sql-editor-toolbar-context-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` only if Task 1-7 discover new database-specific behavior.

- [ ] **Step 1: Run consolidated verification**

Run:

```bash
cd client && npm test -- --run \
  src/features/stage/components/sql-context-toolbar-controls.test.tsx \
  src/features/stage/components/sql-editor-toolbar.test.tsx \
  src/features/stage/components/sql-workbench-tab.test.tsx \
  src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
  src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts \
  src/features/stage/utils/query-editor-actions.test.ts \
  src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
  src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts \
  src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts
cd client && npx tsc --noEmit
cd server && mvn compile -q
git diff --check
```

Expected: all commands exit zero.

- [ ] **Step 2: Complete document housekeeping**

Mark every checkbox in this plan complete, move this plan from Active to Completed in `docs/exec-plans/index.md`, and add a concise completion summary naming the verification commands. If `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` was updated, include the exact compatibility note in the final plan summary.

- [ ] **Step 3: Commit implementation**

Stage only files touched for this plan and commit:

```bash
git add \
  client/src/features/stage/components/sql-context-toolbar-controls.tsx \
  client/src/features/stage/components/sql-context-toolbar-controls.test.tsx \
  client/src/features/stage/components/sql-editor-toolbar.tsx \
  client/src/features/stage/components/sql-editor-toolbar.test.tsx \
  client/src/features/stage/components/sql-workbench-tab.tsx \
  client/src/features/stage/components/sql-workbench-tab.test.tsx \
  client/src/features/stage/components/sql-limit-select.tsx \
  client/src/features/stage/components/sql-context-chip.tsx \
  client/src/features/stage/components/sql-context-chip.test.tsx \
  client/src/features/stage/utils/normalize-query-editor-payload.ts \
  client/src/features/stage/utils/__tests__/normalize-query-editor-payload.test.ts \
  client/src/features/stage/utils/resolve-tab-data-context.ts \
  client/src/features/stage/utils/__tests__/resolve-tab-data-context.test.ts \
  client/src/features/stage/utils/query-editor-actions.ts \
  client/src/features/stage/utils/query-editor-actions.test.ts \
  client/src/features/stage/adapters/QueryEditorAdapter.ts \
  client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts \
  client/src/features/stage/adapters/WorkspaceAdapter.ts \
  client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts \
  client/src/stores/stage-store.ts \
  client/src/stores/stage-store.test.ts \
  client/src/features/stage/stores/sql-workbench-store.ts \
  client/src/features/stage/stores/sql-workbench-store.test.ts \
  client/src/features/stage/persistence/stage-persistence-bootstrap.ts \
  client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts \
  client/src/i18n/messages.ts \
  server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java \
  server/data-talk-adapter/src/main/resources/agents/AGENTS.md \
  server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java \
  server/data-talk-application/src/test/java/com/datatalk/application/opencode/McpActionBridgeTest.java \
  docs/references/ui-objects-reference.md \
  docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
  docs/exec-plans/2026-04-30-sql-editor-toolbar-context-plan.md \
  docs/exec-plans/index.md
git commit -m "feat: inline sql editor execution context"
```

Do not stage unrelated dirty files from outside this implementation.
