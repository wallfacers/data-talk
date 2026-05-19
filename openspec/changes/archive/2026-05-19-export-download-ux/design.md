## Context

当前 `datatalk.export_data` action 结果通过 `GenericTool` 渲染，只展示原始 JSON。`ToolRegistry` 中已注册 4 个专用渲染器（`ExecuteSql`, `ShowSchema`, `ArtifactCreated`, `DatatalkArchiveArtifact`），均遵循 `BasicTool` + `trigger.action` 模式。Tauri 端未安装 `dialog`/`fs` 插件，所有下载走浏览器 blob+anchor 或 `window.open`。

## Goals / Non-Goals

**Goals:**
- 导出结果展示为可交互的下载卡片，含文件信息 + 可点击下载按钮
- Tauri 桌面端弹出原生 "Save As" 对话框
- 浏览器端保持现有下载行为不受影响
- 异步导出 toast 的下载按钮同样走统一下载路径

**Non-Goals:**
- 不修改后端导出逻辑、API 或 TTL 策略
- 不修改 SQL Result Table 和 Markdown Table 的下载流程（它们已有独立实现）
- 不实现导出历史管理 UI

## Decisions

### D1: 专用渲染器取代 GenericTool

注册 `ToolRegistry.register('datatalk.export_data', ExportData)` 渲染器，参考 `artifact-created.tsx` 的 `BasicTool` + `trigger.action` 模式。

卡片结构：
```
┌──────────────────────────────────────────────┐
│ ● 导出数据  100 行, CSV, 14.6 KB    [⬇] │
└──────────────────────────────────────────────┘
```
- `trigger.title`: "导出数据"
- `trigger.subtitle`: "{rowCount} 行, {format}, {fileSize}"
- `trigger.action`: DownloadIcon 按钮，点击调用 `downloadFromUrl()`
- `status: "processing"` 时 subtitle 显示 "处理中..."，下载按钮 disabled

### D2: Tauri 环境检测与分支下载

创建 `client/src/services/tauri/file-download.ts`，导出 `downloadFromUrl(url, filename)`:

```
downloadFromUrl(url, filename)
  ├── 检测 '__TAURI_INTERNALS__' in window → Tauri 路径
  │   ├── save({ defaultPath: filename }) → 用户选择路径
  │   ├── fetch(url) → ArrayBuffer
  │   └── writeFile(path, data) → 写入文件
  └── 浏览器路径
      ├── fetch(url) → Blob
      └── <a download={filename}>.click()
```

使用 `@tauri-apps/plugin-dialog` 的 `save()` 和 `@tauri-apps/plugin-fs` 的 `writeFile()`。

### D3: 文件大小格式化

创建 `formatFileSize(bytes)` 工具函数，输出人类可读格式（14.6 KB, 2.3 MB），复用于渲染器和 toast。

### D4: Toast 下载行为统一

`use-channel.ts` 中 `export.completed` 事件的 toast action 从 `window.open()` 改为 `downloadFromUrl()`。需要从 `downloadUrl` 推断默认文件名（基于 exportId + format）。

## Risks / Trade-offs

- **Tauri 插件增加包体积**: `tauri-plugin-dialog` + `tauri-plugin-fs` 会略微增加 Tauri 应用体积，但这两个是官方维护的标准插件
- **Tauri 权限范围**: 需要在 capabilities 中声明 `dialog:allow-save` 和 `fs:allow-write-file`，范围有限且安全
- **浏览器环境无保存对话框**: 浏览器端仍走默认下载目录，这是 Web 标准限制，无法绕过
- **downloadUrl 推断文件名**: toast 中没有返回 filename，需要从 format 推断扩展名 + 用 exportId 作默认名。可考虑后续在 `ExportCompleted` 事件中增加 `filename` 字段
