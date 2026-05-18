## 1. ScriptDataWriteService 流式写入扩展

- [x] 1.1 在 `ScriptDataWriteService` 新增 `writeStream(connectionId, tableName, ResultSet rs, boolean createTable, Map<String,String> columnTypes)` 方法：从 `ResultSetMetaData` 推断列名/类型，逐批 1000 行 `PreparedStatement.executeBatch()` 写入目标库，返回 `WriteResult`
- [x] 1.2 新增 `ColumnTypeMapper` 工具类：`ResultSetMetaData` JDBC 类型 → 建表 DDL 类型映射（VARCHAR/CHAR/CLOB→VARCHAR(255), INTEGER/BIGINT→BIGINT, FLOAT/DOUBLE→DOUBLE, BOOLEAN/BIT→BOOLEAN, TIMESTAMP/DATE/TIME→TIMESTAMP, 其余→TEXT），可通过 `columnTypes` 参数覆盖
- [x] 1.3 编写 `ScriptDataWriteServiceTest`：测试 `writeStream` 的 cursor 流式写入、列类型推断、`columnTypes` 覆盖、空 ResultSet 处理
- [x] 1.4 验证：`cd server && mvn install -pl data-talk-application -am -DskipTests && mvn test -pl data-talk-application -Dtest=ScriptDataWriteServiceTest`

## 2. FileAnalysisService 流式化改造

- [x] 2.1 将 CSV 分析路径从 `Files.readAllBytes` 改为 `BufferedReader` 逐行读取：只读 header（第 1 行）+ 前 5 行样本 + 估算行数（文件大小 / 首行字节数）。分析结果结构不变。**保留 fullContent 行为**：文件 < 4KB 时仍设置 `fullContent=true` 并返回完整内容（此时 BufferedReader 读完整个文件开销可忽略）
- [x] 2.2 将 JSON 分析路径从 `Files.readString` 改为 `BufferedReader` + 首行/前 2 个元素采样。**保留 fullContent 行为**同上
- [x] 2.3 Excel 分析路径暂不改造（当前 POI workbook 模型只采 sheet 名 + header，开销不大）。Excel 流式解析将在 task 3.3 的导入 SAX 实现中统一处理
- [x] 2.3 编写 `FileAnalysisServiceTest`：验证大文件（模拟 50MB CSV）分析时堆增量 < 50MB、分析结果结构不变、边界情况（空文件、单行文件、BOM 文件）
- [x] 2.4 验证：`cd server && mvn install -pl data-talk-application -am -DskipTests && mvn test -pl data-talk-application -Dtest=FileAnalysisServiceTest`

## 3. DataImportService 核心实现

- [x] 3.1 新建 `DataImportService`（application 层）：实现文件导入入口 `importFromFile(fileId, connectionId, tableName, createTable, columnMappings, columnTypes)` — 从 `UploadedFileRepository` 解析物理路径，按文件类型分发解析器
- [x] 3.2 实现 CSV 流式解析器 `CsvStreamReader`：`BufferedReader.readLine()` → 逗号分隔解析 → `Object[]` batch accumulate → 回调 `ScriptDataWriteService.writeStream`
- [x] 3.3 实现 Excel SAX 流式解析器 `ExcelSaxStreamReader`：POI `XSSFReader` + `SheetContentsHandler` → 逐行回调写入
- [x] 3.4 实现 JSON 流式解析器 `JsonStreamReader`：Jackson `JsonParser` → `array_of_objects` 逐条映射 → batch 写入
- [x] 3.5 实现跨库复制入口 `importFromQuery(sourceConnectionId, sql, targetConnectionId, tableName, createTable)` — 打开源库 cursor (TYPE_FORWARD_ONLY, fetchSize=500, autoCommit=false) → 调用 `writeStream` 写入目标库。**事务模型**：源库方法结束 commit+close；目标库每 1000 行 batch 后 commit；源库异常时已写入行保留，返回部分导入结果（rowsImported + error）
- [x] 3.6 错误处理：FILE_NOT_FOUND、UNSUPPORTED_FILE_TYPE、SOURCE_CONNECTION_FAILED、SOURCE_QUERY_FAILED 错误码
- [x] 3.7 编写 `DataImportServiceTest`：CSV 文件导入、JSON 文件导入、跨库复制（H2 内存库双连接）、fileId 无效、文件类型不支持
- [x] 3.8 验证：`cd server && mvn install -pl data-talk-application -am -DskipTests && mvn test -pl data-talk-application -Dtest=DataImportServiceTest`

## 4. ImportDataActionHandler MCP Action

- [x] 4.1 新建 `ImportDataActionHandler`（adapter 层）：`@DataTalkAction("datatalk.import_data")` 注解，实现 `ActionHandler` 接口，解析 source (file/query) + target 参数，调用 `DataImportService`
- [x] 4.2 实现 `inputSchema()`：定义 source 联合类型（file: {fileId} | query: {connectionId, sql}）、target、createTable、columnMappings、columnTypes 参数 schema。**多文件语义**：source 只接受单个 fileId，不提供 fileIds[] 分支。多文件导入由 AI 逐文件多次调用 action 循环编排
- [x] 4.3 实现 `outputSchema()`：定义 rowsImported、tableName、columns、warnings、sampleRows、importId 字段
- [x] 4.4 确保 `sampleRows` 限制为前 3 行，序列化后总输出 < 128KB
- [x] 4.5 编写 `ImportDataActionHandlerTest`：WireMock + H2 内存库，验证完整 import 流程通过 MCP action 端到端
- [x] 4.6 验证：`cd server && mvn install -pl data-talk-adapter -am -DskipTests && mvn test -pl data-talk-adapter -Dtest=ImportDataActionHandlerTest`

## 5. DataExportService 核心实现

- [x] 5.1 新建 `DataExportService`（application 层）：实现导出入口 `export(source, format, options)` — 执行 SELECT 查询获取 ResultSet，按格式分发输出
- [x] 5.2 实现 CSV 流式输出器：`StreamingResponseBody` + JDBC cursor → 逐行写 CSV（含 BOM UTF-8）
- [x] 5.3 实现 JSON 流式输出器：`StreamingResponseBody` → JSON 数组 `[{}, ...]`
- [x] 5.4 实现 Excel SXSSF 流式输出器：`SXSSFWorkbook` (window=100) → JDBC cursor 逐行写 → `Workbook.write(outputStream)`
- [x] 5.5 实现 SQL INSERT 输出器：batch INSERT 格式，每 100 行一个 INSERT 语句，单引号转义
- [x] 5.6 实现行数分层策略：<10K 同步 StreamingResponseBody，≥10K 后台 virtual thread 写临时文件 `~/.data-talk/exports/{exportId}/`
- [x] 5.7 实现临时文件下载端点 `DataExportController`：`GET /api/exports/{exportId}/download`。TTL 清理采用请求时惰性校验：download 方法在读取前检查文件 `lastModified`，超过 1 小时则删除文件并返回 404。应用启动时通过 `@PostConstruct` 或 `ApplicationRunner` 扫描 `~/.data-talk/exports/` 目录清理残留过期文件
- [x] 5.8 实现 SSE `export.completed` 事件发射：`DataExportService` 在后台 virtual thread 完成写入后，通过 `SessionBus` 发射包含 exportId、downloadUrl、rowCount、format 的事件。复用现有 SSE channel，不新建 WebSocket 或轮询通道
- [x] 5.8 实现安全约束：只允许 SELECT 查询（复用 `CalciteSqlRiskAnalyzer` L1 判断），500MB 文件上限，1M 行默认上限
- [x] 5.9 Excel 行数超限处理：>1,048,576 行返回 XLSX_ROW_LIMIT_EXCEEDED 错误
- [x] 5.10 编写 `DataExportServiceTest`：CSV/JSON/Excel/SQL_INSERT 同步导出（<10K）、临时文件创建与下载、行数限制、XLSX 超限、非 SELECT 拒绝
- [x] 5.11 验证：`cd server && mvn install -pl data-talk-application,data-talk-adapter -am -DskipTests && mvn test -pl data-talk-application -Dtest=DataExportServiceTest && mvn test -pl data-talk-adapter -Dtest=DataExportControllerTest`

## 6. ExportDataActionHandler MCP Action

- [x] 6.1 新建 `ExportDataActionHandler`（adapter 层）：`@DataTalkAction("datatalk.export_data")`，解析 source (sql/tableName) + format + options 参数，调用 `DataExportService`
- [x] 6.2 实现 `inputSchema()`：source 联合类型、format 枚举、options (filename, maxRows) 参数 schema
- [x] 6.3 实现 `outputSchema()`：downloadUrl、rowCount、fileSize、format、exportId、status、warnings 字段
- [x] 6.4 编写 `ExportDataActionHandlerTest`：WireMock + H2 内存库，验证 export 通过 MCP action 端到端
- [x] 6.5 验证：`cd server && mvn install -pl data-talk-adapter -am -DskipTests && mvn test -pl data-talk-adapter -Dtest=ExportDataActionHandlerTest`

## 7. AGENTS.md + Skill 更新

- [x] 7.1 在 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 的 `## Registered Actions` 章节添加 `datatalk_import_data` 和 `datatalk_export_data` 条目及使用说明
- [x] 7.2 修改 `server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md`：CSV/Excel 路由从硬编码"建议导入"改为意图感知——明确提及"导入/入库"→ `datatalk_import_data`；明确提及"分析/统计"→ 现有分析；模糊→主动询问
- [x] 7.3 移除 file-upload-routing SKILL.md 中的 `TODO(Task 13)` 标记，替换为实际 `datatalk_import_data` 调用指引。多文件场景在 skill 中写明："用户拖入多个文件需逐文件调用 action"
- [x] 7.4 确保 AGENTS.md 精简后非空行 ≤ 350 行（骨架体积约束，当前 ~129 行，加 2 个 action 条目很安全）
- [x] 7.5 编写 skill 路由断言测试：在 `file-upload-routing` skill 的测试中，验证当用户消息含"导入"关键词时 AI 选择 `datatalk_import_data` 而非 `datatalk_file_read`（prompt-level 测试，可用 WireMock 验证 tool call 选择）
- [x] 7.6 依赖检查：确认 `agent-skill-routing` spec 中 "MUST NOT 包含 data-ingestion" 的前置 change 已归档。若未归档，本 change 的 delta spec 假设不成立，需在 tasks.md 标注阻塞
- [x] 7.5 验证：`AgentPromptContractTest` 通过（骨架体积、占位符、skill 引用闭合）

## 8. 前端导出 UI

Design Inputs (from `client/DESIGN.md`): 使用 semantic tokens、shadcn/ui 组件、i18n keys、Stage 全局状态不变。

**导入前端 UI 决策**：Day-1 不新增导入专用前端组件。AI 通过 chat message 文本汇报 `rowsImported` + `sampleRows`（前 3 行），复用现有 markdown 表格渲染 + warnings 展示。导入结果不需要特殊组件，因为 `datatalk_import_data` 的返回值经 AI 自然语言转述即可。

- [ ] 8.1 在 `sql-result-table.tsx` 导出按钮组新增 Excel (.xlsx) 和 SQL INSERT 选项，调用后端 `datatalk_export_data` action 或直接 `GET /api/exports/{id}/download`
- [ ] 8.2 在 `markdown-table.ts` 的 chat 表格"更多"菜单中新增 Excel 和 SQL INSERT 下载选项
- [ ] 8.3 大文件导出进度展示：当后端返回 `status: "processing"` 时，在 UI 中展示导出中状态，监听 SSE `export.completed` 事件后显示下载链接
- [ ] 8.4 添加 i18n 消息：`export.format.xlsx`、`export.format.sql_insert`、`export.processing`、`export.download`、`export.completed`
- [ ] 8.5 验证：`cd client && npx tsc --noEmit`

## 9. 数据源类型兼容性检查

- [ ] 9.1 验证 4 种核心数据库（MySQL/PG/H2/SQLite）的 `fetchSize` cursor 行为：MySQL `useCursorFetch=true`、PG auto-commit OFF、H2 默认、SQLite 默认
- [ ] 9.2 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：新增 "Data Import/Export Compatibility" 章节，记录各数据库的 cursor 流式读取、batch INSERT、SXSSF 兼容性状态

## 10. 集成测试与整体验证

- [x] 10.1 后端整体验证：`cd server && mvn clean verify`（全量测试套件）
- [ ] 10.2 前端整体验证：`cd client && npx tsc --noEmit && npm run test`
- [ ] 10.3 端到端冒烟测试（手动）：上传 CSV → AI 对话导入 → 验证数据库表数据；SQL 查询结果 → 导出 Excel → 下载验证。**前置条件**：BUG-0057 (composer attachment stuck uploading) 需已修复合入，否则上传卡住会误判为导入失败
