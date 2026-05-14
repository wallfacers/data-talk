## 1. Helper module (pure, no React/store coupling)

- [ ] 1.1 Create `client/src/features/stage/utils/apply-connection-default-database.ts` exporting `applyConnectionDefaultDatabase({ patch, current, connections })` per design D1
- [ ] 1.2 Create `client/src/features/stage/utils/__tests__/apply-connection-default-database.test.ts` with vitest cases covering: (a) patch.database explicit string preserved, (b) patch.database explicit null preserved, (c) patch.connectionId changed + database omitted → connection's databaseName, (d) patch.connectionId changed + connection has null databaseName → null, (e) patch.connectionId unchanged + database omitted → keep current.database, (f) connections list empty → no-op, (g) patch.connectionId resolves to unknown id → no-op

## 2. setQueryEditorContext write-path integration

- [ ] 2.1 In `client/src/features/stage/utils/query-editor-actions.ts:685`, invoke `applyConnectionDefaultDatabase` before computing `nextDatabase` (lines 719–721). Read `connections` from `useConnectionStore.getState().connections`. Pass `patch = {connectionId: params.connectionId, database: params.database}` and `current = {connectionId: effectiveContext.connectionId, database: effectiveContext.database}`. Use the helper's `database` as the value of `nextDatabase`
- [ ] 2.2 When the helper returns `appliedFallback: true`, emit a `console.debug('[query-editor] connection default applied:', { tabId, connectionId, database })` line for devtools observability (per design D7)
- [ ] 2.3 Leave the `params.useSessionContext === true` branch untouched (rebind path bypasses fallback per design D2 / spec)
- [ ] 2.4 Extend `client/src/features/stage/utils/__tests__/query-editor-actions.test.ts` with cases: (a) `setQueryEditorContext({connectionId: 'A'})` where A has databaseName "x" → contextOverride.database = "x", (b) `setQueryEditorContext({connectionId: 'A', database: null})` → contextOverride.database = null, (c) `setQueryEditorContext({connectionId: 'A', database: 'manual'})` → contextOverride.database = "manual", (d) connection switch from A to B re-seeds, (e) connectionId unchanged + database omitted keeps prior, (f) useSessionContext=true does NOT fall back

## 3. QueryEditorAdapter.set_context: guard alignment

- [ ] 3.1 No translation change needed at `QueryEditorAdapter.ts:524-530` — adapter already spreads `database: p.database` (possibly `undefined`) and downstream `setQueryEditorContext` handles it. Verify by reading the existing code and adding a comment pointing to the contract
- [ ] 3.2 No schema change needed — `paramsSchema` at line 38-57 already allows omitting `database` (`anyOf` covers it). Verify
- [ ] 3.3 Update `effectiveDatabase` computation at `QueryEditorAdapter.ts:499-501` to route through `applyConnectionDefaultDatabase` per design D4b, so the schema/database guards at lines 502 and 505 reflect the post-fallback value. Bring in `useConnectionStore` import if missing
- [ ] 3.4 Extend `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` (`set_context accepts session mode` neighborhood) with: (a) AI sends `{ connectionId: 'A' }` (no database) → contextOverride.database = connection A's databaseName, (b) AI sends `{ connectionId: 'A', database: null }` → contextOverride.database = null, (c) AI sends `{ connectionId: 'B', schema: 'public' }` where current ctx has connectionId 'A' database null AND connection B has databaseName 'warehouse' → success (guard passes via fallback), final contextOverride = `{connectionId: 'B', database: 'warehouse', schema: 'public'}`

## 4. openQueryEditor (initial open) integration

- [ ] 4.1 In `client/src/stores/stage-store.ts:216` `resolveQueryEditorOpenContext`, after computing the raw `connectionId` / `database` / `schema`, invoke `applyConnectionDefaultDatabase` with `connections = useConnectionStore.getState().connections`, `current = {connectionId: null, database: null}`, and a patch reflecting "caller specified database or not"
- [ ] 4.2 Internal contract: thread a `databaseSpecified: boolean` through `ResolvedQueryEditorOpenContext` (or use the input shape directly: `'database' in input` for the explicit-connectionId branch; for the session-context branch, treat session.database as "specified" only when non-null is sourced from session — design D3)
- [ ] 4.3 Extend tests covering openQueryEditor entry: (a) toolbar "+" with sessionId pointing to a session whose connectionContext has connection A (databaseName "x") and database null → editor's `contextOverride.database = "x"`, (b) explicit input `{connectionId: 'A'}` with no database → editor's database = A.databaseName, (c) explicit input `{connectionId: 'A', database: null}` → contextOverride.database stays null

## 5. AI prompt / agent documentation

- [ ] 5.1 Update `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (or the canonical agent prompt file — locate first) to document the new `set_context.database` contract: omit = use Connection's configured default; explicit `null` = clear
- [ ] 5.2 Audit any other agent prompt/skill files (`server/data-talk-adapter/src/main/resources/agents/`, `server/data-talk-adapter/src/main/resources/skills/`) for guidance that says "omit database to clear" or similar — update or remove

## 6. Test cleanup pass (catch baked-in old behavior)

- [ ] 6.1 Run `cd client && npx vitest run src/features/stage` and triage any failures specifically caused by old assertions like `expect(...contextOverride.database).toBeNull()` where the new behavior should produce a fallback value. Fix each by either: (a) updating the test fixture connections to set `databaseName: null` if the test really wants null, or (b) updating the assertion to expect the new fallback value
- [ ] 6.2 Same audit pass for `src/features/session` if any session-driven SQL editor tests touch context override writes

## 7. Manual browser walkthrough (per CLAUDE.md "先浏览器自测再写文档")

- [ ] 7.1 Settings → 编辑一个 Connection A，设置 databaseName = "analytics"，保存
- [ ] 7.2 Stage toolbar "+" → SQL Editor → 在 toolbar 选 Connection A，不动 database：predefined `analytics` 出现，运行按钮启用
- [ ] 7.3 Toolbar 把 database 改成 "无 / None"：保持空，运行按钮 disable（如 connection 没有其它 fallback）
- [ ] 7.4 Toolbar 切换 Connection 到 B（databaseName = "warehouse"）：database 自动变 "warehouse"
- [ ] 7.5 切回 Connection A：database 重新变 "analytics"（不是上一次手动选的 "无"，因为切了 connection）
- [ ] 7.6 AI 触发 `set_context` 设 Connection A 不带 database 字段：编辑器 database 显示 "analytics"
- [ ] 7.7 AI 触发 `set_context` 设 `database: null`：编辑器 database 显示空

## 8. Verification batch

- [ ] 8.1 `cd client && npx tsc --noEmit` — zero type errors
- [ ] 8.2 `cd client && npx vitest run src/features/stage src/features/session` — full surface green
- [ ] 8.3 If any open BUG was discovered during walkthrough 7.x, register at `docs/bugs/` per CLAUDE.md BUG tracking gate
- [ ] 8.4 No backend changes ⇒ no `mvn` step. No JDBC / data-source-type change ⇒ `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A

## 9. Documentation sync

- [ ] 9.1 Update `docs/references/ui-objects-reference.md` if it documents `set_context.database` semantics — clarify the undefined-vs-null contract
- [ ] 9.2 Confirm `client/DESIGN.md` does not need updating (no new visual surface)
