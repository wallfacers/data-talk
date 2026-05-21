## MODIFIED Requirements

### Requirement: 对话框 SHALL 仅渲染单层卡片，不嵌套子卡片

SQL 风险确认对话框 SHALL 使用单一的 shadcn `AlertDialogContent` 作为视觉容器。内部内容区域 (`SqlConfirmationCard` 渲染结果) SHALL NOT 包含独立的圆角、外边框或大色块填充背景（即不构成"对话框内的另一张卡片"）。此对话框仅用于**用户在编辑器中手动触发**的 L2/L3 SQL 执行确认。AI 路径的确认已移至对话式机制（见 `conversational-sql-confirmation` spec）。

#### Scenario: dialog 内只有一层圆角边框容器

- **GIVEN** 用户在 SQL workbench 手动点击 Run 按钮，SQL 包含 L2 风险（如 `UPDATE orders SET ... WHERE id = 1`）
- **WHEN** 确认对话框打开
- **THEN** DOM 中只有 `AlertDialogContent` 一个元素具有 `rounded` + `border` + 阴影类
- **AND** 其子元素 `[data-testid="sql-risk-panel"]` SHALL NOT 同时具有 `rounded-md`、`border`、整片背景填充类

#### Scenario: L3 触发时仍只有一层卡片

- **GIVEN** SQL workbench 触发 L3 风险（如 `DROP TABLE temp_log`）
- **WHEN** 确认对话框打开
- **THEN** DOM 结构与 L2 一致，仅风险标识（彩色边带 + Badge）从 amber 切到 danger，不引入新的卡片层

## REMOVED Requirements

### Requirement: 前端 BlockedInChatCard 组件及编辑器路由

**Reason**: AI 路径不再拦截 L2/L3 SQL，`BlockedInChatCard` 组件及"Open in Workbench"按钮不再有触发场景。AI 路径的 DELETE 确认已移至对话式机制（`conversational-sql-confirmation` spec）。

**Migration**: 前端 `execute-sql.tsx` 中的 `BlockedInChatCard` 组件及其依赖（`useStageStore`、`useConnectionStore`、`useSessionStore`、`Button`）SHALL 被移除。`execute-sql.tsx` 简化为纯结果渲染函数。
