## ADDED Requirements

### Requirement: 导出记录来源会话

`DataExportService` SHALL 在创建导出任务时记录发起调用的 session ID 作为 `originSessionId`，以便存储治理追溯来源。

#### Scenario: 同步导出记录 originSessionId

- **WHEN** AI 调用 `datatalk_export_data` 且结果 < 10K 行（同步路径）
- **THEN** 返回的 `DataExportResult` 中包含 `originSessionId` 字段
- **AND** `originSessionId` 为发起调用的 session ID

#### Scenario: 异步导出记录 originSessionId

- **WHEN** AI 调用 `datatalk_export_data` 且结果 ≥ 10K 行（异步路径）
- **THEN** 创建的 export job metadata 包含 `originSessionId`
- **AND** export 列表查询可返回该字段

#### Scenario: originSessionId 为 null

- **WHEN** 导出由非 session 上下文触发（如直接 API 调用）
- **THEN** `originSessionId` 为 null
