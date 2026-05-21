## Why

当前存储治理页面只覆盖了磁盘概览和孤儿归档资产（connection 删除后的 workspace 文件），但 dashboards、reports、exports、semantic、uploads 五个资源目录的产出文件完全不在任何治理视图中。session 删除后这些资源文件留在磁盘上，用户无法发现、查看、或删除它们。同时，"清空全部会话" 一刀切销毁所有 stage tab（包括与 session 无关的 workspace 级别 tab），而单会话删除又完全不做 tab 清理，两条路径行为不对称。

## What Changes

- **新增**：存储治理页面增加 5 个资源目录的列表管理（dashboards、reports、exports、semantic、uploads），每个资源类型按功能展示不同字段
- **新增**：每个资源目录支持预览内容（dashboard HTML、report HTML、semantic YAML、upload 文本/图片、export 表格数据）
- **新增**：资源目录列表支持单项删除和批量删除
- **新增**：session 删除时资源目录文件保留，但标记"来源会话(已删除)"，在存储治理页面可见
- **修复**：清空全部会话改为精确关闭 session-scoped tab（`artifact_preview`、`files`），不再误伤 workspace 级别 tab（`dashboard`、`report_viewer`、`query_editor`、`er_*`、`semantic_model_editor`）
- **修复**：单会话删除时同步关闭该 session 的 `artifact_preview` 和 `files` tab
- **修复**：`uploaded_file` 表加 `ON DELETE SET NULL` FK 约束（session 删除后上传文件行保留但 `session_id` 变 NULL）
- **修复**：`DataExportService` 记录 `originSessionId` 以便追溯来源

## Capabilities

### New Capabilities

- `storage-governance-resource-list`: 资源目录列表管理后端 API —— 按资源类型列出目录文件，支持筛选、分页、删除
- `storage-governance-resource-preview`: 资源内容预览 API —— 按资源类型返回可预览内容（HTML/YAML/文本/表格片段）
- `storage-governance-resource-ui`: 存储治理资源目录前端 —— 概要卡片 + 类型 Tab + 资源列表表格 + 预览抽屉 + 删除操作
- `session-tab-lifecycle`: Session-Tab 生命周期联动 —— session 删除时精确关闭 session-scoped tab，保留 workspace-scoped tab

### Modified Capabilities

- `data-export`: 导出服务增加 `originSessionId` 记录，export 资源在存储治理中可追溯来源
- `user-preference-storage`: `clearAllLocalSessionResources` 改为精确清理，不再销毁全部 stage tab

## Impact

- **Backend API**: `MaintenanceController` 新增 5 个资源列表端点 + 删除端点；资源预览端点；`SessionService.deleteRecord` 返回被删除 session 关联的资源 ID 列表；`DataExportService` 加 `originSessionId` 字段
- **Database**: `uploaded_file` 表加 FK `REFERENCES sessions(id) ON DELETE SET NULL`（需要 Flyway 迁移）
- **Frontend**: `general-panel.tsx` 的 `clearAllLocalSessionResources` 改为精确 tab 清理；`nav-sessions.tsx` 的单 session 删除加 tab 清理
- **Frontend UI**: `maintenance-page.tsx` 增加资源目录区块（概览卡片 + 类型 Tabs + 资源表格 + 预览抽屉）
- **i18n**: 新增 ~30 个翻译 key

## Design Inputs

来自 [client/DESIGN.md](../../../client/DESIGN.md) 的约束：
- **Layout Mode**: `Resource Management` —— 资源管理类页面，需遵循 table 组件规则（`headerBg: bg.subtle`, `rowHover: interaction.hover`, `rowSelected: interaction.selected`）
- **Density**: `compact` —— 表格、工具栏、选项卡使用紧凑密度
- **Typography**: 表格内容用 `ui-sm`（13px），表头用 `ui-xs`（12px），数字/技术字段用 mono 字体
- **Theme**: 所有颜色通过 semantic token 引用（`text.strong`, `text.muted`, `status.danger` 等），禁用原始 primitive 颜色
- **State communication**: 删除按钮使用 `status.danger` token，禁用状态不可仅靠颜色区分
- **Accessibility**: 表格行键盘可导航，批量操作按钮有 accessible name，删除确认弹框

## Risks

- 无现有 open BUG 直接关联 maintenance 或 storage-governance 模块。BUG-0073（Report iframe 字体 CORS）不在本变更范围内
- Export 资源有 1h TTL 自动过期，列表中可能刚加载就被过期删除——边缘情况需处理
