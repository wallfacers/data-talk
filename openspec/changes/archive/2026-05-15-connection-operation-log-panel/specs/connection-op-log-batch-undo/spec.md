## ADDED Requirements

### Requirement: 批量 undo 端点

系统 SHALL 提供 `POST /api/connections/{connectionId}/op-logs/batch-undo` 端点，接受 `{ undoLogIds: string[] }`，对多条 active + undoable 记录执行 undo。

#### Scenario: 成功批量 undo

- **GIVEN** 3 条 undo_log 记录均 status=active、undoable=true、未过期
- **WHEN** `POST /api/connections/conn-1/op-logs/batch-undo { undoLogIds: ["id1","id2","id3"] }`
- **THEN** 返回 HTTP 200，body 为 `{ results: [ { id: "id1", status: "undone", affectedRows: 3 }, { id: "id2", status: "undone", affectedRows: 1 }, { id: "id3", status: "undone", affectedRows: 5 } ] }`

#### Scenario: 部分记录不可 undo

- **GIVEN** 请求中包含 1 条 active/undoable、1 条 expired、1 条 undone
- **WHEN** 批量 undo 请求
- **THEN** 返回 HTTP 200，results 中 active 记录 status 为 `undone`，expired 记录 status 为 `expired`，undone 记录 status 为 `already_undone`

#### Scenario: 空请求

- **GIVEN** 空 undoLogIds 数组
- **WHEN** `POST /api/connections/conn-1/op-logs/batch-undo { undoLogIds: [] }`
- **THEN** 返回 HTTP 400

#### Scenario: 记录不存在

- **GIVEN** undoLogIds 包含不存在的 ID
- **WHEN** 批量 undo 请求
- **THEN** 不存在的 ID 对应 result status 为 `not_found`

### Requirement: 批量 undo SHALL 逐条执行

批量 undo SHALL 按顺序逐条调用 UndoExecuteService 执行 inverse SQL。单条失败 SHALL NOT 影响后续记录的执行。每条结果独立返回成功或失败状态。

#### Scenario: 中间一条执行失败

- **GIVEN** 3 条记录，第 2 条 inverse SQL 执行失败（目标数据库错误）
- **WHEN** 批量 undo 执行
- **THEN** 第 1 条和第 3 条 SHALL 正常执行并返回 `undone`
- **AND** 第 2 条 SHALL 返回 `error` 状态并附带错误信息

### Requirement: 批量 undo 前端汇总确认

前端 SHALL 在用户选择多条记录并点击 "Batch Undo" 后，展示汇总确认对话框。对话框 SHALL 列出所有选中记录的表名、操作类型、affected_rows 和 inverse SQL 摘要。用户确认后 SHALL 调用批量 undo API。

#### Scenario: 展示汇总确认对话框

- **GIVEN** 用户选中 3 条 active/undoable 记录
- **WHEN** 点击 "Batch Undo" 按钮
- **THEN** 展示 AlertDialog，包含：
  - 标题："Undo 3 operations?"
  - 每条记录的表名 + 操作 + affected rows
  - inverse SQL 摘要（截断至 200 字符）
  - 确认按钮使用 warning variant

#### Scenario: 确认后执行

- **GIVEN** 汇总确认对话框已展示
- **WHEN** 用户点击确认
- **THEN** 调用 `POST /api/connections/{id}/op-logs/batch-undo`
- **AND** 结果返回后更新列表中对应记录的状态

#### Scenario: 选中记录中包含不可 undo 的条目

- **GIVEN** 用户选中 4 条记录，其中 1 条 status=expired
- **WHEN** 点击 "Batch Undo"
- **THEN** 汇总对话框 SHALL 仅展示 undoable 的 3 条记录
- **AND** 不可 undo 的 1 条 SHALL 以灰色/disabled 状态标示
