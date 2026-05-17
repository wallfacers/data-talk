# DataTalk

AI-powered intelligent database collaboration platform. Chat-driven interface backed by the OpenCode protocol for natural language queries, SQL generation, and data visualization.

## Architecture

Three-tier separation: **Tauri desktop** → **Spring Boot backend** → **OpenCode AI service**. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full map.

## Repository Layout

```
server/                            # Spring Boot 3.5 + Java 21
  data-talk-domain/                # Domain models: Part, DtEvent, Action, Ontology
  data-talk-application/           # App services: SessionBus, OpenCodeGateway, Registry
  data-talk-infrastructure/        # Infra: JDBC impls, OpenCode HTTP client
  data-talk-adapter/               # Adapter: REST controllers, Spring config, boot entry

client/                            # Tauri v2 + React 19 + Vite + shadcn/ui
  src/features/                    # Feature modules: chat, session, connection, workspace, data-grid
  src/components/ui/               # shadcn/ui base components
  src-tauri/                       # Tauri Rust backend

openspec/                          # OpenSpec SDD: changes, specs, archive (spec-driven schema)
  changes/                         # Active change proposals — one directory per change
  changes/archive/                 # Completed changes (YYYY-MM-DD-<name>)
  specs/                           # Canonical system behavior specs (GIVEN/WHEN/THEN)
```

## Tech Stack

| Layer    | Stack                                                            |
|----------|------------------------------------------------------------------|
| Desktop  | Tauri v2, React 19, TanStack Router/Query, Zustand, Recharts    |
| Backend  | Spring Boot 3.5, Java 21 (virtual threads), JdbcTemplate, Flyway|
| Database | SQLite (metadata), Dynamic JDBC (user DBs: MySQL/PG/H2)         |
| AI Comm  | OpenCode HTTP (Streamable HTTP + SSE, JSON-RPC)                  |
| Testing  | JUnit 5, AssertJ, WireMock 3.x (FakeOpenCodeServer)             |

## Build & Run

```bash
# Backend
export JAVA_HOME=/path/to/jdk-21                              # or ensure `java -version` is 21.x
cd server && mvn clean verify                                # compile + full test suite
cd server && mvn spring-boot:run -pl data-talk-adapter       # start (port 8080)

# Frontend
cd client && npm install && npm run dev                      # dev server
cd client && npm run tauri dev                               # Tauri dev mode
```

## Key Conventions

- **Dependency direction**: domain ← application ← infrastructure ← adapter (outer depends on inner, never reverse)
- **Adding an Action**: create an `@DataTalkAction`-annotated `ActionHandler` bean — auto-registered, zero core code changes
- **Protocol**: Streamable HTTP — POST response upgrades to SSE; server pushes `action.invoke`, client replies with `action_result`
- **Event model**: `DtEvent` sealed interface, 22 event types, exhaustive switch guarantees compile-time completeness
- **Frontend state**: Zustand store per feature, TanStack Query for server state
- **Stage state**: `useStageStore` is **session-independent / global** — `tabs[]`, `open`, `maximized`, `activeTabId` etc. are single values, not per-session maps. `StageTab` instances have **no `scope` field**; type-level scope (workspace vs session) lives only in `tab-type-registry.ts` as `TabTypeDescriptor.scope`. Switching the active session does not change stage tabs or open/maximized state

## OpenSpec Workflow (SDD)

DataTalk uses **OpenSpec (spec-driven schema)** as the primary workflow for proposing, designing, implementing, and archiving changes.

### Commands

| Command | Purpose |
|---------|---------|
| `/opsx:explore [topic]` | Open-ended exploration — think, compare, diagram. No artifacts, no code. |
| `/opsx:propose <change-name>` | Create a change with proposal.md + design.md + tasks.md + delta specs |
| `/opsx:apply [change]` | Work through tasks.md checkbox by checkbox, implementing each task |
| `/opsx:archive [change]` | Merge delta specs into base specs, move change to `archive/YYYY-MM-DD-<name>/` |

### Workflow

```
/opsx:explore "idea"        → Think, compare, diagram (no artifacts)
        ↓ idea crystallizes
/opsx:propose <kebab-name> → Generate proposal + design + tasks + delta specs
        ↓ review artifacts
/opsx:apply                 → Implement tasks checkbox by checkbox
        ↓ all complete
/opsx:archive               → Merge delta specs, move to archive/
```

### Artifact Layout (per change)

```
openspec/changes/<change-name>/
  .openspec.yaml          # Change metadata
  proposal.md             # Why & What — motivation, scope, impact
  design.md               # How — technical decisions, risks, migration plan
  tasks.md                # Implementation checklist (- [ ] ...)
  specs/                  # Delta specs (ADDED/MODIFIED/REMOVED)
    <capability>/spec.md
```

### Integration with Existing docs/

- **New work** → OpenSpec (`openspec/changes/<name>/`). After `/opsx:archive`, the completed change lives in `openspec/changes/archive/`.
- **Historical docs** → `docs/product-specs/` and `docs/exec-plans/` remain as reference. No new documents should be added there.
- **Canonical specs** → `openspec/specs/` is the source of truth for current system behavior. Delta specs in each change track deviations until archive.

## Knowledge Base Navigation

This file is the map. Deep knowledge lives in `docs/`:

| Looking for...               | Go to                                                        |
|------------------------------|--------------------------------------------------------------|
| Active change proposals      | `openspec/changes/` (run `openspec list`)                   |
| System behavior specs        | `openspec/specs/`                                           |
| Completed/archived changes   | `openspec/changes/archive/`                                  |
| System architecture & layers | [ARCHITECTURE.md](ARCHITECTURE.md)                           |
| Design docs & core beliefs   | [docs/design-docs/index.md](docs/design-docs/index.md)      |
| Historical exec plans        | [docs/exec-plans/index.md](docs/exec-plans/index.md)         |
| Historical product specs     | [docs/product-specs/index.md](docs/product-specs/index.md)   |
| DB schema reference          | [docs/generated/db-schema.md](docs/generated/db-schema.md)   |
| External protocol references | [docs/references/](docs/references/)                         |
| ER tab protocol              | [docs/references/er-tab-protocol.md](docs/references/er-tab-protocol.md) |
| Bezel dashboard skill design  | [docs/product-specs/2026-05-11-bezel-skill-design.md](docs/product-specs/2026-05-11-bezel-skill-design.md) |
| Design patterns & conventions| [docs/DESIGN.md](docs/DESIGN.md)                             |
| Client design contract       | [client/DESIGN.md](client/DESIGN.md)                         |
| Backend dev guide            | [docs/BACKEND.md](docs/BACKEND.md)                           |
| Frontend dev guide           | [docs/FRONTEND.md](docs/FRONTEND.md)                         |
| Data source type compatibility | [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) |
| Quality standards & scoring  | [docs/QUALITY.md](docs/QUALITY.md)                           |
| Reliability practices        | [docs/RELIABILITY.md](docs/RELIABILITY.md)                   |
| Security guide               | [docs/SECURITY.md](docs/SECURITY.md)                         |
| Tech debt tracker            | [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md) |
| BUG tracking & E2E defect registry | [docs/bugs/index.md](docs/bugs/index.md)                     |
| Internationalization guide   | [docs/I18N.md](docs/I18N.md)                             |
| Semantic Model architecture  | `openspec/changes/semantic-model-foundation/design.md`   |
| Semantic Model skills        | `server/.../skills/skill-creator/SKILL.md`, `semantic-model-usage/SKILL.md` |

## Working Rules

### Bug Fixes

- Proactively inspect related code when fixing a bug. In this 4-layer architecture, pay special attention: changes to domain sealed interfaces/records require checking application-layer exhaustive switches for sync updates

### BUG Tracking Gate

Runtime deviations are centrally tracked in `docs/bugs/`. See [docs/bugs/index.md](docs/bugs/index.md) and [docs/bugs/README.md](docs/bugs/README.md).

**Write triggers (MUST create/update BUG documents):**

1. **E2E test discovers product behavior deviation**: When running end-to-end tests via `mcp__playwright__*` or `playwright-cli` skill, any spec deviation — unresponsive buttons, incorrect data, UI misalignment, console errors — **MUST** create a new BUG file under `docs/bugs/` with status `open`, and register it in `index.md`. **Verbal-only reporting is forbidden.**
2. **Fixing an existing BUG**: When the user explicitly requests a BUG fix, or code changes happen to close an open BUG, **MUST** update the corresponding BUG file status to `fixed`, backfill `fixCommit` / `fixPlanRef` fields, and sync the `index.md` row.

**Read triggers (MUST read BUG documents first):**

3. **Before fixing any BUG**: **MUST** grep `docs/bugs/` for keywords/module names to confirm it is not a known issue, not a `wontfix` design trade-off, and not a `duplicate` of an existing BUG.
4. **Before writing a new feature plan/spec**: **MUST** browse `docs/bugs/index.md` "Open BUGs" and "By Module" to check whether the new feature scope overlaps with known BUG areas. If so, must explicitly list them in the plan's "Risks" or "Known Issues."

**Report trigger (MUST state in response):**

5. **When the user explicitly requests E2E testing** (e.g., "run through feature X end-to-end", "verify Y with Playwright"), upon completion **MUST** clearly report in the final response: "Found N BUGs in this run, registered at …". **Must state this even when N=0.**

### Data Source Type Compatibility Gate

- Any task that adds, changes, or depends on a database/data-source type **MUST** read [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) before proposing or implementing changes.
- This gate applies to frontend connection UI, backend JDBC connection handling, schema discovery, SQL execution, SQL splitting/risk analysis, diagnostics, MCP action schemas, and runtime agent prompts.
- If a checklist section is not applicable, explicitly mark it `N/A` with a concrete reason in the plan or final notes.
- When database-related implementation discovers a new compatibility point, update [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) in the same change.

### Post-Edit Verification

- **Backend**: after every edit, run `cd server && mvn compile -q` — confirm zero compilation errors before proceeding
- **Frontend**: after every edit, run `cd client && npx tsc --noEmit` — confirm zero type errors before proceeding
- **Exception**: for trivial edits where you're highly confident (e.g. comment/string tweaks, doc-only changes, single-line literal swaps with no type/signature impact), the compile/type-check step may be skipped. When in doubt, run it

### Backend Run vs Compile

- `mvn spring-boot:run` loads non-adapter modules from `~/.m2`, not `target/classes`. After editing `domain`/`application`/`infrastructure`, use `mvn install -pl <module> -am -DskipTests` — `mvn compile` won't refresh the jar and the running process keeps the old class

### Response Style

- Be concise and direct. No filler

### Frontend Design Contract Gate

- Any task that writes or modifies frontend requirements, product specs, design docs, execution plans, implementation proposals, or UI implementation for `client/` **MUST** read [client/DESIGN.md](client/DESIGN.md) first.
- Before proposing or writing `client/` UI changes, the agent **MUST** explicitly state the applicable constraints taken from `client/DESIGN.md`.
- If the requested frontend direction conflicts with `client/DESIGN.md`, the agent **MUST** stop and ask whether to:
  1. follow `client/DESIGN.md`
  2. update `client/DESIGN.md` first
  3. intentionally diverge with written justification
- Frontend plans/specs/proposals that do not reference `client/DESIGN.md` are incomplete and **MUST NOT** be treated as ready.

### Frontend Plan Gate

- Before drafting any proposal or design for `client/` UI, UX, visual, layout, component, page-shell, interaction, or design-system change, the agent **MUST**:
  1. read [client/DESIGN.md](client/DESIGN.md)
  2. summarize the applicable design constraints
  3. only then draft the proposal/design
- OpenSpec `proposal.md` / `design.md` output for frontend changes **MUST** include a `Design Inputs` section that cites `client/DESIGN.md` and the constraints applied.

### Exploration & Brainstorming

- **OpenSpec explore**: For any new idea, design discussion, or problem investigation, use `/opsx:explore` as the primary thinking tool. It provides OpenSpec context awareness (reads existing specs, active changes) and visual exploration without writing code.
- **Superpowers brainstorming**: Major architectural changes (new modules, cross-layer refactors spanning domain/application/infrastructure/adapter) **MUST** invoke `superpowers:brainstorming` before `/opsx:propose`. For routine feature work, use `/opsx:explore`.

### OpenSpec Apply & Parallel Execution

- When executing tasks via `/opsx:apply`, independent tasks within a batch **MUST** be dispatched in parallel (use `superpowers:dispatching-parallel-agents` and `superpowers:subagent-driven-development` skills).
- Within a batch, **skip per-edit `mvn compile` / `tsc --noEmit`**. Run a single consolidated verification pass — full compile, integration tests, end-to-end smoke — only after every task in the batch has its code written.
- Tasks with explicit ordering dependencies declared in `tasks.md` **MUST** still execute in declared order; only mutually independent tasks are eligible for batching.

### Testing

- New features **MUST** have corresponding tests. No tests = not done
- Backend: JUnit 5 + AssertJ. For OpenCode protocol interactions, use WireMock (FakeOpenCodeServer)
- Frontend: vitest

### Clarify Before Acting

- If requirements, scope, or implementation approach are unclear, **MUST** ask for clarification first. Never guess

### MCP / Skill Temporary Files

- Any temporary file produced by MCP servers or Skills (Playwright traces/screenshots, brainstorming scratch files, intermediate scripts, downloaded artifacts, exploratory dumps, etc.) **MUST** be written under the project root's `tmp/` directory (`/home/wallfacers/project/data-talk/tmp/`). Create the directory if it does not exist
- **Forbidden** locations: repo root, `client/`, `server/`, `docs/`, system `/tmp`, `~/`, or any tracked source path
- The `tmp/` directory is git-ignored and **MUST NOT** be committed. Do not add files inside it via `git add`, and never relocate generated artifacts out of `tmp/` just to bypass the ignore rule
- **Sole exception**: BUG evidence screenshots (`docs/bugs/assets/<BUG-ID>/`, PNG ≤ 500KB each) may be committed to git. Large artifacts (trace, HAR, HTML, etc.) **must remain in `tmp/`** and must not be committed

### Ingestion Artifact Path Convention

- HTTP-fetched payloads are persisted under `~/.data-talk/ingestion/<jobId>/payload.<json|jsonl|csv|html>` (separate from the standard artifact directory). They are registered in `file_artifact` with `kind=ingestion_payload` and `physical_path` set to the absolute filesystem path
- The 500MB cap is enforced by `IngestionPayloadFetcher` before final atomic rename — partial writes go to `payload.staging` and are deleted on overrun. Do not attempt to short-circuit the staging step
- `IngestionConfirmedToken` is in-memory only (`ConcurrentHashMap`, 5-min TTL, single-use). It is not persisted across server restarts — clients must re-confirm after a restart

### Ingestion E2E Profile

- Playwright ingestion specs (`tests/e2e/ingestion-*.spec.ts`) require the backend to be launched with `SPRING_PROFILES_ACTIVE=e2e`. This relaxes SSRF deny so the local mock HTTP server on `127.0.0.1` is reachable and lowers `payload-max-bytes` to 1 MB so the "payload too large" path can be exercised within ~1 s
- Never start the backend with this profile in production, staging, or shared dev environments

### Branching & Release

- Push to `master` triggers 3-platform release build (see [docs/BRANCH_STRATEGY.md](docs/BRANCH_STRATEGY.md))
- Version source of truth: `client/src-tauri/tauri.conf.json`; use [Conventional Commits](https://www.conventionalcommits.org/)

---

## [DEPRECATED] Legacy Workflow Rules

> **Note**: The rules below are from the pre-OpenSpec workflow and have been **superseded by OpenSpec**. All new work **must** follow `/opsx:propose → /opsx:apply → /opsx:archive`. These legacy rules are preserved here only for reference when working with **historical documents** still in `docs/product-specs/` and `docs/exec-plans/`. Do NOT add new files to those directories.

### [DEPRECATED] Plan Mode

- ~~Multi-step changes **MUST** use `/plan` to align on approach before writing code.~~ → Replaced by `/opsx:propose`
- Legacy plan workflow documented in [docs/PLANS.md](docs/PLANS.md) — still applicable for maintaining existing plans in `docs/exec-plans/`, but not for new changes.

### [DEPRECATED] Documentation Paths

- ~~Design specs from Superpowers `brainstorming` → `docs/product-specs/YYYY-MM-DD-<topic>-design.md`~~ → Replaced by OpenSpec `proposal.md` + `design.md`
- ~~Execution plans from Superpowers `writing-plans` → `docs/exec-plans/YYYY-MM-DD-<topic>-plan.md`~~ → Replaced by OpenSpec `tasks.md`
- Historical files remain in `docs/product-specs/` and `docs/exec-plans/` as reference.

### [DEPRECATED] Plan Document Registration

- ~~Every plan **MUST** be persisted under `docs/exec-plans/`, named `YYYY-MM-DD-<topic>-plan.md`~~ → OpenSpec uses `openspec new change <name>` to auto-create the change directory.
- ~~Register plans in `docs/exec-plans/index.md`~~ → Replaced by `openspec list` to view active changes.

### [DEPRECATED] Post-Execution Document Housekeeping

- ~~Mark tasks complete in plan file, move index entry from Active to Completed, propagate outcomes~~ → Replaced by `/opsx:archive`, which auto-merges delta specs and moves the change directory to archive.
- Existing active/completed entries in `docs/exec-plans/index.md` are **no longer maintained** — they remain as a read-only historical snapshot.
