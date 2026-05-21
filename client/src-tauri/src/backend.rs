// Most of this module is only wired up in release builds (the bundled-sidecar
// path); dev builds run the backend separately, so silence dead-code there.
#![cfg_attr(debug_assertions, allow(dead_code))]

use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager};

/// Holds the spawned backend child so it can be terminated on exit.
/// `None` means we reused an already-running backend (dev / external).
pub struct BackendProcess(pub Mutex<Option<Child>>);

const HEALTH_HOST: &str = "127.0.0.1";
const HEALTH_PORT: u16 = 8080;
const STARTUP_TIMEOUT: Duration = Duration::from_secs(90);

/// Probe `GET /api/health` over a raw socket (avoids pulling in an HTTP client crate).
fn health_ok() -> bool {
    let addr: SocketAddr = match format!("{HEALTH_HOST}:{HEALTH_PORT}").parse() {
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
    if health_ok() {
        log::info!("Backend already healthy on {HEALTH_HOST}:{HEALTH_PORT}, reusing.");
        return Ok(());
    }

    let resource_dir = app.path().resource_dir().map_err(|e| e.to_string())?;
    let backend_dir = resource_dir.join("backend");
    let jar = backend_dir.join("app.jar");
    let java = backend_dir
        .join("runtime")
        .join("bin")
        .join(if cfg!(windows) { "java.exe" } else { "java" });

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

    log::info!("Spawning backend: {java:?} -jar {jar:?} (cwd {work_dir:?})");
    let child = Command::new(&java)
        .arg("-jar")
        .arg(&jar)
        .current_dir(&work_dir)
        .spawn()
        .map_err(|e| format!("Failed to spawn backend: {e}"))?;

    app.state::<BackendProcess>()
        .0
        .lock()
        .unwrap()
        .replace(child);

    let deadline = Instant::now() + STARTUP_TIMEOUT;
    while Instant::now() < deadline {
        if health_ok() {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    Err(format!(
        "Backend did not become healthy within {}s",
        STARTUP_TIMEOUT.as_secs()
    ))
}

/// Start the backend on a background thread, then reveal the main window.
/// Keeps the Tauri setup hook (main thread) non-blocking.
pub fn start_async(app: AppHandle) {
    std::thread::spawn(move || {
        match ensure_started(&app) {
            Ok(()) => log::info!("Backend ready."),
            Err(e) => {
                log::error!("Backend startup failed: {e}");
                use tauri_plugin_dialog::{DialogExt, MessageDialogKind};
                app.dialog()
                    .message(format!(
                        "后端服务启动失败：{e}\n部分功能将不可用。"
                    ))
                    .kind(MessageDialogKind::Error)
                    .title("DataTalk")
                    .blocking_show();
            }
        }
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.show();
            let _ = window.set_focus();
        }
    });
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
            let _ = Command::new("taskkill")
                .args(["/PID", &pid.to_string(), "/T", "/F"])
                .output();
        }
        std::thread::sleep(Duration::from_millis(1500));
        let _ = child.kill();
    }
}
