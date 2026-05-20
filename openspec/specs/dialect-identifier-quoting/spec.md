## Requirements

### Requirement: 系统 SHALL 提供按方言派发的标识符引用工具

系统 SHALL 暴露一个 `IdentifierQuoter` 应用层组件，接受 `(identifier, connectionKind)` 两个入参，返回按目标方言正确引用且转义嵌入引号字符的标识符字符串。本组件 SHALL 是无状态纯函数，可作为 `static` 方法或 `@Component` 直接调用。

#### Scenario: MySQL 方言使用反引号引用

- **GIVEN** 调用方传入 `identifier="td_orders"`, `connectionKind="mysql"`
- **WHEN** `IdentifierQuoter.quote(identifier, connectionKind)` 被调用
- **THEN** 返回 `` `td_orders` ``

#### Scenario: PostgreSQL 方言使用双引号引用

- **GIVEN** 调用方传入 `identifier="td_orders"`, `connectionKind="postgresql"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `"td_orders"`

#### Scenario: SQL Server 方言使用方括号引用

- **GIVEN** 调用方传入 `identifier="td_orders"`, `connectionKind="sqlserver"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `[td_orders]`

#### Scenario: MySQL 标识符自身含反引号字符 SHALL 转义为双反引号

- **GIVEN** 调用方传入 `` identifier="weird`name" ``, `connectionKind="mysql"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `` `weird``name` ``（反引号被替换为两个连续反引号）

#### Scenario: PostgreSQL 标识符自身含双引号字符 SHALL 转义为双双引号

- **GIVEN** 调用方传入 `identifier="weird\"name"`, `connectionKind="postgresql"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `"weird""name"`

#### Scenario: SQL Server 标识符自身含右方括号 SHALL 转义为双右方括号

- **GIVEN** 调用方传入 `identifier="weird]name"`, `connectionKind="sqlserver"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `[weird]]name]`（仅右括号需要转义，左括号保留原样）

### Requirement: 系统 SHALL 覆盖 19 种 first-class connection kind 的引用风格

系统 SHALL 按以下风格矩阵派发引用：

- BACKTICK 风格（反引号）：`mysql`, `mariadb`, `tidb`, `oceanbase`, `apache_doris`, `starrocks`, `clickhouse`
- DOUBLE_QUOTE 风格（双引号）：`postgresql`, `h2`, `sqlite`, `oracle`, `duckdb`, `kingbase`, `dameng`, `gaussdb`, `hive`, `trino`, `presto`
- BRACKET 风格（方括号）：`sqlserver`

#### Scenario: MariaDB 视为 MySQL 系，使用反引号

- **GIVEN** `connectionKind="mariadb"`, `identifier="x"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `` `x` ``

#### Scenario: TiDB / OceanBase / Apache Doris / StarRocks / ClickHouse 视为 MySQL 协议系，使用反引号

- **WHEN** 对上述任一 kind 调用 `quote("x", kind)`
- **THEN** 返回 `` `x` ``

#### Scenario: Kingbase / Dameng / GaussDB 视为 PostgreSQL/Oracle 协议系，使用双引号

- **WHEN** 对上述任一 kind 调用 `quote("x", kind)`
- **THEN** 返回 `"x"`

#### Scenario: Hive / Trino / Presto 采用保守 ANSI 双引号

- **WHEN** 对上述任一 kind 调用 `quote("x", kind)`
- **THEN** 返回 `"x"`

### Requirement: 未识别 kind SHALL fallback 双引号并记录 WARN 日志

不在白名单内的 `connectionKind`（含 `null` / 空字符串 / 拼写错）SHALL 按 ANSI 标准双引号引用，且 SHALL 输出一条 WARN 级别日志带上原始 kind 字符串，便于运维追溯。SHALL NOT 抛异常。

#### Scenario: kind 为 null 时 fallback 双引号

- **GIVEN** 调用方传入 `identifier="x"`, `connectionKind=null`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `"x"`
- **AND** 系统输出一条 WARN 日志记录 `connectionKind=null` 的 fallback

#### Scenario: kind 为未知字符串时 fallback 双引号

- **GIVEN** 调用方传入 `identifier="x"`, `connectionKind="unknown_kind_xyz"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `"x"`
- **AND** 系统输出一条 WARN 日志带 `connectionKind=unknown_kind_xyz`

### Requirement: kind 字符串匹配 SHALL 大小写不敏感

`connectionKind` 入参 SHALL 在内部做 `Locale.ROOT` 小写化后再匹配白名单，使 `"MYSQL"` / `"MySql"` / `"mysql"` 等价。

#### Scenario: 大写 kind 匹配 MySQL 反引号

- **GIVEN** 调用方传入 `identifier="x"`, `connectionKind="MYSQL"`
- **WHEN** 调用 `quote()`
- **THEN** 返回 `` `x` ``
