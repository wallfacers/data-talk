## Context

DataTalk 已有文件上传（`FileUploadController` + `FileAnalysisService`）和数据写入（`ScriptDataWriteService`）能力，但两者未连通。前端导出仅限客户端 CSV/JSON ≤5000 行。AI 通过 `file-upload-routing` skill 路由上传文件，但 CSV/Excel 路由硬编码为"建议导入"，不区分用户意图。

现有基础设施：
- `ScriptDataWriteService.write(connectionId, tableName, rows, createTable)` — 接受 `List<Map>` 全量写入，不支持流式输入
- `FileAnalysisService` — CSV 分析用 `Files.readAllBytes` 全量加载，50MB 文件会占 ~150MB 堆
- `datatalk.file_read` MCP tool — 4KB 分块读取，数据经过 AI 上下文
- `ScriptDataBatchService` — session-based 分批写入，但输入仍是内存中的 `List<Map>`
- `DataTalkMcpService` — 128KB 输出 budget，action 结果进入 AI 上下文

约束：
- 4 层架构：domain → application → infrastructure → adapter，外层依赖内层
- 新增 Action = `@DataTalkAction` 注解的 `ActionHandler` bean
- 19 种数据库类型，需确认流式 cursor 行为
- `client/DESIGN.md` 设计约束适用于前端改动

## Goals / Non-Goals

**Goals:**
- 用户上传 CSV/Excel/JSON 文件后，AI 能通过对话引导数据导入到目标数据库表
- 支持跨库表复制（A 库查询 → B 库表），数据不经过 AI 上下文
- 服务端流式导出 CSV/JSON/Excel(.xlsx)/SQL INSERT，支持大文件
- `file-upload-routing` skill 意图感知路由，区分分析与导入意图
- 所有数据流转路径中，实际数据不经过 AI 上下文（防上下文爆炸）

**Non-Goals:**
- 不实现完整的 HTTP ingestion pipeline（Task 10 范围）
- 不修改 `data-ingestion` skill（待改版）
- 不增加新的数据库类型支持
- 不实现前端原生导入向导 UI（Day-1 通过 AI 对话驱动）
- 不实现导出文件定时清理（Day-1 简单 TTL，不做 cron）

## Decisions

### D1: `datatalk_import_data` 接受引用参数，不接受数据

**决定**：`datatalk_import_data` action 只接受 `fileId` / `connectionId+sql` 作为数据源引用，不接受 `rows` 数组。

**理由**：如果接受 `rows`，AI 会尝试通过 `datatalk_file_read` 读取全部数据再传入，导致上下文爆炸。引用参数确保 AI 只做决策（表名、列映射），不碰数据。

**替代方案**：同时支持 `rows` 参数（让 AI 传小数据集）。**拒绝**：双路径增加复杂度，且小数据集场景 AI 直接生成 SQL INSERT 更简单。

### D2: 导入服务端流式解析 + 批量写入

**决定**：`DataImportService` 从文件或 JDBC cursor 流式读取数据，每 1000 行一批，通过 `PreparedStatement.executeBatch()` 写入目标库。内存恒定。

**实现路径**：
- CSV: 逐行 `BufferedReader.readLine()` → 解析为 `Object[]` → accumulate batch → executeBatch
- Excel: Apache POI SAX 事件模型 (`XSSFReader` + `SheetContentsHandler`) → 不加载整个 workbook
- JSON: Jackson `JsonParser` 流式解析 `array_of_objects` → 逐条映射
- 跨库: 源库 JDBC `ResultSet` (fetchSize=500, TYPE_FORWARD_ONLY, CONCUR_READ_ONLY) → 目标库 batch INSERT

**替代方案**：复用 `FileAnalysisService` 的全量解析结果。**拒绝**：50MB CSV 会占 ~150MB 堆，且 `FileAnalysisService` 当前设计为元数据采样，不是全量解析器。

### D3: `ScriptDataWriteService` 新增流式方法

**决定**：在 `ScriptDataWriteService` 中新增 `writeStream(connectionId, tableName, ResultSet, createTable)` 方法，接受 JDBC `ResultSet` cursor 作为输入。

**理由**：跨库复制需要从源库 cursor 读取 → 写入目标库。如果先用 `List<Map>` 中间缓冲，50MB 数据仍会 OOM。流式方法让数据直接从 cursor 流向 batch insert。

**影响**：不修改现有 `write()` 方法签名，仅新增方法。

### D3.1: 跨库复制事务模型

**决定**：源库和目标库各自独立 autoCommit 管理，不做分布式事务。

- 源库连接：`autoCommit=false`（启用 cursor streaming），只读，方法结束时 commit + close
- 目标库连接：`autoCommit=false`，每 1000 行 batch 后 `commit()`，失败时已 commit 的行保留（不回滚）
- 源库异常（连接断开、查询超时）：已写入目标库的行保留，返回已导入行数 + error 信息
- 目标库异常（连接断开、约束冲突）：中止导入，已 commit 的行保留，返回部分导入结果

**理由**：跨库场景无法使用 XA 事务，且部分导入对用户有用（可从中断点续传）。action 返回 `rowsImported` 让 AI 向用户报告实际导入了多少行，由用户决定是否重试。

### D4: 导出三层策略

**决定**：
- **< 10K 行**：同步流式返回（Spring `StreamingResponseBody` + JDBC cursor）。HTTP 响应直接流式输出。
- **10K - 100K 行**：后台 virtual thread 写临时文件 → SSE 通知前端 → 前端通过下载链接获取。
- **> 100K 行**：同上，但 Excel 格式超过 1,048,576 行时自动降级提示用户切换 CSV。

**临时文件管理**：导出文件存放 `~/.data-talk/exports/{exportId}/{filename}`，设置 1 小时 TTL，通过 `DataExportController` 的 `GET /api/exports/{exportId}/download` 提供下载。

**SSE 通知**：`DataExportService` 在后台 virtual thread 完成写入后，通过现有 `SessionBus` / SSE channel 发射 `export.completed` 事件（含 exportId、downloadUrl、rowCount）。前端已有 SSE 监听基础设施（OpenCode 协议），无需新建 WebSocket 或轮询通道。

**TTL 清理机制**：不使用 cron。采用请求时惰性校验——`DataExportController.download()` 在读取文件前检查文件 `lastModified`，超过 1 小时则删除文件并返回 404。应用启动时扫描 `~/.data-talk/exports/` 目录清理残留文件。磁盘只增不增的风险被单文件 500MB 上限 + 请求时清理缓解。

**替代方案**：全量异步 Job。**拒绝**：<10K 行的异步 Job 增加了不必要的复杂度（Job 创建、状态查询、通知），同步流式对用户更友好。

### D5: Excel 导出使用 Apache POI SXSSF

**决定**：使用 `SXSSFWorkbook`（POI 流式 API，窗口 100 行）。

**理由**：项目已有 POI 依赖（`FileAnalysisService` 读 Excel）。SXSSF 内存恒定（~5-10MB），对比 XSSF 的线性增长。`.xlsx` 硬上限 1,048,576 行，超过时导出服务返回错误并建议 CSV。

### D6: `file-upload-routing` 意图感知路由

**决定**：修改 skill 的 CSV/Excel 路由规则，从硬编码"建议导入"改为基于用户消息文字判断意图：
- 明确提及"导入/入库/建表/import" → 触发 `datatalk_import_data`
- 明确提及"分析/看看/统计/趋势/分布" → 用现有分析能力（`file_read` 读样本）
- 意图模糊 → 主动询问用户

**理由**：用户上传文件时必须附带文字（`submitText` 空文本直接 return），所以 AI 天然有意图上下文。

### D7: `FileAnalysisService` 流式化

**决定**：CSV/JSON 分析路径从 `Files.readAllBytes` / `Files.readString` 改为流式读取（只读 header + 5 行样本 + 估算行数）。

**理由**：当前 50MB CSV 分析会占 ~150MB 堆（byte[] + String + lines list）。流式读取只需 ~10KB 内存。分析结果结构不变（headers, estimatedRows, sampleRows, detectedTypes），仅实现方式改变。

### D8: Action schema 设计

**`datatalk_import_data`**：
```
输入:
  source: { type: "file", fileId: string }
       | { type: "query", connectionId: string, sql: string }
  target: { connectionId: string, tableName: string }
  createTable?: boolean (default: true)
  columnMappings?: Record<string, string>   // 源列名 → 目标列名
  columnTypes?: Record<string, string>      // 列名 → 类型覆盖（如 "DECIMAL(10,2)"）
输出:
  rowsImported: number
  tableName: string
  columns: { name: string, type: string, inferred: boolean }[]
  warnings: string[]      // 如 "列 X 含 null 值，默认 TEXT"
  sampleRows: Object[]    // 前 3 行，供 AI 向用户汇报
```

**`datatalk_export_data`**：
```
输入:
  source: { connectionId: string, sql: string }
       | { connectionId: string, tableName: string }
  format: "csv" | "json" | "xlsx" | "sql_insert"
  options?: { filename?: string, maxRows?: number }
输出:
  downloadUrl: string       // 前端可直接打开/下载
  rowCount: number
  fileSize: number
  format: string
  warnings: string[]        // 如 "超过 1M 行，Excel 不支持，请换 CSV"
```

## Risks / Trade-offs

**[大文件导入内存压力]** → 流式解析 + 固定 batch size (1000行)，每批写完释放。但 Excel SAX 模式需验证所有 19 种 JDBC 驱动的 `fetchSize` 行为（特别是 ClickHouse、Hive 等可能忽略 fetchSize）。**缓解**：Day-1 先验证 MySQL/PG/H2/SQLite 四种核心类型，其余 fallback 到 `fetchSize=0`（驱动默认行为）。

**[跨库复制长事务]** → 源库 cursor 读取可能持续数分钟，连接超时风险。**缓解**：设置合理的 query timeout，大结果集建议用户分批（`WHERE id > last_id LIMIT 10000`）。

**[导出临时文件磁盘占用]** → 多用户同时导出大文件可能占满磁盘。**缓解**：单文件大小上限 500MB（与 ingestion 一致），TTL 1 小时自动删除。

**[BUG-0057 上传卡住]** → 导入依赖上传流程正常工作。**缓解**：端到端冒烟测试（task 10.3）需等 BUG-0057 修复合入后再做，否则上传卡住会误判为导入失败。

**[Excel 行数限制]** → `.xlsx` 硬上限 1,048,576 行。**缓解**：导出服务在行数超限时返回错误 + 建议切换 CSV。

**[多文件导入]** → 用户一次拖入多个 CSV 想合并导入。**缓解**：`datatalk_import_data` source 只接受单个 fileId，多文件由 AI 逐文件多次调用 action 循环编排。spec 显式声明此语义，不在 action 层引入 `fileIds[]` 联合分支——避免单次导入混合多文件 schema 的复杂度。

## Open Questions

_全部已裁决，无剩余 open question。_

### Resolved: Q1 — sql_insert 格式选择 batch 多值 INSERT

**决定**：使用 batch INSERT（`INSERT INTO t (c1,c2) VALUES (v1,v2),(v3,v4),...;`），每 100 行一个语句。

**理由**：单行 INSERT 文件体积大 5-10 倍，且导入性能差（mysqldump 默认用 `--extended-insert` 就是 batch）。batch INSERT 是所有数据库通用语法，兼容性无问题。

### Resolved: Q2 — 同 connection 跨 schema 不特殊处理

**决定**：同 connection 跨 database/schema 复制走正常路径——`datatalk_import_data` 的 source 和 target 各自指定 connectionId。如果用户想从同一连接的不同 database 复制，需先切换 context 或在 SQL 中使用全限定表名（`schema.table`）。

**理由**：Day-1 不引入 "同连接跨 schema" 特殊路径，避免与现有 `SessionDataContextService` 的 `use xxx` 语义冲突。AI 可以通过 `datatalk_execute_sql` 先 `USE target_db` 再导入，或在 source SQL 中写全限定名。

### Resolved: Q3 — 导入不需要进度 SSE，一次性返回

**决定**：Day-1 导入完成后一次性返回结果（`rowsImported` + `sampleRows`），不做进度 SSE。输出中包含 `importId` 字段供 Day-2 扩展。

**理由**：导入场景通常在几秒到几十秒内完成（流式解析 + batch insert），不等同于导出可能持续数分钟。导入过程中 AI 正在等待 tool result，用户看到的自然就是"AI 在处理中"的状态。进度汇报增加 SSE 发射端复杂度，收益不明显。
