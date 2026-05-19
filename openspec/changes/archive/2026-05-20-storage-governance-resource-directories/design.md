## Context

当前 `MaintenanceController` 只提供 `storage-overview`（磁盘占用概览）和 `orphaned-files`（孤儿归档文件）两个查询端点。存储治理页面（`maintenance-page.tsx`）显示磁盘概览卡片 + 孤儿归档资产表格。

五个资源目录（`dashboards/`、`reports/`、`exports/`、`semantic/`、`uploads/`）各有独立的持久化方式和生命周期，但没有任何统一的管理入口。session 删除时资源目录文件不受影响，成为"不可见的孤儿"。

同时存在 session-tab 生命周期不一致问题：`clearAllLocalSessionResources()` 一刀切清空所有 stage tab，而单 session 删除完全不做 tab 清理。

## Goals / Non-Goals

**Goals:**
- 在存储治理页面提供 5 个资源目录的列表管理（概览卡片 + 类型 Tab + 表格 + 批量操作）
- 每种资源类型按功能展示不同字段（dashboard 看 widget 数、report 看格式、export 看格式+过期时间 等）
- 支持资源内容预览（HTML 渲染、YAML 高亮、表格数据片段、图片展示、文本查看）
- 支持单项删除和批量删除
- 修复 session-tab 生命周期联动：清空全部会话只关 session-scoped tab，单 session 删除同步关闭对应 tab
- `uploaded_file` 表加 FK `ON DELETE SET NULL`，export 记录 `originSessionId`

**Non-Goals:**
- 不改变资源的创建/更新逻辑（dashboard promote、report generate、export 等核心路径不变）
- 不改变 orphan archived assets 的现有逻辑
- 不给 resource 增加"重新分配连接"（reattach）功能——与 orphan 不同，这些资源没有 connection 归属
- 不改变 semantic model 的核心存储和 MR 流程

## Decisions

### Decision 1: API 设计 —— 类型专有端点 + 专有 DTO

**选择**: 每种资源类型一个独立端点，返回专有 DTO。

```
GET /api/maintenance/dashboards  → List<DashboardResourceDto>
GET /api/maintenance/reports     → List<ReportResourceDto>
GET /api/maintenance/exports     → List<ExportResourceDto>
GET /api/maintenance/semantic    → List<SemanticResourceDto>
GET /api/maintenance/uploads     → List<UploadResourceDto>
DELETE /api/maintenance/dashboards/{id}
DELETE /api/maintenance/reports/{id}
DELETE /api/maintenance/exports/{id}
DELETE /api/maintenance/semantic/{domain}?connectionId=...
DELETE /api/maintenance/uploads/{id}
```

**替代方案**: 统一 `GET /api/maintenance/resources?type=X` + 通用 `ResourceDto`（含 `Map<String,Object> attributes`）。被拒绝原因：类型字段差异大（dashboard 有 widget count、report 有 formats、export 有 TTL），通用 DTO 会使前端类型安全丧失。

### Decision 2: 后端实现路径 —— 直接扫描文件系统 + DB 关联

每个资源端点直接从文件系统扫描对应目录，必要时 join DB 数据：

| 资源 | 扫描路径 | DB 关联 |
|------|---------|---------|
| Dashboard | `dashboards/` 目录 `*.dashboard.json` | `file_artifact WHERE kind='DASHBOARD' AND external=true` |
| Report | `reports/{id}/` 子目录 | `file_artifact WHERE kind='REPORT' AND external=true` |
| Export | `exports/{id}/` 子目录 | 无 DB 表，从文件名和 stat 推断 |
| Semantic | `semantic/{connectionId}/` | `FsSemanticModelRepository` 读取 YAML + index |
| Upload | `uploads/{id}/` 子目录 | `uploaded_file` 表 join session 信息 |

**替代方案**: 全部通过 `FileArtifactRepository` 查询。被拒绝因为 export 和 semantic 不在 `file_artifact` 表中，uploads 在独立表 `uploaded_file` 中。

### Decision 3: 资源预览 API

每种资源类型独立预览端点：

```
GET /api/maintenance/dashboards/{id}/preview   → 返回 HTML 内容 (text/html)
GET /api/maintenance/reports/{id}/preview      → 返回 HTML 内容 (text/html)
GET /api/maintenance/exports/{id}/preview      → 返回前 100 行 (application/json)
GET /api/maintenance/semantic/{domain}/preview?connectionId=... → 返回 YAML 内容 (text/plain)
GET /api/maintenance/uploads/{id}/preview       → 根据 MIME 返回内容或元信息
```

前端用统一预览组件，根据 content-type 选择渲染方式：HTML → iframe sandbox、YAML/文本 → 代码高亮、JSON 表格数据 → 虚拟滚动表格、图片 → `<img>` 标签。

### Decision 4: Tab 清理策略

**Session-scoped tab（删除 session 时关闭）:**
- `artifact_preview`: payload 含 `sessionId`，`originSessionId` 指向 session
- `files`: scope='session'，`originSessionId` 指向 session

**Workspace-scoped tab（保留，不清除）:**
- `dashboard`, `report_viewer`, `semantic_model_editor`, `query_editor`, `er_inspector`, `er_designer`, `script_editor`, `files_library`, `script_library`, `report_library`, `operation_log`

**实现**: `resetSessionResources()` 改为 `closeSessionScopedTabs(sessionId?: string)` —— 无参数时遍历所有 session 找 session-scoped tab 关闭；有参数时只关闭指定 session 的 tab。

### Decision 5: uploaded_file FK 迁移

添加 Flyway 迁移 `Vxx__uploaded_file_session_fk.sql`:

```sql
-- 先清理 session 已不存在的孤儿行
DELETE FROM uploaded_file WHERE session_id NOT IN (SELECT id FROM sessions);
-- 加 FK
ALTER TABLE uploaded_file ADD CONSTRAINT fk_uploaded_file_session
  FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL;
```

注意 SQLite 不支持 `ALTER TABLE ADD CONSTRAINT`。需要用重建表方式：创建新表 → 复制数据 → 删旧表 → 重命名。

### Decision 6: Export originSessionId 追溯

`DataExportService` 在创建异步导出时记录调用 session 的 ID。这个信息保存在内存中（export job metadata），不持久化到文件或 DB。存储治理查询 export 列表时，通过 job metadata 返回。

不影响 `datatalk_export_data` MCP action 的输入/输出 schema。

### Decision 7: 前端组件结构

```
maintenance-page.tsx
├── StorageOverviewCards (已有，增加 5 个资源目录卡片)
├── ResourceDirectoriesSection (新增)
│   ├── ResourceTypeTabs (Dashboard | Report | Export | Semantic | Upload)
│   └── ResourceTable (按类型渲染不同列)
│       ├── columns: checkbox | type-specific fields | size | time | actions
│       └── actions: preview button + delete button
├── ResourcePreviewDrawer (新增)
│   ├── HtmlPreview (iframe sandbox)
│   ├── CodePreview (Shiki 语法高亮)
│   ├── TablePreview (虚拟滚动)
│   └── ImagePreview
└── OrphanArchivesSection (已有，不变)
```

## Risks / Trade-offs

- **[文件系统扫描性能]** → 每个端点从磁盘读取，对大量文件的目录可能有延迟。缓解：限制返回数量（200 条），加索引缓存；大部分用户资源数 < 100
- **[Export 1h TTL 竞态]** → 列表中显示的 export 可能在用户操作前已被自动过期删除。缓解：前端在预览/删除时检查 404，显示友好提示"文件已过期"
- **[SQLite ALTER TABLE 限制]** → 加 FK 需要重建表。缓解：Flyway 迁移中处理，数据量小（uploaded_file 通常 < 1000 行）
- **[session 删除后 tab 引用失效]** → `artifact_preview` tab 的 `payload.sessionId` 变为悬空引用。缓解：session 删除时主动关闭这些 tab，防止用户看到"broken"状态

## Migration Plan

1. **后端**: 新增端点不破坏现有 API，可以独立部署
2. **数据库**: Flyway 迁移自动执行，无需手动操作；SQLite 重建表迁移已测试过多次
3. **前端**: 新增 UI 区块不影响现有 orphan archives 功能；`resetSessionResources` 改为精确关闭对用户透明
4. **回滚**: 如出问题，新端点只是不调用即可（前端 feature flag 控制）；FK 迁移有 `DROP TABLE IF EXISTS` 回滚脚本

## Open Questions

- 暂无。所有关键技术决策已在 explore 阶段澄清。
