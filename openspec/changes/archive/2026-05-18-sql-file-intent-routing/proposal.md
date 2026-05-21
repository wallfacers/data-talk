## Why

用户上传包含 INSERT 语句的 SQL 文件并说"导入数据"时，`file-upload-routing` skill 跳过意图分类直接走 SQL 文件专属路由（按 riskLevel 执行），而不是走 `datatalk_import_data` 的导入路径。这导致 AI 选择了查询编辑器执行 SQL，且缺少目标表存在性检查，第一次执行失败。根因有两个：(1) SKILL.md 明确排除了 SQL 文件的意图分类；(2) `DataImportService.detectFileType()` 不支持 `.sql` 文件，即使路由正确也会报 UNSUPPORTED_FILE_TYPE。

## What Changes

- **file-upload-routing SKILL.md 意图分类扩展**：SQL 文件参与 Intent Classification（条件：`statementTypes` 全部为 INSERT 且 `targetTables.size = 1`），"导入" 意图路由到 `datatalk_import_data`，其他意图/混合语句/多目标表走查询编辑器
- **DataImportService 新增 SQL 文件解析**：`detectFileType()` 新增 `.sql` 支持，语句级状态机分句 + 正则解析 INSERT INTO 语句，提取表名、列名、值，走已有 batch insert 流程
- **目标表名强一致检查**：INSERT 内表名与 AI 传入 `target.tableName` 不一致时报 `TABLE_NAME_MISMATCH` 错误；多目标表报 `MULTI_TABLE_NOT_SUPPORTED` 错误
- **ImportDataActionHandler schema 无需变更**：复用 `source.type=file`，后端按文件扩展名自动分发
- **AGENTS.md File Upload 章节同步更新**：SQL 文件路由规则反映意图分类优先

## Capabilities

### New Capabilities

（无全新能力，扩展已有能力）

### Modified Capabilities

- `data-import`: 新增 SQL 文件（INSERT INTO）作为可导入文件类型，`detectFileType` 支持 `.sql`，语句级状态机分句 + 正则解析 INSERT 语句提取 values 走 batch insert；新增目标表名强一致检查和多目标表拒绝
- `agent-skill-routing`: `file-upload-routing` skill 的 Intent Classification 从排除 SQL 文件改为包含 SQL 文件（条件：全部 INSERT + 单目标表），SQL 文件的路由决策由意图驱动而非文件类型独占；AGENTS.md File Upload 决策树同步更新

## Impact

- **后端**：`DataImportService`（新增 `SqlStatementSplitter` + `SqlStreamReader`）、`ImportDataActionHandler`（inputSchema 无需变更，复用 `source.type=file`）、`CsvStreamReader`/`JsonStreamReader`/`ExcelSaxStreamReader` 同层新增两个类
- **Skill 文件**：`file-upload-routing/SKILL.md` 第 21 行和第 29 行字面修改 + SQL Files 路由规则插入导入意图前置判断；`AGENTS.md` File Upload 决策树同步
- **兼容性**：非导入意图的 SQL 文件行为不变（查询编辑器执行路径保留）；纯 SELECT / 混合 DDL+INSERT / 多目标表均不受影响
- **SQL 方言**：INSERT 语句解析需考虑不同方言差异，参考 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
