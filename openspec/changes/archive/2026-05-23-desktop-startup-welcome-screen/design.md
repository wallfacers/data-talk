## Context

In release (bundled) mode, `lib.rs` setup hook calls `backend::start_async`, which spawns a thread that runs `ensure_started` (`backend.rs`). That function blocks ~10s polling `/api/health` until HTTP 200, then calls `window.show()`. The main window is configured `visible: false` in `tauri.conf.json`, so it is hidden for the entire startup. Failures surface via a blocking `tauri_plugin_dialog` message box.

Two facts shape the design:

1. **`/api/health` always returns HTTP 200** (`QueryController.health()`); the real OpenCode bridge state lives in the JSON body (`status` ∈ {`ok`, `degraded`}, plus `message`/`reason`). So Rust's `health_ok` (which only matches `HTTP/1.x 200`) flips to "ready" the moment Tomcat is listening — i.e. when the Spring web server is up, which is the bulk of the ~10s. OpenCode embedded process start + 17 skill syncs run later in `ApplicationReadyEvent` (`OpenCodeGatewayBeans.registerOnStartup`) and do **not** block the 200.

2. **`OpenCodeBridgeStatus` initializes to `"ok"`** (`OpenCodeBridgeStatus.java:18`) and only flips to `"degraded"` on failure. It never represents "starting". So a banner keyed naively on `status == ok` would never appear — the bridge claims OK before OpenCode has even started. Making the AI-starting banner *honest* requires the backend to report a genuine "starting" state until the embedded OpenCode process is confirmed running.

Dev mode is unaffected (backend runs separately; setup hook just shows the window).

## Goals / Non-Goals

**Goals:**
- Window visible immediately with a welcome/loading animation; no hidden-window dead time.
- Deterministic, race-free transition to the main page (Rust command + event, frontend pull-on-mount fallback).
- Two-phase reveal: enter main on HTTP-200; a non-blocking AI-starting banner clears only on *genuine* bridge readiness.
- Designed in-app error surface with retry, replacing the blocking native dialog.
- Reduce backend cold start (~10s → ~5–6s target) via a bundled AppCDS archive, with graceful fallback.

**Non-Goals:**
- GraalVM native image or CRaC (rejected: dynamic JDBC drivers, multi-platform CI, special JDK).
- `spring.main.lazy-initialization` (rejected: moves cost to first AI query — worse perceived experience).
- Reworking the OpenCode bridge protocol or skill-sync mechanism.
- Fixing BUG-0088 itself (model catalog fetch) — this change only makes its degraded window visible/non-misleading.

## Decisions

### D1: Single window, React-internal welcome surface (A2), not a separate splash window

The welcome screen lives inside the existing main window/React tree, gated before the main app shell renders. Chosen over a separate Rust-managed splash window because it reuses one token system / theme / i18n (per `client/DESIGN.md` "Dual-Core, One System"), keeps the error surface in one place, and minimizes Rust glue (no second webview, no cross-window close timing).

- **Alternative considered**: separate `splashscreen` window with static HTML — fastest first paint, but duplicates styling, needs two-window lifecycle, and can't share the design system. Rejected.
- **White-flash mitigation**: an inline `<style>`+markup loader in `index.html` paints before the React bundle parses; React's welcome surface replaces it on mount. This recovers the only real advantage of the separate-window approach.

### D2: `tauri.conf.json` main window `visible: true`; setup hook no longer gates `window.show()` on health

The window is shown by the OS immediately. `start_async` still spawns the backend on a background thread, but its job becomes "drive backend startup + broadcast status", not "reveal window when done". `ensure_started`'s existing health-poll loop is reused as the source of the `ready`/`failed` transition.

### D3: Readiness signaling = `get_backend_status` command + `backend://status` event

- New Rust state holding `BackendStatus` ∈ {`Starting`, `Ready`, `Failed{message, log_path}`}, updated by `ensure_started`.
- New `#[tauri::command] get_backend_status` returns the current state (pull, race-safe).
- Rust `emit("backend://status", …)` on each transition (push, low-latency).
- Frontend on mount: call `get_backend_status` first (covers the case where `ready` was emitted before the listener attached), then subscribe to `backend://status` for the live transition. No polling of `/api/health` from the frontend is required for the *reveal* (Rust already polls it); the frontend health query (`use-opencode-health`) continues to drive the bridge banner.

- **Alternative considered**: pure frontend `/api/health` polling, zero Rust changes. Simpler, but loses the precise `failed` reason/log-path needed for the designed error surface and re-implements polling the Rust side already does. Rejected in favor of the command+event contract.

### D4: Two-phase reveal + honest bridge banner

- **Phase 1 (reveal)**: `backend://status == ready` (HTTP 200) → dismiss welcome, render main app.
- **Phase 2 (banner)**: an "AI 引擎启动中" banner shows on the main page, driven by the OpenCode bridge `status` from `/api/health` body, and clears when the bridge is genuinely ready.

To make Phase 2 honest, the backend must report "starting" until the embedded OpenCode process is confirmed running. **Minimal backend change**: initialize `OpenCodeBridgeStatus` to a non-OK starting state (e.g. `markStarting()` / initial `status = "starting"`) and only `markOk()` after `processManager.start()` confirms `isRunning()` in `OpenCodeGatewayBeans.startEmbedded`. The health body gains a third `status` value `starting` alongside `ok`/`degraded`. This is an additive change to a value object + one bean; no `DtEvent` sealed-interface impact, no Flyway migration.

- **Frontend banner states**: `starting` → neutral info banner; `degraded` (with `reason`) → problem banner distinct from starting (text + icon, not color alone, per accessibility rule); `ok` → no banner.

### D5: AppCDS archive generated at desktop-bundle time, launched with `-XX:SharedArchiveFile`

Spring Boot 3.5 + Java 21 supports CDS first-class. The desktop bundle is the ideal place: it ships the exact `app.jar` + `runtime/` JRE, so the archive is valid by construction.

- **Build step**: a CDS training run during packaging — `java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -jar app.jar` — produces `app.jsa` next to the jar.
- **Launch**: `backend.rs` adds `-XX:SharedArchiveFile=<backend_dir>/app.jsa -XX:+AutoCreateSharedArchive` to the spawn args. `AutoCreateSharedArchive` regenerates a stale/missing archive at runtime instead of failing.
- **Spring AOT (stretch)**: `process-aot` for further reflection reduction is an *evaluation spike only*, gated behind validation of dynamic JDBC drivers + annotation-scanned `@DataTalkAction` handlers. Not a committed deliverable.

- **Alternative considered**: JVM flags like `-XX:TieredStopAtLevel=1` — speeds startup but hurts steady-state for a long-lived server. Marginal/risky, not pursued.

### D6: Error surface replaces the blocking dialog

The `app.dialog().…blocking_show()` failure path in `start_async` is removed. On `failed`, Rust sets `BackendStatus::Failed{message, log_path}` and emits the event; the React error surface renders message + "view log" + "retry". Retry re-invokes backend startup (a new Rust command, e.g. `restart_backend`) and the UI returns to the welcome state.

## Risks / Trade-offs

- **[BUG-0088 — degraded first-launch bridge]** → The two-phase reveal deliberately surfaces the degraded window via the banner's `degraded` state instead of hiding it. Banner must read `reason` from the health body so a model-catalog failure is visible, not a silent "ready". Coordinate banner copy with BUG-0088's snapshot-injection fix.
- **[Bridge status semantics change]** → Initializing to `starting` changes a long-standing optimistic default. Any code/test asserting the bridge is `ok` at construction must be updated. Audit `OpenCodeBridgeStatus` usages and existing tests (WireMock/health tests) for the new `starting` value. Additive enum-of-strings, so no exhaustive-switch compile break, but tests may assert literals.
- **[Stale CDS archive silently disabling speedup]** → `AutoCreateSharedArchive` self-heals at runtime; the packaging step regenerates `app.jsa` whenever the jar/runtime changes. Worst case is lost speedup, never a startup failure (spec scenario covers this).
- **[Reveal-on-200 vs AI-not-ready]** → Mitigated by D4's honest banner; a user *can* still open the composer before the bridge is `ok`. The banner + degraded state communicate this; gating composer send on bridge readiness is a follow-up, out of scope here.
- **[jsdom can't verify the no-white-flash / animation]** → Per prior learning, layout/paint behavior is not testable in jsdom. The visual reveal, no-flash, and reduced-motion behavior must be verified in a real Tauri/E2E run, not unit tests. Unit tests cover the status state machine + transition logic only.

## Migration Plan

1. Backend: add `starting` bridge state (value object + `OpenCodeGatewayBeans` flip after `isRunning()`), update affected tests. `mvn install -pl data-talk-application -am -DskipTests` so the running adapter picks up the new class.
2. Rust: `BackendStatus` state, `get_backend_status` + `restart_backend` commands, `backend://status` emits, remove blocking dialog, add CDS launch args, register commands in `lib.rs`, flip `visible: true`.
3. Frontend: `index.html` inline loader; welcome surface + error surface + banner components; status hook (command + event); route/shell guard for two-phase reveal.
4. Packaging: CDS training-run step emits `app.jsa` into the bundled backend dir.
5. Verify: Tauri/E2E smoke for reveal, no-flash, reduced-motion, failure→error→retry, banner starting→ok and starting→degraded; backend tests for `starting` state; measure cold-start before/after CDS.

**Rollback**: revert `visible: true` and the setup-hook change to restore hidden-window behavior; CDS args and the `starting` state are independently revertible (each degrades to prior behavior).

## Open Questions

- Should the retry action also re-run the CDS-enabled launch, or fall back to a plain launch on repeated failure? (Default: same launch path; `AutoCreateSharedArchive` already self-heals.)
- Exact cold-start target to assert in CI — fixed seconds vs. relative-to-baseline ratio? (Leaning relative, since absolute timing is hardware-dependent.)
- Does the AI-starting banner belong in the global app shell or the session/composer feature? (Leaning app shell, since it is session-independent like Stage state.)
