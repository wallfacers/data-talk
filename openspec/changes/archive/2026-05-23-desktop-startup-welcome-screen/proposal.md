## Why

On desktop launch the bundled Spring Boot backend takes ~10s to become healthy. Today the main window is `visible: false` and is only shown *after* the backend health probe succeeds (or times out), so during those ~10s the user sees **nothing** — no window, just a taskbar/Dock icon. Startup failures surface as a blunt blocking system dialog. The result feels broken on cold start.

## What Changes

- **Reveal the window immediately** with a welcome screen instead of hiding it until the backend is healthy. The window paints a loading animation from the first frame.
- **Two-phase reveal**: as soon as the backend HTTP server answers (`/api/health` → 200), route into the main app; show a non-blocking "AI 引擎启动中" banner at the top of the main page until the OpenCode bridge reports `status == ok`, then the banner disappears.
- **Readiness signaling**: Rust exposes a `get_backend_status` command (pull, race-safe) and emits a `backend://status` event (`starting` → `ready` → `failed`) so the frontend transitions precisely instead of guessing.
- **Designed error screen**: replace the blocking system dialog (`backend.rs`) with an in-app error surface (message + retry + view-log action) when startup fails or times out.
- **Backend cold-start optimization**: bundle an **AppCDS** class-data-sharing archive (`app.jsa`) with the desktop package and launch the JRE with `-XX:SharedArchiveFile`, targeting ~10s → ~5–6s. Evaluate Spring AOT (JVM mode) as a stretch.
- **Anti-white-flash**: an inline CSS loader in `index.html` paints before the React bundle parses, so there is no blank frame before the React splash takes over.

## Capabilities

### New Capabilities
- `desktop-startup-experience`: How the desktop app behaves from launch to main page — immediate window reveal, welcome/loading animation, backend readiness signaling (Rust command + event), two-phase reveal with an AI-starting banner, failure error screen with retry, and the cold-start performance target backed by an AppCDS archive.

### Modified Capabilities
<!-- None: no existing spec defines desktop startup behavior. -->

## Impact

- **Frontend (`client/`)**:
  - `index.html` — inline pre-React CSS loader.
  - `src/main.tsx` — readiness gating before/around `RouterProvider` render.
  - New welcome/splash component, error screen, and "AI 引擎启动中" banner.
  - Route guard / app shell to gate the main page on HTTP-200 readiness.
  - Status hook listening to the Tauri `backend://status` event + `get_backend_status` pull fallback.
- **Tauri Rust (`client/src-tauri/`)**:
  - `tauri.conf.json` — main window `visible: true`.
  - `backend.rs` — emit `backend://status` events, add `get_backend_status` command, remove the blocking failure dialog (delegate to frontend error screen), add `-XX:SharedArchiveFile` launch args.
  - `lib.rs` — register the new command and adjust the setup hook (no longer gate `window.show()` on health).
- **Backend (`server/`)** — no Java source behavior change required for the welcome flow. Cold-start optimization is build/packaging: generate `app.jsa` (CDS training run) during the desktop bundle step; optional `process-aot` evaluation.
- **Build/packaging** — desktop bundling pipeline gains a CDS archive generation step; the `.jsa` ships alongside `app.jar` + `runtime/`.
- **No database/data-source type impact** — N/A (no JDBC, schema, or data-source kind touched).

## Design Inputs

This change touches `client/` UI, so per the Frontend Design Contract Gate it is bound by [client/DESIGN.md](../../../client/DESIGN.md):

- **Motion as Confirmation**: the loading animation confirms the startup state transition; it must not be decorative. Forbidden: glassmorphism, cyberpunk color noise, decorative animation as base style (Don't list).
- **Token-first**: use semantic tokens — `bg.app` / `bg.canvas` frame, `text.muted` / `text.soft` for sub-status, `accent.primary` (cobalt) for the loading indicator (cobalt is reserved for focus/primary signal, which a single primary loading indicator qualifies as). The AI-starting banner uses `status.*` info semantics, not raw colors.
- **Calm in Light, Crisp in Dark**: welcome screen, banner, and error screen must render correctly in both themes with unchanged semantics.
- **Accessibility**: honor `prefers-reduced-motion` (disable the non-essential spinner motion, keep a static state); error-screen state must not be communicated by color alone (icon + text); icon-only actions need accessible names.

## Risks / Known Issues

- **BUG-0088 (open, P1)** — packaged fresh-install first launch fails to fetch the models.dev catalog, so OpenCode raises `ProviderModelNotFoundError`. This is exactly the "bridge not truly ready" window the two-phase reveal exposes: routing into the main page on HTTP 200 while the bridge is still degraded could let a user send the first message before models are resolved. The AI-starting banner must derive from the real bridge `status`/`reason` (not just HTTP 200), and the error/degraded path must be distinguishable from a clean "starting". Coordinate with BUG-0088's snapshot-injection fix.
- **AppCDS archive validity**: a CDS archive is tied to the exact JRE + jar. The desktop package bundles both, so the archive is valid by construction — but the build step must regenerate `app.jsa` whenever the jar or bundled runtime changes, or startup silently falls back (no correctness risk, only lost speedup). Use `-XX:+AutoCreateSharedArchive` as a safety net.
- **Spring AOT compatibility** (stretch only): DataTalk relies on dynamic JDBC drivers and annotation-scanned `@DataTalkAction` handlers; AOT processing can break reflection-heavy paths. Treat AOT as an evaluation spike, gated behind validation, not a committed deliverable.
