## MODIFIED Requirements

### Requirement: Backend cold-start performance target

The desktop package SHALL bundle a Spring AOT-processed jar **and** an AppCDS class-data-sharing archive, launch the bundled JRE with both enabled, enable Spring lazy-initialization with an explicit pin list for must-be-eager beans, and reduce backend cold-start wallclock to **≤ 10 seconds (p95)** measured from Tauri's `ensure_started()` entry to `BackendStatus::Ready`.

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

## ADDED Requirements

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
