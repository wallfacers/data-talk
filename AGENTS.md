# DataTalk — Agent Handoff

AI-powered intelligent database collaboration platform. Tauri v2 + React 19 + Spring Boot 3.5 + OpenCode HTTP.

## Quick Context

- **Full project rules**: [CLAUDE.md](CLAUDE.md)
- **Architecture**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Active changes**: `openspec list` or see `openspec/changes/`
- **System specs**: `openspec/specs/`

## OpenSpec SDD

This project uses OpenSpec (spec-driven schema). All work goes through:

```
/opsx:explore     → Think, compare, diagram (no code)
/opsx:propose     → Create proposal + design + tasks + delta specs
/opsx:apply       → Implement tasks checkbox-by-checkbox
/opsx:archive     → Merge specs, archive to openspec/changes/archive/
```

Before starting any task, run `openspec list --json` to see active changes.

## Stack Summary

| Layer    | Stack |
|----------|-------|
| Desktop  | Tauri v2, React 19, TanStack Router/Query, Zustand, shadcn/ui |
| Backend  | Spring Boot 3.5, Java 21 (virtual threads), JdbcTemplate, Flyway |
| Metadata | SQLite |
| User DBs | Dynamic JDBC — MySQL, PostgreSQL, H2 + 16 first-class kinds |
| AI       | OpenCode HTTP (Streamable HTTP + SSE, JSON-RPC) |
| Testing  | JUnit 5 + AssertJ + WireMock (backend), vitest (frontend) |

## Build & Verify

```bash
# Backend
cd server && mvn clean verify           # full suite
cd server && mvn compile -q             # quick compile check after edits
# After editing domain/application/infrastructure:
mvn install -pl <module> -am -DskipTests

# Frontend
cd client && npx tsc --noEmit           # type check after edits
cd client && npm run dev                # dev server
```

## Critical Gates (always check before acting)

1. **Frontend UI** → read [client/DESIGN.md](client/DESIGN.md) first, cite constraints
2. **Database/data-source types** → read [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md)
3. **Bug fixes** → grep `docs/bugs/` for existing/duplicate BUGs first
4. **New plans/specs** → check `docs/bugs/index.md` open BUGs for overlap
5. **Post-edit** → `mvn compile -q` (backend) or `npx tsc --noEmit` (frontend)
6. **Temp files** → `tmp/` only (git-ignored). Never: repo root, `client/`, `server/`, `docs/`, system `/tmp`

## Hard MUSTs

- **Testing**: New features MUST have tests. No tests = not done.
- **Clarify**: If requirements or approach are unclear, MUST ask before acting. Never guess.
- **BUG reporting**: After E2E runs, MUST report "Found N BUGs" even when N=0.
- **Major architecture**: MUST invoke `superpowers:brainstorming` before `/opsx:propose`.
- **BUG evidence**: Screenshots at `docs/bugs/assets/<BUG-ID>/` (PNG ≤ 500KB) may be committed. Traces/HAR/HTML stay in `tmp/`.

## Key Conventions

- **Architecture**: domain ← application ← infrastructure ← adapter (no reverse)
- **Actions**: `@DataTalkAction`-annotated `ActionHandler` bean = auto-registered
- **Events**: `DtEvent` sealed interface, 22 types, exhaustive switch
- **Stage state**: global/session-independent (`useStageStore` single values, not per-session maps)
- **Ingestion**: `SPRING_PROFILES_ACTIVE=e2e` for E2E tests (never in production)
- **E2E BUGs**: discovered deviations MUST create `docs/bugs/` files, never verbal-only
