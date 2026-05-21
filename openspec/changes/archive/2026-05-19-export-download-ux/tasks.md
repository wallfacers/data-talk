## Tasks

### Batch 1: Tauri 插件安装 + 统一下载函数

- [x] **T1**: 安装 Tauri 插件依赖
  - Cargo.toml 添加 `tauri-plugin-dialog = "2"`, `tauri-plugin-fs = "2"`
  - npm 安装 `@tauri-apps/plugin-dialog`, `@tauri-apps/plugin-fs`
  - `src/lib.rs` 注册两个 `.plugin()`
  - `capabilities/default.json` 添加 `dialog:allow-save`, `fs:allow-write-file`

- [x] **T2**: 创建统一下载工具函数 `client/src/services/tauri/file-download.ts`
  - `isTauriEnvironment()`: `'__TAURI_INTERNALS__' in window`
  - `downloadFromUrl(url, filename)`: Tauri 路径 (save dialog → fetch → writeFile) / 浏览器路径 (fetch → blob → anchor click)
  - `formatFileSize(bytes)`: 人类可读文件大小

### Batch 2: 前端渲染器 + Toast 改造

- [x] **T3**: 创建 `client/src/features/chat/components/tools/renderers/export-data.tsx`
  - 解析 `part.state.output` 提取 downloadUrl, rowCount, fileSize, format, status, exportId
  - BasicTool 卡片: title="导出数据", subtitle="{rowCount} 行, {format}, {fileSize}", trigger.action=DownloadIcon 按钮
  - status=processing 时 subtitle="处理中...", 按钮 disabled
  - status=error 时显示错误信息
  - 下载按钮调用 `downloadFromUrl()`

- [x] **T4**: 注册渲染器 `client/src/features/chat/components/tools/renderers/index.ts`
  - `ToolRegistry.register('datatalk.export_data', ExportData)`

- [x] **T5**: 更新 toast 下载行为 `client/src/services/channel/use-channel.ts`
  - `export.completed` 事件: toast action.onClick 从 `window.open()` 改为 `downloadFromUrl()`
  - 从 format 推断 filename: `export-{exportId}.{ext}`

### Batch 3: i18n + 验证

- [x] **T6**: 添加 i18n 键 `client/src/i18n/messages.ts`
  - `export.toolTitle`: "导出数据" / "Export Data"
  - `export.statusProcessing`: "处理中..." / "Processing..."
  - `export.downloadFile`: "下载文件" / "Download File"

- [x] **T7**: 验证
  - `cd client && npx tsc --noEmit` 零类型错误
  - 启动后端 + 前端手动测试导出流程
