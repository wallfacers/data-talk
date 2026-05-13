## Why

数据库时间类型字段（DATETIME、TIMESTAMP、TIMESTAMPTZ 等）通常以 UTC 存储，但不同地区用户需要按本地时区查看时间。当前系统所有时间值按 JDBC 驱动返回的原始对象直传前端，不做任何时区转换——中国用户看到 UTC 时间比预期早 8 小时，美国用户看到的时间也不对。需要在用户偏好中增加时区设置，并在 SQL 查询结果中对时间列按用户选择的时区进行格式化。

## What Changes

- 后端新建 `user_preferences` 表，存储 timezone 和 dateFormat 两个字段
- 后端新增 REST API：`GET/PUT /api/preferences` 读写用户偏好
- `JdbcResultValueNormalizer` 增加时间类型检测与转换能力（需传入列类型元数据）
- `QueryResult.columns` 扩展为携带列类型信息，或增加独立元数据载体，使前端能识别时间列
- 前端 Settings > General 页面新增时区选择器和日期格式选择器
- SQL 结果表格对时间列应用用户选择的时区格式化

## Capabilities

### New Capabilities
- `user-preference-storage`: 后端用户偏好持久化——新建 `user_preferences` 表、Repository、Service，提供读写 API
- `timezone-aware-result-normalization`: SQL 结果中时间类型列按用户时区转换为格式化字符串
- `timezone-settings-ui`: 前端 Settings > General 页面新增时区选择器与日期格式选择器

### Modified Capabilities
<!-- No existing specs require modification -->

## Impact

- **后端 domain**: 新增 `UserPreferences` 值对象/记录；`QueryResult` 需扩展列类型元数据
- **后端 application**: 新增 `UserPreferencesService`、`UserPreferencesRepository` 接口；`JdbcResultValueNormalizer` 增加时间转换逻辑；`SqlExecuteService` 在读取 ResultSet 时捕获列类型信息
- **后端 infrastructure**: 新增 `UserPreferencesRepositoryJdbc`、Flyway 迁移；`DynamicSqlExecutionRepository` 传递列类型信息
- **后端 adapter**: 新增 `PreferencesController` REST 端点
- **前端**: `general-panel.tsx` 新增两个表单控件；新增 `timezone-store.ts`（或扩展现有 store）；数据表格渲染层对时间列做格式化
- **数据源兼容**: 需验证 MySQL（DATETIME 无时区）、PostgreSQL（TIMESTAMPTZ 带时区）、ClickHouse（DateTime/DateTime64）、DuckDB（TIMESTAMP WITH TIME ZONE）等 20 种数据源的 JDBC 时间类型行为
- **Breaking**: `QueryResult` 结构变更可能影响所有消费方（action handler、测试 fixture）
