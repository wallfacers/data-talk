## 1. SqlStatementSplitter + SqlStreamReader 实现

- [x] 1.1 创建 `SqlStatementSplitter.java`（application 模块 `importexport` 包），实现语句级状态机分句：底层 BufferedReader 逐行读，上层跟踪 `'` / `"` / `` ` `` 引号配对、`--` / `/* */` 注释、`;` 语句终止符，输出完整语句字符串。单条语句大小上限 10MB，超出 warning 跳过。约 100 行
- [x] 1.2 创建 `SqlStreamReader.java`，调用 `SqlStatementSplitter` 分句后，对每条完整语句用正则匹配 `INSERT INTO ... (cols) VALUES (vals)`，支持 schema 前缀（`mydb.orders`、`"public"."orders"`）。解析 cols 和 vals 为 `Map<String, Object>`，累积到 batchSize 后回调 consumer
- [x] 1.3 支持多行 VALUES 语法：`INSERT INTO t (a,b) VALUES (1,2),(3,4),(5,6)` 每对括号作为独立行
- [x] 1.4 实现值类型解析：字符串去引号（`'` / `"` / `` ` `` + `''` / `\"` 转义）、数字解析、NULL 关键字映射为 Java null、布尔值识别
- [x] 1.5 实现 `inferDdlTypesFromTokens()`：SQL 解析器在 token 化阶段已知值的语法类型，直接映射——无引号数字→INT/DOUBLE、有引号→VARCHAR、NULL→跳过、TRUE/FALSE→BOOLEAN。不复用 CsvStreamReader 的字符串推断逻辑
- [x] 1.6 编写 `SqlStreamReaderTest`：纯 INSERT 文件、mysqldump 单行巨型 INSERT、跨行 INSERT、值内含分号/换行、多行 VALUES、部分解析失败（warning 计数）、空文件、列名不一致、单条语句超 10MB

## 2. DataImportService 扩展

- [x] 2.1 修改 `detectFileType()`：新增 `filename.endsWith(".sql")` → return `"sql"`
- [x] 2.2 在 `importFromFile()` switch 中新增 `case "sql"` 分支，调用 `SqlStreamReader.stream()`，处理返回结果并批量写入
- [x] 2.3 实现目标表名强一致检查：从第一条 INSERT 语句提取表名，与 AI 传入的 `target.tableName` 比较（忽略大小写、去引号），不一致时抛出 `TABLE_NAME_MISMATCH` 错误。检测到多目标表时抛出 `MULTI_TABLE_NOT_SUPPORTED` 错误
- [x] 2.4 SQL 分支中，如果第一条 INSERT 解析成功，后续 INSERT 列名不匹配时跳过该行并添加 warning；全部解析失败时抛出 `SQL_PARSE_FAILED` 错误
- [x] 2.5 编写 `DataImportServiceTest` SQL 文件导入场景：纯 INSERT 导入、部分失败、全部失败、.sql 文件但无 INSERT 语句、目标表名冲突、多目标表
- [x] 2.6 `mvn install -pl data-talk-application -am -DskipTests` 刷新 jar

## 3. Skill 路由更新

- [x] 3.1 修改 `file-upload-routing/SKILL.md`：(a) 第 21 行 `For CSV, Excel, and JSON` 改为 `For CSV, Excel, JSON, and SQL (when statementTypes all = INSERT AND targetTables.size = 1)`；(b) 第 29 行删除 `SQL,` 关键字，改为 `For Text, Image, and Unknown files — no intent classification needed`
- [x] 3.2 修改 `file-upload-routing/SKILL.md` SQL Files 路由规则（第 33-39 行）：在 riskLevel 分级之前插入导入意图前置判断——`If user intent = Import AND statementTypes 全部为 INSERT AND targetTables.size = 1 → use datatalk_import_data(source: { type: 'file', fileId }). Otherwise fall through to riskLevel routing.`
- [x] 3.3 修改 `AGENTS.md` File Upload & Analysis 章节 SQL file 决策树：与 SKILL.md 同步，在 L1/L2/L3 之前新增导入意图分支

## 4. 集成验证

- [x] 4.1 `mvn install -pl data-talk-adapter -am -DskipTests` 刷新 adapter 依赖
- [x] 4.2 全量编译 `cd server && mvn compile -q` 确认零错误
- [x] 4.3 运行 `SqlStreamReaderTest` 和 `DataImportServiceTest` 确认测试通过（39 tests, 0 failures）
- [x] 4.4 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：在 SQL file import 相关章节标注支持状态
- [ ] 4.5 E2E 验证：上传含 3 条 INSERT 的 .sql 文件 + 用户说"导入"，验证 AI 一次性走通 `datatalk_import_data` → `rowsImported=3`；对照场景：上传混合 CREATE+INSERT 的 .sql 文件 + "导入"，验证仍走查询编辑器
- [ ] 4.6 回归验证：上传纯 SELECT 的 .sql 文件 + 用户说"看看"，验证仍走 query_editor + L1 路径，不触发 import_data
