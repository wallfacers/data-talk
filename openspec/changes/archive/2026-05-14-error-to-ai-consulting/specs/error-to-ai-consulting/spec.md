## ADDED Requirements

### Requirement: Ask AI button on SQL error panel

The `SqlErrorResultPanel` component SHALL render an "问 AI" button. When clicked, the system SHALL generate a structured markdown message containing the full error context and populate it into the `PromptComposer` of the active session.

The generated markdown SHALL include:
- Error title ("SQL 执行报错")
- Connection name and kind
- Database name
- Schema name
- The executed SQL statement (in a code fence)
- The error message (in a code fence)

The button SHALL use `accent.primary` semantic token for its text/icon color and SHALL include a visible icon (e.g., Sparkles or MessageCircle).

#### Scenario: User clicks Ask AI with full context

- **GIVEN** a SQL editor tab with `executeStatus = 'error'` and active result kind `'error'`
- **AND** the tab has `resolvedContext = { connectionId: 'conn-1', connectionName: 'TiDB-prod', database: 'analytics', schema: 'public' }`
- **AND** the error result has `statementText = 'SELECT * FROM nonexistent'` and `errorMessage = 'Table does not exist'`
- **WHEN** the user clicks the "问 AI" button
- **THEN** the `PromptComposer` SHALL be populated with a markdown message containing connection name "TiDB-prod", database "analytics", schema "public", the SQL statement, and the error message
- **AND** the composer SHALL gain focus
- **AND** the user SHALL be able to edit the text before sending

#### Scenario: Ask AI button hidden when no active session

- **GIVEN** a SQL editor tab with an error result
- **AND** no active session exists
- **WHEN** the error panel renders
- **THEN** the "问 AI" button SHALL NOT render

#### Scenario: Ask AI button hidden during execution

- **GIVEN** a SQL editor tab with `executeStatus = 'running'`
- **WHEN** the error panel renders
- **THEN** the "问 AI" button SHALL NOT render

### Requirement: Generic error-to-AI hook

The system SHALL provide a `useAskAIAboutError` hook that accepts an error context object and returns an `askAI` function and an `isAvailable` boolean.

The hook SHALL:
- Accept `ErrorContext` with fields: `title`, `connectionName`, `connectionKind`, `database`, `schema`, `statementText`, `errorMessage`, and optional `extraContext`
- Generate a standardized markdown message from the context
- Populate the active session's `PromptComposer` with the generated text
- Return `isAvailable = false` when no active session exists or the composer is already streaming a response

The hook SHALL NOT send the message automatically.

#### Scenario: Hook populates composer with markdown

- **GIVEN** an active session exists and is not streaming
- **AND** an error context with `title = '连接失败'`, `connectionName = 'MySQL-prod'`, `errorMessage = 'Connection refused'`
- **WHEN** `askAI()` is called
- **THEN** the `PromptComposer` SHALL contain a markdown message with heading "连接失败", connection name "MySQL-prod", and error "Connection refused"
- **AND** `isAvailable` SHALL be `true`

#### Scenario: Hook unavailable without active session

- **GIVEN** no active session
- **WHEN** `isAvailable` is read
- **THEN** it SHALL be `false`

### Requirement: Error context markdown template

The system SHALL generate error-to-AI messages using a standardized markdown template.

The template SHALL follow this structure:
```
**{title}**

- **连接**: {connectionName} ({connectionKind})
- **数据库**: {database}
- **Schema**: {schema}
- **执行的 SQL**:
```sql
{statementText}
```
- **错误信息**:
```
{errorMessage}
```
```

Fields that are null or empty SHALL be rendered as "未设置" (or the localized equivalent). The `statementText` section SHALL be omitted when `statementText` is empty. The `connectionKind`, `database`, and `schema` fields SHALL be omitted when their values are null.

#### Scenario: Full context generates complete markdown

- **GIVEN** all fields in `ErrorContext` are populated
- **WHEN** the markdown template is generated
- **THEN** all sections (连接, 数据库, Schema, SQL, 错误信息) SHALL appear in the output

#### Scenario: Partial context omits empty sections

- **GIVEN** `ErrorContext` with `database = null` and `schema = null`
- **WHEN** the markdown template is generated
- **THEN** the 数据库 and Schema sections SHALL NOT appear
- **AND** the 连接 and 错误信息 sections SHALL still appear
