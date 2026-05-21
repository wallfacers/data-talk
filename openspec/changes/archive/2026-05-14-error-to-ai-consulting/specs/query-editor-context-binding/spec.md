## MODIFIED Requirements

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
