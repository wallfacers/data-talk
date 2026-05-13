# ingestion-lifecycle Specification

## Purpose
TBD - created by archiving change ingestion-name-creator-and-stop. Update Purpose after archive.
## Requirements
### Requirement: Ingestion job MUST carry a human-readable name

Every `ingestion_job` SHALL have a `name` (1–80 chars) provided at creation time. The MCP `datatalk_http_request` tool's inputSchema SHALL declare `name` as a required string with `minLength: 1, maxLength: 80`. The backend SHALL reject job creation requests missing `name` with HTTP 400 / structured `INGESTION_NAME_REQUIRED` error.

#### Scenario: AI omits name in datatalk_http_request

- **GIVEN** the AI invokes `datatalk_http_request` without a `name` field
- **WHEN** the MCP server validates input against the inputSchema
- **THEN** the call SHALL fail with a schema validation error, and **NO** row SHALL be inserted into `ingestion_job`

#### Scenario: AI provides a 1–80 char name

- **GIVEN** the AI invokes `datatalk_http_request` with `name: "Stripe customers — 2026-05"`
- **WHEN** `IngestionPayloadFetcher.fetch` persists the new job row
- **THEN** the `ingestion_job.name` column SHALL equal `"Stripe customers — 2026-05"`, and the corresponding Stage Tab SHALL render this string as its title

#### Scenario: Legacy jobs without name fallback to short-id title

- **GIVEN** an `ingestion_job` row created before V23 migration with `name = NULL`
- **WHEN** the library list and Tab title render this job
- **THEN** they SHALL display `Job <id前8位>` as a fallback, **without** error

### Requirement: Ingestion job MUST record its creator attribution

`ingestion_job` SHALL store three creator fields populated at insert time and **never** updated thereafter:

- `created_by_kind` (NOT NULL): one of `'ai'` or `'user'`
- `created_by_session_id` (nullable): the DataTalk session ID that originated the job, or NULL when not applicable
- `created_by_label` (nullable): a display string materialized at write time (e.g. `"AI · <session title>"`), decoupled from the live session record

`HttpRequestActionHandler` SHALL pass `CreatorKind.AI` and `ctx.sessionId()` to the fetcher. The fetcher SHALL resolve `created_by_label` via `SessionLookup` once at write time and SHALL NOT update it on subsequent reads.

#### Scenario: AI-originated job records sessionId and materialized label

- **GIVEN** AI invokes `datatalk_http_request` inside session `sess_abc123` whose title is `"Q2 reporting"`
- **WHEN** the row is inserted
- **THEN** `created_by_kind = 'ai'`, `created_by_session_id = 'sess_abc123'`, `created_by_label = 'AI · Q2 reporting'`

#### Scenario: Session deleted after job creation does not erase label

- **GIVEN** an ingestion job with `created_by_label = 'AI · Q2 reporting'` exists, and the user deletes session `sess_abc123`
- **WHEN** the library list re-fetches the job
- **THEN** the Creator cell SHALL still display `AI · Q2 reporting`, but the cell SHALL be visually marked as **non-clickable** (session no longer exists)

#### Scenario: Session renamed after job creation does not update label

- **GIVEN** an ingestion job with `created_by_label = 'AI · Q2 reporting'` exists, and the user renames session `sess_abc123` to `"Q3 reporting"`
- **WHEN** the library list re-fetches the job
- **THEN** the Creator cell SHALL still display `AI · Q2 reporting` (frozen at write time)

### Requirement: MCP outputSchema MUST expose name and createdBy

The following MCP tools SHALL expose `name` and `createdBy: { kind, sessionId, label }` in their outputSchema and concrete result payloads:

- `datatalk_http_request` (echo back the name + createdBy from the newly-created job)
- `datatalk_get_ingestion_job`
- `datatalk_list_ingestion_jobs`

#### Scenario: list_ingestion_jobs returns name and createdBy

- **WHEN** an AI / client calls `datatalk_list_ingestion_jobs`
- **THEN** each item in the result array SHALL contain non-null `name` (or null for pre-migration rows), and `createdBy.kind`, `createdBy.sessionId` (nullable), `createdBy.label` (nullable) keys

#### Scenario: ui_find / ui_read / ui_exec are unchanged

- **GIVEN** a Stage Tab of type `ingestion_job` exists with title set to its job `name`
- **WHEN** AI calls `ui_find({ filter: { type: 'ingestion_job' }, query: 'Stripe customers' })`
- **THEN** the match SHALL be found by tab title without any code changes to the `ui_find` / `ui_read` / `ui_exec` action handlers

### Requirement: Stop API MUST truly interrupt running ingestion threads

The system SHALL expose `POST /api/ingestion/jobs/{id}/stop?force=<boolean>` that interrupts running fetch / write threads via an `IngestionRunRegistry`-backed cancellation flag.

For an active job (currently in `fetching` / `writing` and known to the in-process `IngestionRunRegistry`):
- The endpoint SHALL set the cancellation flag and `Thread.interrupt()` the worker
- `IngestionExecutor.executeBatchInsert` SHALL check the cancellation flag at every batch boundary, and on detection SHALL `Connection.rollback()` the in-flight batch and throw `IngestionCancelledException`
- The endpoint SHALL return `HTTP 202 Accepted` immediately (does NOT block on thread exit)

For a job in a non-running but non-terminal state (`pending`, `mapping`, `fetched`, `mapped`, `confirmed`):
- The endpoint SHALL synchronously update status to `cancelled` and delete the payload artifact (if any)
- The endpoint SHALL return `HTTP 204 No Content`

For a job already in a terminal state (`completed`, `failed`, `cancelled`):
- The endpoint SHALL return `HTTP 409 Conflict`

For an unknown job ID:
- The endpoint SHALL return `HTTP 404 Not Found`

With `force=true`:
- The endpoint SHALL bypass the `IngestionRunRegistry` presence check
- The endpoint SHALL synchronously flip the DB status to `cancelled` regardless of in-process state, and execute cleanup (DROP TABLE if applicable, see "Cleanup on stop")
- The endpoint SHALL be idempotent (double-stop returns 204)

#### Scenario: Stop during writing interrupts the batch loop

- **GIVEN** a job `j1` is in status `writing`, and `IngestionExecutor.executeBatchInsert` is mid-loop with `IngestionRunRegistry` holding its handle
- **WHEN** `POST /api/ingestion/jobs/j1/stop` arrives
- **THEN** the endpoint SHALL return 202 immediately; within `≤ batchSize * row-time-budget` ms the worker SHALL detect the flag at the next batch boundary, rollback the current batch, throw `IngestionCancelledException`, and the catch block SHALL transition the job to `cancelled`

#### Scenario: Stop with force on a zombie writing job (no RunRegistry entry)

- **GIVEN** a job `j2` is in status `writing` from a previous JVM process; the current process restarted; `IngestionRunRegistry` does NOT contain `j2`
- **WHEN** `POST /api/ingestion/jobs/j2/stop?force=true` arrives
- **THEN** the endpoint SHALL return 204, set status to `cancelled`, delete the payload artifact, and attempt DROP TABLE on the target table

#### Scenario: Stop on terminal state is rejected

- **GIVEN** a job `j3` is in status `completed`
- **WHEN** `POST /api/ingestion/jobs/j3/stop` arrives
- **THEN** the endpoint SHALL return 409 Conflict with body `{ error: { code: 'INGESTION_ALREADY_TERMINAL', ... } }`

### Requirement: Active-stop MUST clean target table

When the user actively stops a job that has reached `confirmed` or `writing` (i.e. `CREATE TABLE` has executed), the system SHALL execute `DROP TABLE IF EXISTS target_schema.target_table` on the target connection before completing the stop, leaving the user's business database with zero residue from the cancelled job.

The system SHALL extend `IngestionDdlAdapter` with `generateDropTable(schema, table)` implemented by all four Day-1 dialects (mysql, postgresql, h2, sqlite) as `DROP TABLE IF EXISTS [schema.]table`.

If `DROP TABLE` execution itself fails (network, permission, etc.), the system SHALL still set status to `cancelled` but SHALL record `errorMessage = "stopped but table cleanup failed: <reason>"` for UI to surface.

#### Scenario: Stop during writing drops the target table

- **GIVEN** a job in `writing` has executed `CREATE TABLE sales.tx_2026q2` and committed 3 batches
- **WHEN** the user clicks Stop and the worker exits via `IngestionCancelledException`
- **THEN** the stop cleanup SHALL execute `DROP TABLE IF EXISTS sales.tx_2026q2` on the target connection, and the target database SHALL no longer contain `sales.tx_2026q2`

#### Scenario: Stop in mapping phase does NOT drop (no table created)

- **GIVEN** a job in `mapped` status — `CREATE TABLE` has NOT executed
- **WHEN** `POST /jobs/{id}/stop` arrives
- **THEN** the endpoint SHALL delete the payload artifact, transition to `cancelled`, and SHALL NOT attempt DROP TABLE

#### Scenario: DROP TABLE failure surfaces in errorMessage

- **GIVEN** a writing-phase stop where the target DB connection has been revoked between CREATE and DROP
- **WHEN** the stop cleanup attempts DROP TABLE
- **THEN** the job status SHALL still be `cancelled`, `errorMessage` SHALL begin with `"stopped but table cleanup failed:"`, and the UI FailedPhase SHALL render a Copy-able `DROP TABLE IF EXISTS schema.table;` SQL string

### Requirement: System MUST guarantee status consistency across restarts

The system SHALL run two complementary sweepers to guarantee that no `ingestion_job` remains in a non-terminal status indefinitely:

**Startup sweeper** (`@PostConstruct`, single run per process boot):
- SHALL select all rows with `status IN ('pending','fetching','mapping','confirmed','writing')` and transition each to `failed` with `error_message = "server restarted while running — manually drop target table if needed"`
- SHALL NOT execute DROP TABLE (safe-conservative, see Risks/Trade-offs in design.md)

**Heartbeat sweeper** (`@Scheduled(fixedDelay = 60_000)`):
- SHALL select all rows with `status IN ('fetching','writing')` and `heartbeat_at < now() - 5 * 60_000`
- SHALL transition each to `failed` with `error_message = "task heartbeat lost — manually drop target table if needed"`
- SHALL NOT execute DROP TABLE

`IngestionPayloadFetcher.fetchPage` and `IngestionExecutor.executeBatchInsert` SHALL update `heartbeat_at = now()` on every page fetch / batch commit.

#### Scenario: Process crashes mid-writing

- **GIVEN** a job `j4` is in status `writing` and the JVM process is `kill -9`'d
- **WHEN** the new JVM boots and `IngestionStartupSweeper` runs `@PostConstruct`
- **THEN** within ≤ 5 s of Spring context ready, `j4`'s status SHALL be `failed` with the prescribed errorMessage; the target table SHALL remain untouched

#### Scenario: Worker thread dies but JVM survives

- **GIVEN** a job `j5` is in `writing` with `heartbeat_at = T0`; an OOM kills the worker thread; the JVM continues running
- **WHEN** 5 minutes pass and `IngestionHeartbeatSweeper` fires
- **THEN** `j5`'s status SHALL transition to `failed` with the heartbeat-lost message

#### Scenario: Heartbeat ticks during active work

- **GIVEN** a long-running job is fetching pages
- **WHEN** each page returns successfully
- **THEN** `heartbeat_at` SHALL be updated to `now()` before the next page request, ensuring heartbeat_at never exceeds page-fetch-time + commit-time during normal operation

### Requirement: Frontend Stop button MUST cover all non-terminal stages

The library list (`ingestion-library-tab.tsx`) and the job detail Tab (`ingestion-job-tab.tsx`) SHALL render a Stop button when the job is in any non-terminal status: `pending`, `fetching`, `mapping`, `fetched`, `mapped`, `confirmed`, `writing`.

- Library list: stop button SHALL appear on row hover in the action column
- Job Tab header: stop button SHALL be permanently visible (not behind hover), placed beside the existing Delete button
- Confirm dialog SHALL warn that target table data will be dropped if the job has reached `confirmed` / `writing`
- If 30 s pass after a stop request without a status change observed via `useIngestionJobQuery` polling, the UI SHALL switch the button to **Force stop** (visually `bg-destructive/15 text-destructive`), which sends `POST /stop?force=true`

#### Scenario: Stop button absent on terminal jobs

- **GIVEN** a job in status `completed`
- **WHEN** the user hovers the library row
- **THEN** the Stop button SHALL NOT appear; the Delete button MAY appear (existing behavior)

#### Scenario: Force-stop appears after 30s

- **GIVEN** the user clicked Stop on a zombie job whose backend hasn't responded
- **WHEN** 30 s elapse with status unchanged
- **THEN** the button text SHALL change to "Force stop" with `bg-destructive` warning style, and clicking it SHALL POST `?force=true`

### Requirement: AGENTS.md MUST require AI to provide a meaningful name

The `## Data Ingestion` section in `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` SHALL include an explicit instruction that every `datatalk_http_request` call MUST provide a `name` (1–80 chars) that describes WHAT is being fetched and the scope, with at least one positive and one negative example. The `datatalk_http_request` tool entry SHALL list `name` as a required input.

#### Scenario: AGENTS.md prompt contains the name requirement

- **GIVEN** the deployed AGENTS.md
- **WHEN** an AI session loads the prompt
- **THEN** the `## Data Ingestion` section SHALL contain a sentence enforcing the `name` field with `MUST`, and SHALL provide a "Bad: 'fetch users' / Good: 'Stripe customers — 2026-05'" example pair

