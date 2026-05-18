## ADDED Requirements

### Requirement: list_connections 响应携带 session 激活态

`datatalk_list_connections` Action 的响应载荷 SHALL 包含两类 session 激活态标记，使 AI 即使在未先调用 `datatalk_get_data_context` 的情况下也能从单次响应中判断当前会话激活的数据源：

1. **顶层字段** `activeSessionConnectionId`：`string | null`。当当前 session 的 `SessionDataContextRecord` 存在且包含 `connectionId` 时，等于该 `connectionId`；否则为 `null`。该字段 MUST 始终出现在响应根对象中（值可为 `null`，但 key 不可省略）。
2. **每项字段** `isActiveInSession`：`boolean`。对响应 `connections` 数组中的每一条 connection 对象，该字段值等于 `connection.id == activeSessionConnectionId`。该字段 MUST 始终出现（值为 `true` 或 `false`，不允许省略），不允许仅在 `true` 时出现。

字段命名 MUST 使用 lowerCamelCase 与既有字段保持一致。`outputSchema()` MUST 同步声明这两个新字段（顶层字段加入 properties，但不加入 required；每项字段在 connection 子对象 schema 中声明为 required）。

#### Scenario: 有激活连接的 session 响应携带正确标记

- **GIVEN** 当前 session `S-1` 的 `SessionDataContextRecord` 存在，`connectionId = "C-2"`
- **AND** `ConnectionService.list()` 返回 3 条连接 `[C-1, C-2, C-3]`
- **WHEN** 通过 ActionContext(sessionId="S-1") 调用 `ListConnectionsAction.handle`
- **THEN** 响应根对象的 `activeSessionConnectionId` 等于 `"C-2"`
- **AND** 响应 `connections[0]` (id=C-1) 的 `isActiveInSession` 字段存在且等于 `false`
- **AND** 响应 `connections[1]` (id=C-2) 的 `isActiveInSession` 字段存在且等于 `true`
- **AND** 响应 `connections[2]` (id=C-3) 的 `isActiveInSession` 字段存在且等于 `false`

#### Scenario: 无激活连接的 session 响应字段仍存在

- **GIVEN** 当前 session `S-2` 没有 `SessionDataContextRecord` 或其 `connectionId` 为 `null`
- **AND** `ConnectionService.list()` 返回 2 条连接 `[C-1, C-2]`
- **WHEN** 通过 ActionContext(sessionId="S-2") 调用 `ListConnectionsAction.handle`
- **THEN** 响应根对象的 `activeSessionConnectionId` 字段存在且值等于 `null`
- **AND** 响应 `connections[0]` 与 `connections[1]` 的 `isActiveInSession` 字段都存在且都等于 `false`

#### Scenario: outputSchema 显式声明新字段

- **WHEN** 调用 `ListConnectionsAction.outputSchema()`
- **THEN** 返回的 schema map 中 `properties` 含 key `activeSessionConnectionId`，类型声明为 `string` 且 `nullable=true`（或等价表达）
- **AND** `properties.connections.items.properties` 含 key `isActiveInSession`，类型声明为 `boolean`
- **AND** `properties.connections.items.required` 数组包含 `"isActiveInSession"`

### Requirement: connection-management skill 含 Answer-Style Playbook

`classpath:/skills/connection-management/SKILL.md` SHALL 在 `## Session data context` 段落之后或之内新增一个 Answer-Style Playbook 小节，明示三类常见问答的标准调用顺序：

1. **"哪个连接 / 哪个数据库 / 当前用什么"（session-attribution）** → 先 `datatalk_get_data_context` → 再渲染答复，无需 `datatalk_list_connections`。
2. **"有哪些数据库 / 有哪些 schema"（当前连接的子目标）** → 先 `datatalk_get_data_context` 确认有 connection → 再 `datatalk_list_connection_targets`。
3. **"有哪些连接 / 列出所有保存的数据源"（全局列表）** → `datatalk_list_connections` 后，从响应里的 `activeSessionConnectionId` / `isActiveInSession` 字段告知用户当前激活的是哪条。

#### Scenario: SKILL.md 含 Answer-Style Playbook 段

- **GIVEN** `classpath:/skills/connection-management/SKILL.md`
- **WHEN** 扫描文本
- **THEN** 文件包含字面子串 "Answer-Style Playbook" 或语义等价的章节标题（如 "回答模板" / "Answer playbook"）
- **AND** 该段落中至少各出现一次 `datatalk_get_data_context`、`datatalk_list_connection_targets`、`datatalk_list_connections`、`activeSessionConnectionId`
- **AND** 该段落中至少出现一次 "session-attribution" 或语义等价的中文短语

### Requirement: i18n description 文案与 schema 行为一致

`action.list_connections.description` 在 `messages.properties` 与 `messages_zh_CN.properties` 中的文案 SHALL 与本 capability 的 schema 行为保持一致：明示"该工具列出的是全局已保存连接，不指示当前 session 激活态，欲查询激活态请使用 `datatalk_get_data_context`"。

#### Scenario: 英文文案含全局/会话提示

- **WHEN** 读取 `messages.properties` 中 key `action.list_connections.description`
- **THEN** value 包含子串 "does NOT" 或 "global" 或 "not session"（不区分大小写）
- **AND** value 包含字面子串 `datatalk_get_data_context`

#### Scenario: 中文文案含全局/会话提示

- **WHEN** 读取 `messages_zh_CN.properties` 中 key `action.list_connections.description`
- **THEN** value 包含子串 "不反映" 或 "全局" 或 "不指示" 之一
- **AND** value 包含字面子串 `datatalk_get_data_context`
