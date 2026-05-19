## 1. BUG 登记（合规闸前置）

- [x] 1.1 在 `docs/bugs/` 新建 `BUG-0066-identifier-quoting-not-dialect-aware.md`（status=fixed，fixCommit 占位 pending；按本 change 实施完成填回）
- [x] 1.2 在 `docs/bugs/` 新建 `BUG-0067-script-run-direct-db-connect-misuse.md`（status=fixed，同上）
- [x] 1.3 在 `docs/bugs/index.md` 把下一个分配 ID 改为 `BUG-0068`，并在 In Progress 表加 BUG-0066 / BUG-0067 两行

## 2. 引入 IdentifierQuoter（application 层新组件）

- [x] 2.1 新建 `server/data-talk-application/src/main/java/com/datatalk/application/dialect/QuoteStyle.java`（enum：BACKTICK / DOUBLE_QUOTE / BRACKET）
- [x] 2.2 新建 `server/data-talk-application/src/main/java/com/datatalk/application/dialect/IdentifierQuoter.java`：
  - 提供 `static String quote(String identifier, String connectionKind)`
  - 内部 `static QuoteStyle resolve(String connectionKind)` 按 19 种 first-class kind 派发（含大小写不敏感）
  - 未识别 kind fallback `DOUBLE_QUOTE` + slf4j `WARN` 日志（带原始 kind 字符串）
  - 按 style 实施转义：BACKTICK → ` ` ` → ` `` `；DOUBLE_QUOTE → `"` → `""`；BRACKET → `]` → `]]`（仅右括号）
- [x] 2.3 新建 `server/data-talk-application/src/test/java/com/datatalk/application/dialect/IdentifierQuoterTest.java`：覆盖
  - MySQL / MariaDB / TiDB / OceanBase / Doris / StarRocks / ClickHouse → 反引号
  - PostgreSQL / H2 / SQLite / Oracle / DuckDB / Kingbase / Dameng / GaussDB / Hive / Trino / Presto → 双引号
  - SQLServer → 方括号
  - 大小写不敏感（MYSQL / MySql / mysql）
  - 嵌入引号字符转义（` `` `、`""`、`]]`）
  - null / 空串 / 未知 kind → fallback 双引号（断言无异常）

## 3. 替换 4 处 quoteIdentifier 调用站点（可并行执行 3.1–3.3，3.4 串行因签名 BREAKING）

- [x] 3.1 改 `DataImportService.buildCreateTableSql` / `buildInsertSql`：
  - 构造方法注入 `IdentifierQuoter` 或直接调用 static 方法
  - 增加 `String connectionKind` 内部传递路径：`writeRowsToTable` 取得 kind（通过 `ConnectionRecord` lookup，或在外层 `importFromFile` / `importFromQuery` 解析后透传）
  - 删除私有 `quoteIdentifier(String)` 静态方法
- [x] 3.2 改 `ScriptDataWriteService.buildCreateTableSql` / `buildCreateTableFromColumns` / `buildInsertSql`：
  - 同样改为接收 kind 参数
  - `writeStream(...)` / `write(...)` / `batch(...)` 公开方法签名加 `kind`，由 controller / caller 取自 `ConnectionRecord` 透传
  - 删除私有 `quoteIdentifier(String)`
- [x] 3.3 改 `DataExportService.exportSqlInsert` 路径：
  - `quoteIdentifier(String)` 改为按 `connectionKind` 派发（kind 已可通过 source `ConnectionRecord` 获取）
  - 注意 export `effectiveTable` / `colNames` 两处都要走新引用
- [x] 3.4 改 `InverseSqlGenerator` 全部 4 个 public 方法（buildInverseForInsert / Update / Delete / Mixed）：
  - 签名末尾追加 `String connectionKind`
  - 内部 `static quoteIdentifier(String)` 替换为 `IdentifierQuoter.quote(id, kind)`
  - 更新所有 caller（grep `InverseSqlGenerator\.` 找：`UndoLogService` / `UndoLogRepository` / 测试装配）—— 从 `ConnectionRecord.kind()` 取出 kind 传入

## 4. 更新 4 处对应测试

- [x] 4.1 `DataImportServiceTest` 增加用例：MySQL 反引号 + 含保留字列名（select, order, group）+ 列名含反引号字符的转义
- [x] 4.2 `ScriptDataWriteServiceTest`：cross-DB MySQL→MySQL roundtrip 验证 + PG→PG + SQLServer→SQLServer
- [x] 4.3 `DataExportServiceTest`：MySQL 导出 .sql → 内容包含反引号断言 + 通过 `DataImportService` 回灌同库成功
- [x] 4.4 `InverseSqlGeneratorTest`：含保留字 / 空格 / 引号字符列名 + 三种方言（MySQL / PG / SQLServer）+ kind=null fallback 不崩

## 5. ui_exec 错误自愈（client-side 改动）

- [x] 5.1 在 `client/src/features/` 内定位 `query_editor.run_sql` / `apply_text_edits` / `set_context` / `format_sql` / `focus` 的 handler（搜 `object === 'query_editor'`）—— 统一在 `UIRouter.handle` 中心化路由
- [x] 5.2 为每个 handler 在"无活跃 query_editor tab"分支返回结构化 `{ error: { code: "no_active_query_editor", message, nextAction: { object: "workspace", action: "open", params: { type: "query_editor", title: "Untitled SQL" } } } }`
- [x] 5.3 对 `er_inspector` / `er_designer` / `dashboard` 三类 object 也走同模式：`no_active_er_inspector` / `no_active_er_designer` / `no_active_dashboard`
- [x] 5.4 `UiActionsTest`（adapter 层契约测试）增加断言：当 client 返回 `error.code` 形如 `no_active_*` 时，response payload 校验通过、不抛 schema 异常
- [x] 5.5 frontend 单测（vitest）：mock dispatch 无活跃 tab 场景，断言返回的错误对象结构符合 spec

## 6. data-collection skill 加 DO NOT 段

- [x] 6.1 修改 `server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md`：在 `# Data Collection Skill` 标题之后、`## Tool Surface` 之前插入 `## ❗ DO NOT` 段（按 design.md D7 文字）
- [x] 6.2 新建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/skills/DataCollectionSkillContractTest.java`：
  - 加载 classpath 资源 `skills/data-collection/SKILL.md`
  - 断言包含 "DO NOT" / "pymysql" / "psycopg2" / "/api/script-data/write"
- [x] 6.3 确认 `server/.opencode/skills` 与 `server/data-talk-adapter/.opencode/skills` 是否需要同步 —— 实测两处 `.opencode/skills` 仅装外部 bezel；data-collection 单源于 `src/main/resources/skills/data-collection/SKILL.md`，无需同步

## 7. 文档同步

- [x] 7.1 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：
  - 新增 `### Identifier Quoting Per Dialect` 章节（19 种 kind × 三种 style 的完整矩阵 + fallback 规则）
  - 修正 `### Streaming Export` 表第 984 行原"按方言引用"声明 —— 改写为引用新章节而非内嵌
  - 在 `Dynamic SQL Execution Repository` 决策段加 "Identifier quoting follows IdentifierQuoter"
- [x] 7.2 把 BUG-0066 / BUG-0067 文档的 `fixCommit` 字段从 `pending` 替换为本 change 实际 commit hash，并在 `index.md` 同步行（commit 后由 8.7 回填）—— 已回填 `2ab9039f`（含 BUG-0069）
- [x] 7.3 在 proposal.md 的 `D` 段 + design.md `Q1` 记录用户最终决定（DEFERRED，本 change 不动 update_connection schema；A 修复后 AI 已无需绕）

## 8. 验收 / 端到端

- [x] 8.1 `cd server && mvn install -pl data-talk-application -am -DskipTests`（因为改了 application 层，run 模式才看得到）
- [x] 8.2 `cd server && mvn clean verify`：全测试套件绿（4 模块 BUILD SUCCESS；附带修复 2 处 pre-existing 失败：sql-execution SKILL.md description 超长 + 缺 READ-ONLY，ExecuteSqlActionIT.deleteReturnsBlockedInChat 与 c10289fc 新契约不一致）
- [x] 8.3 `cd client && npx tsc --noEmit`：前端零类型错误
- [x] 8.4 `cd client && npx vitest run`：1353/1353 用例通过（含新增的 7 个 UIRouter 结构化错误用例 + stage-ui-object-registry 同步更新）
- [ ] 8.5 浏览器手测：本次原始失败场景（`mcp__playwright__*` 或 dev server）
  - 上传 SQL 文件 → 在 MySQL 连接上 `datatalk_import_data`
  - 期望：一次成功，反引号 DDL 执行通过，rowsImported > 0
  - 如失败：按 BUG Tracking Gate 登记新 BUG，回到 task 列表
- [ ] 8.6 浏览器手测：`ui_exec(query_editor, run_sql)` 在无活跃 tab 时返回 `no_active_query_editor` + nextAction
- [x] 8.7 把 commit hash 回填 7.2 步骤的 BUG 文档 —— `2ab9039f` 已写入 BUG-0066 / BUG-0067 / BUG-0069 + index.md
