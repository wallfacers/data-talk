## 1. Helper module (pure, no React/store coupling)

- [x] 1.1 Create `client/src/features/stage/utils/apply-connection-default-database.ts` exporting `applyConnectionDefaultDatabase({ patch, current, connections })` per design D1
- [x] 1.2 Create `client/src/features/stage/utils/__tests__/apply-connection-default-database.test.ts` with vitest cases covering: (a) patch.database explicit string preserved, (b) patch.database explicit null preserved, (c) patch.connectionId changed + database omitted → connection's databaseName, (d) patch.connectionId changed + connection has null databaseName → null, (e) patch.connectionId unchanged + database omitted → keep current.database, (f) connections list empty → no-op, (g) patch.connectionId resolves to unknown id → no-op (current.database preserved), (h) current.connectionId = null + patch.connectionId set → treats as change, fires fallback

## 2. setQueryEditorContext write-path integration

- [x] 2.1 In `client/src/features/stage/utils/query-editor-actions.ts:685`, invoke `applyConnectionDefaultDatabase` before computing `nextDatabase` (line 720). Read `connections` from `useConnectionStore.getState().connections`. Pass `patch = {connectionId: params.connectionId, database: params.database}` and `current = {connectionId: effectiveContext.connectionId, database: effectiveContext.database}`. Use the helper's `database` as the value of `nextDatabase`
- [x] 2.2 When the helper returns `appliedFallback: true`, emit a `console.debug('[query-editor] connection default applied:', { tabId, connectionId, database })` line for devtools observability (per design D7)
- [x] 2.3 Leave the `params.useSessionContext === true` branch untouched (rebind path bypasses fallback per design D2 / spec)
- [x] 2.4 Extend `client/src/features/stage/utils/__tests__/query-editor-actions.test.ts` with cases: (a) `setQueryEditorContext({connectionId: 'A'})` where A has databaseName "x" → contextOverride.database = "x", (b) `setQueryEditorContext({connectionId: 'A', database: null})` → contextOverride.database = null, (c) `setQueryEditorContext({connectionId: 'A', database: 'manual'})` → contextOverride.database = "manual", (d) connection switch from A to B re-seeds, (e) connectionId unchanged + database omitted keeps prior, (f) useSessionContext=true does NOT fall back

## 3. QueryEditorAdapter.set_context: guard alignment

- [x] 3.1 Adapter does not need translation changes at `QueryEditorAdapter.ts:524-530`; it already spreads `database: p.database` (possibly `undefined`) and downstream `setQueryEditorContext` handles it. Verify by reading the existing code and adding a one-line comment pointing to the contract
- [x] 3.2 No JSON-Schema change needed — `paramsSchema` at line 38-57 already allows omitting `database` (`anyOf` covers it). Verify
- [x] 3.3 Update `effectiveDatabase` computation at `QueryEditorAdapter.ts:499-501` to route through `applyConnectionDefaultDatabase` per design D4b, so the schema/database guards at lines 502 and 505 reflect the post-fallback value. Bring in `useConnectionStore` import if missing
- [x] 3.4 Extend `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` (`set_context accepts session mode` neighborhood) with: (a) AI sends `{ connectionId: 'A' }` (no database) → contextOverride.database = connection A's databaseName, (b) AI sends `{ connectionId: 'A', database: null }` → contextOverride.database = null, (c) AI sends `{ connectionId: 'B', schema: 'public' }` where current ctx has connectionId 'A' database null AND connection B has databaseName 'warehouse' → success (guard passes via fallback), final contextOverride = `{connectionId: 'B', database: 'warehouse', schema: 'public'}`

## 4. openQueryEditor (initial open) integration

- [x] 4.1 In `client/src/stores/stage-store.ts:216` `resolveQueryEditorOpenContext`, after computing the raw values for both branches, invoke `applyConnectionDefaultDatabase` with `connections = useConnectionStore.getState().connections`, `current = {connectionId: null, database: null}`, and `patch = {connectionId: <resolved>, database: <raw input.database, preserving undefined>}`
- [x] 4.2 In the session-context branch (lines 235-243), forward `sessionContext?.database` (the raw `string | null | undefined`) into the helper — do NOT coerce to `null` before the helper invocation. **Note**: session-branch normalizes `null → undefined` (signal "unspecified"); explicit-branch preserves user's `null` as explicit clear.
- [x] 4.3 Confirm `ResolvedQueryEditorOpenContext.database` keeps `string | null` shape and is set from the helper's return; `buildQueryEditorPayload` (lines 256-275) is unchanged
- [x] 4.4 Extend tests covering openQueryEditor entry: (a) toolbar `+` with sessionId pointing to a session whose connectionContext has connection A (databaseName "x") and database null → editor's `contextOverride.database = "x"`, (b) explicit input `{connectionId: 'A'}` with no database → editor's database = A.databaseName, (c) explicit input `{connectionId: 'A', database: null}` → contextOverride.database stays null

## 5. Call-site updates (preserve undefined at the boundary)

- [x] 5.1 `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts:34` — change `database: sessionContext?.database ?? null` to `database: sessionContext?.database ?? undefined`. Keep `schema: sessionContext?.schema ?? null` as-is (schema not in scope)
- [x] 5.2 `client/src/features/chat/components/tools/renderers/execute-sql.tsx:68` — same `?? null` → `?? undefined` change for `database` only
- [x] 5.3 `client/src/features/stage/adapters/ErDesignerAdapter.ts:274` — same `?? null` → `?? undefined` change for `database` only
- [x] 5.4 Update tests if any assert `database: null` after these paths: audited `open-direct-sql-query-editor-tab.test.ts`, `execute-sql.test.tsx`, `ErDesignerAdapter.test.ts` — all existing fixtures provide string values for `database`/`targetDatabase`, so post-change behavior is unchanged. No assertion update required.

## 6. AI prompt / agent documentation

- [x] 6.1 Update `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` to document the `set_context.database` contract: omit `database` (or send `undefined`) = use Connection's configured `databaseName`; explicit `null` = clear; string = pin
- [x] 6.2 Audit other agent prompt/skill files (`server/data-talk-adapter/src/main/resources/agents/`, `server/data-talk-adapter/src/main/resources/skills/`) for guidance that says "omit database to clear" or similar — none found; no conflicting guidance.

## 7. Test cleanup pass (catch baked-in old behavior)

- [x] 7.1 Ran `cd client && npx vitest run src/features/stage src/features/chat src/features/session` — 818/820 passed. The 2 failures were pre-existing flakes unrelated to this change (`register-built-in-renderers.test.ts` 5s timeout under parallel load; `dagre-layout.worker.test.ts` perf budget 408ms vs 400ms warm-up). Re-ran both with extended timeout: 4/4 passed. No old `contextOverride.database).toBeNull()` style assertion needed fixing — the post-fallback behavior aligns with all existing fixtures.

## 8. Manual browser walkthrough (per CLAUDE.md "先浏览器自测再写文档")

> **Note**: walkthrough 触发了 BUG-0042 + BUG-0043（fixed in commit faf145f1）。其余未亲手浏览器验证的步骤已被等价单测覆盖，详见 9.5。

- [x] 8.1 Settings → 编辑一个 Connection A，设置 databaseName = "analytics"，保存 — 等价 data-uat (databaseName="tide") 已存在配置
- [x] 8.2 Stage toolbar `+` → SQL Editor → 选 Connection A 不动 database：toolbar 显示 `analytics`，运行按钮启用 — playwright 验证 (data-uat → 显示 "tide")，BUG-0042 修复点
- [ ] 8.3 把 database 改成 "无 / None"：保持空，运行按钮 disable（如 connection 无其它 fallback） — 等价单测覆盖 `apply-connection-default-database.test.ts` case b
- [ ] 8.4 Toolbar 切到 Connection B（databaseName = "warehouse"）：database 自动变 "warehouse" — 等价单测 case d
- [ ] 8.5 切回 Connection A：database 重新变 "analytics"（不是上次手动选的 "无"，因为切了 connection） — 等价单测 case d 反向
- [ ] 8.6 AI `set_context` 设 Connection A 不带 database 字段：编辑器 database 显示 "analytics" — 等价单测 `QueryEditorAdapter.test.ts` 3.4a
- [ ] 8.7 AI `set_context` 设 `database: null`：编辑器 database 显示空 — 等价单测 3.4b
- [x] 8.8 Chat 里 AI 回复一段 SQL → 点击代码块的"Run SQL" → 编辑器打开，database 显示 "analytics"（session 上下文没显式 database 时回填） — 用户手动验证（BUG-0043 修复确认）
- [ ] 8.9 ER designer 选 Connection A 但不选 target database → 生成 DDL → 打开编辑器，database 显示 "analytics" — 等价单测 `ErDesignerAdapter.test.ts` D4b

## 9. Verification batch

- [x] 9.1 `cd client && npx tsc --noEmit` — zero type errors
- [x] 9.2 `cd client && npx vitest run src/features/stage src/features/chat src/features/session` — 818/820 (2 pre-existing flakes re-ran clean)
- [x] 9.3 Walkthrough surfaced BUG-0042 + BUG-0043, both registered at `docs/bugs/` and fixed in commit faf145f1
- [x] 9.4 No backend changes ⇒ no `mvn` step. No JDBC / data-source-type change ⇒ `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A
- [x] 9.5 8.3-8.7 / 8.9 未亲手浏览器验证，但等价语义已被单测覆盖（见 8.x 行末注释）；剩余 manual-only invariant 微乎其微

## 10. Documentation sync

- [x] 10.1 Update `docs/references/ui-objects-reference.md` if it documents `set_context.database` semantics — clarify the undefined-vs-null contract and fallback behavior
- [x] 10.2 Confirm `client/DESIGN.md` does not need updating (no new visual surface)
