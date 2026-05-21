## Why

当用户在 SQL 编辑器 Tab 中点击"问 AI"按钮时，AI 收到的上下文缺少三个关键信息：**消息来源**（哪个 Tab）、**可用选项**（连接有哪些 database/schema）、**操作指引**（在哪里修改设置）。导致 AI 只能给出通用答案（"请选择 database/schema"），而无法给出具体可操作的建议（"你的 TiDB-prod 连接有 analytics/test/production 三个库，请在「用户查询」Tab 工具栏选择"）。数据已经在 `SqlWorkbenchTab` 内存中（`tab.title`、`connectionTargetsByConnectionId`），只需打通数据通路。

## What Changes

- `ErrorContext` 新增 `tabTitle`、`availableDatabases`、`availableSchemas` 字段
- `buildErrorMarkdown` 模板在消息顶部新增"消息来源"块，当 database/schema 未设置时补充可用选项列表和操作指引
- `SqlErrorResultPanel` / `SqlResultPanel` 透传 `tabTitle` 和 `availableTargets`
- `SqlWorkbenchTab` 将 `tab.title` 和已缓存的 `connectionTargetsByConnectionId` 向下传递给 `SqlResultPanel`

## Capabilities

### New Capabilities

- `error-to-ai-tab-context`: 错误转 AI 咨询时，消息中附加 Tab 来源标识和该连接可用的 database/schema 列表

### Modified Capabilities

- `error-to-ai-consulting`: ErrorContext 结构和 markdown 模板扩展，增加消息来源、可用选项、操作指引三个部分

## Design Inputs

依据 `client/DESIGN.md`：
- 此变更只涉及数据通路（props 透传 + markdown 模板字符串），不涉及视觉/布局/组件样式变更，无新增 semantic token 需求
- 现有"问 AI"按钮已使用 `accent.primary` token 和 `interaction.hover`/`active` 语义，不变

## Impact

- `client/src/features/chat/error-to-ai/error-to-ai-context.ts` — ErrorContext 接口 + buildErrorMarkdown 模板
- `client/src/features/stage/components/sql-error-result-panel.tsx` — 接收新 props
- `client/src/features/stage/components/sql-result-panel.tsx` — 透传新 props
- `client/src/features/stage/components/sql-workbench-tab.tsx` — 传递 tab.title 和 connectionTargets
- `client/src/i18n/messages.ts` — 新增 3-4 条 i18n 文案
