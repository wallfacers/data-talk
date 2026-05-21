## ADDED Requirements

### Requirement: Bound-session context resolution

The system SHALL resolve a query editor tab's execution context (connection, database, schema) from a tab-level `boundSessionId` field. Each tab SHALL carry `boundSessionId`. On creation, `boundSessionId` SHALL equal the originating session id (the active session at tab creation). The editor's effective context SHALL be read from the session identified by `boundSessionId`, regardless of which session is currently active.

This binding rule SHALL apply uniformly to `source == 'ai'` and `source == 'user'` editors. The two source types differ only in whether `boundSessionId` can change after creation (see "Manual re-bind" requirement) and in toolbar UI affordances (toggle visibility, mismatch badge).

#### Scenario: AI editor preserves bound context after session switch

- **GIVEN** an AI-opened SQL editor with `boundSessionId = A`, where A's context is `{conn: data-uat, db: orders, schema: public}`
- **WHEN** the user switches the active session to session B
- **THEN** the editor's effective context SHALL remain `{conn: data-uat, db: orders, schema: public}` (read from bound session A)
- **AND** `canRun` SHALL remain true if SQL text is non-empty
- **AND** the toolbar toggle SHALL render OFF (derived: bound != active)
- **AND** the mismatch badge SHALL appear

#### Scenario: User editor unchanged across session switch

- **GIVEN** a user-opened SQL editor with `boundSessionId = A`, where A's context is `{conn: data-uat, db: orders, schema: public}`
- **WHEN** the user switches the active session to session B
- **THEN** the editor's effective context SHALL remain `{conn: data-uat, db: orders, schema: public}` (read from bound session A)
- **AND** `canRun` SHALL remain true if SQL text is non-empty
- **AND** no toggle SHALL be rendered
- **AND** no mismatch badge SHALL appear

#### Scenario: boundSessionId defaults to originSessionId at creation

- **WHEN** a new query editor tab is created (by AI tool action or user toolbar action)
- **THEN** `boundSessionId` SHALL be set to the active session id at creation time
- **AND** `originSessionId` SHALL also be set to the same value
- **AND** the toggle SHALL render ON (derived: bound == active, no override)

### Requirement: Toggle is a derived UI signal, not stored state

The "use session context" toggle in the SQL editor toolbar SHALL be rendered as a derived value computed from `boundSessionId`, `activeSessionId`, and `contextOverride`. The system SHALL NOT persist a `useSessionContext: boolean` field as the source of truth for the toggle's display.

Computation rule:
- `toggle = ON  ⟺  boundSessionId === activeSessionId  AND  contextOverride == null`
- `toggle = OFF  otherwise`

#### Scenario: Toggle reflects re-binding state

- **GIVEN** an AI editor with `boundSessionId = A` and `contextOverride = null`
- **WHEN** the active session is A
- **THEN** the toggle SHALL render as ON

- **WHEN** the active session is switched to B
- **THEN** the toggle SHALL render as OFF without any state field write

#### Scenario: Toggle reflects manual override

- **GIVEN** an AI editor with `boundSessionId = A`, active session A, `contextOverride = null` (toggle ON)
- **WHEN** the user picks a different connection in the toolbar dropdown
- **THEN** `contextOverride` SHALL be set with the new selection
- **AND** the toggle SHALL render as OFF

#### Scenario: Persisted payload without useSessionContext flag still renders correctly

- **GIVEN** a freshly hydrated tab whose payload contains `boundSessionId` but no `useSessionContext` flag
- **WHEN** the toolbar renders
- **THEN** the toggle SHALL be derived correctly from the binding rule above without reading any stored boolean

### Requirement: Manual re-bind action

Activating the toggle when it is currently OFF SHALL re-bind the editor to the active session:
1. `boundSessionId := activeSessionId`
2. `contextOverride := null`

After this action completes, the editor SHALL immediately use the active session's current context for display and execution.

Activating the toggle when it is currently ON SHALL be a no-op.

#### Scenario: Manual ON synchronizes to new session

- **GIVEN** an AI editor with `boundSessionId = A`, active session = B, toggle OFF
- **AND** session B has context `{conn: prod-db, db: null, schema: null}`
- **WHEN** the user activates the toggle
- **THEN** `boundSessionId` SHALL be set to B
- **AND** `contextOverride` SHALL be set to null
- **AND** the toolbar SHALL display `prod-db / 未设置 / 未设置`

#### Scenario: Manual ON clears prior override

- **GIVEN** an AI editor with `boundSessionId = A`, active session = A, `contextOverride = {conn: x, db: y, schema: z}` (toggle OFF)
- **WHEN** the user activates the toggle
- **THEN** `contextOverride` SHALL be set to null
- **AND** the editor SHALL show A's session context (not the previous override)

### Requirement: Mismatch indicator badge for AI editors

When `source == 'ai'` AND `boundSessionId != activeSessionId`, the toolbar SHALL render a textual badge with the format `来自会话: <session title>` where `<session title>` is the title of the session identified by `boundSessionId`. The badge SHALL:
- Be visible whenever the mismatch condition holds
- Be informational (non-interactive)
- Use `text.muted` for the label and `text.base` for the title, per `client/DESIGN.md`
- Truncate the session title with ellipsis if it exceeds the available width

When the mismatch condition is false, the badge SHALL NOT render.

#### Scenario: Badge appears on session switch

- **GIVEN** an AI editor with `boundSessionId = A`, session A titled "数据探索"
- **WHEN** the user switches active session to B
- **THEN** the toolbar SHALL render a badge containing the text `来自会话: 数据探索`

#### Scenario: Badge hidden when bound matches active

- **GIVEN** an AI editor with `boundSessionId = A`, active session = A
- **WHEN** the toolbar renders
- **THEN** no mismatch badge SHALL appear

#### Scenario: Badge hidden for user editors

- **GIVEN** a user-opened editor in any session-mismatch state
- **WHEN** the toolbar renders
- **THEN** no mismatch badge SHALL appear

### Requirement: User editor toolbar omits the toggle

For query editor tabs with `source == 'user'`, the toolbar SHALL NOT render the "use session context" toggle. The toolbar SHALL render the connection, database, schema, and row-limit selects. All selects SHALL be enabled at all times.

Initial values for the selects SHALL come from the snapshot of the originating session's context captured at tab creation, persisted in the tab's payload (`connectionId`, `database`, `schema`).

#### Scenario: User editor toolbar has no toggle

- **GIVEN** a user-opened SQL editor
- **WHEN** the toolbar renders
- **THEN** the toggle widget SHALL NOT be present in the DOM
- **AND** connection / database / schema / limit selects SHALL be present and enabled

### Requirement: Run-gate rule unchanged

The system SHALL preserve the existing rule for enabling the run button:

`canRun = Boolean(effectiveContext.connectionId) AND sql.trim().length > 0`

The system SHALL NOT require a database or schema selection to enable the run button.

Additionally, the system SHALL make the "问 AI" button available on error result panels whenever `effectiveContext.connectionId` is populated (the same condition as `canRun` without the SQL text requirement). The button SHALL use the tab's `resolvedContext` to assemble the error-to-AI markdown context.

#### Scenario: Run enabled with connection only

- **GIVEN** an AI editor whose bound session has `{conn: data-uat, db: null, schema: null}` and SQL text is `SELECT 1`
- **WHEN** the user attempts to run
- **THEN** the run button SHALL be enabled

#### Scenario: Run disabled when connection is null

- **GIVEN** an AI editor whose bound session has no connection set and no override is configured
- **WHEN** the toolbar renders
- **THEN** the run button SHALL be disabled
- **AND** a tooltip SHALL explain why (e.g., "请选择数据连接")

#### Scenario: Ask AI button available when connection exists

- **GIVEN** a SQL editor tab with an error result and `resolvedContext.connectionId != null`
- **WHEN** the error panel renders
- **THEN** the "问 AI" button SHALL be visible and enabled

#### Scenario: Ask AI button hidden when connection is null

- **GIVEN** a SQL editor tab with an error result and `resolvedContext.connectionId == null`
- **WHEN** the error panel renders
- **THEN** the "问 AI" button SHALL NOT render

### Requirement: AI set_context action honors the new model

The QueryEditor adapter's `set_context` action invoked by AI SHALL map onto the new binding model:
- `set_context({useSessionContext: true})` on an `ai` editor SHALL be equivalent to manual re-bind: `boundSessionId := activeSessionId`, `contextOverride := null`
- `set_context({useSessionContext: true})` on a `user` editor SHALL be a no-op (a user editor has no "follow session" semantic)
- `set_context({connectionId, database, schema})` SHALL write `contextOverride` for both source types

#### Scenario: AI re-binds its own editor

- **GIVEN** an AI editor with `boundSessionId = A`, active session = B
- **WHEN** AI invokes `set_context({useSessionContext: true})`
- **THEN** `boundSessionId` SHALL be set to B and `contextOverride` SHALL be set to null

#### Scenario: AI cannot follow-session a user editor

- **GIVEN** a user-opened editor in any state
- **WHEN** AI invokes `set_context({useSessionContext: true})`
- **THEN** the editor state SHALL NOT change
- **AND** the adapter SHALL return success without effect (or a non-fatal warning result)

#### Scenario: AI sets override on either source type

- **GIVEN** an editor of either source type
- **WHEN** AI invokes `set_context({connectionId: 'conn-x', database: 'db-y', schema: 'sc-z'})`
- **THEN** `contextOverride` SHALL be set to `{conn-x, db-y, sc-z}`
- **AND** subsequent rendering SHALL reflect the override

### Requirement: Persistence migration for legacy payloads

The query editor payload normalizer SHALL accept stored payloads that lack `boundSessionId` and back-fill the field on hydrate:

`boundSessionId := payload.boundSessionId ?? tab.originSessionId`

The normalizer SHALL ignore any persisted `useSessionContext` value on read, treating the field as deprecated. Subsequent writes of the payload SHALL persist `boundSessionId` and SHALL NOT write `useSessionContext`.

#### Scenario: Legacy payload hydrates with boundSessionId default

- **GIVEN** a stored stage tab payload with `originSessionId = 'sess-A'` and no `boundSessionId` field
- **WHEN** the payload is hydrated
- **THEN** the normalized payload SHALL have `boundSessionId = 'sess-A'`

#### Scenario: Legacy useSessionContext ignored

- **GIVEN** a stored payload with `useSessionContext: true` and `boundSessionId: 'sess-B'`, with active session 'sess-A'
- **WHEN** the toolbar renders
- **THEN** the toggle SHALL be OFF (derived from boundSessionId != activeSessionId), NOT ON (which would be implied by the stored useSessionContext)

### Requirement: Orphaned binding fallback

When a query editor's `boundSessionId` references a session that no longer exists (deleted or otherwise unreadable), the system SHALL treat the editor's effective context as the active session's context (equivalent to a transient re-bind). The system SHOULD surface a single toast notification informing the user that the bound session is no longer available.

#### Scenario: Bound session deleted

- **GIVEN** an AI editor with `boundSessionId = A`, where session A has been deleted
- **WHEN** the active session is B and the toolbar renders
- **THEN** the editor SHALL read context from session B
- **AND** the mismatch badge SHALL NOT render (the orphan state takes precedence)
- **AND** a one-time toast SHALL be shown
