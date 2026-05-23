# desktop-startup-experience Specification

## Purpose

定义桌面端启动到主页的过渡体验:窗口秒开、欢迎/加载动画、后端就绪信号(Rust 命令 + `backend://status` 事件)、两阶段揭示(HTTP 200 进主页 + "AI 引擎启动中"非阻塞横幅按真实 OpenCode 桥状态消隐)、启动失败的设计化错误屏与重试,以及由 AppCDS 归档支撑的冷启动性能目标。

本 spec 在归档时作为 capability `desktop-startup-experience` 的基线落入 `openspec/specs/desktop-startup-experience/`。

## Requirements

### Requirement: Immediate window reveal with welcome screen

The desktop app SHALL make its main window visible immediately on launch and display a welcome/loading surface, instead of keeping the window hidden until the backend is healthy.

#### Scenario: Window appears at launch before backend is ready

- **WHEN** the user launches the desktop app and the bundled backend has not yet become healthy
- **THEN** the main window is visible within the OS window-creation time (not gated on backend health)
- **AND** a welcome surface with a loading animation is shown

#### Scenario: No blank frame before the React welcome surface

- **WHEN** the window becomes visible but the React bundle has not finished parsing/mounting
- **THEN** an inline (pre-React) loading indicator rendered from `index.html` is painted
- **AND** when React mounts, the React welcome surface replaces the inline indicator with no visible blank/white frame in between

#### Scenario: Loading animation respects reduced motion

- **WHEN** the OS / browser reports `prefers-reduced-motion: reduce`
- **THEN** the welcome surface presents a static loading state instead of animated motion
- **AND** the readiness state remains communicated by text, not motion or color alone

### Requirement: Backend readiness signaling to the frontend

The Tauri Rust layer SHALL expose backend startup status to the frontend through both a pull command and a push event, so the frontend can transition deterministically without a registration race.

#### Scenario: Frontend pulls current status on mount

- **WHEN** the frontend invokes the `get_backend_status` command
- **THEN** Rust returns the current backend startup state as one of `starting`, `ready`, or `failed`
- **AND** when the state is `failed`, the returned payload includes a human-readable failure message and a reference to the backend log path

#### Scenario: Rust emits status transitions as events

- **WHEN** the backend startup state changes (e.g. health probe first succeeds, or startup times out / the process exits prematurely)
- **THEN** Rust emits a `backend://status` event carrying the new state (`starting` → `ready`, or → `failed`)

#### Scenario: Event missed before listener registration is recovered by pull

- **WHEN** the frontend registers its `backend://status` listener after Rust has already emitted `ready`
- **THEN** the frontend still observes readiness via the `get_backend_status` pull it performs on mount
- **AND** the app transitions to the main page without waiting indefinitely

### Requirement: Two-phase reveal into the main app

The app SHALL route into the main page as soon as the backend HTTP server answers (HTTP-200 readiness), and SHALL NOT block the main page on full AI-bridge readiness.

#### Scenario: Enter main page on HTTP-200 readiness

- **WHEN** the backend `/api/health` endpoint first returns HTTP 200 (state `ready`)
- **THEN** the welcome surface is dismissed and the main application page is shown
- **AND** the transition happens without an additional manual user action

#### Scenario: Welcome surface remains while backend HTTP server is not yet up

- **WHEN** the backend has not yet returned any HTTP 200 response
- **THEN** the welcome/loading surface remains visible
- **AND** the main page is not shown

### Requirement: AI-engine starting banner reflects genuine bridge readiness

After entering the main page on HTTP-200 readiness, the app SHALL display a non-blocking banner indicating the AI engine is still starting, and SHALL clear it only when the OpenCode bridge genuinely reports readiness — not merely because the HTTP server is up.

#### Scenario: Banner shown while OpenCode bridge is still starting

- **WHEN** the main page is shown but the OpenCode bridge has not yet reached a ready state
- **THEN** a non-blocking "AI 引擎启动中" banner is displayed at the top of the main page
- **AND** the rest of the main page remains interactive (the banner does not block navigation or non-AI features)

#### Scenario: Banner clears when the bridge is genuinely ready

- **WHEN** the OpenCode bridge reports a genuine ready state (bridge status `ok` after the embedded OpenCode process is confirmed running, not the optimistic initial default)
- **THEN** the AI-starting banner is removed

#### Scenario: Bridge readiness is distinguishable from a degraded bridge

- **WHEN** the OpenCode bridge reports `degraded` with a failure reason (e.g. model catalog unavailable)
- **THEN** the banner conveys a degraded/problem state distinct from a normal "starting" state
- **AND** the state is communicated with text and an icon, not color alone

### Requirement: Designed startup-failure surface

When the backend fails to start or does not become healthy within the startup timeout, the app SHALL present an in-app error surface with a retry action, instead of a blocking native system dialog.

#### Scenario: Startup timeout shows the error surface

- **WHEN** the backend does not become healthy within the startup timeout, or the backend process exits prematurely
- **THEN** the app shows an in-app error surface inside the existing window
- **AND** the surface displays the failure message and an action to view the backend log
- **AND** no blocking native system dialog is shown for this failure

#### Scenario: Retry re-attempts backend startup

- **WHEN** the user activates the retry action on the error surface
- **THEN** the app re-attempts backend startup and returns to the welcome/loading state
- **AND** on success it proceeds with the normal two-phase reveal

### Requirement: Backend cold-start performance target

The desktop package SHALL bundle a Spring AOT-processed jar **and** an AppCDS class-data-sharing archive, launch the bundled JRE with both enabled, defer non-essential pool initialization off the main startup path, and reduce backend cold-start wallclock to **≤ 10 seconds (p95)** measured from Tauri's `ensure_started()` entry to `BackendStatus::Ready`.

#### Scenario: AOT artifacts present in the packaged jar

- **WHEN** the desktop package is built
- **THEN** the bundled `app.jar` contains Spring AOT-generated artifacts under `META-INF/spring/aot.factories` (or equivalent AOT output paths)
- **AND** the backend is launched with `-Dspring.aot.enabled=true`

#### Scenario: CDS archive is bundled and used at launch

- **WHEN** the desktop package is built and launched
- **THEN** an AppCDS archive (`app.jsa`) is present alongside the bundled `app.jar` and JRE
- **AND** the backend JRE is launched with class-data-sharing enabled against that archive

#### Scenario: Missing or stale archive degrades gracefully

- **WHEN** the bundled CDS archive is absent or incompatible with the bundled jar/JRE
- **THEN** the backend still starts correctly without the archive (no startup failure)
- **AND** only the cold-start speedup is lost, not correctness

#### Scenario: SQLite metadata pool initialization is deferred off the startup path

- **WHEN** the backend starts
- **THEN** the `datatalk-sqlite` Hikari pool (used for DataTalk's own metadata/persistence) is NOT eagerly created during Spring context refresh
- **AND** Flyway-style migration of that pool runs only after `ApplicationReadyEvent` fires, ordered at `Ordered.HIGHEST_PRECEDENCE` so it completes before any other ApplicationReady listener queries it
- **AND** any `@Scheduled` task that depends on the SQLite metadata schema uses `initialDelay` long enough (≥ 60 seconds) to skip the post-ready warming window

#### Scenario: Demo H2 datasource bypasses DriverManager for fast pool startup

- **WHEN** the backend starts
- **THEN** the `demoDataSource` Hikari pool is built using `HikariConfig.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource")` rather than via `jdbcUrl`/`driverClassName`, so its initialization does not trigger the JDBC `DriverManager` SPI scan over all bundled JDBC drivers

#### Scenario: JDBC driver `<clinit>` work is pre-warmed in parallel

- **WHEN** the JVM starts the backend `main()`
- **THEN** a daemon thread invokes `ServiceLoader.load(java.sql.Driver.class).stream().parallel().forEach(p -> p.get())` before `SpringApplication.run(...)`, overlapping driver class loading + `<clinit>` with Spring's bootstrap on the main thread

#### Scenario: Cold-start wallclock meets the 10-second SLA on Linux

- **WHEN** the desktop package is launched on Linux (developer baseline machine) after a clean process exit
- **THEN** the wallclock time from `ensure_started()` entry to `BackendStatus::Ready` is ≤ 10 seconds at p95 across at least 3 consecutive runs
- **AND** the measured duration is recorded to the backend log file

### Requirement: Startup wallclock observability

The Tauri Rust layer SHALL record the cold-start wallclock duration in the backend log on every successful startup, so future regressions can be detected without instrumenting new code.

#### Scenario: Successful startup logs measured duration

- **WHEN** `BackendStatus` transitions from `Starting` to `Ready`
- **THEN** the Rust process logs a line containing the measured duration in milliseconds (e.g. `Backend ready in 6432ms`)
- **AND** the log line is included in the per-launch backend log file

#### Scenario: Failed startup does not log a fake duration

- **WHEN** `BackendStatus` transitions from `Starting` to `Failed`
- **THEN** no "Backend ready in ...ms" line is written
- **AND** the existing failure-state log path and message are preserved as the diagnostic source

### Requirement: Startup-blocking work moved off the main initialization path

Any backend `@PostConstruct` initializer that performs blocking I/O (such as disk scans, network probes, or large file enumeration) SHALL be moved to an `ApplicationReadyEvent` listener executed asynchronously, so the main Spring context refresh phase is not blocked by housekeeping work.

#### Scenario: Export housekeeping runs after readiness

- **WHEN** the backend starts
- **THEN** export-history cleanup runs only after `ApplicationReadyEvent` is published
- **AND** the cleanup runs on a background thread (not the main initialization thread)
- **AND** cleanup failures do not affect `/api/health` readiness or `BackendStatus::Ready` signaling
