mod backend;

use std::collections::HashMap;
use std::fs;
use std::io::{BufRead, BufReader};
use std::process::{Command, Stdio};
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};

struct RunningPids(Mutex<HashMap<String, u32>>);

#[derive(Serialize, Deserialize, Clone)]
struct ScriptOutput {
    run_id: String,
    channel: String,
    data: String,
}

#[derive(Serialize, Deserialize, Clone)]
struct ScriptCompleted {
    run_id: String,
    exit_code: i32,
}

#[derive(Serialize, Deserialize)]
struct EnvInfo {
    python: Option<String>,
    node: Option<String>,
}

#[tauri::command]
fn detect_script_env() -> Result<EnvInfo, String> {
    let python = detect_runtime("python3")
        .or_else(|_| detect_runtime("python"))
        .ok();
    let node = detect_runtime("node").ok();

    Ok(EnvInfo { python, node })
}

fn detect_runtime(name: &str) -> Result<String, String> {
    let output = Command::new(name)
        .arg("--version")
        .output()
        .map_err(|e| format!("{} not found: {}", name, e))?;
    Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
}

#[tauri::command]
fn run_script(
    app: AppHandle,
    run_id: String,
    script_content: String,
    language: String,
    env_vars: Option<HashMap<String, String>>,
) -> Result<(), String> {
    let (cmd_name, ext) = match language.as_str() {
        "python" => ("python3", "py"),
        "javascript" => ("node", "js"),
        _ => return Err(format!("Unsupported language: {}", language)),
    };

    let temp_dir = std::env::temp_dir().join("datatalk-scripts");
    fs::create_dir_all(&temp_dir).map_err(|e| format!("Failed to create temp dir: {}", e))?;
    let script_path = temp_dir.join(format!("{}.{}", run_id, ext));
    fs::write(&script_path, &script_content)
        .map_err(|e| format!("Failed to write script: {}", e))?;

    let mut cmd = Command::new(cmd_name);
    cmd.arg(&script_path)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    if let Some(vars) = &env_vars {
        for (k, v) in vars {
            cmd.env(k, v);
        }
    }

    let mut child = cmd
        .spawn()
        .map_err(|e| format!("Failed to spawn {}: {}", cmd_name, e))?;

    let pid = child.id();
    {
        let pids = app.state::<RunningPids>();
        pids.0.lock().unwrap().insert(run_id.clone(), pid);
    }

    let stdout = child.stdout.take().unwrap();
    let stderr = child.stderr.take().unwrap();

    let app_out = app.clone();
    let rid_out = run_id.clone();
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines().map_while(Result::ok) {
            let _ = app_out.emit(
                "script-output",
                ScriptOutput {
                    run_id: rid_out.clone(),
                    channel: "stdout".into(),
                    data: line,
                },
            );
        }
    });

    let app_err = app.clone();
    let rid_err = run_id.clone();
    std::thread::spawn(move || {
        let reader = BufReader::new(stderr);
        for line in reader.lines().map_while(Result::ok) {
            let _ = app_err.emit(
                "script-output",
                ScriptOutput {
                    run_id: rid_err.clone(),
                    channel: "stderr".into(),
                    data: line,
                },
            );
        }
    });

    let app_done = app.clone();
    let rid_done = run_id.clone();
    std::thread::spawn(move || {
        let status = child.wait().expect("process wait failed");
        {
            let pids = app_done.state::<RunningPids>();
            pids.0.lock().unwrap().remove(&rid_done);
        }
        let _ = app_done.emit(
            "script-completed",
            ScriptCompleted {
                run_id: rid_done,
                exit_code: status.code().unwrap_or(-1),
            },
        );
        let _ = fs::remove_file(script_path);
    });

    Ok(())
}

#[tauri::command]
fn stop_script(app: AppHandle, run_id: String) -> Result<(), String> {
    let pid = {
        let pids = app.state::<RunningPids>();
        let removed = pids.0.lock().unwrap().remove(&run_id);
        removed.ok_or("Run not found")?
    };

    // SIGTERM
    let _ = Command::new("kill")
        .arg(pid.to_string())
        .output();

    // Wait then SIGKILL if still alive
    std::thread::sleep(std::time::Duration::from_secs(5));
    let _ = Command::new("kill")
        .arg("-9")
        .arg(pid.to_string())
        .output();

    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    simple_logger::SimpleLogger::new().init().unwrap();
    log::info!("DataTalk starting...");
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_shell::init())
        .manage(RunningPids(Mutex::new(HashMap::new())))
        .manage(backend::BackendProcess(Mutex::new(None)))
        .manage(backend::BackendPort(Mutex::new(0)))
        .manage(backend::BackendStatusState(Mutex::new(
            backend::BackendStatus::Starting,
        )))
        .invoke_handler(tauri::generate_handler![
            greet,
            detect_script_env,
            run_script,
            stop_script,
            backend::get_backend_url,
            backend::get_backend_status,
            backend::restart_backend
        ])
        .setup(|app| {
            // Dev runs the backend separately; just reveal the window.
            // Release manages the bundled sidecar and reveals once it is healthy.
            #[cfg(debug_assertions)]
            {
                // Dev backend runs separately and is assumed up; mark Ready so
                // the frontend welcome screen doesn't block on it.
                *app.state::<backend::BackendStatusState>().0.lock().unwrap() =
                    backend::BackendStatus::Ready;
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.show();
                }
            }
            #[cfg(not(debug_assertions))]
            {
                backend::start_async(app.handle().clone());
            }
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while running tauri application")
        .run(|app_handle, event| {
            if let tauri::RunEvent::ExitRequested { .. } = event {
                backend::stop(app_handle);
            }
        });
}

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}
