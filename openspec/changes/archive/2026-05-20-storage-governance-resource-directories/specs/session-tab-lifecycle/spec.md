## ADDED Requirements

### Requirement: 清空全部会话时精确关闭 Session-scoped Tab

系统 SHALL 在清空全部会话时，只关闭 scope 为 'session' 的 tab（`artifact_preview`、`files`），保留 workspace-scoped tab。

#### Scenario: 清空全部会话保留 workspace tab

- **GIVEN** 用户打开了 1 个 `query_editor` tab、1 个 `dashboard` tab、1 个 `artifact_preview` tab
- **WHEN** 用户执行"清空全部会话"
- **THEN** `query_editor` tab 和 `dashboard` tab 保持打开
- **AND** `artifact_preview` tab 被关闭（因为其 scope='session'）
- **AND** 若关闭了当前 active tab，自动切换到下一个可用 tab

#### Scenario: 无 session-scoped tab 时不清除

- **GIVEN** 用户只打开了 workspace-scoped tab
- **WHEN** 用户执行"清空全部会话"
- **THEN** 所有 tab 保持打开，stage 面板状态不变

#### Scenario: 仅 session-scoped tab 时关闭 stage

- **GIVEN** 用户只打开了 `artifact_preview` tab（session-scoped）
- **WHEN** 用户执行"清空全部会话"
- **THEN** 所有 tab 关闭，stage 面板关闭（`open: false`）

### Requirement: 单 Session 删除时关闭关联 Tab

系统 SHALL 在单个 session 被删除时，关闭所有引用该 session 的 session-scoped tab。

#### Scenario: 侧边栏删除 session

- **GIVEN** 用户有 session A（当前 active），有一个 `artifact_preview` tab 引用 session A
- **WHEN** 用户从侧边栏删除 session A
- **THEN** session A 被删除
- **AND** 引用 session A 的 `artifact_preview` tab 被关闭
- **AND** 触发新的空 session 创建（现有行为）

#### Scenario: 删除非当前 session

- **GIVEN** 用户有 session A（当前 active），session B 有一个 `artifact_preview` tab
- **WHEN** 用户从侧边栏删除 session B
- **THEN** 引用 session B 的 tab 被关闭
- **AND** 当前 active session（session A）保持不变
- **AND** stage 面板状态不受影响（除非被关闭的 tab 是 activeTabId）

### Requirement: Session-scoped Tab 识别

系统 SHALL 通过 `tab-type-registry` 的 `TabTypeDescriptor.scope` 字段识别 session-scoped tab。

#### Scenario: scope 查询

- **WHEN** 系统需要判断一个 tab 是否为 session-scoped
- **THEN** 从 `tab-type-registry` 查找该 tab 的 `type` 对应的 `TabTypeDescriptor`
- **AND** 若 `descriptor.scope === 'session'`，则该 tab 为 session-scoped
- **AND** 当前 session-scoped 的 tab type 有：`artifact_preview`、`files`

### Requirement: closeSessionTabs 工具函数

系统 SHALL 在 stage-store 中提供 `closeSessionTabs(sessionId?: string)` 函数用于精确关闭 session-scoped tab。

#### Scenario: 关闭指定 session 的 tab

- **WHEN** 调用 `closeSessionTabs('sess-123')`
- **THEN** 关闭所有 `originSessionId === 'sess-123'` 且 scope='session' 的 tab
- **AND** 不关闭 workspace-scoped tab 也不关闭其他 session 的 tab

#### Scenario: 关闭所有 session 的 tab（无参数）

- **WHEN** 调用 `closeSessionTabs()`（无参数）
- **THEN** 关闭所有 scope='session' 的 tab（不限于特定 session）
- **AND** workspace-scoped tab 保持打开
