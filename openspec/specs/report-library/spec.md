# report-library Specification

## Purpose
TBD - created by archiving change report-document-generation. Update Purpose after archive.
## Requirements
### Requirement: SQLite `report` 表 schema 与 Flyway migration

系统 SHALL 通过 Flyway migration `V4__create_report_table.sql`（位于 `server/data-talk-infrastructure/src/main/resources/db/migration/`）创建 SQLite `report` 表，字段包括：
- `id` (text, PK)
- `workspace_id` (text, indexed)
- `group_id` (text, indexed) — 同一份报告的重新生成版本共享同一 group_id
- `version` (integer, default 1) — 在 group 内单调递增
- `title` (text)
- `subtitle` (text nullable)
- `template_id` (text)
- `template_version` (text)
- `accent_color` (text, hex)
- `generated_at` (timestamp)
- `generated_by_session_id` (text, FK soft)
- `user_prompt` (text nullable) — 原始用户诉求文本，v0 仅存储
- `artifact_paths_json` (text, JSON: `{json, html, md?, pdf?}`)
- `pdf_status` (text enum: `processing` / `ready` / `failed`)
- `md_status` (text enum: `processing` / `ready` / `failed`)
- `pdf_fail_reason` (text nullable)
- `md_fail_reason` (text nullable)

#### Scenario: Flyway 创建表

- **WHEN** 应用首次启动应用 Flyway migration
- **THEN** SQLite 中 MUST 存在 `report` 表
- **AND** 上述所有字段 MUST 存在

#### Scenario: 索引存在

- **WHEN** 检查 SQLite indexes
- **THEN** MUST 存在 `idx_report_workspace` 覆盖 `workspace_id` 列
- **AND** MUST 存在 `idx_report_group` 覆盖 `group_id` 列

### Requirement: REST API `/api/reports` 列表 + 详情 + 下载

系统 SHALL 提供 REST 端点：
- `GET /api/reports?workspaceId={wsId}` → 列表（按 `generated_at desc`，分页 `limit/offset`）
- `GET /api/reports/{id}` → 详情（含 `report.json` 内联或 `jsonUrl`）
- `GET /api/reports/{id}/download/{format}` → 下载 html / md / pdf 三种格式之一
- `POST /api/reports/{id}/pdf-render` → 手动触发或重试 PDF 渲染
- `DELETE /api/reports/{id}` → 删除报告及其全部 artifact

#### Scenario: 列表按时间倒序

- **GIVEN** workspace ws-1 中有 3 个不同 group 的报告（各 1 个版本），generatedAt 分别为 T1 < T2 < T3
- **WHEN** `GET /api/reports?workspaceId=ws-1`
- **THEN** 响应 items 顺序为 [T3, T2, T1]

#### Scenario: 跨 workspace 隔离

- **GIVEN** workspace ws-1 有 2 份报告，ws-2 有 1 份
- **WHEN** `GET /api/reports?workspaceId=ws-1`
- **THEN** 响应 items.length = 2
- **AND** 不包含 ws-2 的报告

#### Scenario: 下载 PDF 未就绪

- **GIVEN** report `pdf_status='processing'`
- **WHEN** `GET /api/reports/{id}/download/pdf`
- **THEN** 响应 HTTP 409 Conflict
- **AND** body 含 `{ pdfStatus: "processing", retryAfterSec: 3 }`

#### Scenario: 下载 Markdown 未就绪

- **GIVEN** report `md_status='processing'`
- **WHEN** `GET /api/reports/{id}/download/md`
- **THEN** 响应 HTTP 409 Conflict
- **AND** body 含 `{ mdStatus: "processing", retryAfterSec: 3 }`

#### Scenario: 下载 HTML 不受 PDF/MD 状态影响

- **GIVEN** report `pdf_status='failed'` 且 `md_status='failed'`
- **WHEN** `GET /api/reports/{id}/download/html`
- **THEN** 响应 HTTP 200 with HTML body
- **AND** Content-Type = "text/html"

#### Scenario: 删除清理 artifact

- **GIVEN** report `r-1` 存在，artifact 含 json/html/md/pdf 与附录 csv
- **WHEN** `DELETE /api/reports/r-1`
- **THEN** SQLite row 被删除
- **AND** 全部 artifact 文件被删除（file artifact `report-asset` / `report-data-csv` 同步清理）

### Requirement: 前端 report tab 类型注册（global stage）

`client/src/features/stage/registry/tab-type-registry.ts` SHALL 注册两种 workspace-scope tab type：`report-library`（报告库列表）与 `report-viewer`（单份报告查看器）。两种 tab MUST `scope: "workspace"`；`StageTab` instance MUST NOT 携带 `scope` 字段。切换 active session 时这两种 tab 的开关/激活状态 MUST 保持不变。

#### Scenario: tab 注册存在

- **WHEN** 解析 `client/src/features/stage/registry/tab-type-registry.ts`
- **THEN** MUST 包含 `report-library` 描述符，`scope: 'workspace'`
- **AND** MUST 包含 `report-viewer` 描述符，`scope: 'workspace'`

#### Scenario: 切换 session 不变 tab 状态

- **GIVEN** stage 已打开 `report-library` tab 并激活
- **WHEN** 用户切换到另一个 session
- **THEN** `useStageStore.activeTabId` 不变
- **AND** `useStageStore.tabs` 不变

### Requirement: 报告库列表 UI 与 client/DESIGN.md token 合规

`report-library-tab.tsx` 列表 UI SHALL 仅使用 `client/DESIGN.md` semantic token（`bg.canvas` / `bg.subtle` / `text.strong` / `text.muted` / `accent.primary` / `border.default` / `interaction.hover` / `interaction.selected`）；MUST NOT 使用 raw primitive color 字面量（如 `#xxxxxx` 在 className 或 style 中）。

#### Scenario: 列表项 hover 状态用 token

- **WHEN** 检查 `report-library-tab.tsx` 列表行的 hover 样式
- **THEN** 使用 `interaction.hover` 对应 className 或 token reference
- **AND** 不含 raw color literal

#### Scenario: 选中报告高亮用 accent.primary

- **WHEN** 用户在列表中点击某行
- **THEN** 该行 left-border 使用 `accent.primary` 对应 token

### Requirement: 报告查看器 UI 顶栏与 iframe 沙箱

`report-viewer-tab.tsx` SHALL 顶部呈现：报告标题、生成时间、`导出 PDF` / `导出 Markdown` 按钮、`重新生成` 按钮、PDF/MD 状态指示器（processing 中显示 spinner）；主体为 iframe sandbox 加载 `report.html`；iframe MUST `sandbox="allow-scripts"`（与 [client/src/features/dashboard/iframe-shell.tsx](../../../client/src/features/dashboard/iframe-shell.tsx) 一致，允许 ECharts 浏览器端渲染；本地字体通过 srcDoc + `@font-face` 不需要 `allow-same-origin`）；iframe 加载完成（`onLoad`）前显示 `<Skeleton>` loader。

#### Scenario: iframe sandbox 仅含 allow-scripts

- **WHEN** report-viewer 渲染
- **THEN** iframe 元素的 `sandbox` 属性 MUST 等于字符串 `"allow-scripts"`
- **AND** MUST NOT 含 `allow-same-origin` / `allow-forms` / `allow-popups` / `allow-top-navigation`

#### Scenario: PDF 按钮在 system_not_ready 时禁用

- **GIVEN** `GET /api/reports/system-status` 返回 `{ chromiumReady: false }`
- **WHEN** 用户打开 report-viewer
- **THEN** `导出 PDF` 按钮 disabled
- **AND** 显示 tooltip "PDF 准备中…"

#### Scenario: PDF 按钮在 pdf_status=processing 时显示 spinner

- **GIVEN** 当前 report `pdf_status = 'processing'`
- **WHEN** 用户打开 report-viewer
- **THEN** `导出 PDF` 按钮 disabled
- **AND** 按钮内显示 spinner 图标

#### Scenario: iframe loader 等到 onLoad 才隐藏

- **GIVEN** report-viewer 刚打开
- **WHEN** iframe `onLoad` 事件未触发
- **THEN** loader Skeleton 仍在屏
- **WHEN** iframe `onLoad` 事件触发
- **THEN** loader 隐藏，iframe 显示

### Requirement: 派生状态前端轮询

前端 SHALL 使用 TanStack Query 轮询 `GET /api/reports/{id}` 监听 `pdf_status` 与 `md_status` 字段。当 report-viewer 打开且其中任一字段 = `processing` 时，`refetchInterval = 3000`（3 秒）；当两字段均为终止态（`ready` 或 `failed`）时 `refetchInterval = false`（停止轮询）；用户关闭 viewer 时立即停止轮询。

#### Scenario: 轮询启动条件

- **GIVEN** 用户打开 report-viewer，report `pdf_status='processing'`, `md_status='processing'`
- **WHEN** 组件挂载
- **THEN** TanStack Query 启动 `GET /api/reports/{id}` 每 3 秒轮询

#### Scenario: 轮询在两字段终态时停止

- **GIVEN** 轮询中收到响应 `{ pdf_status: 'ready', md_status: 'ready' }`
- **WHEN** TanStack Query 处理响应
- **THEN** `refetchInterval` 切换为 false
- **AND** 不再发新请求

#### Scenario: 一个字段失败一个成功仍停止轮询

- **GIVEN** 轮询中收到 `{ pdf_status: 'failed', md_status: 'ready' }`
- **WHEN** TanStack Query 处理响应
- **THEN** 轮询停止（两字段均为终止态）
- **AND** PDF 按钮显示失败图标 + tooltip fail_reason
- **AND** MD 按钮可点击

#### Scenario: 关闭 viewer 立即停止

- **GIVEN** report-viewer 处于轮询状态
- **WHEN** 用户切换到其他 tab 或关闭 stage
- **THEN** 该 query 被 `unmount` 并停止轮询

### Requirement: chat 内 report promote 完成卡片

AI 完成 `datatalk_promote_report` 后，chat 消息流中 SHALL 显示一张报告卡片，含报告标题、生成时间、`打开` 按钮；点击 `打开` SHALL 通过 `useStageStore.openTab({ type: 'report-viewer', payload: { reportId } })` 打开查看器；如果当前 stage 未打开，自动打开 stage。

#### Scenario: 卡片点击打开 viewer

- **GIVEN** chat 消息流中有一张 report 卡片 reportId="r-1"
- **WHEN** 用户点击 `打开`
- **THEN** `useStageStore.tabs` 中新增或激活 `{ type: 'report-viewer', payload: { reportId: 'r-1' } }`
- **AND** `useStageStore.open = true`

#### Scenario: 卡片显示生成时间

- **GIVEN** report 卡片对应的 report `generatedAt` 为 "2026-05-19T10:00:00+08:00"
- **WHEN** 卡片渲染
- **THEN** 显示相对时间（"刚刚" / "5 分钟前" / "今天 10:00"）

### Requirement: 重新生成入口与 group_id 关联

`重新生成` 按钮点击 SHALL 在当前 chat session 中以 user message 形式注入提示文本 "请基于 {templateId} 模板和上次的需求重新生成报告（groupId={groupId}）"，触发 AI 重新走 promote 流程；新生成的报告 SHALL 共享原报告的 `group_id`，`version` = 同 group 内当前最大 version + 1，原报告 SHALL 保留。`datatalk_promote_report` action input 中如包含 `groupId` 参数，服务端 MUST 校验该 groupId 在 workspace 下已存在，并把新 report 写入该 group。

#### Scenario: 重新生成产出同 group 新版本

- **GIVEN** 报告 r-1 (group_id=g-1, version=1)
- **WHEN** 用户在 viewer 点击 `重新生成`，AI 完成新 promote（带 groupId=g-1）
- **THEN** SQLite 中新增 r-2 (group_id=g-1, version=2)
- **AND** r-1 仍存在
- **AND** 报告库列表默认显示 r-2，r-1 在 group 展开时可见

#### Scenario: 重新生成需要 active session

- **GIVEN** 用户当前无 active session
- **WHEN** 用户点击 `重新生成`
- **THEN** 弹出 toast "请先打开或创建会话"
- **AND** 不发送 message

#### Scenario: 非法 groupId 拒绝

- **GIVEN** AI 提交 `datatalk_promote_report` 带 `groupId="g-nonexistent"`（该 workspace 下不存在）
- **WHEN** `ReportArtifactService.promote()` 校验
- **THEN** 返回错误 `{ errorCode: "REPORT_GROUP_NOT_FOUND" }`

### Requirement: 报告库列表按 group 折叠

`GET /api/reports?workspaceId={wsId}` 默认 SHALL 按 group 折叠：每个 group 只返回最新 version 的报告，items 中含 `groupSize` 字段（同 group 内的 version 总数）。可选 query 参数 `groupId={gid}` 时返回该 group 的所有版本（不折叠，按 version desc）。

#### Scenario: 默认列表折叠

- **GIVEN** workspace 中 group g-1 有 3 个版本 (v1/v2/v3)，group g-2 有 1 个版本 (v1)
- **WHEN** `GET /api/reports?workspaceId=ws-1`
- **THEN** 响应 items.length = 2
- **AND** g-1 的条目 version=3，`groupSize=3`
- **AND** g-2 的条目 version=1，`groupSize=1`

#### Scenario: 按 group 展开

- **GIVEN** group g-1 有 v1/v2/v3
- **WHEN** `GET /api/reports?workspaceId=ws-1&groupId=g-1`
- **THEN** 响应 items.length = 3
- **AND** items 按 version desc 排序 [v3, v2, v1]

### Requirement: 系统状态端点 `/api/reports/system-status`

系统 SHALL 提供 `GET /api/reports/system-status` 返回 `{ chromiumReady: boolean, fontsReady: boolean, skillReady: boolean, message?: string }`；`chromiumReady` 由 Playwright `chromium` 可执行文件是否存在决定；`fontsReady` 由 ledger skill 解压后的字体文件是否存在决定；`skillReady` 由 ledger skill 解压目录是否存在 `SKILL.md` 决定。

#### Scenario: 启动期未就绪

- **GIVEN** 服务端启动 30 秒内 Playwright 仍在下载 Chromium
- **WHEN** `GET /api/reports/system-status`
- **THEN** 响应 `{ chromiumReady: false, fontsReady: true, skillReady: true, message: "Chromium installing" }`

#### Scenario: 全部就绪

- **GIVEN** 服务端启动完成且 Chromium 已就绪
- **WHEN** `GET /api/reports/system-status`
- **THEN** 响应 `{ chromiumReady: true, fontsReady: true, skillReady: true }`

