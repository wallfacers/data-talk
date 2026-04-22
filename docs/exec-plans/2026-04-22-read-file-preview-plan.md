# Read File Preview In Session Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `read` 工具的文件型输出增加专属聊天渲染与“同步到工作台”入口，并在当前会话的 Stage 中以 `file_preview` Tab 承载只读高亮预览。

**Architecture:** 前端新增一个 `read` 专属 renderer，将 `state.output` 从固定的 `<path>/<type>/<content>` 字符串解析为结构化文件预览数据；点击同步按钮时，通过 session-scoped Stage tab helper 创建或聚焦 `file_preview` Tab。工作台内容区新增 `FilePreviewTab`，复用 Monaco 主题体系以只读模式高亮正文，并保持路径/文件属性为普通文本。

**Tech Stack:** React 19, TypeScript, Zustand, Stage store, Vitest, Monaco Editor, `@monaco-editor/react`

---

## Spec Mapping

- [2026-04-22-read-file-preview-design.md](../product-specs/2026-04-22-read-file-preview-design.md)
  - §3/§6：新增 `read` 专属 renderer、session `file_preview` tab helper、Stage 内容分发
  - §4/§5：解析 `<path>/<type>/<content>`、生成稳定 `sourceKey`、重复点击只聚焦不重复开 tab
  - §7：正文高亮复用 Monaco，语言按文件后缀映射，未知后缀回退 `plaintext`
  - §8/§10：补齐 renderer / stage helper / file preview tab 三层测试，并完成 `npx tsc --noEmit`

## File Structure

### Chat / Tool Rendering

- Create: `client/src/features/chat/components/tools/renderers/read-file-output.ts`
- Create: `client/src/features/chat/components/tools/__tests__/read-file-output.test.ts`
- Create: `client/src/features/chat/components/tools/renderers/read-file.tsx`
- Create: `client/src/features/chat/components/tools/__tests__/read-file.test.tsx`
- Modify: `client/src/features/chat/components/tools/renderers/index.ts`

### Stage / Session Preview Tabs

- Create: `client/src/features/stage/utils/open-or-focus-file-preview-tab.ts`
- Create: `client/src/features/stage/utils/open-or-focus-file-preview-tab.test.ts`
- Create: `client/src/features/stage/components/file-preview-tab.tsx`
- Create: `client/src/features/stage/components/file-preview-tab.test.tsx`
- Create: `client/src/features/stage/components/monaco-theme.ts`
- Create: `client/src/features/stage/components/stage-tab-content.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`

### Docs / Verification

- Modify: `docs/exec-plans/2026-04-22-read-file-preview-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/2026-04-22-read-file-preview-design.md`

## Parallelization Notes

- Task 1 必须先完成，因为后续聊天 renderer 与 Stage helper 都依赖统一的解析/映射规则。
- Task 2 完成后，Task 3（Stage helper + routing）与 Task 4（Monaco 主题共享 + `FilePreviewTab`）可以并行执行，写集不重叠。
- Task 5 负责把聊天入口接到 Stage，依赖 Task 1/3/4 全部完成后再做。
- 最终只做一轮 consolidated verification，符合仓库“并行批次后统一验证”的要求。

## Task 1: 固定 `read` 文件输出解析与语言映射

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/read-file-output.ts`
- Create: `client/src/features/chat/components/tools/__tests__/read-file-output.test.ts`

- [x] Step 1: 先写 failing tests，覆盖：
  - 正常提取 `<path>`、`<type>`、`<content>`
  - 缺少 `<content>` 时返回 `null`
  - `content` 保留原始换行
  - `callID` 缺失时用 `${messageID}:${part.id}` 回退生成 `sourceKey`
  - 常见后缀映射到 `markdown/json/yaml/typescript/javascript/java/sql/shell/xml/ini/toml/plaintext`
- [x] Step 2: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/read-file-output.test.ts`，确认先红。
- [x] Step 3: 实现 `read-file-output.ts`，提供最小稳定 API：
  - `export type FilePreviewPayload = { sourceKey: string; filePath: string | null; filename: string; fileType: string; content: string; truncated: boolean; language: string }`
  - `parseReadFileOutput(raw: string): { filePath: string | null; fileType: string; content: string } | null`
  - `inferFilePreviewLanguage(filenameOrPath: string | null): string`
  - `buildReadFilePreviewPayload(input): FilePreviewPayload | null`
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/read-file-output.test.ts`，确认转绿。

## Task 2: 建立 session `file_preview` tab 打开/聚焦 helper

**Files:**
- Create: `client/src/features/stage/utils/open-or-focus-file-preview-tab.ts`
- Create: `client/src/features/stage/utils/open-or-focus-file-preview-tab.test.ts`

- [x] Step 1: 写 failing tests，覆盖：
  - 当前 session 下首次同步会创建 `scope='session'`、`type='file_preview'` 的 tab
  - 同一 `sourceKey` 二次同步只 `focusTab`
  - 无 `sessionId` 时抛出明确错误
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/utils/open-or-focus-file-preview-tab.test.ts`，确认先红。
- [x] Step 3: 实现 helper，复用现有 `StageState.openTab/focusTab`，只在当前 session 的 `tabsBySession` 中按 `payload.sourceKey` 去重。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/utils/open-or-focus-file-preview-tab.test.ts`，确认转绿。

## Task 3: 让 Stage 能承载 `file_preview` tab

**Files:**
- Create: `client/src/features/stage/components/stage-tab-content.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`
- Modify: `client/src/features/stage/components/stage-tab-bar.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [x] Step 1: 先补 failing tests，覆盖：
  - `StageTabContent` 在 active tab 为 `file_preview` 时渲染 `FilePreviewTab`
  - `StageWindow` 在有 `file_preview` tab 时不落空态
  - `StageTabBar` 为 `file_preview` 使用文件图标，而不是默认 sparkle 图标
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/components/stage-tab-content.test.tsx src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/components/stage-window.test.tsx`，确认先红。
- [x] Step 3: 修改 `StageTabContent` 做 `query_editor/file_preview` 分发；修改 `StageTabBar` 的 `getTabIcon()`，为 `file_preview` 增加 `FileTextIcon` 分支；更新 Stage 相关测试最小通过。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/components/stage-tab-content.test.tsx src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/components/stage-window.test.tsx`，确认转绿。

## Task 4: 共享 Monaco 主题并实现 `FilePreviewTab`

**Files:**
- Create: `client/src/features/stage/components/monaco-theme.ts`
- Create: `client/src/features/stage/components/file-preview-tab.tsx`
- Create: `client/src/features/stage/components/file-preview-tab.test.tsx`
- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`

- [x] Step 1: 先写 failing tests，覆盖：
  - `FilePreviewTab` 正确显示语言 / 类型 / 截断 Tag
  - 完整路径作为普通文本显示
  - 正文区只展示 `<content>`，不重复出现结构标签
  - `truncated=true` 时展示提示 Tag
- [x] Step 2: 运行 `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx`，确认先红。
- [x] Step 3: 抽出 `monaco-theme.ts`，让 `SqlMonacoEditor` 改为复用共享主题注册；实现 `FilePreviewTab`，使用 `@monaco-editor/react` 只读模式加载 `payload.language` 与 `payload.content`。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`，确认 `FilePreviewTab` 转绿且未破坏现有 SQL workbench 测试。

## Task 5: 接通聊天 `read` renderer 到 Stage 入口

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/read-file.tsx`
- Create: `client/src/features/chat/components/tools/__tests__/read-file.test.tsx`
- Modify: `client/src/features/chat/components/tools/renderers/index.ts`

- [x] Step 1: 写 failing tests，覆盖：
  - `tool=read` 且输出可解析为文件时显示“同步到工作台”按钮
  - 解析失败时回退普通工具输出
  - 点击按钮会调用 `openStage(sessionId)` 并通过 helper 创建/聚焦 `file_preview` tab
  - 非 `completed` 状态或非文件输出时不显示按钮
- [x] Step 2: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/read-file.test.tsx`，确认先红。
- [x] Step 3: 实现 `read-file.tsx`，内部复用 Task 1 的解析 helper 与 Task 2 的 open/focus helper，并在 `renderers/index.ts` 为 `read` 注册专属 renderer。
- [x] Step 4: 重新运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/read-file.test.tsx src/features/chat/components/tools/__tests__/tool-registry.test.ts`，确认转绿。

执行过程中根据 spec / quality review 反馈，追加收紧了 shipped parser 契约：`read-file-output.ts` 最终仅接受固定有序的 `<path>...</path><type>file</type><content>...</content>` 形态，要求 `path` 非空且必须能解析出 basename；否则聊天 renderer 安全回退到 `GenericTool`。

## Task 6: 批量验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-22-read-file-preview-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/2026-04-22-read-file-preview-design.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/features/chat/components/tools/__tests__/read-file-output.test.ts src/features/chat/components/tools/__tests__/read-file.test.tsx src/features/stage/utils/open-or-focus-file-preview-tab.test.ts src/features/stage/components/stage-tab-content.test.tsx src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/components/file-preview-tab.test.tsx src/features/stage/components/stage-window.test.tsx`
  Result: 为覆盖共享 Monaco 主题抽取带来的回归风险，实际额外执行了 `src/features/stage/components/sql-workbench-tab.test.tsx`；最终 `8` 个 test files、`62` 个 tests 全绿。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`
  Result: 通过。
- [x] Step 3: 将本计划中的 checkbox 全部按实际结果勾完，并补充最终命令结果。
- [x] Step 4: 完成后把 `docs/exec-plans/index.md` 中本计划从 Active 移到 Completed；若实现与 spec 有实质偏差，再同步更新 [2026-04-22-read-file-preview-design.md](../product-specs/2026-04-22-read-file-preview-design.md) 状态和说明。

## Decisions

- 本计划故意不触碰后端协议、不引入 ontology artifact，不把 `read` 结果提升为 `Artifact.kind='file'`。
- `file_preview` 使用 session-scoped Stage tab，而不是 workspace-scoped tab，保持与用户已确认的交互模型一致。
- 文件正文高亮复用 Monaco，而不是为聊天/Stage 新增第二套高亮引擎。
- `read` 输出解析仅支持固定的 `<path>/<type>/<content>` 形态；如果输出形态变化，renderer 必须安全回退而不是硬崩。

## Self-Review

- Spec coverage: 聊天入口、解析 helper、session tab 打开/聚焦、Stage 分发、只读高亮预览、Tag 信息、重复点击去重、最终验证均有对应任务。
- Placeholder scan: 无模糊执行描述或未展开的任务说明。
- Type consistency: 统一使用 `file_preview` 作为 tab type，统一使用 `sourceKey` 做 session 内去重，统一以 `plaintext` 作为未知后缀回退语言。
