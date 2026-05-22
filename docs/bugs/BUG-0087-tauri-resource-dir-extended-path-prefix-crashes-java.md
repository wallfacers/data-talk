---
id: BUG-0087
title: Tauri resource_dir() 返回 \\?\ 扩展路径前缀导致 bundled Java 启动崩溃
status: fixed
priority: P0
source: manual-report
modules: [desktop]
discovered: 2026-05-22
discoveredBy: user
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

Tauri 的 `resource_dir()` 在 Windows 上返回带 `\\?\` Extended-Length Path 前缀的路径（如 `\\?\D:\software\data-talk`）。当此路径作为 `java.exe -jar` 参数传递时，Java 内部的 jimage classloader 无法识别 `\\?\` 前缀，导致 JVM 以 fatal error 崩溃（`classLoader.cpp:372 guarantee(name != nullptr) failed: jimage file name is null`）。对外表现为 `ClassNotFoundException: org.springframework.boot.loader.launch.JarLauncher`。

## Reproduction Steps

1. 在 Windows 上安装 DataTalk MSI/NSIS 安装包（安装目录非 `C:\Program Files` 等短路径时必现）。
2. 启动 DataTalk。
3. 后端 Java 进程立即退出（exit code 1），前端显示"后端服务启动失败"对话框。

## Root Cause

`app.path().resource_dir()` 返回 `\\?\D:\software\data-talk`，`PathBuf::join` 会将前缀传播到所有子路径（`java.exe`、`app.jar`）。`Command::new` 将这些路径传给 `CreateProcessW`（Windows API 能正确解析 `\\?\`），Java 进程启动后尝试从 `modules` image 加载核心类时，`jimage` 文件路径仍带 `\\?\` 前缀，导致内部断言失败。

## Fix

在 `backend.rs` 中添加 `strip_extended_path_prefix()` 函数，将 `\\?\D:\...` 还原为 `D:\...`（同时处理 UNC 路径 `\\?\UNC\server\...` → `\\server\...`）。同时清除 `CLASSPATH`、`JAVA_TOOL_OPTIONS`、`_JAVA_OPTIONS`、`JAVA_HOME` 四个可能干扰 Java 启动的环境变量。

## Environment

- OS: Windows 11 Pro 10.0.26200
- Tauri: v2.x
- Java: OpenJDK 21.0.11 (Temurin, bundled via jlink)
- Install dir: `D:\software\data-talk`
