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

## Knowledge Base Navigation (docs/)

This file is the map. Deep knowledge lives in `docs/`:

| Looking for...               | Go to                                                        |
|------------------------------|--------------------------------------------------------------|
| System architecture & layers | [ARCHITECTURE.md](ARCHITECTURE.md)                           |
| Design docs & core beliefs   | [docs/design-docs/index.md](docs/design-docs/index.md)      |
| Execution plans (active/done)| [docs/exec-plans/index.md](docs/exec-plans/index.md)         |
| Product specs & features     | [docs/product-specs/index.md](docs/product-specs/index.md)   |
| DB schema reference          | [docs/generated/db-schema.md](docs/generated/db-schema.md)   |
| External protocol references | [docs/references/](docs/references/)                         |
| Design patterns & conventions| [docs/DESIGN.md](docs/DESIGN.md)                             |
| Backend dev guide            | [docs/BACKEND.md](docs/BACKEND.md)                           |
| Frontend dev guide           | [docs/FRONTEND.md](docs/FRONTEND.md)                         |
| Plan workflow                | [docs/PLANS.md](docs/PLANS.md)                               |
| Quality standards & scoring  | [docs/QUALITY.md](docs/QUALITY.md)                           |
| Reliability practices        | [docs/RELIABILITY.md](docs/RELIABILITY.md)                   |
| Security guide               | [docs/SECURITY.md](docs/SECURITY.md)                         |
| Tech debt tracker            | [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md) |

## Current Status

- **Plan A** (Backend Platform Foundation): **completed** — 4-module skeleton, Action/Ontology Registry, SessionBus, Streamable HTTP Channel, SQLite persistence, OpenCode Gateway
- **Plan B** (MVP Actions): pending
- **Plan C** (Client Split-View): pending

See [docs/exec-plans/index.md](docs/exec-plans/index.md) for details.

## Working Rules

### Bug Fixes

- Proactively inspect related code when fixing a bug. In this 4-layer architecture, pay special attention: changes to domain sealed interfaces/records require checking application-layer exhaustive switches for sync updates

### Post-Edit Verification

- **Backend**: after every edit, run `cd server && mvn compile -q` — confirm zero compilation errors before proceeding
- **Frontend**: after every edit, run `cd client && npx tsc --noEmit` — confirm zero type errors before proceeding

### Backend Run vs Compile

- `mvn spring-boot:run` loads non-adapter modules from `~/.m2`, not `target/classes`. After editing `domain`/`application`/`infrastructure`, use `mvn install -pl <module> -am -DskipTests` — `mvn compile` won't refresh the jar and the running process keeps the old class

### Response Style

- Be concise and direct. No filler

### Plan Mode

- Multi-step changes **MUST** use `/plan` to align on approach before writing code. Follow the existing plan workflow in [docs/PLANS.md](docs/PLANS.md)

### Brainstorming

- Major changes (new modules, architecture adjustments, cross-layer refactors spanning domain/application/infrastructure/adapter) **MUST** invoke the `brainstorming` skill first

### Testing

- New features **MUST** have corresponding tests. No tests = not done
- Backend: JUnit 5 + AssertJ. For OpenCode protocol interactions, use WireMock (FakeOpenCodeServer)
- Frontend: vitest

### Clarify Before Acting

- If requirements, scope, or implementation approach are unclear, **MUST** ask for clarification first. Never guess

### Documentation Paths

- Design specifications generated by the Superpowers `brainstorming` skill **MUST** be stored at `docs/product-specs/YYYY-MM-DD-<topic>-design.md` and indexed in [docs/product-specs/index.md](docs/product-specs/index.md) under §8 "Individual Design Documents"
- Execution plans generated by the Superpowers `writing-plans` skill **MUST** be stored at `docs/exec-plans/YYYY-MM-DD-<topic>-plan.md` and indexed in [docs/exec-plans/index.md](docs/exec-plans/index.md) under the applicable section (Active / Completed)
- The skills' built-in default paths (`docs/superpowers/specs/` and `docs/superpowers/plans/`) are **deprecated**; the paths above supersede them in all cases
