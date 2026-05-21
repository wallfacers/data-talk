## Context

当前系统无任何时区感知能力。数据库时间类型字段按 JDBC 驱动返回的原始 Java 对象直传前端（`java.sql.Timestamp`、`java.sql.Date`、`java.sql.Time`、`String` 等），前端 JSON 序列化后直接渲染。不同数据库和 JDBC 驱动的返回行为各异：MySQL Connector/J 对 DATETIME/TIMESTAMP 均返回 `java.sql.Timestamp`（受 `serverTimezone` 参数影响），PostgreSQL 对 TIMESTAMPTZ 返回 UTC 时区的 `Timestamp`，而 ClickHouse 直接返回 ISO 8601 字符串。用户无论身处哪个时区，看到的时间均非其本地时间。

`docs/product-specs/2026-05-13-timezone-settings-design.md` 已提出初步需求。本设计基于该文档扩展为可落地的技术方案。

### 现有约束
- `QueryResult` 值对象只有 `columns: List<String>` + `rows: List<Map<String, Object>>` + `durationMs`，无列类型元数据
- `JdbcResultValueNormalizer` 是 final class + static method，基于 `instanceof` 做值归一化，不感知列类型
- 用户偏好存储模式参考 `ai_user_prefs` 表（单行、JdbcTemplate、固定 ID）
- 前端 Settings > General 页面当前所有设置项均为 localStorage 存储，本功能首次引入后端持久化设置

## Goals / Non-Goals

**Goals:**
- 用户可选择目标时区（IANA 标准），SQL 结果中时间戳类型字段按该时区格式化展示
- 用户可选择日期格式模板（预设 + 自定义）
- 偏好持久化到后端 SQLite 元数据库
- 不影响非时间类型的归一化行为
- 无偏好配置时，系统行为与当前一致（向后兼容）

**Non-Goals:**
- 不在本次变更中区分 MySQL DATETIME（无时区语义）与 TIMESTAMP（UTC 语义）——两者均按 UTC epoch millis 转用户时区处理。未来可通过列类型元数据精细化区分
- 不处理客户端本地时间（如消息时间戳、操作日志）——这些继续使用浏览器本地时区
- 不引入每连接级别的时区覆盖——用户级全局设置足够

## Design Inputs (Frontend)

来自 [client/DESIGN.md](../../../client/DESIGN.md):
- **Typography**: 表单标签使用 `ui-sm` (13px/18px)，控件文本使用 `ui-md` (14px/20px)
- **Spacing**: 表单项间距使用 `spacing.4` (16px)，Section 间距使用 `spacing.6` (24px)
- **Radius**: 输入框和下拉菜单使用 `radius.sm` (8px)
- **Density**: Settings 页面使用 `compact` 密度
- **Tokens**: 标签使用 `text.muted`，内容使用 `text.base`。禁用态使用 `interaction.disabled`
- **Focus**: 输入框聚焦使用 `interaction.focusRing`
- 禁止使用 raw 色值，必须使用语义 token
- 控件五态（default/hover/focus/active/disabled）必须显式映射 token

## Decisions

### Decision 1: 用户偏好存储——沿用 ai_user_prefs 单行模式

**选择**: 新建 `user_preferences` 表（而非扩展现有 `ai_user_prefs` 表），单行设计，`id = 'default'`。

**理由**: `ai_user_prefs` 负责 AI 模型偏好，语义上属于不同域（AI 设置 vs 通用设置）。新建独立表使未来扩展（如多用户支持）更清晰。单行设计保持简单——当前无多用户需求。

**备选方案**: 
- 扩展现有 `ai_user_prefs` 表加列 → 被拒绝：语义混乱，且 `AiUserPrefsRepository` 接口边界模糊
- Key-value 多行设计（`key TEXT, value TEXT`）→ 被拒绝：过度设计，单行足够

### Decision 2: 时间值转换——服务端统一处理

**选择**: 在 `JdbcResultValueNormalizer` 中接收时区参数和列类型信息，对时间列在服务端完成时区转换和格式化，返回 String 给前端。

**理由**: 
- 不同 JDBC 驱动返回时间值的 Java 类型不一致（Timestamp / OffsetDateTime / LocalDateTime / String），服务端统一处理避免前端需要处理多态类型
- 转换逻辑与数据库类型知识耦合，放在后端更自然
- 前端只需展示格式化后的字符串，无需引入 `moment.js` / `luxon` 等重量级时区库

**备选方案**:
- 客户端转换 → 被拒绝：需引入 `Intl.DateTimeFormat` + IANA 时区支持，且不同驱动返回不同 JS 类型（Date/string/number），处理复杂
- Normalizer 仅标准化为 epoch millis，客户端格式化 → 被拒绝：同样面临多态解析问题

### Decision 3: 列类型元数据传递——新增 ColumnMeta 到 QueryResult

**选择**: 在 `QueryResult` 中新增 `columnTypes: List<Integer>`（`java.sql.Types` 常量），不改变 `columns: List<String>` 的现有结构。

**理由**: 最小化 breaking change。所有现存的 `new QueryResult(columns, rows, durationMs)` 调用点需要适配新构造器，但消费方可以选择忽略 `columnTypes`。

**备选方案**:
- 不传递列类型，纯 instanceof 判断 → 被拒绝：无法区分 MySQL DATETIME vs TIMESTAMP（均返回 Timestamp），也无法识别 ClickHouse 的 DateTime 字符串
- 使用 `ColumnMeta` 对象列表（含 name + type + typeName）→ 被拒绝：对 v1 过度设计，`List<Integer>` 足够

### Decision 4: 时间值识别策略——instanceof 为主 + 列类型辅佐

**选择**: 正常化器同时检查值的 `instanceof` 和列的 `sqlType`。

- **instanceof 检测**: `java.sql.Timestamp|Date|Time`、`java.time.OffsetDateTime|LocalDateTime|LocalDate|LocalTime|Instant`
- **sqlType 辅佐**: `Types.TIMESTAMP_WITH_TIMEZONE (2014)` → 强制时区转换；`Types.DATE (91)` / `Types.TIME (92)` → 跳过时区转换

**时区转换规则**:
- `java.sql.Timestamp` / `java.util.Date` → `toInstant()` 获取 UTC epoch millis → `ZonedDateTime.ofInstant(instant, userZoneId)` → 格式化
- `java.time.OffsetDateTime` → `toInstant()` → 同上
- `java.time.Instant` → 同上
- `java.time.LocalDateTime` → 格式化，不转换（无时区语义）
- `java.time.LocalDate` / `java.sql.Date` → 格式化为日期部分，不转换
- `java.time.LocalTime` / `java.sql.Time` → 格式化为时间部分，不转换
- 已是 String → 透传（如 ClickHouse 驱动返回的格式化字符串）

### Decision 5: 前端时区选择器——Combobox + 搜索

**选择**: 使用 shadcn/ui `Command` (Combobox) 组件，内建搜索过滤，选项为完整 IANA 时区列表（`Intl.supportedValuesOf('timeZone')`）。

**理由**: shadcn/ui 已有 Combobox 模式，与现有 Settings 页面的 Select 组件风格一致。`Intl.supportedValuesOf('timeZone')` 返回浏览器原生时区列表（~440 项），无需引入外部时区数据库。

**备选方案**:
- 自定义下拉 → 被拒绝：增加维护成本，且 Combobox 已满足需求
- 仅列出常用时区（~20 项）+ "其他" → 部分接受：可提供常用时区快捷组，但完整搜索能力仍需要

### Decision 6: 日期格式选择——预设 + 自定义

**选择**: 提供 4 个预设格式 + 自定义输入框。预设根据所选时区自动推荐默认格式。

**预设列表**:
| 预设 | 格式 | 示例 |
|------|------|------|
| CN (ISO) | `yyyy-MM-dd HH:mm:ss` | 2025-06-15 16:00:00 |
| US | `MM/dd/yyyy hh:mm:ss a` | 06/15/2025 04:00:00 PM |
| EU | `dd/MM/yyyy HH:mm:ss` | 15/06/2025 16:00:00 |
| CN Long | `yyyy年MM月dd日 HH:mm:ss` | 2025年06月15日 16:00:00 |

**自定义模式**: 用户可输入任意 `DateTimeFormatter` 兼容的格式字符串。

## Risks / Trade-offs

- **[R] MySQL DATETIME 值会被错误地当作 UTC 时间转换** → 因为 `java.sql.Timestamp` 无法区分 DATETIME（local wall-clock）与 TIMESTAMP（UTC），两者均被 `toInstant()` 按 UTC epoch millis 处理。对 DATETIME 列，用户看到的会比期望的多 8 小时（在 UTC+8 时区下）。**缓解**: 在 v1 中接受此限制，文档说明。v2 可通过列类型元数据（`ResultSetMetaData.getColumnTypeName()` 返回 "DATETIME" vs "TIMESTAMP"）做区分。
- **[R] ClickHouse/DuckDB 时间列可能是字符串** → 如果驱动已将 DateTime 格式化为字符串返回，normalizer 无法识别这是时间值，时区转换不会应用。**缓解**: 在列类型检测中识别 `Types.TIMESTAMP` → 如果此时值是 String，仍尝试解析并转换。或等待 v2 处理。
- **[R] `QueryResult` 结构变更导致编译错误** → 所有 `new QueryResult(...)` 调用点需要更新。**缓解**: 提供工厂方法或 builder，使无类型信息的调用方无需传 null。
- **[R] 时区列表可能因浏览器而异** → `Intl.supportedValuesOf('timeZone')` 在不同浏览器返回列表不完全一致。**缓解**: 主流程使用浏览器 API；后端验证时区有效性时使用 `ZoneId.of()` 校验。

## Migration Plan

1. **Flyway 迁移**: 新增 `V<next>__user_preferences.sql`，插入默认行
2. **部署顺序**: 先部署后端（新 API + 迁移），再部署前端（新 UI）。旧前端不感知新 API，无影响。旧后端收到 PUT 请求返回 404，前端静默失败（用户需刷新）。
3. **回滚**: 无破坏性变更。回滚后端 → 时区设置 API 不可用，前端设置页恢复旧状态（localStorage）。`user_preferences` 表不删除。

## Open Questions

- **Q1**: 是否需要支持 workbench 级时区覆盖？目前设计为全局用户级，但如果用户同时操作多个跨时区数据源，可能需要在工作区级别覆盖。→ 推迟到 v2
- **Q2**: 是否需要在 AGENTS.md 中告知 AI 当前时区设置？AI 生成含时间的 SQL 时可能需要感知用户时区。→ 不在本次范围，后续评估
