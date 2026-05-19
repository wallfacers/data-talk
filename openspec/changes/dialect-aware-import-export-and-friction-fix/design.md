## Context

### 触发事件
一次 E2E 实测："把 SQL 文件导入到 datatalk_ctx" 任务消耗 20+ 工具调用未完成。复盘暴露 4 个独立缺陷：
1. 主路径 `datatalk_import_data` 生成的 DDL/INSERT 用 ANSI 双引号 → MySQL 默认 sql_mode 下崩
2. 退路 `datatalk_ui_exec(query_editor, run_sql)` 返回模糊 "no active client"，AI 不知道要先 `workspace.open`
3. 二次退路 `datatalk_script_run` 被 AI 误用为"装 pymysql 直连 DB"（违反 `data-collection` SKILL.md 已写明的合同）
4. 三次退路 `datatalk_update_connection_confirmable` 不支持 `sql_mode` / session variables，是死胡同

### 现状代码
| 站点 | 实现 | 问题 |
|---|---|---|
| `DataImportService.quoteIdentifier` | `"\"" + id + "\""` | 硬编码 ANSI |
| `ScriptDataWriteService.quoteIdentifier` | `"\"" + id + "\""` | 硬编码 ANSI |
| `DataExportService.quoteIdentifier` | `"\"" + id + "\""` | 硬编码 ANSI（但 DATA_SOURCE_TYPE_COMPATIBILITY.md L984 谎称已按方言派发） |
| `InverseSqlGenerator.quoteIdentifier` | `return identifier` | **完全不引用**（保留字 / 空格 / 引号字符列名会爆） |

### 既有抽象
- `Dialect` enum (`domain.er`) 已有 7 种：MYSQL/POSTGRESQL/H2/SQLITE/MARIADB/ORACLE/SQLSERVER
- `ConnectionKind` (`application.connection`) 覆盖 19 种 first-class（含 TiDB/Doris/StarRocks/ClickHouse/OceanBase/Kingbase/Dameng/GaussDB 等）
- 两者不对齐：ER 关心 DDL 语义（窄），quoting 只关心引用字符（宽）

### 约束
- 必须按 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 写作协议：覆盖完整 19 种，未识别 kind 显式 fallback
- 本次不动 client/，无 `client/DESIGN.md` 约束
- 不引入 Flyway 迁移（无 schema 变化）
- 后端模块跨改：application 层新组件 + adapter 层 ui_exec 错误结构 + adapter 层 skill 文档

## Goals / Non-Goals

**Goals:**
- 4 处数据移动代码在 19 种 kind 下都生成方言正确的标识符引用
- `datatalk_ui_exec` 返回的错误能让 AI 自动找到下一步
- `data-collection` skill 阻止 AI 走直连 DB 的反模式（文档 + 合同测试）
- 一次回归测试矩阵覆盖：MySQL 反引号、PG 双引号、SQLServer 方括号，含保留字 / 空格 / 引号字符列名

**Non-Goals:**
- 不重写 `Dialect` enum（ER 模块继续用它）
- 不引入 `extraJdbcParams` 透传（OPEN QUESTION D 默认走"显式拒绝"分支）
- 不优化 `DataExportService` 输出格式之外的逻辑
- 不动 19 种 cursor / autoCommit 参数（DATA_SOURCE_TYPE_COMPATIBILITY 已记录，本次仅引用层修复）
- 不动 19 种 type inference 映射（VARCHAR(255) / BIGINT / DOUBLE 等）

## Decisions

### D1 · 新建独立 `IdentifierQuoter`，不复用 `Dialect`
**选择**：新建 `application/dialect/IdentifierQuoter` + `QuoteStyle` enum（BACKTICK / DOUBLE_QUOTE / BRACKET），按 `ConnectionKind` 字符串派发。

**为什么不用 `Dialect`**：
- `Dialect` 是 ER 模块的窄域抽象，只懂 7 种 + 是否生成 DDL
- Quoting 需要覆盖 19 种 kind（包括 TiDB / Kingbase 等 ER 不支持的）
- 把 quoting 塞进 `Dialect` 会污染 ER 的"unsupported"语义
- 单独抽象后两个抽象各自演化，无耦合

**替代方案**：扩展 `Dialect` 到 19 → 拒绝（混淆 ER scope）；把方法挂在 `ConnectionKind` 上 → 拒绝（domain 不该知道 SQL 语法）。

### D2 · `QuoteStyle` 三档（BACKTICK / DOUBLE_QUOTE / BRACKET），不为 SQLServer 单独加 escape rule
**选择**：
| Style | Open / Close | Escape rule | 适用 kinds |
|---|---|---|---|
| BACKTICK | `` ` ` `` | `` ` `` → `` `` `` | mysql, mariadb, tidb, oceanbase, apache_doris, starrocks, clickhouse |
| DOUBLE_QUOTE | `" "` | `"` → `""` | postgresql, h2, sqlite, oracle, duckdb, kingbase, dameng, gaussdb, hive, trino, presto |
| BRACKET | `[ ]` | `]` → `]]`（左 `[` 不需转义） | sqlserver |

**为什么 hive/trino/presto 进 DOUBLE_QUOTE**：ANSI SQL 标准是双引号，这三个 kind 即使有 backtick 变体，双引号也总是合法。保守选择降低风险。

### D3 · 未识别 kind fallback DOUBLE_QUOTE + WARN
不抛异常 —— 引用层失败让上游导入流程直接崩损坏用户体验；fallback 双引号在大多数方言（除 MySQL 系）安全。WARN 日志带 kind 名便于追溯。

### D4 · `InverseSqlGenerator` 注入 `IdentifierQuoter`，但需要 `ConnectionKind` 入参
**问题**：`InverseSqlGenerator` 是无状态 utility 风格，方法签名只接受 `tableName / columns / values`，不持 connection 信息。

**选择**：扩展所有 public 方法签名加 `String connectionKind` 参数，由调用方（`UndoLogService` / `UndoLogRepository`）从 `ConnectionRecord` 取出。
- 不引入 connection lookup 依赖（保持 utility 风格）
- **BREAKING 内部 API**：3-5 处调用站点需要回填 kind 参数
- 测试装配（fixture）也要同步更新

**替代**：把 `InverseSqlGenerator` 改 Spring bean 注入 `ConnectionRepository` 然后接 `connectionId` 入参 → 拒绝（多一层 IO，inverse 生成需在 DML 提交后立即同步执行，不该再 query 元数据 DB）。

### D5 · `IdentifierQuoter` 作为 Spring `@Component`，但 `InverseSqlGenerator` 接收 `IdentifierQuoter` 实例参数而非注入
`InverseSqlGenerator` 现状是 `static` 风格 utility class —— 改为 Spring bean 涉及大量调用站点。折衷：保留 static 方法，`IdentifierQuoter.quote(id, kind)` 也设为 `static`（QuoteStyle 派发是纯函数），无需注入即可调用。

### D6 · `ui_exec` 错误结构
现状 `UiExecAction.handle` 直接抛 `UnsupportedOperationException`（实际派发在 client，server 侧 throw 是死代码）。但 client 端 `query_editor.run_sql` 当无活跃 tab 时返回的错误结构没规范。

**选择**：在 client（Tauri / React）端 `query_editor.run_sql` handler 出错时返回：
```json
{
  "error": {
    "code": "no_active_query_editor",
    "message": "No query_editor tab is currently active",
    "nextAction": {
      "object": "workspace",
      "action": "open",
      "params": { "type": "query_editor", "title": "Untitled SQL" }
    }
  }
}
```
枚举 `error.code`：`no_active_query_editor` / `no_active_er_inspector` / `no_active_er_designer` / `no_active_dashboard` / `version_conflict` / `expected_text_mismatch`（后两者已存在）。

**为什么 nextAction 在 server response 而非 prompt 文档**：让 LLM 自适应 —— prompt 改起来风险大且无法保证 LLM 遵守；错误中带 next-action 是最强的"恢复信号"，符合 BUG-0065 修复后"硬契约写在数据里而非文档里"的取向。

### D7 · `data-collection` SKILL.md DO NOT 段
顶部加：
```
## ❗ DO NOT
Scripts MUST NOT connect to the target database directly.
Forbidden: pymysql, mysql.connector, psycopg2, sqlite3, pyodbc, JDBC, Sequelize, mysql2, pg, sqlite3 (Node), etc.
Reason: DataTalk script runtime is sandboxed and cannot reach user DB networks.
The ONLY supported write path is: POST {DT_BACKEND_URL}/api/script-data/write (or /batch)
```
+ `DataCollectionSkillContractTest` 断言此段存在（参照 `AgentsTemplateContractTest`）。

### D8 · `DataExportService.exportToSql` 默认 dialect = 跟随源 connection
不破坏现有 caller 默认行为。后续如需"导出到中性 ANSI"再加 `dialect=neutral` 选项（本 change 不做）。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| `InverseSqlGenerator` 改签名导致 undo log 写入路径断 | 全 grep 调用方一次性修；测试覆盖 `UndoLogService` write path |
| Hive/Trino/Presto 实际可能用 backtick 更原生，DOUBLE_QUOTE 是保守选择 | 暂不优化；如真实用户场景报问题，单独 follow-up |
| `useCursorFetch=true` / `autoCommit=false` 等 URL 参数与 quoting 无关，但读者可能误以为本 change 也修了 | design.md 明确 Non-Goals 排除 |
| ClickHouse `INSERT FORMAT` bulk-load 仍未用，本 change 不解 | 与 quoting 正交，由 `data-import` Day-2 follow-up 跟踪 |
| `data-collection` SKILL.md 新加 DO NOT 段后 LLM 仍可能违反（参考 BUG-0065 经验：prompt 不是硬契约） | 合同测试只防 prompt 退化；运行时执法需要在 `RunScriptActionHandler` 注入网络隔离 sandbox（本 change 不做，作为 follow-up） |
| `ui_exec` 错误结构 schema 扩展可能被旧 client 忽略 | 新字段 `error.nextAction` 是 optional，旧客户端忽略即可；server-side schema validation 不强制 |

## Migration Plan

无数据迁移。代码迁移按 tasks.md 顺序：
1. 新增 `IdentifierQuoter` + 单元测试
2. 改 `DataImportService` / `ScriptDataWriteService` / `DataExportService` 调用站点（这 3 个调用方相互独立，可并行）
3. 改 `InverseSqlGenerator` 签名 + 所有 caller（这一步串行，因为 BREAKING 内部 API）
4. 改 `UiExecAction` schema 文档 + client 端 query_editor handler 错误结构
5. 改 `data-collection/SKILL.md` + 加 `DataCollectionSkillContractTest`
6. 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` "Identifier quoting per dialect" 章节
7. 登记 BUG-0066 / BUG-0067 并立刻标 `fixed`（commit ref 同本 change）
8. `mvn clean verify` + `npx tsc --noEmit` + 浏览器手测一次 import_data MySQL 路径

**Rollback**：单次 commit 回退即可；无 schema / 协议状态。

## Open Questions

### Q1 · `update_connection_confirmable` 是否暴露 `extraJdbcParams`？
本 change 默认 **不做**（走"显式拒绝"分支：当 AI 调用 update 并未实际修改字段时返回 "no-op" 提示，避免 AI 钻牛角尖）。

但如果用户希望真的能透传（例：让 MySQL 连接走 `useCursorFetch=true` 不再要求用户手填 URL），需要：
- ConnectionRecord 加 `extraJdbcParams` 字段（Flyway 迁移）
- ConnectionUrlBuilder 拼接逻辑
- 安全审计：白名单参数 vs 自由文本（后者引入 SSRF / 凭据泄露面）

**待用户在 propose review 阶段决定**。本 change 先把 A/B/C 闭环，D 单独发起。

### Q2 · `Hive / Trino / Presto` 真实生产中应该用什么 quote 字符？
本 change 选 DOUBLE_QUOTE 保守路线。如有真实用户用 Trino + 反引号 schema，需要 follow-up 加 `BACKTICK_ALTERNATIVE` 标志位。**暂不解，依证据驱动**。

### Q3 · `ClickHouse` quote 字符
ClickHouse 同时接受 backtick 和 double-quote。本 change 选 BACKTICK（与 MySQL 系一致，降低生态认知成本）。若 ClickHouse 用户有反馈再调。
