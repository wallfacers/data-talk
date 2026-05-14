## Context

当前 SQL 编辑器报错后，`SqlErrorResultPanel` 仅静态展示 markdown 格式的错误信息，无任何交互按钮。用户想要让 AI 帮助分析错误时，需要手动复制错误信息、切换 session、粘贴到 `PromptComposer`、补充数据库/连接等上下文，流程割裂且低效。

同时，`UserBubble` 组件仅渲染纯文本（`HighlightedText` = passthrough），不支持 markdown。发送 markdown 格式的咨询消息后无法正确展示。

现有基础设施已经部分就绪：
- `PromptComposer` 已有 `SQL_EXPLAIN_EVENT` 自定义 DOM 事件模式用于程序化填充文本
- `Markdown` 组件已在 AI 消息气泡中使用（marked + DOMPurify + morphdom）
- `sqlWorkbenchStore` 的 `resolvedContext` 和 `lastRequest` 包含连接、数据库、schema、SQL 等上下文
- 后端 `SqlExecuteService` 已返回 markdown 格式的错误信息

**Design Inputs (from client/DESIGN.md):**
- 按钮使用 `accent.primary` 语义 token 作为主色调
- 错误面板使用 `status.dangerSurface` + `status.danger` 语义 token
- 消息气泡：user surface 使用 `bg.subtle`，assistant surface 使用 `bg.canvas`
- 交互反馈：hover/active/focus-visible 使用 `interaction.*` token
- 按钮文本使用 `ui-sm` typography（13px, 400 weight）
- 不做饱和色气泡、不做玻璃态、不做装饰性动画

## Goals / Non-Goals

**Goals:**
- SQL 错误面板新增"问 AI"按钮，一键将错误上下文填充到 `PromptComposer`
- 创建通用 `useAskAIAboutError` hook，任何模块可复用
- `UserBubble` 支持 markdown 渲染，复用现有 `Markdown` 组件
- 错误上下文以结构化 markdown 模板生成，用户可编辑后发送

**Non-Goals:**
- 不自动发送消息（用户始终需要手动审核并点击发送）
- 不修改后端 API（错误信息已包含足够上下文）
- 不修改 `TextPart` 数据结构
- 不改变 AI 消息气泡的渲染逻辑

## Decisions

### 1. 填充输入框而非直接发送

**选择**：点击"问 AI"后将 markdown 填充到 `PromptComposer`，用户审核后可编辑并手动发送。

**理由**：用户可能需要补充额外信息（如"帮我看看为什么这个查询慢"），直接发送剥夺了编辑机会。`PromptComposer` 已有的 `SQL_EXPLAIN_EVENT` 模式可作为参考实现。

**替代方案考虑**：直接发送（跳过输入框）更快捷，但用户失去审核和补充上下文的能力，且需要处理 session 不存在、streaming 中等边界情况。

### 2. 通用 hook 而非组件内联实现

**选择**：创建 `useAskAIAboutError` hook，接受 error context 对象，返回 `{ askAI, isAvailable }`。

**理由**：提案要求"其他地方的报错也得这样的风格和流程"。通用 hook 允许任何错误面板（SQL 执行、连接失败、数据导入、ER 图等）以一致的方式触发 AI 咨询。hook 内部负责：
1. 生成标准 markdown 模板
2. 通过 `useSessionStore.setComposerDraft()` 或类似机制填充到输入框
3. 确保目标 session 存在

**替代方案考虑**：每个错误面板内联实现按钮逻辑会导致重复代码和风格不一致。

### 3. UserBubble markdown 渲染策略

**选择**：将 `UserBubble` 中的 `HighlightedText`（纯文本 passthrough）替换为 `Markdown` 组件。不区分"问 AI"消息和普通消息——所有用户消息统一使用 markdown 渲染。

**理由**：
- 简化实现，无需在 `TextPart` 上添加 display kind 标记
- 纯文本是 markdown 的子集，向后兼容
- `Markdown` 组件已有 sanitize（DOMPurify），安全性无退化
- 用户偶尔发送包含代码块/列表的消息也能正确渲染

**风险**：用户输入中意外包含 markdown 语法（如 `*text*`）会被渲染为斜体。这是可接受的行为变化——markdown 渲染对于技术用户来说是预期行为。

**替代方案考虑**：仅对带 display kind 标记的消息启用 markdown 渲染。更保守但增加了 `TextPart` 模型复杂度，且"问 AI"产生的 markdown 消息和普通用户消息在视觉上不一致。

### 4. 错误上下文 markdown 模板

**选择**：前端组装 markdown，而非依赖后端返回的错误信息。

模板结构：
```markdown
**SQL 执行报错**

- **连接**: {connectionName} ({connectionKind})
- **数据库**: {database}
- **Schema**: {schema}
- **执行的 SQL**:
```sql
{statementText}
```
- **错误信息**:
```
{errorMessage}
```
```

**理由**：
- 后端错误信息仅包含错误消息+连接详情，缺少 SQL 文本、schema 等前端已知的上下文
- 前端 store（`sqlWorkbenchStore`、`connectionStore`）持有完整信息
- 模板由 hook 生成，其他模块可传入不同的 context 结构使用相同模板

### 5. 现有填充机制的复用

**选择**：扩展现有的 `composerRestoreDraft` 机制，增加一个新的方法 `setComposerDraftAndFocus(sessionId, text)` 来填充文本并聚焦输入框。

**理由**：现有的 `SQL_EXPLAIN_EVENT` 是基于 window 事件的模式，适合跨组件通信但耦合度较高。新增一个 store 方法更清晰，且不需要 `PromptComposer` 注册额外的事件监听器。

## Risks / Trade-offs

- **markdown 注入风险**：用户输入被渲染为 markdown → 已由 `Markdown` 组件的 DOMPurify sanitize 缓解
- **纯文本意外渲染**：用户输入 `*text*` 会变成斜体 → 对于数据平台的技术用户是可接受的行为变化
- **hook 依赖 session store**：`useAskAIAboutError` 需要活跃 session 才能填充输入框 → 需处理无活跃 session 的情况（创建新 session 或显示提示）

## Open Questions

- 是否需要将填充的文本同时作为草稿持久化（刷新后恢复），还是仅一次性填充？（建议：仅一次性填充，避免草稿覆盖用户原有文本）
