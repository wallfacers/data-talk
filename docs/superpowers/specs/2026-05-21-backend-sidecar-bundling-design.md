# Backend Sidecar Bundling — Design (v0.0.1)

## Problem

DataTalk ships as a Tauri desktop app, but the Spring Boot backend (which itself
launches OpenCode) is not bundled. The packaged installer therefore cannot reach
a backend: `VITE_API_BASE_URL` defaults to empty, so requests resolve against the
`tauri://localhost` webview origin and fail. This change bundles the backend so the
installer is self-contained for the first release.

## Decision

Bundle the backend as a **full-module Java runtime + executable fat jar**, spawned
by the Tauri Rust layer on startup.

## Components

### 1. Backend artifact
- `mvn -pl data-talk-adapter -am package` produces the Spring Boot fat jar
  (`data-talk-adapter/target/data-talk-adapter-<maven-version>.jar`), copied to a
  stable name `app.jar`.

### 2. Bundled runtime
- Per platform, `jlink --add-modules ALL-MODULE-PATH` from the Temurin JDK 21 used
  by CI produces a complete-module runtime image (~70MB). All modules are present,
  so no module-enumeration risk for Spring's dynamic JDBC / Flyway / reflection.

### 3. Bundle layout (Tauri `bundle.resources`)
```
resources/backend/
  app.jar
  runtime/        # jlink image; bin/java[.exe]
```

### 4. Rust launcher (`src-tauri/src/lib.rs`, setup hook)
- Probe `http://127.0.0.1:8080/api/health`. If already healthy (dev / already
  running), reuse it and do not spawn.
- Otherwise spawn `<resource>/backend/runtime/bin/java -jar <resource>/backend/app.jar`
  with **working dir set to a writable per-user app-data dir** (install dir is
  read-only on macOS/Windows; backend writes `./data/*.db`, report/file artifacts).
- Poll `/api/health` until 200 or ~60s timeout; gate webview readiness on it.
- On app exit, kill the backend process tree (backend + its OpenCode child;
  backend already configured `server.shutdown: graceful`).

### 5. Frontend API base
- `client/.env.production` sets `VITE_API_BASE_URL=http://127.0.0.1:8080`. Dev stays empty.

### 6. CORS
- Add Tauri webview origins to `CorsConfig` uiConfig allowed-origin patterns:
  `tauri://localhost`, `http://tauri.localhost`, `https://tauri.localhost`.

### 7. CI (`release.yml`)
Per-platform build, before `tauri build`: setup-java JDK 21 → `mvn package` (fat jar)
→ `jlink` runtime → copy both into `client/src-tauri/resources/backend/` →
export `VITE_API_BASE_URL` → `tauri build`.

## Data flow
App start → Rust spawns backend → backend runs Flyway (single V1 baseline), starts
on 8080, launches OpenCode on 4096 → Rust health-poll → ready → webview loads →
frontend hits `127.0.0.1:8080/api/**` → CORS allows tauri origin.

## Error handling
- Backend start failure / health timeout → error dialog, then quit.
- Port 8080 occupied by a healthy DataTalk backend → reuse. Occupied by a foreign
  process / unhealthy → surface error.

## Known limitations (v0.0.1)
- **Ledger PDF export** depends on Playwright Chromium (~150MB) and is **not**
  bundled this release; PDF export fails in the packaged app, MD/HTML still work.
- **Local verification** is Linux-only (WSL): fat jar boot + `/api/health` + Rust
  compile. Three-platform installer GUI smoke relies on CI.

## Out of scope
- OpenCode binary bundling — backend's `OpenCodeBinaryResolver` resolves it at
  runtime (already on `develop`).
- Code signing / notarization — `release.yml` already builds unsigned when secrets
  are absent.
