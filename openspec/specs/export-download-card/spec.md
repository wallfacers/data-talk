## ADDED Requirements

### Requirement: 导出工具卡片渲染

系统 SHALL 为 `datatalk.export_data` 注册专用 ToolPart 渲染器，替代 GenericTool 的默认 JSON 渲染。

#### Scenario: 同步导出完成 — 卡片渲染

- **GIVEN** AI 调用了 `datatalk.export_data` 且 action 完成（`status: "completed"`）
- **WHEN** ToolPart 渲染该 tool part
- **THEN** 渲染为 BasicTool 卡片：
  - title: "导出数据"
  - subtitle: "{rowCount} 行, {format}, {fileSize}"（fileSize 为人类可读格式如 "14.6 KB"）
  - trigger.action: 可点击的下载按钮（DownloadIcon）
  - 点击下载按钮调用 `downloadFromUrl(downloadUrl, filename)`
- **AND** 下载按钮可多次点击（TTL 1 小时内）

#### Scenario: 异步导出处理中 — 卡片渲染

- **GIVEN** AI 调用了 `datatalk.export_data` 且 action 返回 `status: "processing"`
- **WHEN** ToolPart 渲染该 tool part
- **THEN** subtitle 显示 "处理中..."
- **AND** 下载按钮 disabled

#### Scenario: 导出失败 — 卡片渲染

- **GIVEN** AI 调用了 `datatalk.export_data` 且 action 返回 `status: "error"`
- **WHEN** ToolPart 渲染该 tool part
- **THEN** subtitle 显示错误信息
- **AND** 不展示下载按钮

### Requirement: 文件大小格式化

系统 SHALL 提供文件大小格式化工具函数。

#### Scenario: 格式化文件大小

- **WHEN** 调用 `formatFileSize(bytes)`
- **THEN** 返回人类可读字符串：
  - < 1024 → "{n} B"
  - < 1024*1024 → "{n} KB"（保留 1 位小数）
  - < 1024*1024*1024 → "{n} MB"（保留 1 位小数）
