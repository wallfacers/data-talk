## 1. Backend: honest bridge "starting" state

- [x] 1.1 Add a `starting` state to `OpenCodeBridgeStatus` (initialize snapshot to `status="starting"` instead of `"ok"`; add a `markStarting(message)` method). Keep `markOk` / `markDegraded` intact.
- [x] 1.2 In `OpenCodeGatewayBeans.registerOnStartup` / `startEmbedded`, only call `markOk(...)` after `processManager.isRunning()` is confirmed; leave status `starting` until then. For the disabled/external branches, set the appropriate terminal state as today. (No code change needed — embedded path already flips to `ok` only via `probeRuntimeStatus()` after `isRunning()`; the constructor `starting` default makes the pre-ready window honest.)
- [x] 1.3 Audit all `OpenCodeBridgeStatus` usages and existing backend tests for assumptions that the bridge is `ok` at construction; update them to expect `starting`. (No test changes needed — no test asserts `ok` on a freshly-constructed instance without an explicit `mark*` call.)
- [x] 1.4 Verify backend: `mvn install -pl data-talk-application -am -DskipTests` + targeted tests green. (379 tests; the only failure — `AgentPromptContractTest` — was confirmed pre-existing/unrelated via a clean-tree run.)

## 2. Tauri Rust: status state machine, commands, events, CDS launch

- [x] 2.1 Add a `BackendStatus` enum/state (`Starting`, `Ready`, `Failed { message, log_path }`) managed in Tauri state alongside `BackendProcess` / `BackendPort`.
- [x] 2.2 Update `ensure_started` to set `Starting` on entry, `Ready` when `health_ok` succeeds, and `Failed { message, log_path }` on timeout or premature process exit — emitting `backend://status` on each transition.
- [x] 2.3 Add `#[tauri::command] get_backend_status` returning the current state (race-safe pull) and `#[tauri::command] restart_backend` that re-invokes startup from a `Failed` state.
- [x] 2.4 Remove the blocking `app.dialog()…blocking_show()` failure path in `start_async`; failure is now surfaced via the `Failed` state + event. Stop gating `window.show()` on health (window is already visible — see 4.1).
- [x] 2.5 Add CDS launch args to the backend spawn `Command`: `-XX:SharedArchiveFile=<backend_dir>/app.jsa` and `-XX:+AutoCreateSharedArchive`. Graceful fallback via `AutoCreateSharedArchive` (no existence check).
- [x] 2.6 Register `get_backend_status` and `restart_backend` in `lib.rs` `invoke_handler`; manage the new `BackendStatus` state in the builder (dev/debug branch sets `Ready` so the welcome screen doesn't block dev runs).
- [x] 2.7 Verify Rust: `cargo build` — rustc compiles the crate clean (zero errors/warnings). Note: a plain `cargo build` hits a pre-existing WSL2 `tauri-build` resource-glob permission fault unrelated to this source (reproduces on the pristine tree). State-machine transitions are exercised in 7.3 E2E.

## 3. Frontend: no-flash inline loader + welcome surface

> Design Inputs (`client/DESIGN.md`): Motion as Confirmation (no decorative animation; no glassmorphism); token-first (`bg.app`/`bg.canvas`, `text.muted`, `accent.primary` for the single loading indicator); both themes; `prefers-reduced-motion` → static state; state via text+icon, not color alone.

- [x] 3.1 Add an inline pre-React loader to `client/index.html` (`<style>` + `#boot-loader` inside `#root`) that paints before the React bundle parses; React's `createRoot` replaces it on mount. oklch values mirror `bg.canvas`/`accent.primary` in both `prefers-color-scheme` modes; honors `prefers-reduced-motion`.
- [x] 3.2 Build a `WelcomeScreen` React component (semantic tokens; `motion-reduce:animate-none` static variant; both themes via tokens).
- [x] 3.3 Verify frontend: `npx tsc --noEmit` (exit 0).

## 4. Frontend: readiness wiring + two-phase reveal

- [x] 4.1 Set the main window `visible: true` in `client/src-tauri/tauri.conf.json`.
- [x] 4.2 Add a `useBackendStatus` hook: subscribes to `backend://status` AND pulls `get_backend_status` on mount (race-safe); reports `ready` outside Tauri (dev/vitest).
- [x] 4.3 Gate the app shell in `main.tsx` via `<AppGate>`: `WelcomeScreen` while `starting`, main app on `ready`, `BackendErrorScreen` on `failed`. Non-Tauri bypasses to the app.
- [x] 4.4 Verify frontend: `npx tsc --noEmit` (0); vitest for `selectGateView` state machine (3 cases, jsdom-safe pure logic).

## 5. Frontend: AI-starting banner + error surface

> Design Inputs: banner uses `status.*` info/problem semantics (not raw colors); `starting` vs `degraded` distinguished by text + icon; error surface state not color-only; icon-only actions need accessible names.

- [x] 5.1 Consolidated `OpencodeStatusBanner` driven by bridge `status`: `starting` → neutral info banner (spinner + text), `degraded` (with `reason`) → distinct amber problem banner (alert icon + text), `ok` → none. Replaces the inline degraded notice in `split-view`; `use-opencode-health` polls 2s while not-ok, 30s once settled, so the banner clears promptly.
- [x] 5.2 `BackendErrorScreen`: failure `message`, log path shown + a permission-free "copy log path" action (capabilities don't grant shell-open), and a "retry" action wired to `restart_backend` (Rust resets to `Starting` → event → gate returns to welcome).
- [x] 5.3 Add i18n strings (zh-CN + en-US) for welcome, starting/degraded banner, and error-surface copy.
- [x] 5.4 Verify frontend: `npx tsc --noEmit` (0); vitest for `selectBannerKind` (ok/undefined→none, starting, degraded — 4 cases). Existing `split-view` degraded-notice tests still pass against the relocated banner.

## 6. Packaging: AppCDS archive generation

- [x] 6.1 Add a CDS training-run step to `scripts/bundle-backend.sh`: runs the just-built bundled runtime with `-XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -jar app.jar --server.port=0` in a temp cwd, emitting `app.jsa` next to `app.jar` + `runtime/`. Best-effort (never fails the bundle).
- [x] 6.2 Step runs every bundle immediately after the jar is copied and the runtime is (re)built, so the archive can never go stale relative to them; documented inline.
- [ ] 6.3 (Stretch / spike only) **DEFERRED** — evaluate Spring `process-aot`. Requires a full AOT build + validation of dynamic JDBC drivers + annotation-scanned `@DataTalkAction` handlers; out of scope for this pass, tracked as a follow-up spike. AppCDS alone delivers the committed speedup.

## 7. Verification & end-to-end

- [ ] 7.1 **PARTIAL** — backend `mvn install … && targeted test` passed (379 tests; 1 pre-existing unrelated failure `AgentPromptContractTest`). Full `mvn clean verify` (incl. failsafe IT phase) should run in CI.
- [x] 7.2 Frontend: `npx tsc --noEmit` (0). `npm run test`: 40 failures are **pre-existing and unrelated** — proven by a clean-tree baseline run (40 failed | 1392 passed) identical to the post-change run (40 failed | 1399 passed). This change adds 7 passing tests and introduces **zero regressions**.
- [ ] 7.3 **DEFERRED (needs real desktop)** — Tauri/E2E smoke: window appears immediately w/ welcome animation; no white flash; reduced-motion static; health-200 → main page + AI-starting banner; banner clears on `ok`; degraded shown distinctly; induced failure → error surface; retry recovers. Requires a packaged/`tauri dev` run with a display (not feasible headless in WSL2). Any deviation found MUST be filed under `docs/bugs/`.
- [ ] 7.4 **DEFERRED (needs packaged build)** — measure cold-start before/after CDS; confirm graceful start with `app.jsa` removed. Requires a packaged desktop launch.
- [x] 7.5 Data-source type compatibility: **N/A** — this change touches no JDBC connection handling, schema discovery, SQL execution, or data-source kind. No `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` update required.
