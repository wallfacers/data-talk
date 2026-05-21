## Why

AI 导出数据后，用户无法直接下载文件：`datatalk.export_data` 的 action 结果通过 `GenericTool` 渲染为原始 JSON 文本（`downloadUrl` 埋在 JSON 中不可点击）；异步导出的 toast 通知 30 秒消失且使用 `window.open` 在 Tauri webview 中行为不可控。Tauri 桌面端也没有原生 "Save As" 对话框，所有下载直接进入系统默认目录。

## What Changes

- 为 `datatalk.export_data` 注册专用 ToolPart 渲染器，展示文件信息（格式、行数、大小）+ 可点击下载按钮，替代 GenericTool 的 JSON 输出
- 创建统一下载工具函数 `downloadFromUrl()`：Tauri 环境弹出原生保存对话框（`tauri-plugin-dialog` + `tauri-plugin-fs`），浏览器环境走 fetch → blob → anchor click
- 安装 Tauri `dialog` 和 `fs` 插件（Cargo + npm + capabilities）
- 更新 `export.completed` toast 通知中的下载行为，从 `window.open()` 替换为统一下载函数

## Capabilities

### New Capabilities

- `export-download-card`: 导出工具卡片渲染器 — 将 `datatalk.export_data` 的 action 结果渲染为可交互的下载卡片，包含文件信息和下载按钮
- `tauri-native-save`: Tauri 原生文件保存 — 检测 Tauri 环境，提供原生 "Save As" 对话框 + 文件写入能力

### Modified Capabilities

- `data-export`: 新增要求 — 导出完成后前端 SHALL 展示可交互的下载卡片而非原始 JSON；toast 通知的下载行为 SHALL 使用统一下载函数

## Impact

- **Frontend**: 新增 1 个渲染器组件、1 个下载工具函数；修改 ToolRegistry 注册、toast 下载行为
- **Tauri**: 新增 `tauri-plugin-dialog` 和 `tauri-plugin-fs` 依赖（Cargo.toml + package.json + capabilities）
- **Backend**: 无变更
- **API**: 无变更
