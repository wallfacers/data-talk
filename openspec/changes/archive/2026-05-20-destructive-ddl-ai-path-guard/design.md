## Context

`ExecuteSqlAction` 是 AI 聊天路径下唯一的 SQL 执行入口。当前只有 `containsDelete()` 一个拦截门——仅匹配 DELETE 关键字。

**关键发现**：`ExecuteSqlAction.java:229` 调用 `riskAnalyzer.analyze(sql, Category.QUERY, kind)` 时传入 `Category.QUERY`。对于 MySQL/PostgreSQL/H2/MariaDB（不在 `classifyDialectSpecific` 方言列表中），DROP TABLE / TRUNCATE 走 Calcite 通用解析器 → 无法解析 DDL → 抛 `SqlParseException` → `fallbackFor(QUERY, ...)` → `SqlRiskAnalysis.fallback()` → `riskLevel = null`、`reason = "parse_failed:..."`。因此**不能依赖 `riskLevel == L3` 作为拦截前提**。

对比工作台 UI 路径 (`SqlExecuteService`)：所有 L2/L3 语句都有 `confirmed + riskAck` 确认门，TRUNCATE/DROP 被正确拦截。

AI 侧的 AGENTS.md 和 sql-execution SKILL.md 明确写了"no READ-ONLY gate"，等于告诉 AI 所有非 DELETE 操作可以直接执行，包括 DROP TABLE。

## Goals / Non-Goals

**Goals:**

- AI 路径拦截数据删除型 DDL：DROP *、TRUNCATE、ALTER...DROP *、INSERT OVERWRITE
- AI 路径拦截权限/会话管理型语句：GRANT/REVOKE/DENY、KILL、SHUTDOWN、SET GLOBAL、PURGE
- DELETE 保持现有独立的确认流程（`requires_confirmation`），不纳入 DDL denylist
- 拦截后返回 `redirect_to_editor` 响应，AI 打开 query_editor 写入 SQL，让用户自行执行
- 保留 DELETE 现有确认流程不变
- 所有建设性操作（CREATE *、ALTER...ADD/MODIFY、RENAME、MERGE、OPTIMIZE 等）保持放开
- 拦截机制必须与方言无关，对 MySQL/PG/H2 等主流库同样生效

**Non-Goals:**

- 不改动工作台 UI 路径 (`SqlExecuteService`)，该路径已有完善的 L2/L3 拦截
- 不新增前端代码，redirect 行为由 AGENTS.md + skill 指令控制
- 不改动 `CalciteSqlRiskAnalyzer`，风险分级逻辑不变
- **不覆盖文件/网络/管理类 L3 操作**：SQLite ATTACH/DETACH、DuckDB COPY/ATTACH/INSTALL/CREATE SECRET/read_csv(...)、SQL Server BACKUP/RESTORE/DBCC/EXEC、Doris DECOMMISSION/ADMIN/STREAM LOAD、ClickHouse SYSTEM/OPTIMIZE/ATTACH 等属于另一威胁模型（数据外泄/文件系统访问），不在本次范围。这些操作由 `CalciteSqlRiskAnalyzer` 在方言分类器层面标记为 L3，未来可单独评估

## Decisions

### Decision 1: 在 `containsDelete()` 之前新增 `containsDestructiveDdl()` 拦截门

**选择**: 在 `containsDelete()` **之前**、`executeSql()` 之前新增一个基于原始 SQL 文本关键词扫描的判断方法，与 `containsDelete()` 同构。检查顺序：先 DDL 门 → 再 DELETE 门 → 最后执行。

**理由**: 纯文本扫描与方言无关，不依赖 Calcite 解析结果。必须放在 DELETE 检查之前：`containsDelete()` 命中即 return 走确认流程，若 DDL 门在后，`DELETE FROM t; DROP TABLE t` 会先被 DELETE 门捕获 → 确认后回放整批 → DROP TABLE 照样执行。DDL 门在先可截断此绕过路径。

**执行顺序**：
```
1. containsDestructiveDdl(sql)? → YES: redirect_to_editor (拒绝整批)
2. containsDelete(sql)?         → YES: requires_confirmation (确认后执行)
3. executeSql()                 → 直接执行
```

**备选方案**: 扩展 `containsDelete()` 为 `requiresConfirmation()` 统一处理所有 L3。但 DELETE 是确认后执行，DDL 是拒绝+引导编辑器，两种流程本质不同，不宜合并。

### Decision 2: 基于原始 SQL 文本的方言无关关键词 denylist

**选择**: 不依赖 `SqlRiskAnalysis` 的 riskLevel/reason（它们对主流库的 DDL 为 null/"parse_failed"），直接对 SQL 文本做语句边界关键词检测，与现有 `containsDelete()` 和各方言的 `startsWithKeyword()` 同构。

**为什么不用 riskLevel/reason**：
1. MySQL/PG/H2/MariaDB 的 DDL 走 Calcite 通用解析器，无法解析 → `fallbackFor(QUERY)` → `riskLevel = null`，L3 前提为假
2. 即使是被方言分类器正确判为 L3 的库，reason 也不含关键词（如 `dameng_admin_command`、`kingbase_admin_command`、`gaussdb_admin_command`、`starrocks_unrecognized`、`tidb_unrecognized`、`oceanbase_unrecognized`）
3. `affectedObjects` 提取依赖 Calcite 解析成功或 `DDL_OBJECT_PATTERNS` 正则，KILL/SHUTDOWN/PURGE/INSERT OVERWRITE 不在 pattern 列表中

**具体实现**：

新增 `containsDestructiveDdl(String sql)` 方法：

```
1. 按 ; 切分 SQL 为独立语句
2. 对每条语句：
   a. trim + toUpperCase
   b. 检测首关键词是否在破坏性 denylist 中：
      - DROP
      - TRUNCATE
      - GRANT
      - REVOKE
      - DENY        (SQL Server)
      - KILL
      - SHUTDOWN
      - PURGE       (Oracle)
      - SET GLOBAL  (StarRocks/TiDB)
   c. 正则匹配 ALTER...DROP 子句：
      (?i)ALTER\s+\w+\s+.*\bDROP\b
      覆盖 ALTER TABLE DROP COLUMN/PARTITION/CONSTRAINT 等
   d. 正则匹配 INSERT OVERWRITE：
      (?i)INSERT\s+OVERWRITE
      (StarRocks/Hive 特有，整表覆写)
3. 任一语句命中 → 返回 true
```

**备选方案 A（已否决）**: 依赖 `risk.riskLevel() == L3 && reason 含关键词`。如上分析，主流库的 L3 为 null，特殊方言的 reason 不含关键词。

**备选方案 B（已否决）**: 把 `Category.QUERY` 改为 `Category.DDL`。这会改变风险分析器对所有 SQL 的行为，影响范围不可控。

### Decision 3: `redirect_to_editor` 响应格式与 AI 引导路径

**选择**: 返回结构化 JSON：

```json
{
  "status": "redirect_to_editor",
  "reason": "destructive_ddl",
  "riskLevel": "<from risk analysis, may be null>",
  "sql": "<original SQL>",
  "affectedObjects": "<from risk.affectedObjects(), populated by DDL_OBJECT_PATTERNS even on parse_failed>",
  "message": "该操作涉及破坏性 DDL，请在 SQL 编辑器中确认后执行",
  "suggestion": "use_query_editor"
}
```

`affectedObjects` 直接复用 `ExecuteSqlAction.java:229` 已计算的 `risk.affectedObjects()`——`fallbackFor()` 即使解析失败也会通过 `DDL_OBJECT_PATTERNS` 正则填充对象名（如 DROP TABLE td_orders → `["td_orders"]`），无需重复造提取逻辑。

AI 收到此响应后 SHALL 执行以下**单步动作**：

```
datatalk_ui_exec(
  object  = "workspace",
  action  = "open",
  params  = {
    type:    "query_editor",
    title:   "<描述性标题，如 'Destructive DDL'>",
    payload: {
      initialSql: "<redirect 响应中的 sql 字段>",
      autoRun:    false    // 关键：不自动执行，让用户自行确认
    }
  }
)
```

**schema 约束**（来自 `UiExecAction.java:201`）：
- `workspace open` 强制要求 `type` + `title`，缺一即被 schema 校验拒绝
- `payload.autoRun` 默认值未定义，必须显式设为 `false`——redirect 的全部意义是让用户手动确认执行
- 不需要单独 `workspace focus`——`open` 本身聚焦新建 tab，且 focus 要求 `target`（tab id），AI 在 open 返回前拿不到

**理由**: 不需要前端改动。AI 是 MCP 工具的调用方，收到 redirect 响应后按 AGENTS.md + skill 指令执行上述单步动作。动作参数严格对齐 `UiExecAction` schema，避免被校验拒绝。

### Decision 4: 建设性操作保持放开

以下操作不在破坏性 denylist 中，`containsDestructiveDdl()` 返回 false，自然放行：

| 操作 | 首关键词 | 在 denylist? |
|------|---------|-------------|
| CREATE TABLE/VIEW/INDEX | CREATE | 否 |
| ALTER TABLE ADD/MODIFY | ALTER（无 DROP 子句） | 否 |
| RENAME TABLE | RENAME | 否 |
| MERGE INTO | MERGE | 否 |
| OPTIMIZE TABLE | OPTIMIZE | 否 |
| VACUUM/REINDEX | VACUUM/REINDEX | 否 |
| ANALYZE TABLE | ANALYZE | 否 |
| INSERT INTO | INSERT（非 OVERWRITE） | 否 |
| UPDATE ... SET | UPDATE | 否 |

## Risks / Trade-offs

- **[误拦截]** ALTER TABLE 包含 DROP 子句但主要是建设性操作（如 `ALTER TABLE t ADD COLUMN a INT, DROP COLUMN b`）会被整体拦截 → 可接受，复合 ALTER 中含 DROP 子句，整条引导到编辑器让用户确认更安全
- **[SQL 注释绕过]** 理论上 `DROP/*comment*/TABLE t` 可能绕过首关键词检测 → `containsDelete()` 也有同样的注释绕过风险，当前被接受；实际生产中 AI 不太可能生成注释混淆的 SQL
- **[AI 不遵守指令]** AI 收到 redirect 后仍尝试其他方式绕过 → 代码层已确保 DDL 不执行（`containsDestructiveDdl` 直接拦截），AI 无法通过 `execute_sql` 绕过；只能走 query_editor 路径，那条路径有独立的 L2/L3 确认门
- **[affectedObjects 不完整]** KILL/SHUTDOWN/PURGE/INSERT OVERWRITE 不在 `DDL_OBJECT_PATTERNS` 列表中，`affectedObjects` 为空 → 可接受，这些操作的目标不是表对象，返回空的 affectedObjects 不影响 AI 引导逻辑
- **[文件/网络 L3 未覆盖]** 本次 denylist 不包含 ATTACH/COPY/BACKUP/CREATE SECRET 等文件/网络访问操作（见 Non-Goals）。这些操作在方言分类器中被标记为 L3，但 AI 路径的 `execute_sql` 不读 riskLevel，不会拦截 → 有意识的取舍：数据删除型威胁优先，文件/网络外泄型威胁属于另一威胁模型，后续单独评估
