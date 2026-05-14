## Why

AI 聊天消息中的 SQL 代码块（L1/SELECT）有"执行 SQL"按钮，当前点击后会将 SQL 作为文本消息发送给 AI，由 AI 决定下一步——用户无法直接查看结果或编辑 SQL。这个间接路径增加了延迟和不可控性。应改为直接打开 SQL 查询编辑器，预填 SQL、复用当前 session 上下文、SELECT 自动运行。

## What Changes

- **"执行 SQL"按钮行为变更**：点击后不再 dispatch `CustomEvent` → 填入 composer → 发消息给 AI；改为调用 `openDirectSqlQueryEditorTab(sql)` 直接打开 `source: 'user'` 的查询编辑器
- **统一 composer 状态**：不管 composer 是否为空，点击"执行 SQL"都统一开编辑器，移除"追加到 composer + toast"分支
- **上下文复用**：编辑器继承当前 session 的 `connectionId` / `database` / `schema`（通过 `SessionDataContext`）
- **SELECT 自动运行**：打开编辑器后自动执行 SQL（利用已有 `direct-sql-auto-run-policy.ts` 的 L1 策略）
- **"解释 SQL"按钮不变**：仍走 AI 消息路径

## Capabilities

### New Capabilities

- `chat-sql-execute-direct`: AI 聊天中"执行 SQL"按钮的直接执行行为——点击后打开查询编辑器、预填 SQL、继承 session 上下文、SELECT 自动运行

### Modified Capabilities

（无现有 spec 需要修改。`sql-confirmation` spec 涉及 L2/L3 确认对话框，本变更不触及该流程。）

## Impact

- **前端代码**：
  - `client/src/features/session/prompt-composer.tsx` — 移除 `SQL_EXECUTE_EVENT` 监听及 `onExecute` 回调
  - `client/src/features/chat/components/markdown/markdown.tsx` — "执行 SQL"按钮 click handler 改为调用 `openDirectSqlQueryEditorTab`
  - `client/src/features/chat/components/markdown/sql-code-block.ts` — 可能需要 export SQL 文本或调整事件方式
  - `client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` — 已有，直接复用
  - `client/src/features/stage/utils/direct-sql-auto-run-policy.ts` — 可能需要确认 L1 auto-run 策略
- **测试**：`prompt-composer.tsx` 相关测试需更新
- **无后端变更、无数据库变更、无 API 变更**
