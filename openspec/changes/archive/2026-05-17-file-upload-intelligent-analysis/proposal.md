## Why

DataTalk 当前只能通过文字与 AI 交互。用户要导入外部数据或分析文件内容，必须手动打开终端工具、复制粘贴内容——这在处理大文件时体验极差（50MB SQL 文件不可能粘贴进对话框）。三期核心主线是"数据怎么进来"，文件上传是所有入口功能（导入、采集、报告）的统一起点。

## What Changes

- **Composer 文件入口**：PromptComposer 支持拖拽 / Ctrl+V 粘贴文件，显示附件 Chip（文件名 + 大小 + 类型 icon + 删除按钮），上传进度条。
- **后端文件接收与预分析**：新增 `POST /api/files/upload` multipart 端点。文件暂存到 `~/.data-talk/uploads/`，后端本地预分析生成元数据摘要（MIME 检测、语句类型分布、列名推断、行数估算等），小文件（< 4KB）直接全文返回，大文件只返回摘要。
- **摘要注入对话**：上传完成后，文件摘要作为 `file_upload` part 自动注入当前用户消息，AI 基于摘要（非原文）做决策。
- **AI 智能路由**：AGENTS.md 新增 `## File Upload & Analysis` 节，定义 AI 基于文件类型和预分析结果的决策树（导入建表 / SQL 执行 / 文本分析 / 询问意图）。
- **按需读取 Action**：新增 `datatalk_file_read` MCP action，AI 可按需读取文件指定片段（单次 ≤ 4KB），不需要时模型不接触原始内容。
- **安全约束**：拒绝可执行文件、二进制文件（白名单：CSV/Excel/JSON/SQL/TXT/MD/LOG）、0 字节文件。文件 24h TTL 自动清理。

## Capabilities

### New Capabilities

- `file-upload`: 用户通过 Composer 拖拽/粘贴上传文件，后端接收、暂存、预分析并返回元数据摘要
- `file-upload-routing`: AI 基于文件预分析摘要进行智能路由决策（导入/执行/分析/询问）

### Modified Capabilities

- `user-message-markdown`: 消息 part 类型新增 `file_upload`，前端需渲染文件上传摘要卡片

## Impact

- **Frontend**: PromptComposer 新增拖拽区 + 粘贴处理 + 附件 Chip + 上传进度。Chat 消息渲染新增 `file_upload` part 卡片。
- **Backend**: 新增 `FileUploadController`（multipart 端点）、`FileAnalysisService`（预分析）、`FileUploadPart`（domain part）。新增 Flyway migration 存储文件元数据。
- **AGENTS.md**: 新增 `## File Upload & Analysis` 规则节 + Trigger Gate 行。
- **New MCP Action**: `datatalk_file_read`（按需读取文件片段）。
- **Dependencies**: Apache Tika（MIME 检测）或自研魔数检测。CSV/Excel 解析可复用已有库。
- **Task 13 依赖**: `datatalk_import_data` action 由 Task 13 实现，本 change 在 AGENTS.md 路由规则中用 TODO 标注引用点。

## Design Inputs

- [client/DESIGN.md](../../../client/DESIGN.md): 文件上传控件使用 `bg.subtle` + `border.strong` 虚线拖拽区 + `accent.primary` 进度条；附件 Chip 五态映射（idle/hover/active/focus/disabled）；键盘可达，焦点环 `interaction.focusRing`。
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../../../docs/DATA_SOURCE_TYPE_COMPATIBILITY.md): SQL 文件预分析需覆盖所有 17 种 first-class 数据源方言的语句类型识别差异（如 MySQL 的 `INSERT ... ON DUPLICATE KEY UPDATE` vs PostgreSQL 的 `ON CONFLICT`）。

## Risks

- **大文件内存占用**: 50MB 文件的 multipart 接收和预分析需要流式处理，避免全量加载进内存。
- **SQL 解析准确度**: 正则匹配无法处理所有方言特有语法（存储过程体内分号、`DELIMITER` 等），需要合理的降级策略。
- **Open BUGs 无重叠**: 已检查 `docs/bugs/index.md`，当前 open BUG（BUG-0010/0049/0050/0053）均与文件上传无关。
