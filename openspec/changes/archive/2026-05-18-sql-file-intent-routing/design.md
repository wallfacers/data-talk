## Context

当前 `file-upload-routing/SKILL.md` 将 SQL 文件排除在意图分类之外，直接按 riskLevel 走查询编辑器路径。`DataImportService` 只支持 CSV/Excel/JSON 三种文件类型。用户上传 INSERT INTO 的 SQL 文件说"导入"时，AI 只能走查询编辑器执行，缺少自动建表和批量写入能力，且不检查目标表是否存在。

## Goals / Non-Goals

**Goals:**

- SQL 文件参与意图分类，"导入" 意图路由到 `datatalk_import_data`（仅当 `statementTypes` 全部为 INSERT 且 `targetTables.size = 1`）
- 后端新增 SQL 文件解析（INSERT INTO 语句），走已有 batch insert 流程
- 非"导入"意图的 SQL 文件行为不变（查询编辑器执行路径保留）

**Non-Goals:**

- 不支持 CREATE TABLE + INSERT 混合 SQL 文件走 import 路径（混合文件仍走查询编辑器，由 guarded DDL/DML flow 处理）
- 不支持 UPDATE / DELETE / REPLACE 等 DML 语句的解析导入（仅 INSERT INTO）
- 不支持多目标表的 SQL 文件走 import 路径（targetTables.size > 1 时走查询编辑器）
- 不修改前端代码（纯后端 + skill 文件变更）
- 不修改 `FileUploadController` 的文件分析逻辑（`analysis.type = "SQL"` 和 `analysis.summary` 已有足够的元数据）

## Decisions

### D1: SQL 文件解析策略 — 语句级状态机 + 正则提取

**选择：语句级状态机分句 + 正则解析单条语句**

SQL 是语句导向而非行导向的格式。用户输入的三类场景会打穿逐行解析：
- 情况 A：mysqldump 风格单行巨型 INSERT（`INSERT INTO t VALUES (...),(...),...,(...);`）
- 情况 B：跨行 INSERT（`INSERT INTO t \n (id, name) \n VALUES \n (1, 'O''Brien', 99.5);`）
- 情况 C：值内包含分号或换行（`INSERT INTO logs (msg) VALUES ('error: line 1;\nline 2');`）

**实现方案：双层架构**
1. **底层**：`BufferedReader` 逐行读取（复用现有 IO 模式）
2. **上层**：最小状态机 `SqlStatementSplitter`，跟踪 `'` / `"` / `` ` `` 引号配对、`--` / `/* */` 注释、`;` 语句终止符，拼出完整语句后才喂给正则解析器
3. **内存模型**：一次只在内存中持有"当前一条语句 + 当前 batch"，内存恒定
4. **单条语句解析**：正则匹配 INSERT INTO 的表名、列名、VALUES

正则改进：支持 schema 前缀（`mydb.orders`、`"public"."orders"`），表名匹配改为 `(?<table>[\w."'\`]+)` 后用 `.` split 取最后一段。

**Alternative: JSqlParser 等 SQL Parser 库**
- 优势：语法正确性保证、支持子查询值
- 劣势：引入新依赖、方言兼容性风险、大文件解析内存开销不可控
- 结论：作为 Day-2 优化方向，不在本次引入

**Rationale**: 状态机分句 + 正则解析的组合覆盖绝大多数 INSERT 文件（mysqldump、pg_dump、手写 SQL），~150 行可解决。遇到解析失败的语句，降级为 warning 跳过，不中断整个导入。

### D2: source.type 复用 "file" vs 新增 "sql_file"

**选择：复用 `source.type = "file"`，后端按文件扩展名自动分发**

- AI 在路由阶段通过 `analysis.type` 和 `statementTypes` 判断是否调用 `datatalk_import_data`，但调用时不需要把格式编码到 `source.type` 里
- 后端 `DataImportService.importFromFile()` 内部根据 `detectFileType()` 按文件扩展名分发到对应 reader
- 与 CSV/Excel/JSON 完全一致的模式，AI 无需学习新参数

**Alternative**: 新增 `source.type = "sql_file"`，让 AI 显式区分。
**Rationale**: 增加一个 type 值需要更新 `ImportDataActionHandler.inputSchema()` 的 enum，且 AI 在路由时需要额外判断。复用 `file` 更简洁。

### D3: SQL 文件意图分类的判断边界

**选择：SQL 文件进入意图分类表，条件收紧为"全部 INSERT + 单目标表"**

| 条件 | Action |
|---|---|
| 用户意图 = "导入" AND `statementTypes` 全部为 INSERT AND `targetTables.size = 1` | `datatalk_import_data` |
| 其他所有情况（混合 DDL+INSERT、多目标表、非 INSERT、非导入意图） | 保留原 SQL 文件路由规则（查询编辑器 / guarded flow） |

**Rationale**: `analysis.summary.statementTypes` 和 `targetTables` 已经提供了充分的元数据。多目标表的 INSERT 文件（如 `INSERT INTO orders ...` + `INSERT INTO customers ...`）无法映射到单个 `target.tableName`，走查询编辑器更安全。

### D4: SqlStreamReader 实现

**选择：语句级状态机分句 + 正则解析 + batch 累积器**

```
SqlStatementSplitter (新建, ~100 行)
  → BufferedReader 逐行读
  → 状态机跟踪引号/注释/分号
  → 输出完整语句字符串

SqlStreamReader.stream(filePath, batchSize, consumer, columnMappings, columnTypes)
  → SqlStatementSplitter 分句
  → 正则匹配 INSERT INTO ... (cols) VALUES (vals)
  → 解析 cols 和 vals 为 Map<String, Object>
  → 累积到 batchSize → consumer.accept(batch)
  → 返回 StreamReadResult { columns, sampleRows, totalRows }
```

### D5: target.tableName 与 INSERT 内表名冲突策略

**选择：强一致检查，不一致直接报错（策略 C）**

`datatalk_import_data` 的 `target.tableName` 与 SQL 文件内 INSERT 目标表名不一致时，返回 `{ errorCode: "TABLE_NAME_MISMATCH", message: "SQL file targets <sql_table> but tableName=<param_table>" }`。

**Alternative A**: AI 传的覆盖 SQL 内的 → 风险：导入到错误表，用户没察觉。
**Alternative B**: SQL 内为准，target.tableName 作后备 → AI 行为前后一致性差。
**Rationale**: 强一致检查最安全。AI 在路由时已经能从 `analysis.summary.targetTables` 获取表名，传入 `target.tableName` 时应与之一致。

### D6: SQL 类型推断独立实现

**选择：SqlStreamReader 自行实现 `inferDdlTypesFromTokens()`**

SQL 解析器在 token 化阶段已知每个值的语法类型，推断应直接用语法信息：

| 值形态 | 语法标记 | DDL 类型 |
|---|---|---|
| `123` | 无引号数字 | INT |
| `'123'` / `"123"` | 有引号 | VARCHAR |
| `99.5` | 无引号小数 | DOUBLE |
| `NULL` | NULL 关键字 | 跳过（不参与推断） |
| `TRUE` / `FALSE` | 布尔关键字 | BOOLEAN |

CSV 的 `inferDdlTypes()` 基于字符串值推断（如 `"123"` 会被推断为 INT），语义不同。SQL 有明确的语法边界区分字符串和数字，不应复用 CSV 推断逻辑。

## Risks / Trade-offs

- **[正则解析不覆盖全部 INSERT 语法]** → 有嵌套函数、子查询的 INSERT 可能解析失败。Mitigation：解析失败的语句作为 warning 跳过，报告跳过行数；如果全部失败，返回错误提示用户使用查询编辑器执行
- **[大文件内存]** → 状态机一次只持有一条完整语句 + 当前 batch，内存恒定。但单条巨型 INSERT（mysqldump 风格，百万行 VALUES）会将整条语句加载到内存。Mitigation：设单条语句大小上限（如 10MB），超出时 warning 跳过
- **[SQL 方言差异]** → 不同数据库导出的 INSERT 格式（引号风格、转义方式）有差异。Mitigation：状态机支持 `'` / `"` / `` ` `` 三种引号 + `''` / `\"` 转义；遇到无法解析的值视为字符串
- **[非导入意图走 import 路径]** → Skill 明确限定"全部为 INSERT + 单目标表" 才走 import；混合语句、多目标表、DDL 走查询编辑器
- **[skill 路由冲突]** → AGENTS.md 的 File Upload Decision Tree 也需同步更新，避免两个文件的路由规则不一致

## Open Questions

- 是否需要支持 `INSERT INTO ... SET col=val, col=val` 语法（MySQL 特有）？建议 Day-2
- 单条巨型 INSERT 的内存上限设多少？暂定 10MB，后续可按实际使用调整
