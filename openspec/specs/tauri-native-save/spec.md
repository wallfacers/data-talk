## ADDED Requirements

### Requirement: Tauri 环境检测

系统 SHALL 检测当前运行环境是否为 Tauri 桌面应用。

#### Scenario: Tauri 环境检测

- **WHEN** 调用 `isTauriEnvironment()`
- **THEN** 通过 `'__TAURI_INTERNALS__' in window` 判断
- **AND** Tauri webview 中返回 `true`，标准浏览器中返回 `false`

### Requirement: 统一下载函数

系统 SHALL 提供统一的文件下载函数 `downloadFromUrl(url, filename)`。

#### Scenario: Tauri 环境下载 — 弹出保存对话框

- **GIVEN** 应用运行在 Tauri webview 中
- **WHEN** 调用 `downloadFromUrl("http://localhost:8080/api/exports/{id}/download", "export.csv")`
- **THEN** 弹出原生 "Save As" 对话框，默认文件名为传入的 `filename`
- **AND** 用户选择保存路径后，fetch URL 获取 ArrayBuffer，使用 `writeFile()` 写入用户选择的路径
- **AND** 用户取消对话框时不执行任何操作

#### Scenario: 浏览器环境下载 — Blob + Anchor

- **GIVEN** 应用运行在标准浏览器中
- **WHEN** 调用 `downloadFromUrl(url, filename)`
- **THEN** fetch URL 获取 Blob
- **AND** 创建临时 `<a>` 元素设置 `download={filename}` 并 `.click()` 触发浏览器下载

### Requirement: Tauri 插件安装

系统 SHALL 安装并配置 `tauri-plugin-dialog` 和 `tauri-plugin-fs`。

#### Scenario: Cargo 依赖

- **WHEN** 构建 Tauri 应用
- **THEN** `Cargo.toml` 包含 `tauri-plugin-dialog = "2"` 和 `tauri-plugin-fs = "2"`

#### Scenario: npm 依赖

- **WHEN** 安装前端依赖
- **THEN** `package.json` 包含 `@tauri-apps/plugin-dialog` 和 `@tauri-apps/plugin-fs`

#### Scenario: 权限配置

- **WHEN** Tauri 应用启动
- **THEN** `capabilities/default.json` 包含 `dialog:allow-save`、`fs:allow-write-file` 权限

#### Scenario: 插件注册

- **WHEN** Tauri Builder 初始化
- **THEN** 注册 `tauri_plugin_dialog::init()` 和 `tauri_plugin_fs::init()`
