# PostgreSQL SQL Splitter Design

## 背景

当前 Stage SQL Workbench 已经把 `/api/sql/execute` 重构为多语句、多结果契约，但执行链路仍在 [SqlExecuteService.java](/home/wallfacers/project/data-talk/server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java) 内部使用手写 `splitStatements()` 拆分 SQL 脚本。

这套手写 splitter 可以处理普通单引号、双引号和注释，但无法可靠覆盖 PostgreSQL 的原生语法扩展，尤其是：

- dollar-quoted string，例如 `$$...$$`、`$tag$...$tag$`
- `DO $$ ... $$`
- `CREATE FUNCTION ... LANGUAGE plpgsql AS $$ ... $$`
- function body 内部带分号、嵌套 dollar quote、注释混合的脚本

这会导致 PostgreSQL procedural SQL 被错误切断，进而出现执行失败、错误定位失真，甚至在未来演进中引入更难排查的行为偏差。

## 目标

- 为 PostgreSQL 连接提供可靠的多语句拆分能力。
- 保持 `SqlExecuteService` 的多结果执行模型不变，只替换“如何切分语句”这一层。
- 把 splitter 从 `SqlExecuteService` 中抽离，形成可按数据库方言替换的边界。
- 为后续引入更强 PostgreSQL 原生 parser 留出稳定接口。

## 非目标

- 本次不重写风险分析器；`CalciteSqlRiskAnalyzer` 继续保留。
- 本次不统一替换 MySQL / H2 / SQLite 的解析策略。
- 本次不引入完整 PostgreSQL AST 语义分析。
- 本次不处理结果执行策略、事务模型、结果聚合规则的重构。

## 方案比较

### 方案 A：继续增强当前手写 splitter

在现有 `splitStatements()` 基础上继续补：

- dollar quote 起止识别
- tagged dollar quote
- PostgreSQL 注释/嵌套场景
- procedural body 中分号跳过

优点：

- 无新增依赖
- 改动局部

缺点：

- 本质上是在业务代码里重写 PostgreSQL lexer
- 测试面非常大，仍然难以保证完整性
- 后续继续补方言特性成本只会越来越高

结论：不采用。

### 方案 B：使用 PgJDBC 内部 `Parser` 作为 PostgreSQL splitter

依赖 PostgreSQL JDBC 驱动内部的 `org.postgresql.core.Parser.parseJdbcSql(..., splitStatements=true, ...)` 进行脚本拆分。

优点：

- 纯 Java，无需 JNI / sidecar
- 已有 dollar quote 处理能力
- 接入成本低，适合作为近期可交付方案

缺点：

- 属于 PgJDBC 内部 API，不是稳定公共契约
- 驱动升级时有破坏风险
- 更偏 query splitting，不是长期基础设施边界

结论：作为当前阶段的推荐落地方案。

### 方案 C：引入 `libpg_query` 系列 parser

基于 PostgreSQL 服务器源码提取的 parser/scanner，为 PostgreSQL 提供原生精度的 splitting / parsing。

优点：

- 语法正确性最高
- 对 `DO $$` / `CREATE FUNCTION` / PL/pgSQL 场景最稳
- 长期最符合“不要自己实现 PostgreSQL parser”的原则

缺点：

- Java 集成成本高于纯 Java 方案
- 需要处理 JNI / native packaging / sidecar 或其他跨语言桥接
- 当前项目不具备可直接复用的 Java 封装

结论：作为长期替换目标，不作为这次第一阶段交付。

## 推荐设计

采用“两层设计，分阶段落地”：

- 第一阶段：抽象 `SqlStatementSplitter` 接口，并以 PgJDBC 内部 `Parser` 实现 PostgreSQL splitter
- 第二阶段：若 PostgreSQL procedural SQL 能力继续扩张，再把 PostgreSQL 实现替换为 `libpg_query` 封装

这样可以先解决当前 Stage SQL Workbench 的真实故障点，同时避免把 `SqlExecuteService` 和具体 parser 绑定死。

## 架构设计

### 新边界

在 application 层定义语句拆分接口：

```java
public interface SqlStatementSplitter {
    boolean supports(String connectionKind);
    List<String> split(String sql);
}
```

或使用一个 registry/facade：

```java
public interface SqlStatementSplitters {
    List<String> split(String connectionKind, String sql);
}
```

推荐后者，对 `SqlExecuteService` 更简洁。

### 实现分层

- `application`
  - 定义 splitter 门面接口
  - `SqlExecuteService` 仅依赖接口，不关心 PostgreSQL 细节
- `infrastructure`
  - `PostgresJdbcSqlStatementSplitter`
  - `GenericSqlStatementSplitter`
  - `DefaultSqlStatementSplitters` / registry

### 运行时选择

- `connection.kind in ('postgres', 'postgresql')`
  - 走 PostgreSQL 专用 splitter
- `mysql` / `h2` / `sqlite`
  - 暂时继续走 generic splitter

连接 kind 常量继续沿用 [ConnectionKind.java](/home/wallfacers/project/data-talk/server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java) 的现有定义，并兼容历史上的 `postgres` 字符串。

## 详细行为

### PostgreSQL splitter

第一阶段使用 PgJDBC 内部 `Parser.parseJdbcSql(...)`：

- `withParameters = false`
- `splitStatements = true`
- `isBatchedReWriteConfigured = false`

输出的 native queries 逐条提取 SQL 文本，按顺序返回 `List<String>`。

约束：

- 只用于 PostgreSQL 连接
- 不承担参数重写，不承担 RETURNING 注入，不承担执行逻辑
- 只负责“如何把一个脚本拆成有序语句”

### Generic splitter

保留当前手写逻辑，但迁移到独立类中，避免 `SqlExecuteService` 持续膨胀。

它的职责仅限于：

- 非 PostgreSQL 方言的基础切分
- 单引号、双引号、注释规避

它不再承担“试图完整兼容 PostgreSQL procedural SQL”的责任。

## 对现有代码的影响

### `SqlExecuteService`

重构后：

- 删除内部 `splitStatements()` 私有实现
- 注入 `SqlStatementSplitters`
- 根据连接 kind 选择合适 splitter
- 其余执行逻辑保持不变：
  - 上下文解析
  - 风险分析
  - 多结果返回
  - DML 汇总
  - 失败回滚

### 风险分析

`CalciteSqlRiskAnalyzer` 暂不调整。

理由：

- 当前问题是执行前的脚本切分，不是风险分级准确率
- procedural SQL 的风险分析可在后续独立治理
- 本次优先把执行链路做稳，避免同时改两套 parser

## 测试策略

新增 PostgreSQL splitter 单测，至少覆盖：

- `SELECT $$a;b$$; SELECT 1;`
- `SELECT $tag$a;b$tag$; SELECT 1;`
- `DO $$ BEGIN PERFORM 1; PERFORM 2; END $$; SELECT 1;`
- `CREATE FUNCTION ... AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql; SELECT 1;`
- 函数体内嵌套不同 tag 的 dollar quote

新增 application 层测试，验证：

- `postgresql` 连接走 PostgreSQL splitter
- `mysql` / `h2` 继续走 generic splitter
- splitter 产出的语句顺序不变

新增 adapter/integration test，验证 `/api/sql/execute` 在 PostgreSQL procedural SQL 场景下不再被错误切断。

## 风险与后续

### 已知风险

- PgJDBC 的 `org.postgresql.core.Parser` 是内部 API，后续驱动升级可能带来不兼容
- 第一阶段仍是 PostgreSQL 专用增强，不代表所有方言都具备完整脚本 parser
- 风险分析继续使用 Calcite，对 PL/pgSQL 的理解仍有限

### 后续演进

当以下任一条件出现时，应启动第二阶段：

- Stage SQL Workbench 要稳定支持更多 PostgreSQL DDL / function / trigger 脚本
- 需要对 PostgreSQL 脚本做更强的结构化分析
- PgJDBC 内部 API 升级成本开始显著上升

届时将 PostgreSQL splitter 实现从 PgJDBC 替换为 `libpg_query` 封装，但保持 application 层接口不变。

## 决策

- 不再继续增强当前手写 PostgreSQL splitter
- 第一阶段采用 PgJDBC 内部 `Parser` 作为 PostgreSQL 专用 splitter
- 通过 application/infrastructure 分层把 parser 依赖隔离起来
- 为未来迁移到 `libpg_query` 保留稳定替换点
