// Most of this module is only wired up in release builds (the bundled-sidecar
// path); dev builds run the backend separately, so silence dead-code there.
#![cfg_attr(debug_assertions, allow(dead_code))]

use std::fs::File;
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Emitter, Manager};

/// Holds the spawned backend child so it can be terminated on exit.
/// `None` means we reused an already-running backend (dev / external).
pub struct BackendProcess(pub Mutex<Option<Child>>);

/// The port the backend is listening on. Set by `ensure_started`.
pub struct BackendPort(pub Mutex<u16>);

/// Lifecycle status of the bundled backend, surfaced to the frontend.
///
/// Serializes with a `state` tag so the frontend gets a discriminated union:
///   `{"state":"starting"}` / `{"state":"ready"}` /
///   `{"state":"failed","message":"...","log_path":"..."}`
#[derive(Clone, serde::Serialize)]
#[serde(tag = "state", rename_all = "lowercase")]
pub enum BackendStatus {
    Starting,
    Ready,
    Failed { message: String, log_path: String },
}

/// Managed wrapper around the current backend status.
pub struct BackendStatusState(pub Mutex<BackendStatus>);

/// Event name pushed to the frontend whenever the backend status changes.
const STATUS_EVENT: &str = "backend://status";

/// Store the new status into managed state and emit it to the frontend.
fn set_status(app: &AppHandle, status: BackendStatus) {
    {
        let state = app.state::<BackendStatusState>();
        *state.0.lock().unwrap() = status.clone();
    }
    let _ = app.emit(STATUS_EVENT, status);
}

const HEALTH_HOST: &str = "127.0.0.1";
const STARTUP_TIMEOUT: Duration = Duration::from_secs(90);

/// Bind to port 0 on localhost, read the assigned port, close the socket.
fn find_free_port() -> u16 {
    let listener = TcpListener::bind("127.0.0.1:0")
        .expect("Failed to bind to a random port");
    listener.local_addr().unwrap().port()
}

/// Probe `GET /api/health` over a raw socket (avoids pulling in an HTTP client crate).
fn health_ok(port: u16) -> bool {
    let addr: SocketAddr = match format!("{HEALTH_HOST}:{port}").parse() {
        Ok(a) => a,
        Err(_) => return false,
    };
    let mut stream = match TcpStream::connect_timeout(&addr, Duration::from_millis(500)) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(2000)));
    let req = format!(
        "GET /api/health HTTP/1.0\r\nHost: {HEALTH_HOST}\r\nConnection: close\r\n\r\n"
    );
    if stream.write_all(req.as_bytes()).is_err() {
        return false;
    }
    let mut buf = String::new();
    let _ = stream.read_to_string(&mut buf);
    buf.starts_with("HTTP/1.0 200") || buf.starts_with("HTTP/1.1 200")
}

fn ensure_started(app: &AppHandle) -> Result<(), String> {
    // Wallclock baseline for cold-start observability. Captured at entry so it
    // includes find_free_port + jar-existence checks. See
    // openspec/changes/backend-startup-fast-path/specs/.../spec.md
    // "Startup wallclock observability".
    let t0 = Instant::now();
    set_status(app, BackendStatus::Starting);
    let port = find_free_port();

    // Store port so the frontend can read it via get_backend_url
    {
        let state = app.state::<BackendPort>();
        *state.0.lock().unwrap() = port;
    }

    // Check if something is already healthy on this port (extremely unlikely
    // for a freshly-allocated port, but cheap to verify).
    if health_ok(port) {
        log::info!("Backend already healthy on {HEALTH_HOST}:{port}, reusing.");
        log::info!("Backend ready in {}ms", t0.elapsed().as_millis());
        set_status(app, BackendStatus::Ready);
        return Ok(());
    }

    let resource_dir = app.path().resource_dir().map_err(|e| e.to_string())?;

    // Tauri's resource_dir() may return a Windows extended-length path prefixed with
    // `\\?\`.  Java's internal jimage classloader cannot handle that prefix and crashes
    // with "jimage file name is null".  Strip it so all child paths are plain Win32 paths.
    let resource_dir = strip_extended_path_prefix(resource_dir);

    let backend_dir = resource_dir.join("backend");
    let jar = backend_dir.join("app.jar");
    let java = backend_dir
        .join("runtime")
        .join("bin")
        // javaw.exe is the windowless variant — avoids a CMD console popup
        .join(if cfg!(windows) { "javaw.exe" } else { "java" });

    if !jar.exists() || !java.exists() {
        return Err(format!(
            "Bundled backend missing (jar exists: {}, java exists: {})",
            jar.exists(),
            java.exists()
        ));
    }

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if let Ok(meta) = std::fs::metadata(&java) {
            let mut perm = meta.permissions();
            perm.set_mode(0o755);
            let _ = std::fs::set_permissions(&java, perm);
        }
    }

    // Backend writes ./data/*.db and report artifacts relative to its working dir.
    // The install dir is read-only on macOS/Windows, so run from a writable per-user dir.
    let work_dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&work_dir).map_err(|e| e.to_string())?;

    // Point the backend at the bundled OpenCode binary so it never downloads on first
    // launch. Users can swap this file to switch versions. If absent, the backend falls
    // back to its own resolution chain (local cache → classpath → GitHub download).
    let opencode_bin = backend_dir
        .join("opencode")
        .join(if cfg!(windows) { "opencode.exe" } else { "opencode" });

    // Redirect backend stdout/stderr to a log file for diagnostics.
    let log_dir = work_dir.join("logs");
    std::fs::create_dir_all(&log_dir).map_err(|e| format!("Failed to create log dir: {e}"))?;
    let log_path = log_dir.join("backend.log");
    let stdout_file = File::create(&log_path).map_err(|e| format!("Failed to create log file: {e}"))?;
    let stderr_file = stdout_file.try_clone().map_err(|e| format!("Failed to clone log file: {e}"))?;

    log::info!("Spawning backend: {java:?} -jar {jar:?} --server.port={port} (cwd {work_dir:?}, log {log_path:?})");
    // AppCDS: share a class-data archive across launches for faster startup.
    // AutoCreateSharedArchive regenerates the archive automatically when it is
    // missing, stale, or incompatible — graceful fallback, no existence check.
    let jsa = backend_dir.join("app.jsa");
    let mut command = Command::new(&java);
    command
        .arg("-XX:+AutoCreateSharedArchive")
        .arg(format!("-XX:SharedArchiveFile={}", jsa.display()))
        // Spring AOT: activates the BeanFactoryInitializer generated at build
        // time by spring-boot-maven-plugin process-aot, eliminating runtime
        // BeanDefinition reflection. See
        // openspec/changes/backend-startup-fast-path/design.md D1.
        .arg("-Dspring.aot.enabled=true")
        // Desktop sidecar doesn't need JMX endpoints — skips MBean registration.
        .arg("-Dspring.jmx.enabled=false")
        // Skip Spring's background validator/converter preinit; the CPU it
        // would consume during startup is better spent on the main thread.
        .arg("-Dspring.backgroundpreinitializer.ignore=true")
        .arg("-jar")
        .arg(&jar)
        .arg(format!("--server.port={}", port))
        .stdout(Stdio::from(stdout_file))
        .stderr(Stdio::from(stderr_file))
        .current_dir(&work_dir)
        .env_remove("CLASSPATH")
        .env_remove("JAVA_TOOL_OPTIONS")
        .env_remove("_JAVA_OPTIONS")
        .env_remove("JAVA_HOME");
    if opencode_bin.exists() {
        log::info!("Using bundled OpenCode binary: {opencode_bin:?}");
        command.env("DATATALK_OPENCODE_SERVE_BINARY_PATH", &opencode_bin);
    } else {
        log::warn!("Bundled OpenCode binary not found at {opencode_bin:?}; backend will auto-resolve");
    }
    let child = command
        .spawn()
        .map_err(|e| format!("Failed to spawn backend: {e}"))?;

    let pid = child.id();
    app.state::<BackendProcess>()
        .0
        .lock()
        .unwrap()
        .replace(child);

    let deadline = Instant::now() + STARTUP_TIMEOUT;
    while Instant::now() < deadline {
        if health_ok(port) {
            log::info!("Backend ready in {}ms", t0.elapsed().as_millis());
            set_status(app, BackendStatus::Ready);
            return Ok(());
        }
        // Check if the backend process has already exited (crashed).
        {
            let state = app.state::<BackendProcess>();
            let mut guard = state.0.lock().unwrap();
            if let Some(ref mut child) = *guard {
                match child.try_wait() {
                    Ok(Some(status)) => {
                        let code = status.code().map_or("N/A".to_string(), |c| c.to_string());
                        let message = format!(
                            "Backend process (pid {pid}) exited prematurely with code {code}. \
                             Check log at {}",
                            log_path.display()
                        );
                        set_status(
                            app,
                            BackendStatus::Failed {
                                message: message.clone(),
                                log_path: log_path.display().to_string(),
                            },
                        );
                        return Err(message);
                    }
                    Ok(None) => {} // still running
                    Err(e) => {
                        log::warn!("Failed to check backend process status: {e}");
                    }
                }
            }
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    let message = format!(
        "Backend did not become healthy within {}s (pid {pid}). \
         Check log at {}",
        STARTUP_TIMEOUT.as_secs(),
        log_path.display()
    );
    set_status(
        app,
        BackendStatus::Failed {
            message: message.clone(),
            log_path: log_path.display().to_string(),
        },
    );
    Err(message)
}

/// Run startup on a background thread, then reveal the main window.
/// Keeps the Tauri setup hook (main thread) non-blocking. Failure is surfaced
/// purely via the `Failed` status + `backend://status` event (set inside
/// `ensure_started`) — no blocking dialog.
fn spawn_startup(app: AppHandle) {
    std::thread::spawn(move || {
        match ensure_started(&app) {
            Ok(()) => log::info!("Backend ready."),
            Err(e) => log::error!("Backend startup failed: {e}"),
        }
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.show();
            let _ = window.set_focus();
        }
    });
}

/// Start the backend on a background thread (initial launch).
pub fn start_async(app: AppHandle) {
    spawn_startup(app);
}

/// Tauri command: returns the current backend status (race-safe pull, so the
/// frontend recovers even if it missed the `backend://status` event).
#[tauri::command]
pub fn get_backend_status(app: AppHandle) -> BackendStatus {
    app.state::<BackendStatusState>().0.lock().unwrap().clone()
}

/// Tauri command: re-attempt startup. No-op unless the current status is
/// `Failed`. Resets status to `Starting` and reuses the standard startup path.
#[tauri::command]
pub fn restart_backend(app: AppHandle) {
    {
        let state = app.state::<BackendStatusState>();
        let guard = state.0.lock().unwrap();
        if !matches!(*guard, BackendStatus::Failed { .. }) {
            return;
        }
    }
    set_status(&app, BackendStatus::Starting);
    spawn_startup(app);
}

/// Terminate the backend we spawned. Sends a graceful signal first so the backend's
/// shutdown hook can stop its OpenCode child, then force-kills as a fallback.
pub fn stop(app: &AppHandle) {
    let child = app.state::<BackendProcess>().0.lock().unwrap().take();
    if let Some(mut child) = child {
        let pid = child.id();
        log::info!("Stopping backend pid {pid}");
        #[cfg(unix)]
        {
            let _ = Command::new("kill").arg(pid.to_string()).output();
        }
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            // CREATE_NO_WINDOW: taskkill is a console app; spawning it from the
            // GUI process without this flag flashes a console window on exit.
            const CREATE_NO_WINDOW: u32 = 0x0800_0000;
            let _ = Command::new("taskkill")
                .args(["/PID", &pid.to_string(), "/T", "/F"])
                .creation_flags(CREATE_NO_WINDOW)
                .output();
        }
        std::thread::sleep(Duration::from_millis(1500));
        let _ = child.kill();
    }
}

/// Tauri command: returns the backend URL for the frontend to use.
#[tauri::command]
pub fn get_backend_url(app: AppHandle) -> String {
    let state = app.state::<BackendPort>();
    let port = *state.0.lock().unwrap();
    if port == 0 {
        // Backend hasn't started yet — fallback to 8080
        return "http://127.0.0.1:8080".to_string();
    }
    format!("http://127.0.0.1:{}", port)
}

/// Strip the Windows extended-length path prefix (`\\?\`) from a path.
///
/// Tauri's `resource_dir()` returns paths with this prefix on Windows when the
/// install directory has a long absolute path.  Java's internal jimage classloader
/// treats the prefix as part of the file name and fails to locate the runtime
/// modules, crashing with "jimage file name is null".  Stripping the prefix
/// produces a normal Win32 path that both Windows APIs and Java understand.
fn strip_extended_path_prefix(p: std::path::PathBuf) -> std::path::PathBuf {
    let path_str = p.to_string_lossy();
    // `\\?\` is the Win32 extended-length prefix (4 chars: two backslashes + ? + backslash).
    // `\\?\UNC\` is the UNC variant (8 chars).
    if let Some(rest) = path_str.strip_prefix(r"\\?\") {
        // UNC path: \\?\UNC\server\share\... → \\server\share\...
        if let Some(unc_rest) = rest.strip_prefix("UNC\\") {
            return std::path::PathBuf::from(format!(r"\\{}", unc_rest));
        }
        // Normal path: \\?\D:\... → D:\...
        return std::path::PathBuf::from(rest);
    }
    p
}
