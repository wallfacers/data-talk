## ADDED Requirements

### Requirement: User preferences table
The system SHALL persist user preferences in a `user_preferences` table with a single-row design (keyed by a fixed ID), storing at minimum `timezone` (IANA timezone ID string) and `date_format` (date format pattern string).

#### Scenario: Table schema
- **WHEN** the Flyway migration is applied
- **THEN** a `user_preferences` table exists with columns: `id TEXT PRIMARY KEY`, `timezone TEXT NOT NULL DEFAULT 'UTC'`, `date_format TEXT NOT NULL DEFAULT 'yyyy-MM-dd HH:mm:ss'`, `updated_at INTEGER NOT NULL`

#### Scenario: Default values on first access
- **WHEN** no row exists for the fixed ID
- **THEN** the repository returns a default with `timezone = 'UTC'` and `date_format = 'yyyy-MM-dd HH:mm:ss'`

### Requirement: Read user preferences API
The system SHALL expose a REST endpoint `GET /api/preferences` that returns the current user's timezone and date format preferences.

#### Scenario: Successful read
- **WHEN** a GET request is made to `/api/preferences`
- **THEN** the response contains `{ "timezone": "Asia/Shanghai", "dateFormat": "yyyy-MM-dd HH:mm:ss" }` with HTTP 200

#### Scenario: No preferences stored yet
- **WHEN** a GET request is made and no preferences row exists
- **THEN** the response contains the system default values with HTTP 200

### Requirement: Update user preferences API
The system SHALL expose a REST endpoint `PUT /api/preferences` that accepts `timezone` and `dateFormat` fields and persists them.

#### Scenario: Successful update
- **WHEN** a PUT request is made with `{ "timezone": "America/New_York", "dateFormat": "MM/dd/yyyy HH:mm:ss" }`
- **THEN** the values are persisted and HTTP 200 is returned with the updated values

#### Scenario: Invalid timezone rejected
- **WHEN** a PUT request is made with `{ "timezone": "Not/A_Real_Zone" }`
- **THEN** HTTP 400 is returned with an error message indicating the timezone is not valid

#### Scenario: Partial update
- **WHEN** a PUT request is made with only `{ "timezone": "Asia/Tokyo" }`
- **THEN** the timezone is updated and dateFormat remains unchanged

### Requirement: 清空全部会话时精确清理本地资源

`clearAllLocalSessionResources()` SHALL 精确清理 session-scoped 数据，不再销毁 workspace-scoped stage tab。

#### Scenario: 保留 workspace-scoped tab

- **GIVEN** stage 中有 `query_editor`（workspace scope）和 `artifact_preview`（session scope）tab
- **WHEN** 用户执行"清空全部会话"
- **THEN** `query_editor` tab 保留，`artifact_preview` tab 被关闭
- **AND** stage 面板不关闭（除非所有 tab 都被清除）

#### Scenario: 清理所有 session 关联的 store 数据

- **WHEN** 用户执行"清空全部会话"
- **THEN** 清理 `useChatPartsStore` 中的 session 数据
- **AND** 清理 `useOntologyStore` 中的 session artifacts
- **AND** 清理 `useTimelineStore` 中的 session 数据
- **AND** 清理 `useSessionStore` 中的 active/mode/hasEverSent/dataContext
- **AND** 清理 `useChannelStore` 中的 lastEventId
- **AND** 清理 localStorage 中的 `dt.draft.*` key
- **AND** 调用 `closeSessionTabs()` 精确关闭 session-scoped tab
- **AND** 不再调用 `resetSessionResources()`（已废弃，替换为 `closeSessionTabs()`）
