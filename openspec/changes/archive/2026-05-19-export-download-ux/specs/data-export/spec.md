## MODIFIED Requirements

### Requirement: export.completed SSE 事件处理

修改现有 `export.completed` toast 通知的下载行为。

#### Scenario: Toast 下载按钮使用统一下载函数

- **GIVEN** 前端收到 `export.completed` SSE 事件
- **WHEN** toast 通知展示 "下载" 按钮
- **THEN** 点击按钮调用 `downloadFromUrl(downloadUrl, filename)` 而非 `window.open(url, '_blank')`
- **AND** filename 基于 format 推断（`export-{exportId}.{ext}`）
