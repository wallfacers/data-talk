# Data Source Coverage Governance Design

Date: 2026-04-30
Status: Ready for review

## 1. Purpose

DataTalk is expanding from a small set of SQL database kinds to a broader data
source matrix. This design defines the governance layer for that expansion. It
does not implement any new database kind and does not mark any roadmap
candidate as supported.

The core rule is simple: a new data source is first-class only when DataTalk can
create and test the connection, discover metadata, resolve database/schema
context, execute SQL through the guarded paths, expose honest diagnostics, and
let both the frontend and OpenCode runtime use it without hidden assumptions.
UI-only, prompt-only, and JDBC-only support are not valid.

## 2. Design Inputs

This design applies the mandatory gate in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
That document remains the acceptance checklist for every database-related
change. Each child spec and child plan must cite it, apply every relevant
section, and mark non-applicable sections as `N/A` with a concrete reason.

Frontend-facing child work must also apply
[client/DESIGN.md](../../client/DESIGN.md). The applicable constraints are:

- connection forms, pickers, Query Editor context controls, diagnostics cards,
  and empty/unsupported states must use semantic tokens instead of raw feature
  colors;
- Chat and Workbench remain one visual system, with Stage state treated as
  global workbench state;
- accessible controls, keyboard focus, disabled states, and icon-only actions
  need explicit affordances;
- every user-visible frontend string must go through i18n;
- UI changes must not introduce a separate visual language for data-source
  setup or SQL workbench controls.

Repository context from the current scan:

- `mysql` and `postgresql`/`postgres` are first-class.
- `h2` is development/demo support.
- `sqlite` has backend/runtime traces but incomplete frontend exposure.
- `oracle` and `sqlserver` have stubs or legacy enum traces, but are not
  first-class.
- Connection kind behavior is currently split across `ConnectionRecord.kind()`,
  `ConnectionKind`, `DbType`, `JdbcUrlBuilder`, diagnostics providers, frontend
  hand-written kind lists, formatter mappings, SQL outline dictionaries, MCP
  schemas, and runtime prompt rules.

## 3. Goals

- Establish one governance model for all future data-source work.
- Preserve `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` as the hard gate and source
  of truth for acceptance.
- Define the candidate matrix and wave order.
- Require one child design and one child execution plan per database kind.
- Make unsupported capabilities explicit and structured instead of silent,
  fake-empty, or prompt-only.
- Keep SQL risk guards and large-schema/result protections intact for every
  kind.

## 4. Non-Goals

- This design does not add any driver, frontend dropdown option, MCP schema
  value, or runtime prompt claim.
- This design does not decide detailed JDBC URL forms for every candidate.
  Those decisions belong in per-kind child specs.
- This design does not force non-SQL sources into DataTalk's SQL execution
  contract.
- This design does not refactor kind handling by itself. It requires child
  plans to address shared kind handling when their implementation needs it.

## 5. Governance Model

Data source expansion uses two layers.

### Shared Governance Layer

The shared layer defines rules that every child spec must follow:

- canonical lower-case kind string;
- explicit aliases and a single normalization boundary before persistence and
  routing;
- documented mapping of catalog, database, schema, namespace, warehouse,
  project, dataset, or search path;
- backend support across connection test, JDBC URL, driver packaging, metadata
  discovery, target resolution, SQL execution, result normalization, splitter,
  risk guard, and diagnostics;
- frontend support across connection form, default ports, data source picker,
  Query Editor context, formatter, outline, schema/context controls, and i18n;
- AI/MCP support across action schemas, runtime `AGENTS.md`, prompt contract
  tests, and tool naming;
- documentation and verification updates.

The shared layer should push future implementation toward centralized kind
metadata instead of scattered string branches. A child plan may introduce or
extend a registry when that reduces duplication across `ConnectionKind`,
`JdbcUrlBuilder`, frontend type lists, SQL formatter mappings, diagnostics
routing, and MCP schemas. It must remain conservative and follow existing
module boundaries.

### Per-Kind Child Design Layer

Every database kind gets its own child design and child execution plan:

- `docs/product-specs/YYYY-MM-DD-data-source-coverage-<kind>-design.md`
- `docs/exec-plans/YYYY-MM-DD-data-source-coverage-<kind>-plan.md`

The child design must be indexed in
[docs/product-specs/index.md](./index.md) under section 8. The child plan must
be indexed in [docs/exec-plans/index.md](../exec-plans/index.md) under Active
while in progress and moved to Completed during housekeeping.

Child specs must not rely on vague compatibility statements such as "works like
MySQL" or "PostgreSQL-compatible". If reuse is valid, the child spec must still
prove the exact driver, URL, metadata, splitter, risk, diagnostics, and frontend
contract for that kind.

## 6. Candidate Matrix And Waves

| Wave | Candidate kinds | Design rule |
|---|---|---|
| A | `sqlite`, `oracle`, `sqlserver`, `mariadb` | Close partial/stub support and common enterprise SQL first. |
| B | `apache_doris`, `starrocks`, `clickhouse`, `hive`, `trino`, `presto`, `duckdb` | Treat OLAP engines as SQL engines only after validating catalog/schema semantics, splitter safety, and driver behavior. |
| C | `gaussdb`, `opengauss`, `dameng`, `kingbase`, `oceanbase`, `tidb` | Do not assume MySQL/PostgreSQL compatibility is enough; each requires explicit proof. |
| D | `snowflake`, `bigquery`, `redshift`, `databricks_sql` | Design cloud-warehouse fields, authentication, billing-sensitive metadata scans, and result limits explicitly. |
| E | `mongodb`, `elasticsearch`, `opensearch` | Design a separate query/read model first; do not expose these as fake SQL databases. |

Reserve candidates: `db2`, `sap_hana`, and `teradata`. They remain outside the
first A-E execution waves unless a user or product priority explicitly pulls
them forward.

Wave A is the first implementation wave:

- `sqlite`: complete frontend exposure and verify backend runtime behavior.
- `oracle`: turn current stubs into real first-class support only after the
  connection stack, metadata, execution, diagnostics, UI, and MCP contracts are
  complete.
- `sqlserver`: close the legacy enum/stub gap with real connection kind, JDBC
  URL, driver, metadata, diagnostics, UI, and tests.
- `mariadb`: treat as a separate kind, not an alias hidden under MySQL, unless
  the child spec explicitly proves and documents the normalization choice.

## 7. Child Spec Required Sections

Each child design must include these sections:

1. **Compatibility Gate Application**
   Cite `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` and list every applicable gate
   area. Mark `N/A` sections with concrete reasons.

2. **Support Statement**
   State the target support level: first-class, partial, or explicitly
   unsupported. A first-class statement must include the exact capabilities
   that will work at the end of the child plan.

3. **Kind Naming**
   Define canonical kind, accepted aliases, persistence behavior, API payload
   values, frontend display labels, and MCP-visible schema values.

4. **Connection And Persistence**
   Define driver artifact, driver class, URL shape, default port, credential
   fields, timeout behavior, SSL or extra properties, and whether the metadata
   DB needs migrations.

5. **Catalog, Database, Schema, And Target Resolution**
   Map DataTalk `database` and `schema` fields to the database's actual naming
   model. Define `use xxx`, system schema filtering, case sensitivity, quoting,
   target discovery, and ambiguity behavior.

6. **SQL Execution**
   Define context application, transaction strategy, multi-statement support,
   batch DML behavior, generated keys/update counts, max row behavior, result
   value normalization, and connection error formatting.

7. **SQL Splitter And Risk Guard**
   Choose a proven driver parser, a dedicated splitter, or a tested generic
   splitter. Define dialect-specific read-only, mutating, and high-risk SQL
   cases for `CalciteSqlRiskAnalyzer` or any fallback guard.

8. **Schema Discovery And ER Features**
   Define `datatalk_read_schema` discovery/describe behavior, large-schema
   bounds, ER Inspector support or structured unsupported, and ER Designer DDL
   support or structured unsupported.

9. **Diagnostics**
   Define `EXPLAIN`, index hints, lock info, pool status, table space,
   terminate session, and optimize table support. Unsupported diagnostics must
   return structured unsupported responses.

10. **Frontend**
    Apply `client/DESIGN.md`. Define connection form fields, defaults, labels,
    picker display, Query Editor context behavior, formatter language, SQL
    outline dictionaries, diagnostics UI state, i18n keys, and type updates.

11. **AI/MCP And Runtime Prompt**
    Define action schema changes, `datatalk_*` tool naming, runtime
    `AGENTS.md` guidance, prompt contract tests, and any kind-specific AI
    safety rules.

12. **Acceptance And Verification**
    List automated tests, manual/integration smoke, compile/typecheck commands,
    and documentation housekeeping.

## 8. Acceptance Matrix

Every first-class child implementation must satisfy these acceptance points, or
explicitly document why a capability is structured unsupported:

| Area | Required outcome |
|---|---|
| Connection | Create, edit, test, list, select, and delete behavior work with localized errors. |
| Target discovery | `list_connection_targets` and `resolve_use_target` handle matched, ambiguous, and not-found cases. |
| Schema read | `datatalk_read_schema` supports discover and explicit describe with `limit`, `cursor`, `pattern`, and large-schema bounds. |
| Query Editor | L1 SQL executes; L2/L3 uses the existing Workbench confirmation state machine. |
| AI SQL | `datatalk_execute_sql` supports analytical execution and blocks chat-path L2/L3 bypass. |
| Splitter | Dialect splitting is explicitly chosen and covered by tests. |
| Risk guard | Common read-only, mutation, and high-risk dialect statements are tested. |
| Diagnostics | Real implementation or structured unsupported; no fake empty success. |
| Frontend | Form, picker, Query Editor context, formatter, outline, diagnostics UI, and i18n are updated. |
| MCP/prompt | `tools/list`, action schemas, runtime `AGENTS.md`, and prompt contract tests stay honest. |
| Docs | Compatibility gate, support snapshot, product spec index, exec plan index, and canonical docs are updated. |

## 9. Test Strategy

Child plans must add tests proportionate to risk. The minimum set is:

- backend unit tests for kind normalization, `JdbcUrlBuilder`, splitter routing,
  risk analysis, and result normalization;
- backend service/action tests for connection test, target discovery,
  `resolve_use_target`, `datatalk_read_schema`, `SqlExecuteService`, and
  `ExecuteSqlAction`;
- diagnostics provider tests or structured unsupported tests;
- MCP schema and runtime prompt contract tests;
- frontend tests for connection form, data source picker, Query Editor context,
  SQL formatter, SQL outline, and diagnostics rendering when changed;
- integration or manual smoke for create connection, test connection, list
  targets, read schema, run Query Editor SQL, run AI `datatalk_execute_sql`,
  exercise L2/L3 guard, and run diagnostics or verify unsupported.

At minimum, code-changing child plans must run:

```bash
cd server && mvn compile -q
cd client && npx tsc --noEmit
```

Targeted tests and full verify commands should be selected by the child plan.
Doc-only child work may skip compile/typecheck only when no typed code,
schemas, prompts, or frontend text changed.

## 10. Documentation And Housekeeping

Each child plan must update
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
in the same change as implementation. If a new compatibility point is
discovered, the gate document must be expanded before the child plan claims
completion.

When a child plan completes:

- mark every task/checklist item in the child plan complete, including
  deviations and skipped items;
- move the child plan index entry from Active to Completed;
- update the product spec index when support status changes;
- update canonical docs such as `ARCHITECTURE.md`, `docs/BACKEND.md`,
  `docs/FRONTEND.md`, `docs/generated/db-schema.md`, runtime `AGENTS.md`, and
  `docs/references/*` when their contracts change;
- report verification outcomes and any residual manual smoke that remains.

## 11. Risks And Mitigations

| Risk | Mitigation |
|---|---|
| A kind is exposed in UI before backend support is real. | Child plan must update backend, frontend, MCP, prompt, tests, and support snapshot together. |
| Alias handling becomes scattered. | Child specs must define one normalization boundary and tests for canonical persistence. |
| Database/schema semantics are overloaded into `databaseName`. | Child specs must document exact field mapping and add migrations if the existing model is insufficient. |
| Generic splitter corrupts procedural or dialect-specific scripts. | Every child spec must choose and test a splitter strategy explicitly. |
| Calcite or fallback risk analysis misses vendor-specific mutation commands. | Child specs must include dialect-specific L1/L2/L3 examples and tests. |
| Diagnostics overclaim support. | Providers must declare capabilities honestly and return structured unsupported where needed. |
| Cloud warehouse metadata scans create cost or latency surprises. | Wave D child specs must bound discovery, document billing-sensitive operations, and avoid broad scans by default. |
| Non-SQL sources are forced into SQL semantics. | Wave E requires a separate query/read model before any implementation plan. |

## 12. First Follow-Up Work

After this governance spec is accepted, the next step is to create child specs
for Wave A in this order:

1. `sqlite` frontend completion
2. `oracle`
3. `sqlserver`
4. `mariadb`

Each child spec will go through its own brainstorming/design approval, then its
own execution plan, before implementation begins.
