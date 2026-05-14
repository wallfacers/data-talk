## MODIFIED Requirements

### Requirement: SQL 预览、受影响对象、L2/L3 描述、L3 不可撤销提示 SHALL 全部保留

对话框内容区 SHALL 渲染下列信息（无一可少）：

1. SQL 预览块：以 `<pre>` 等同语义 + 等宽字体 (`typography.mono-sm`) 展示 `sqlPreview` 全文，超长 SHALL 横向滚动 (`overflow-x-auto`)；背景与边框 SHALL 跟随风险等级：L2 使用 `accent.warnSurface` 背景 + amber 半透明边框，L3 使用 `status.dangerSurface` 背景 + danger 半透明边框。该染色与顶部彩色边带、Badge 颜色共同形成同色系视觉提示。
2. "受影响对象"标签 + 对象列表：标签使用 `sqlConfirmation.affectedObjects` 的 i18n 值；列表以等宽字体逐行展示 `affectedObjects`；当数组为空时占位符为 `—`。
3. L2/L3 描述段：L2 渲染 `sqlConfirmation.l2.body`，L3 渲染 `sqlConfirmation.l3.body`，文案中 `{objects}` 插入对象列表的 `join(', ')` 串。
4. **L3 仅当 `level === 'L3'` 且该操作不在 undo-log 可回滚范围内时，渲染 `sqlConfirmation.l3.irreversible`**，使用 `status.danger` 强调色 + `font-semibold`。对 DML 操作（INSERT/UPDATE/DELETE），SHALL 改为渲染 `sqlConfirmation.l3.undoable` 提示（"此操作可在 3 天内通过 Undo 回滚"/"This action can be undone within 3 days"），使用 `accent.info` 色调。

#### Scenario: L2 完整渲染主体内容

- **GIVEN** `risk = { level: 'L2', reason: 'update_with_where', affectedObjects: ['orders'] }`、`sqlPreview = "UPDATE orders SET status = 'paid' WHERE id = 1"`
- **WHEN** 确认对话框打开
- **THEN** 内容区 SHALL 同时包含：SQL 文本节点（含 `UPDATE orders SET status`）、"受影响对象"标签、列表项 `orders`、描述段"将修改 orders 中的数据。"
- **AND** SHALL NOT 包含 `sqlConfirmation.l3.irreversible` 文案

#### Scenario: L3 DML 操作显示可回滚提示

- **GIVEN** `risk = { level: 'L3', reason: 'delete_without_where', affectedObjects: ['temp_log'] }`、`sqlPreview = "DELETE FROM temp_log WHERE created_at < '2025-01-01'"`、目标表有主键
- **WHEN** 确认对话框打开
- **THEN** 内容区 SHALL 包含 SQL 文本、"受影响对象"标签、描述段
- **AND** SHALL NOT 渲染 `sqlConfirmation.l3.irreversible`（"此操作无法撤销。"）
- **AND** SHALL 渲染 `sqlConfirmation.l3.undoable`（"此操作可在 3 天内通过 Undo 回滚。"）提示，使用 `accent.info` 色调

#### Scenario: L3 DDL 操作仍显示不可撤销提示

- **GIVEN** `risk = { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] }`、`sqlPreview = "DROP TABLE temp_log"`（DDL 操作不在 undo 范围）
- **WHEN** 确认对话框打开
- **THEN** 内容区 SHALL 渲染 `sqlConfirmation.l3.irreversible`（"此操作无法撤销。"），行为与原 spec 一致
