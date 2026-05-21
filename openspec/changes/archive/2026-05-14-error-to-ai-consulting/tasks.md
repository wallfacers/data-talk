## 1. 通用"问 AI" Hook

- [x] 1.1 创建 `ErrorContext` 类型定义，包含 `title`, `connectionName`, `connectionKind`, `database`, `schema`, `statementText`, `errorMessage`, `extraContext` 字段（可选字段允许 null）
- [x] 1.2 实现 `buildErrorMarkdown(errorContext: ErrorContext): string` 函数，按 spec 模板生成 markdown，空值字段自动省略或显示"未设置"
- [x] 1.3 在 `useSessionStore` 中新增 `setComposerText(sessionId: string, text: string)` action，将文本设置到对应 session 的 composer draft 并触发 focus
- [x] 1.4 创建 `useAskAIAboutError` hook：接收 `ErrorContext`，返回 `{ askAI: () => void, isAvailable: boolean }`。`isAvailable` 在无活跃 session 或正在 streaming 时为 false。`askAI()` 调用 `buildErrorMarkdown` 生成文本，通过 `setComposerText` 填充到活跃 session 的 composer

## 2. SQL 错误面板"问 AI"按钮

- [x] 2.1 在 `SqlErrorResultPanel` 中添加"问 AI"按钮（使用 `Sparkles` 或 `MessageCircle` 图标 + "问 AI" 文本），放置于错误信息卡片底部
- [x] 2.2 按钮使用 `accent.primary` 语义 token 着色，hover/active 状态使用 `interaction.hover` / `interaction.active`
- [x] 2.3 `SqlErrorResultPanel` 接收新的 props：`connectionName`, `connectionKind`, `database`, `schema`（从 `sqlWorkbenchTabState.resolvedContext` 传入）
- [x] 2.4 在 `sql-result-panel.tsx` 中组装 `ErrorContext`（从 tab state 读取 `resolvedContext`、`lastRequest`、当前 error result），传入 `SqlErrorResultPanel`
- [x] 2.5 "问 AI"按钮仅在 `isAvailable && resolvedContext.connectionId != null` 时渲染和可用

## 3. UserBubble Markdown 渲染

- [x] 3.1 在 `UserBubble` 中将 `HighlightedText` 替换为 `Markdown` 组件，移除 `HighlightedText` 定义
- [x] 3.2 `Markdown` 组件使用 `cacheKey={`user:${info.id}`}` 保持缓存隔离
- [x] 3.3 验证现有功能不受影响：纯文本消息、bang-query 模式（terminal 图标 + rerun 按钮）、失败消息（红色边框 + retry/delete）、pending 消息（opacity-85）、复制按钮、时间戳
- [x] 3.4 确保用户消息气泡中的 `Markdown` 不渲染 AI 专属功能（SQL 执行按钮、图表渲染、dashboard 渲染等）——通过 `Markdown` 组件的已有 props 或新增 `disableActions` prop 控制

## 4. 验证

- [x] 4.1 运行 `cd client && npx tsc --noEmit` 确认零类型错误
- [x] 4.2 手动验证：在 SQL 编辑器中执行一个会报错的 SQL，点击"问 AI"按钮，确认 composer 被正确填充，发送后用户气泡正确渲染 markdown
