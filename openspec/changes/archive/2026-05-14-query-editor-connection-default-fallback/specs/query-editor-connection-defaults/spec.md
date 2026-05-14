## ADDED Requirements

### Requirement: Connection default database fallback on context-override write

When a SQL editor's `contextOverride` is written with a `connectionId` and the `database` field is **unspecified** (caller passes `undefined` / omits the field), the system SHALL substitute the target `Connection`'s configured `databaseName` for `database` before persisting. When the caller specifies `database` explicitly (including `null`), the system SHALL persist the caller's value as-is.

#### Scenario: User opens new SQL editor with a connection that has a default database
- **GIVEN** a Connection `A` with `databaseName = "analytics"` configured in settings
- **WHEN** the user opens a new SQL editor (toolbar `+` or empty-state button) and the open input specifies `connectionId = "A"` with no `database` field
- **THEN** the resulting `contextOverride.database` MUST be `"analytics"`

#### Scenario: User opens new SQL editor with a connection that has no default database
- **GIVEN** a Connection `B` with `databaseName = null`
- **WHEN** the user opens a new SQL editor specifying `connectionId = "B"` with no `database` field
- **THEN** the resulting `contextOverride.database` MUST be `null`

#### Scenario: AI calls set_context omitting database
- **GIVEN** an open AI SQL editor and a Connection `A` with `databaseName = "analytics"`
- **WHEN** `QueryEditorAdapter.set_context` is invoked with payload `{ connectionId: "A" }` (no `database` key)
- **THEN** the editor's `contextOverride.database` MUST be `"analytics"`

#### Scenario: AI calls set_context with explicit null database
- **GIVEN** an open AI SQL editor and a Connection `A` with `databaseName = "analytics"`
- **WHEN** `QueryEditorAdapter.set_context` is invoked with payload `{ connectionId: "A", database: null }`
- **THEN** the editor's `contextOverride.database` MUST be `null` (no fallback)

#### Scenario: AI calls set_context with explicit string database
- **GIVEN** an open AI SQL editor and a Connection `A` with `databaseName = "analytics"`
- **WHEN** `QueryEditorAdapter.set_context` is invoked with payload `{ connectionId: "A", database: "warehouse" }`
- **THEN** the editor's `contextOverride.database` MUST be `"warehouse"`

#### Scenario: AI changes connection and sets schema in the same call
- **GIVEN** an open AI SQL editor with current `contextOverride = { connectionId: "A", database: null, schema: null }`
- **AND** a Connection `B` with `databaseName = "warehouse"` exists
- **WHEN** `QueryEditorAdapter.set_context` is invoked with payload `{ connectionId: "B", schema: "public" }` (no `database`)
- **THEN** the call MUST succeed (the schema guard MUST evaluate `database` after fallback substitution, not against the prior `null`)
- **AND** the editor's `contextOverride` MUST become `{ connectionId: "B", database: "warehouse", schema: "public" }`

### Requirement: Chat-driven "Run SQL" path honors connection default

When the user clicks the "Run SQL" button on a SQL code block in the AI chat, the resulting query editor SHALL receive `database` from the **session's current `dataContext.database` if non-null**, otherwise from the **target Connection's `databaseName`**, otherwise `null`. The same priority applies to the AI `execute_sql` tool renderer that opens a query editor.

#### Scenario: Chat "Run SQL" with session that already has a database
- **GIVEN** the current session's `dataContext = { connectionId: "A", database: "analytics", schema: null }`
- **WHEN** the user clicks "Run SQL" on a code block
- **THEN** the opened editor's `contextOverride.database` MUST be `"analytics"`

#### Scenario: Chat "Run SQL" with session that has connection but no database
- **GIVEN** the current session's `dataContext = { connectionId: "A", database: null, schema: null }`
- **AND** Connection `A` has `databaseName = "analytics"`
- **WHEN** the user clicks "Run SQL" on a code block
- **THEN** the opened editor's `contextOverride.database` MUST be `"analytics"` (connection default applied)

#### Scenario: Chat "Run SQL" with connection that has no default
- **GIVEN** the current session's `dataContext = { connectionId: "B", database: null, schema: null }`
- **AND** Connection `B` has `databaseName = null`
- **WHEN** the user clicks "Run SQL" on a code block
- **THEN** the opened editor's `contextOverride.database` MUST be `null`

#### Scenario: Chat execute_sql tool renderer opens query editor
- **GIVEN** the AI emitted an `execute_sql` tool message and the current session has `dataContext = { connectionId: "A", database: null }` while Connection `A` has `databaseName = "analytics"`
- **WHEN** the user clicks the renderer's "Open in query editor" button
- **THEN** the opened editor's `contextOverride.database` MUST be `"analytics"`

### Requirement: ER designer DDL handoff honors connection default

When the ER designer generates DDL and opens it in a query editor, if the user did not pick a `targetDatabase` for the DDL (or picked nothing), the opened editor SHALL receive `database` from the target Connection's `databaseName`. When the user explicitly chose a target database, that value SHALL be preserved.

#### Scenario: ER designer with explicit target database
- **GIVEN** an ER designer draft with `targetConnectionId = "A"`, `targetDatabase = "staging"`
- **AND** Connection `A` has `databaseName = "analytics"`
- **WHEN** the user generates DDL and clicks "Open in query editor"
- **THEN** the opened editor's `contextOverride.database` MUST be `"staging"` (user's explicit choice wins)

#### Scenario: ER designer without target database falls back to connection default
- **GIVEN** an ER designer draft with `targetConnectionId = "A"`, `targetDatabase = null`
- **AND** Connection `A` has `databaseName = "analytics"`
- **WHEN** the user generates DDL and clicks "Open in query editor"
- **THEN** the opened editor's `contextOverride.database` MUST be `"analytics"` (connection default applied)

### Requirement: Re-seed database on connection change

When `setQueryEditorContext` is called with a `connectionId` that differs from the editor's current `connectionId` AND the patch does not specify `database`, the system SHALL re-seed `database` from the new connection's `databaseName`. The previous database value SHALL NOT be carried over.

#### Scenario: User switches the connection in the toolbar dropdown
- **GIVEN** an editor currently bound to Connection `A` (`databaseName = "analytics"`) with `contextOverride.database = "analytics"`
- **AND** a Connection `B` exists with `databaseName = "warehouse"`
- **WHEN** the user picks `B` in the connection dropdown (dropdown's onChange omits `database`)
- **THEN** the editor's `contextOverride.database` MUST become `"warehouse"`

#### Scenario: User switches to a connection without a default database
- **GIVEN** an editor currently bound to Connection `A` (`databaseName = "analytics"`)
- **AND** a Connection `C` exists with `databaseName = null`
- **WHEN** the user picks `C` in the connection dropdown
- **THEN** the editor's `contextOverride.database` MUST become `null`

#### Scenario: Connection unchanged, database omitted in patch
- **GIVEN** an editor with `contextOverride = { connectionId: "A", database: "warehouse" }`
- **WHEN** `setQueryEditorContext` is called with a patch that does not change `connectionId` and does not specify `database`
- **THEN** the editor's `contextOverride.database` MUST remain `"warehouse"` (no fallback re-fires)

### Requirement: Explicit user clear is preserved

When the caller writes `database: null` explicitly (e.g., user picks the empty option in the database dropdown, or AI explicitly sends `null`), the system SHALL persist `null` and SHALL NOT replace it with a connection default.

#### Scenario: User picks "无 / None" in the database dropdown
- **GIVEN** an editor bound to Connection `A` (`databaseName = "analytics"`) with `contextOverride.database = "analytics"`
- **WHEN** the user picks the empty option (toolbar emits `setQueryEditorContext({ database: null })`)
- **THEN** the editor's `contextOverride.database` MUST be `null`
- **AND** the value MUST remain `null` until the user makes another explicit change

### Requirement: Helper no-ops when connection cache is empty

When the connection list is empty (e.g., during app boot before `listConnections` resolves), the fallback helper SHALL NOT write a substitute value and SHALL leave `database` at its caller-or-current value. The system SHALL NOT block, retry, or asynchronously wait for the list to load.

#### Scenario: Set context fires before connection list is loaded
- **GIVEN** `useConnectionStore.connections` is `[]`
- **WHEN** `setQueryEditorContext({ connectionId: "A" })` is invoked with no `database`
- **THEN** the editor's `contextOverride.database` MUST equal the prior effective database (`null` if first write)
- **AND** no error MUST be thrown
- **AND** the next write that occurs after the connection list loads MUST trigger fallback normally

### Requirement: Session rebind path bypasses fallback

When `setQueryEditorContext` is invoked with `useSessionContext: true` (the AI-editor session-rebind path), the system SHALL clear `contextOverride` and SHALL NOT invoke the connection-default fallback. The resulting database is whatever the session's `connectionContext` provides via the resolver.

#### Scenario: AI editor rebinds to active session
- **GIVEN** an AI editor with `contextOverride = { connectionId: "A", database: "warehouse" }`
- **AND** the active session has `connectionContext = { connectionId: "B", database: null }`
- **WHEN** `setQueryEditorContext({ useSessionContext: true })` is invoked
- **THEN** `contextOverride` MUST be `null`
- **AND** the effective database MUST come from the session's `connectionContext`, not from Connection `B`'s `databaseName`

### Requirement: Schema field is unaffected

The `schema` field in `contextOverride` SHALL NOT be subject to connection-default fallback. The `Connection` record does not carry a default schema today, so `schema` continues to be a per-editor explicit choice.

#### Scenario: Patch sets connection but not schema
- **GIVEN** a Connection `A` with `databaseName = "analytics"`
- **WHEN** `setQueryEditorContext({ connectionId: "A" })` is invoked (no `database`, no `schema`)
- **THEN** `contextOverride.database` MUST be `"analytics"` (fallback fires)
- **AND** `contextOverride.schema` MUST equal the prior effective schema (`null` if first write — no fallback applied)
