## Why

用户已能上传 CSV/Excel/JSON 文件并获得 AI 分析摘要，但无法将文件数据实际导入数据库。前端导出仅支持 CSV/JSON 且限制在客户端 5000 行以内。缺少：文件→数据库导入、跨库表复制、服务端大文件流式导出（CSV/JSON/Excel/SQL INSERT）。这些是数据协作平台的核心数据流转能力。

## What Changes

- **新增 MCP Action `datatalk_import_data`**：接受文件引用（fileId）或跨库查询（connectionId + SQL）作为数据源，服务端流式解析并批量写入目标数据库表。AI 只传引用参数，实际数据不经过 AI 上下文。
- **新增 MCP Action `datatalk_export_data`**：接受查询（connectionId + SQL）或表名，服务端流式导出为 CSV/JSON/Excel/SQL INSERT。<10K 行同步流式返回，≥10K 行写临时文件后提供下载链接。
- **修改 `file-upload-routing` skill**：CSV/Excel 路由从硬编码"建议导入"改为意图感知——根据用户文字判断是分析还是导入，意图模糊时主动询问。
- **修改 `FileAnalysisService`**：CSV/JSON 分析路径从 `readAllBytes` 改为流式读取（只读 header + 样本行），避免大文件分析时堆内存爆炸。
- **修改 `ScriptDataWriteService`**：新增流式写入方法，接受 JDBC ResultSet cursor 输入，边读边写，内存恒定。

## Capabilities

### New Capabilities
- `data-import`: 文件导入（CSV/Excel/JSON → 数据库表）与跨库复制（A 库查询结果 → B 库表）。多文件场景由 AI 逐文件多次调用 action 自行循环编排，action 本身只接受单个 fileId。覆盖数据解析、类型推断、表创建、批量写入全流程。AI 角色为决策者（定表名、列映射、确认），不碰实际数据。
- `data-export`: 服务端流式数据导出，支持 CSV/JSON/Excel(.xlsx)/SQL INSERT 四种格式。三层策略：<10K 同步流式，10K-100K 写临时文件，>100K 异步 Job。

### Modified Capabilities
- `file-read-image-pipeline`: 修改意图——`file-upload-routing` skill 的 CSV/Excel 路由规则从硬编码建议导入改为意图感知路由。
- `script-data-write`: 修改要求——新增流式写入接口，支持 JDBC cursor 输入而非仅 `List<Map>` 批量输入。
- `agent-skill-routing`: 修改要求——新增 `datatalk_import_data` 和 `datatalk_export_data` 两个 MCP tool 的注册与路由。

## Impact

### Backend
- **新 Action Handler**: `ImportDataActionHandler`, `ExportDataActionHandler`（adapter 层，@DataTalkAction 注解）
- **新 Service**: `DataImportService`（application 层）, `DataExportService`（application 层）
- **修改 Service**: `ScriptDataWriteService` 新增流式方法, `FileAnalysisService` 改为流式解析
- **新 Controller**: `DataExportController` — 提供临时文件下载端点
- **依赖**: Apache POI SXSSF（已有 POI 依赖，流式写 Excel 无需新依赖）；CSV 流式解析可用 OpenCSV 或手写 line-by-line reader
- **Action Registry**: 新增 2 个 MCP tool（`datatalk_import_data`, `datatalk_export_data`）

### Frontend
- **导出 UI**: SQL 结果表 / Stage 组件新增"导出"按钮组（CSV/JSON/Excel/SQL INSERT），大文件场景展示进度或下载链接
- **导入 UI**: Day-1 不新增前端组件。AI 通过 chat message 文本汇报 `rowsImported` + `sampleRows`，复用现有 markdown 表格渲染 + warnings 展示，无需新组件

### Skills
- **修改**: `file-upload-routing/SKILL.md` — CSV/Excel 意图感知路由规则
- **修改**: `AGENTS.md` — 新增 import/export 工具使用指引

### Specs
- 涉及 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：import/export 操作需要确认所有 19 种数据库类型的兼容性（ScriptDataWriteService 已全支持，但流式 cursor 读取需确认各驱动 fetchSize 行为）
- 不涉及新增数据源类型，不触发完整 Gate 流程

### Open Bugs Overlap
- BUG-0057 (composer attachment stuck uploading, verified) — 导入功能依赖上传流程稳定，需关注此 BUG 修复进度
- BUG-0059 (composer enter lag with file, verified) — 同上
- BUG-0062 (spring multipart 1MB limit, fixed) — 已修复归档。`align-spring-multipart-with-file-upload-limit` change 已归档，50MB 限制已闭合，不阻塞本 change
