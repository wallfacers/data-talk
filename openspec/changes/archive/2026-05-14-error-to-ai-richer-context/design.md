## Context

当前 `error-to-ai-consulting` 功能已实现：SQL 错误面板有"问 AI"按钮，点击后将 ErrorContext（连接名、数据库、schema、SQL 语句、错误消息）格式化为 markdown 填入 PromptComposer。但缺少 Tab 来源标识和该连接可用的 database/schema 列表，导致 AI 无法给出具体可操作的建议。

数据已存在于 `SqlWorkbenchTab` 内存中：`tab.title`（Tab 名称）、`connectionTargetsByConnectionId`（每个连接的 `{ databases: string[], schemas: string[] }` 缓存）。只需打通数据通路。

## Goals / Non-Goals

**Goals:**
- 错误转 AI 的 markdown 消息中附加消息来源（Tab 名称），让 AI 知道问题来自哪里
- 当 database 或 schema 为 null 时，列出该连接可用的 database/schema 选项
- 补充操作指引文案，告诉用户在哪里修改设置

**Non-Goals:**
- 不改变"问 AI"按钮的交互流程（仍然是填入 composer，不自动发送）
- 不改变后端 API
- 不改变 UI 视觉样式
- 不处理非 SQL 编辑器的错误转 AI 场景（当前只有 SqlErrorResultPanel 调用）

## Decisions

### Decision 1: 数据传递方式 — props 透传

`tabTitle` 和 available targets 通过 props 逐层传递：`SqlWorkbenchTab` → `SqlResultPanel` → `SqlErrorResultPanel` → `ErrorContext`。

**Alternatives considered:**
- 从 Zustand store 读取：`connectionTargetsByConnectionId` 是局部 state（在 SqlWorkbenchTab 组件内部），不在 store 中。移入 store 会增加不必要的全局状态。props 透传更简洁，只涉及 3 个组件。
- 在 `SqlErrorResultPanel` 内部重新 fetch：浪费请求，数据已经缓存好了。

### Decision 2: markdown 模板结构

在现有模板基础上新增三个可选块（仅当有值时才渲染）：
1. **消息来源**（blockquote 引用格式）：`> 消息来源：查询编辑器 Tab「{tabTitle}」`
2. **可用选项**（列表格式）：仅当 database/schema 为 null 且有可用列表时渲染
3. **操作指引**（blockquote）：仅当 database/schema 为 null 时渲染，引导用户在 Tab 工具栏修改

**Alternatives considered:**
- 把可用选项放最前面：会让消息头重脚轻，核心错误信息被淹没
- 用表格展示可用选项：markdown 表格在小屏幕上可能不好渲染，简单列表更通用

### Decision 3: ErrorContext 字段扩展

```typescript
interface ErrorContext {
  // existing fields unchanged
  title: string
  connectionName?: string | null
  connectionKind?: string | null
  database?: string | null
  schema?: string | null
  statementText?: string | null
  errorMessage?: string | null
  extraContext?: string | null
  // new fields
  tabTitle?: string | null
  availableDatabases?: string[] | null
  availableSchemas?: string[] | null
}
```

所有新字段 optional 且 nullable，向后兼容。`useAskAIAboutError` 等消费者无需改动。

## Risks / Trade-offs

- **可用列表可能为空数组**：当 `fetchConnectionTargets` 失败或进行中时，`connectionTargetsByConnectionId[id]` 可能为 undefined。此时不渲染可用选项块，行为上退化为当前逻辑。→ 风险极低
- **markdown 消息变长**：增加了消息来源 + 可用选项 + 操作指引后，消息可能显著变长。但这对 AI 上下文有益——信息越充分，回答越准确。→ 可接受
- **Tab 标题可能包含特殊字符**：如果用户将 Tab 命名为含 markdown 特殊字符的字符串（如 `*`、`_`），直接插入可能导致格式错误。→ 写入 markdown 前需对 Tab 标题做转义

## Open Questions

- 非 SQL 编辑器的错误转 AI 场景（如连接失败、导入错误）后续是否需要同样的 Tab 来源信息？目前仅处理 SQL 错误面板。
