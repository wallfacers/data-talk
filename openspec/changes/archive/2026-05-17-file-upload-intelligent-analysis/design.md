## Context

DataTalk 当前消息管道仅支持 `TextPart`（用户文本）和 `FilePart`（协议定义但未启用）。PromptComposer 是纯文本输入。后端有 FileArtifact 系统（AI 生成文件的管理），但没有用户上传文件的入口。

OpenCode 协议的消息管道：用户消息 → `ChannelService.partForWire()` → 转发给 OpenCode AI。系统 prompt 通过 AGENTS.md bootstrap 注入，不支持 mid-conversation 注入。

## Goals / Non-Goals

**Goals:**

- Composer 支持拖拽/粘贴上传文件（CSV/Excel/JSON/SQL/TXT/MD/LOG，单文件 ≤ 50MB）
- 后端接收文件后本地预分析，生成元数据摘要（模型不读原文）
- 小文件（< 4KB）直接全文注入对话；大文件只注入摘要
- AI 基于摘要做智能路由决策
- AI 可通过 action 按需读取文件指定片段
- 文件 24h 自动清理

**Non-Goals:**

- 不实现实际导入执行（`datatalk_import_data` 由 Task 13 实现）
- 不支持图片/音视频/可执行文件上传
- 不支持文件夹上传
- 不支持断点续传

## Decisions

### D1: 文件预分析策略 — 小文件直通、大文件摘要

**决策**: 以 4KB 为阈值。小于 4KB 的文件直接将全文作为 `file_upload` part 的 `content` 字段；大于 4KB 的文件由后端本地预分析，只返回结构化摘要。

**理由**: 4KB 约等于 500-1000 token，对模型上下文窗口影响可忽略。小于这个阈值，预分析的成本（代码复杂度+延迟）超过收益。

**降级**: 如果文件类型无法识别（MIME 检测失败），按大文件处理，摘要中标注 `type: unknown`，让 AI 询问用户意图。

### D2: 摘要注入方式 — 隐式 part 注入

**决策**: 上传完成后，前端在发送用户消息时附带 `file_upload` part。`ChannelService.partForWire()` 将 `file_upload` part 序列化为 OpenCode 可理解的格式（type=file + 元数据）。

**替代方案**:
- ~~AGENTS.md `{{FILE_UPLOAD_DIGEST}}` 占位符~~ — bootstrap 层，OpenCode 启动后不更新，无法反映运行时上传
- ~~AI 主动调 `datatalk_file_analyze` action~~ — 多一轮 tool call，增加延迟

**理由**: 复用现有 part 管道，per-conversation 隔离，零额外往返。

### D3: 后端预分析实现 — 分文件类型策略

**决策**: 不引入 Apache Tika，使用轻量方案：

| 文件类型 | MIME 检测 | 预分析方式 |
|---------|----------|-----------|
| SQL | 扩展名 + 内容关键词 | 正则分割语句，统计类型/数量/目标表 |
| CSV | 扩展名 + 魔数检测 | 读 header 行 + 前 5 行采样 |
| Excel | Apache POI（已有依赖） | 读 sheet 列表 + 每个 sheet 的 header + 行数 |
| JSON | 扩展名 + `[{` 开头 | 解析顶层结构，提取 key 集合/数组长度 |
| TXT/MD/LOG | 扩展名 + fallback | 行数 + 前 20 行 + 大小 |

**理由**: 避免引入 Tika（30MB+ 依赖），项目中已有 Apache POI（用于导出 Excel），SQL/CSV/JSON 可用 JDK 内置或轻量解析。

### D4: 按需读取 Action — `datatalk_file_read`

**决策**: 新增 `datatalk_file_read` MCP action。

```json
{
  "fileId": "uuid",
  "offset": 0,
  "limit": 4096
}
```

单次返回 ≤ 4KB。后端从磁盘读取指定偏移的文件内容。

**约束**: risk level = L1（只读操作），无确认流。

### D5: 文件存储 — `~/.data-talk/uploads/`

**决策**: 文件暂存到 `~/.data-talk/uploads/<fileId>/<original-name>`。元数据存 SQLite（新表 `uploaded_file`）。24h TTL 由 `HousekeepingScheduler` 清理。

**理由**: 复用已有的 `HousekeepingScheduler` 和 `~/.data-talk/` 目录约定。文件 ID 作为子目录避免文件名冲突。

### D6: Domain Part 扩展 — 新增 `FileUploadPart`

**决策**: 在 domain 层新增 `FileUploadPart` record，不修改现有 `FilePart`（那是 OpenCode 协议的 file 引用）。

```
FileUploadPart(String fileId, String filename, String mimeType,
               long sizeBytes, Map<String, Object> analysis)
```

`ChannelService.partForWire()` 将 `FileUploadPart` 转换为 OpenCode `file` part（type=file + metadata 字段承载分析结果）。

**sealed interface 影响**: `Part` sealed interface 新增 `FileUploadPart` → 需要更新 `ChannelService.partForWire()` 和 `DtEvent` 序列化链中所有 exhaustive switch。

### D7: 前端组件策略

**决策**: PromptComposer 内新增 `FileDropZone` + `FileAttachmentChip` 组件。使用 shadcn/ui 基础样式。

**Design.md token 映射**:
- 拖拽区: `bg.subtle` 背景 + `border.strong` 虚线 + hover `accent.primary` 边框
- 附件 Chip: `bg.soft` 背景 + `text.base` 文件名 + `text.muted` 大小
- 进度条: `accent.primary` + `bg.subtle` track
- 五态: idle → 拖入 hover(highlight) → 上传中 active(进度) → 完成 focus → 删除 disabled

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 大文件 multipart 接收占用内存 | Spring Boot multipart 配置 `spring.servlet.multipart.max-file-size=50MB`，使用临时文件而非内存缓冲 |
| SQL 正则解析无法覆盖所有方言 | 降级策略：解析失败时标注 `parseError: true`，AI 走 "询问用户" 路径 |
| Excel POI 读取大文件耗时 | 仅读取 sheet 元数据（header + 行数估算），不加载全部数据 |
| 50MB 上传超时 | 配置合理的 upload timeout，前端显示进度条 |
| `Part` sealed interface 新增成员 | exhaustive switch 编译检查会在编译期捕获所有遗漏 |

## Open Questions

- SQL 语句分割是否需要处理 `DELIMITER` 语法（MySQL 存储过程）？建议一期不支持，降级为 `parseError`。
