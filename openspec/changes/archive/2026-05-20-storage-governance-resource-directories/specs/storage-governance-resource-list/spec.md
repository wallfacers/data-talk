## ADDED Requirements

### Requirement: 按类型列出资源目录文件

系统 SHALL 为每种资源目录提供独立的列表端点，返回该类型的所有资源文件及其元信息。

#### Scenario: 获取 dashboard 资源列表

- **WHEN** 前端请求 `GET /api/maintenance/dashboards`
- **THEN** 返回 List<DashboardResourceDto>，每个 DTO 包含：`id`, `title` (从 dashboard.json 解析), `filename`, `sizeBytes`, `widgetCount`, `createdAt`, `updatedAt`, `originSessionId` (可能为 null)
- **AND** 最多返回 200 条

#### Scenario: 获取 report 资源列表

- **WHEN** 前端请求 `GET /api/maintenance/reports`
- **THEN** 返回 List<ReportResourceDto>，每个 DTO 包含：`id`, `title`, `availableFormats` (如 ["html","pdf","md"]), `sizeBytes`, `createdAt`, `updatedAt`, `originSessionId`

#### Scenario: 获取 export 资源列表

- **WHEN** 前端请求 `GET /api/maintenance/exports`
- **THEN** 返回 List<ExportResourceDto>，每个 DTO 包含：`exportId`, `filename`, `format` (csv/json/xlsx/sql_insert), `sizeBytes`, `rowCount`, `createdAt`, `expiresAt`, `originSessionId`

#### Scenario: 获取 semantic 资源列表

- **WHEN** 前端请求 `GET /api/maintenance/semantic`
- **THEN** 返回 List<SemanticResourceDto>，每个 DTO 包含：`domain`, `connectionId`, `connectionName`, `status` (active/pending), `sizeBytes`, `updatedAt`

#### Scenario: 获取 upload 资源列表

- **WHEN** 前端请求 `GET /api/maintenance/uploads`
- **THEN** 返回 List<UploadResourceDto>，每个 DTO 包含：`id`, `filename`, `mimeType`, `sizeBytes`, `originSessionId`, `createdAt`, `expiresAt` (24h TTL)

### Requirement: 资源概览统计

系统 SHALL 在 storage-overview 响应中增加各资源目录的磁盘占用和文件数量统计。

#### Scenario: 资源目录概览

- **WHEN** 前端请求 `GET /api/maintenance/storage-overview`
- **THEN** 响应中增加 `resourceDirectories` 字段，包含 `{ dashboards: {count, sizeBytes}, reports: {count, sizeBytes}, exports: {count, sizeBytes}, semantic: {count, sizeBytes}, uploads: {count, sizeBytes} }`

### Requirement: 删除资源目录文件

系统 SHALL 为每种资源目录提供删除端点，永久删除物理文件和关联的 DB 记录。

#### Scenario: 删除 dashboard

- **WHEN** 前端请求 `DELETE /api/maintenance/dashboards/{id}`
- **THEN** 删除 `dashboards/{id}.dashboard.json` 和 `dashboards/{id}.html`（如存在）
- **AND** 删除 `file_artifact WHERE id = {id}` 记录（如存在）
- **AND** 返回 204 No Content

#### Scenario: 删除 report

- **WHEN** 前端请求 `DELETE /api/maintenance/reports/{id}`
- **THEN** 递归删除 `reports/{id}/` 整个目录
- **AND** 删除 `file_artifact WHERE id = {id}` 记录
- **AND** 返回 204

#### Scenario: 删除 export

- **WHEN** 前端请求 `DELETE /api/maintenance/exports/{exportId}`
- **THEN** 删除 `exports/{exportId}/` 整个目录
- **AND** 返回 204

#### Scenario: 删除 semantic model

- **WHEN** 前端请求 `DELETE /api/maintenance/semantic/{domain}?connectionId={connectionId}`
- **THEN** 删除对应 YAML 文件和 patches，或移动到 `_trash/semantic/`
- **AND** 返回 204

#### Scenario: 删除 upload

- **WHEN** 前端请求 `DELETE /api/maintenance/uploads/{id}`
- **THEN** 删除 `uploads/{id}/` 目录
- **AND** 删除 `uploaded_file WHERE id = {id}` 记录
- **AND** 返回 204

#### Scenario: 删除不存在的资源

- **WHEN** 删除一个不存在的资源 ID
- **THEN** 返回 404 Not Found

### Requirement: uploaded_file session 外键约束

`uploaded_file` 表 SHALL 通过 FK 引用 `sessions(id)` 并在 session 删除时 SET NULL。

#### Scenario: session 删除后 upload 行保留

- **GIVEN** `uploaded_file` 表中存在 `session_id = 'sess-123'` 的行
- **WHEN** session 'sess-123' 被删除
- **THEN** 对应 upload 行的 `session_id` 变为 NULL
- **AND** 物理文件保留在 `uploads/` 目录中

#### Scenario: FK 迁移

- **WHEN** Flyway 迁移执行
- **THEN** 清理 `session_id` 在 `sessions` 表中不存在的 upload 行
- **AND** 在新表上创建 FK 约束 `FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL`
