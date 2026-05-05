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

## Knowledge Base Navigation (docs/)

This file is the map. Deep knowledge lives in `docs/`:

| Looking for...               | Go to                                                        |
|------------------------------|--------------------------------------------------------------|
| System architecture & layers | [ARCHITECTURE.md](ARCHITECTURE.md)                           |
| Design docs & core beliefs   | [docs/design-docs/index.md](docs/design-docs/index.md)      |
| Current status & exec plans  | [docs/exec-plans/index.md](docs/exec-plans/index.md)         |
| Product specs & features     | [docs/product-specs/index.md](docs/product-specs/index.md)   |
| DB schema reference          | [docs/generated/db-schema.md](docs/generated/db-schema.md)   |
| External protocol references | [docs/references/](docs/references/)                         |
| ER tab protocol              | [docs/references/er-tab-protocol.md](docs/references/er-tab-protocol.md) |
| Design patterns & conventions| [docs/DESIGN.md](docs/DESIGN.md)                             |
| Client design contract       | [client/DESIGN.md](client/DESIGN.md)                         |
| Backend dev guide            | [docs/BACKEND.md](docs/BACKEND.md)                           |
| Frontend dev guide           | [docs/FRONTEND.md](docs/FRONTEND.md)                         |
| Data source type compatibility | [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) |
| Plan workflow                | [docs/PLANS.md](docs/PLANS.md)                               |
| Quality standards & scoring  | [docs/QUALITY.md](docs/QUALITY.md)                           |
| Reliability practices        | [docs/RELIABILITY.md](docs/RELIABILITY.md)                   |
| Security guide               | [docs/SECURITY.md](docs/SECURITY.md)                         |
| Tech debt tracker            | [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md) |
| Internationalization guide   | [docs/I18N.md](docs/I18N.md)                             |

## Working Rules

### Bug Fixes

- Proactively inspect related code when fixing a bug. In this 4-layer architecture, pay special attention: changes to domain sealed interfaces/records require checking application-layer exhaustive switches for sync updates

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

### Plan Mode

- Multi-step changes **MUST** use `/plan` to align on approach before writing code. Follow the existing plan workflow in [docs/PLANS.md](docs/PLANS.md)

### Frontend Design Contract Gate

- Any task that writes or modifies frontend requirements, product specs, design docs, execution plans, implementation proposals, or UI implementation for `client/` **MUST** read [client/DESIGN.md](client/DESIGN.md) first.
- Before proposing or writing `client/` UI changes, the agent **MUST** explicitly state the applicable constraints taken from `client/DESIGN.md`.
- If the requested frontend direction conflicts with `client/DESIGN.md`, the agent **MUST** stop and ask whether to:
  1. follow `client/DESIGN.md`
  2. update `client/DESIGN.md` first
  3. intentionally diverge with written justification
- Frontend plans/specs/proposals that do not reference `client/DESIGN.md` are incomplete and **MUST NOT** be treated as ready.

### Frontend Plan Gate

- Before entering `/plan` for any `client/` UI, UX, visual, layout, component, page-shell, interaction, or design-system change, the agent **MUST**:
  1. read [client/DESIGN.md](client/DESIGN.md)
  2. summarize the applicable design constraints
  3. only then draft the plan/spec
- Frontend `/plan` output **MUST** include a `Design Inputs` section that cites `client/DESIGN.md` and the constraints applied.

### Parallel Plan Execution

- When executing an implementation plan from `docs/exec-plans/`, write code for independent tasks in **concurrent batches** (dispatch parallel subagents — see the `superpowers:dispatching-parallel-agents` and `superpowers:subagent-driven-development` skills), not sequentially one task at a time
- Within a batch, **skip per-edit `mvn compile` / `tsc --noEmit`**. Run a single consolidated verification pass — full compile, integration tests, end-to-end smoke — only after every task in the batch has its code written
- Tasks with explicit ordering dependencies declared in the plan document **MUST** still execute in declared order; only mutually independent tasks are eligible for batching

### Brainstorming

- Major changes (new modules, architecture adjustments, cross-layer refactors spanning domain/application/infrastructure/adapter) **MUST** invoke the `brainstorming` skill first

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

### Documentation Paths

- Design specifications generated by the Superpowers `brainstorming` skill **MUST** be stored at `docs/product-specs/YYYY-MM-DD-<topic>-design.md` and indexed in [docs/product-specs/index.md](docs/product-specs/index.md) under §8 "Individual Design Documents"
- Execution plans generated by the Superpowers `writing-plans` skill **MUST** be stored at `docs/exec-plans/YYYY-MM-DD-<topic>-plan.md` and indexed in [docs/exec-plans/index.md](docs/exec-plans/index.md) under the applicable section (Active / Completed)
- The skills' built-in default paths (`docs/superpowers/specs/` and `docs/superpowers/plans/`) are **deprecated**; the paths above supersede them in all cases

### Plan Document Registration

- Every plan produced by the `/plan` command **MUST** be persisted as a standalone file under `docs/exec-plans/`, named `YYYY-MM-DD-<topic>-plan.md` (kebab-case topic, ISO date prefix). Inline plans that live only in chat are not acceptable
- Immediately after the file is written, register it in [docs/exec-plans/index.md](docs/exec-plans/index.md) under the appropriate section (Active while in progress, Completed once finished). A plan that is not indexed is considered non-existent

### Post-Execution Document Housekeeping

- When a plan or spec finishes execution, document housekeeping is **mandatory** and part of the task's definition of done — not optional follow-up:
  1. Mark every task/checklist item in the plan file as completed, with status notes for any deviations, skipped steps, or deferred work
  2. Move the entry in [docs/exec-plans/index.md](docs/exec-plans/index.md) from Active to Completed (and mirror the same for specs in [docs/product-specs/index.md](docs/product-specs/index.md) where applicable)
  3. Propagate any material outcomes (new conventions, schema changes, architectural decisions) back into the canonical docs they affect — CLAUDE.md, ARCHITECTURE.md, docs/DESIGN.md, docs/generated/db-schema.md, etc.
- A task is **not** "done" until this housekeeping is complete. Do not open PRs, claim completion, or move on to the next plan before the index and parent documents reflect the new state
