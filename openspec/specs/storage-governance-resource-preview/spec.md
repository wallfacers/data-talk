### Requirement: Dashboard 内容预览

系统 SHALL 提供 dashboard HTML 内容预览端点。

#### Scenario: 预览 dashboard HTML

- **WHEN** 前端请求 `GET /api/maintenance/dashboards/{id}/preview`
- **THEN** 返回 `text/html`，内容为 dashboard 的 HTML 渲染结果（从 `{id}.html` 文件读取）
- **AND** 响应头包含 `Content-Security-Policy: sandbox`

#### Scenario: dashboard 不存在 HTML 文件

- **WHEN** dashboard 有 `.dashboard.json` 但没有 `.html`
- **THEN** 返回 404，body 为 `{ "error": "HTML_NOT_FOUND", "message": "Dashboard HTML has not been built" }`

### Requirement: Report 内容预览

系统 SHALL 提供 report HTML 内容预览端点。

#### Scenario: 预览 report HTML

- **WHEN** 前端请求 `GET /api/maintenance/reports/{id}/preview`
- **THEN** 返回 `text/html`，内容为 `reports/{id}/report.html` 文件
- **AND** 响应头包含 `Content-Security-Policy: sandbox`

### Requirement: Export 内容预览

系统 SHALL 提供 export 文件数据预览端点，返回前 N 行数据。

#### Scenario: 预览 CSV export

- **WHEN** 前端请求 `GET /api/maintenance/exports/{exportId}/preview`
- **THEN** 返回 `application/json`，格式 `{ "format": "csv", "columns": [...], "rows": [[...], ...], "totalRows": N, "previewRows": 100 }`
- **AND** 最多返回前 100 行

#### Scenario: 预览 JSON export

- **WHEN** export 格式为 JSON
- **THEN** 解析 JSON 数组，返回同样格式的 `{ columns, rows, totalRows, previewRows }`

#### Scenario: 预览 XLSX export

- **WHEN** export 格式为 XLSX
- **THEN** 读取第一个 sheet 的前 100 行，返回 `{ columns, rows, totalRows, previewRows }`

### Requirement: Semantic 内容预览

系统 SHALL 提供 semantic model YAML 内容预览端点。

#### Scenario: 预览 YAML 模型

- **WHEN** 前端请求 `GET /api/maintenance/semantic/{domain}/preview?connectionId={connectionId}`
- **THEN** 返回 `text/plain; charset=utf-8`，内容为完整的 `.model.yaml` 文件

### Requirement: Upload 内容预览

系统 SHALL 提供 upload 文件内容预览端点，根据 MIME 类型返回适当格式。

#### Scenario: 预览文本文件

- **WHEN** upload 文件的 MIME 类型为 `text/*` 或 `application/json`
- **THEN** 返回 `text/plain; charset=utf-8`，内容为文件原始文本（限制 1MB）

#### Scenario: 预览图片文件

- **WHEN** upload 文件的 MIME 类型为 `image/*`
- **THEN** 返回图片二进制流，Content-Type 为原始 MIME

#### Scenario: 预览其他二进制文件

- **WHEN** upload 文件的 MIME 类型不是文本也不是图片
- **THEN** 返回 `application/json`，格式 `{ "mimeType": "...", "sizeBytes": N, "previewable": false, "message": "Binary file preview not supported" }`
