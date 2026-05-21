## Context

当前 AI 聊天消息中的 SQL 代码块提供"执行 SQL"按钮（仅 L1/SELECT）。点击后通过 `CustomEvent('datatalk.sql.execute')` 将 SQL 文本发送到 `InnerComposer`，composer 填入 SQL 并作为普通消息发送给 AI。这条路径延迟高、用户无法编辑 SQL、无法直接看到查询结果。

已有基础设施：`openDirectSqlQueryEditorTab()` 函数可直接打开 `source: 'user'` 的查询编辑器 tab，支持预填 SQL、继承 session 上下文、auto-run 策略。

## Goals / Non-Goals

**Goals:**
- 点击"执行 SQL"后直接打开查询编辑器，预填 SQL，复用当前 session 上下文
- SELECT 自动运行
- 不管 composer 是否为空，统一行为

**Non-Goals:**
- 不改变"解释 SQL"按钮的行为（仍走 AI 消息路径）
- 不改变 L2/L3 风险级别的按钮渲染逻辑（它们本来就不显示"执行 SQL"）
- 不改变 SQL 编辑器本身的任何行为

## Decisions

### D1: 使用 `openDirectSqlQueryEditorTab` 而非新建入口

**选择**: 复用已有 `openDirectSqlQueryEditorTab()` 函数。

**理由**: 该函数已经做了所有需要的事——打开 `source: 'user'`、`entryMode: 'direct_sql'` 的编辑器 tab，继承 `sessionId`/`connectionId`/`database`/`schema`，支持 auto-run。无需新增代码路径。

**备选**: 在 `markdown.tsx` 中直接调用 `useStageStore.getState().openQueryEditor()`——但这需要手动解析 session 上下文，且绕过了 `openDirectSqlQueryEditorTab` 中已有的错误处理（如无连接时抛错）。

### D2: 事件监听从 composer 迁移到 markdown click handler

**选择**: 移除 `prompt-composer.tsx` 中的 `SQL_EXECUTE_EVENT` 监听，在 `markdown.tsx` 的 click handler 中直接调用 `openDirectSqlQueryEditorTab`。

**理由**: "执行 SQL"不再需要与 composer 交互，无需跨组件事件桥接。直接在 click handler 中处理更简单。

**实现**: `markdown.tsx` 的 `onClick` 中 `[data-slot="sql-execute"]` 分支改为调用 `openDirectSqlQueryEditorTab({ sessionId, connectionId, sql, autoRun: true })`。需要从当前 session 获取 `sessionId` 和 `connectionId`——通过 store 读取（`useSessionStore.getState().activeSessionId` + `useSessionStore.getState().dataContextBySession`）。

### D3: 移除 `SQL_EXECUTE_EVENT` 常量及所有监听

**选择**: 移除 `sql-code-block.ts` 中的 `SQL_EXECUTE_EVENT` export 和 `prompt-composer.tsx` 中的 `onExecute` 监听。

**理由**: 该事件不再有任何消费者（click handler 直接调用函数），保留只会造成困惑。`SQL_EXPLAIN_EVENT` 保留，因为"解释 SQL"仍走事件路径。

### D4: Auto-run 策略直接传 `true`

**选择**: 调用 `openDirectSqlQueryEditorTab` 时传 `autoRun: true`。

**理由**: "执行 SQL"按钮只对 L1/SELECT 渲染（`sql-code-block.ts` 第 57 行 `if (risk === 'L1')`），所以到这一步的 SQL 一定是 L1。无需再调 `shouldAutoRunDirectSql()` 判断。

## Risks / Trade-offs

- **[markdown.tsx 中读 store 的方式]** → markdown click handler 在事件回调中，不在 React 渲染周期内。使用 `useSessionStore.getState()` / `useStageStore.getState()` 读取最新值是 Zustand 的标准用法，不会有时效问题。
- **[无 session 时]** → `openDirectSqlQueryEditorTab` 要求 `connectionId` 非空。如果用户未选择连接，会抛错。需要增加防御：无连接时 toast 提示用户先选择连接。
- **[已有同名 SQL 的 tab]** → `openDirectSqlQueryEditorTab` 使用 `openMode: 'always_new'`，每次都新建 tab。与现有行为一致，可接受。
