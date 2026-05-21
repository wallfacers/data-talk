## 1. 移除旧路径

- [x] 1.1 从 `sql-code-block.ts` 移除 `SQL_EXECUTE_EVENT` 常量的 export（保留 `SQL_EXPLAIN_EVENT`）
- [x] 1.2 从 `prompt-composer.tsx` 的 `InnerComposer` useEffect 中移除 `SQL_EXECUTE_EVENT` 的 `addEventListener` / `removeEventListener` 及 `onExecute` 回调（保留 `SQL_EXPLAIN_EVENT` / `onExplain`）

## 2. 实现"执行 SQL"直接开编辑器

- [x] 2.1 在 `markdown.tsx` 的 `onClick` handler 中，将 `[data-slot="sql-execute"]` 分支从 `window.dispatchEvent(new CustomEvent(SQL_EXECUTE_EVENT, ...))` 改为调用 `openDirectSqlQueryEditorTab({ sessionId, connectionId, sql: content, autoRun: true })`，其中 `sessionId` 和 `connectionId` 从 `useSessionStore.getState()` 读取当前 session 的 data context
- [x] 2.2 增加防御：当 `connectionId` 为空时，toast 提示用户先选择连接，不调用 `openDirectSqlQueryEditorTab`

## 3. 更新测试

- [ ] 3.1 更新 `sql-code-block.ts` 相关测试：移除 `SQL_EXECUTE_EVENT` 的引用
- [ ] 3.2 更新 `prompt-composer.tsx` 相关测试：移除 `onExecute` / `SQL_EXECUTE_EVENT` 的测试用例
- [ ] 3.3 为 `markdown.tsx` 的"执行 SQL"新行为添加测试：验证调用 `openDirectSqlQueryEditorTab` 而非 dispatch event
- [ ] 3.4 运行 `cd client && npx tsc --noEmit` 确认零类型错误
- [ ] 3.5 运行 `cd client && npx vitest run` 确认所有测试通过
