## 1. Backend: Domain Layer

- [x] 1.1 创建 Flyway 迁移 `V<next>__user_preferences.sql`：`user_preferences` 表（id TEXT PRIMARY KEY, timezone TEXT NOT NULL DEFAULT 'UTC', date_format TEXT NOT NULL DEFAULT 'yyyy-MM-dd HH:mm:ss', updated_at INTEGER NOT NULL），插入默认行
- [x] 1.2 创建 `UserPreferences` domain record：`String id()`, `ZoneId timezone()`, `String dateFormat()`, `long updatedAt()`，带 `DEFAULT` 工厂常量
- [x] 1.3 扩展 `QueryResult`：新增 `columnTypes` 字段 `List<Integer>`（可为空列表，向后兼容），更新所有构造调用点以通过编译

## 2. Backend: Application Layer

- [x] 2.1 创建 `UserPreferencesRepository` 接口：`findPreferences()` 返回 `UserPreferences`（无记录时返回默认值），`save(UserPreferences)` 执行 UPSERT
- [x] 2.2 创建 `UserPreferencesService`：封装 repository，提供 `getPreferences()` / `updateTimeZone(String zoneId)` / `updateDateFormat(String pattern)` 方法，时区校验使用 `ZoneId.of()` 验证
- [x] 2.3 增强 `JdbcResultValueNormalizer`：新增 `normalize(Object rawValue, int sqlType, ZoneId userZoneId, String dateFormat)` 重载方法，实现时间类型 instanceof 检测 + 时区转换 + 格式化；原有 `normalize(Object)` 保持向后兼容，委托到无时区参数版本
- [x] 2.4 更新 `SqlExecuteService.readResultSet()`：在遍历 ResultSet 时通过 `ResultSetMetaData.getColumnType()` 收集列类型，调用新 normalize 重载时传入用户时区偏好

## 3. Backend: Infrastructure Layer

- [x] 3.1 创建 `UserPreferencesRepositoryJdbc`：实现 `UserPreferencesRepository`，使用 `@Qualifier("datatalkJdbc")` JdbcTemplate，单行读写
- [x] 3.2 更新 `DynamicSqlExecutionRepository.execute()`：在构建 `QueryResult` 时传递 `columnTypes` 列表

## 4. Backend: Adapter Layer

- [x] 4.1 创建 `UserPreferencesRequest` / `UserPreferencesResponse` DTO（含 `timezone` 和 `dateFormat` 字段，Request 中均为可选）
- [x] 4.2 创建 `PreferencesController`：`GET /api/preferences` 返回当前偏好，`PUT /api/preferences` 更新偏好（HTTP 400 对无效时区）

## 5. Frontend: Settings UI

- [x] 5.1 创建 `PreferencesApi` client 模块（`GET` / `PUT` fetch 封装），支持 TanStack Query
- [x] 5.2 创建 `TimezoneSelector` 组件：shadcn/ui Combobox + 搜索过滤，选项来自 `Intl.supportedValuesOf('timeZone')`，顶部显示常用时区分组（`Asia/Shanghai`, `America/New_York`, `Europe/London`, `Asia/Tokyo`, `UTC`）
- [x] 5.3 创建 `DateFormatSelector` 组件：4 个预设 radio + 自定义输入框（仅在选择"Custom"时显示）
- [x] 5.4 更新 `GeneralPanel`：在 Language 选择器下方新增 Timezone 和 DateFormat 行，从 `useQuery(GET /api/preferences)` 读取当前值，`useMutation(PUT /api/preferences)` 保存变更
- [x] 5.5 验证控件五态 token 映射（default → text.muted/border.default, hover → interaction.hover, focus → interaction.focusRing + cobalt border, active → interaction.active, disabled → interaction.disabled）

## 6. Testing & Verification

- [x] 6.1 后端：`UserPreferencesRepositoryJdbcTest` — 验证读写、无记录时返回默认值
- [x] 6.2 后端：`JdbcResultValueNormalizerTest` — 验证各种 Java 时间类型的时区转换输出正确性（Timestamp 转 Asia/Shanghai、OffsetDateTime 转 America/New_York、LocalDateTime 不转换等）
- [x] 6.3 后端：`PreferencesControllerTest` — 验证 GET/PUT 端点返回正确值和错误码
- [x] 6.4 前端：`timezone-settings.test.tsx` — 验证组件渲染、搜索过滤、保存回调
- [x] 6.5 全栈：`mvn install -pl data-talk-adapter -am -DskipTests` + `npx tsc --noEmit` 确认零编译/类型错误
