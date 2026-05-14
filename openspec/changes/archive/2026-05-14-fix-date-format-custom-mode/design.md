## Context

`DateFormatSelector` 自定义模式通过 `<Input>` 自由输入格式字符串，每次按键触发 `prefsMutation.mutate()` → `PUT /api/preferences`。后端只检查 `!= null && !isBlank()`，不验证模式合法性。`JdbcResultValueNormalizer` 调用 `DateTimeFormatter.ofPattern(dateFormat)` 无 try-catch。

## Goals

1. 自定义格式输入不再逐键触发 API 写入
2. 用户输入无效模式时立即得到反馈
3. 即使绕过前端校验，后端也能拒绝无效格式

## Non-Goals

- 不改变预设格式的行为
- 不改变格式字符串的 Java DateTimeFormatter 规范本身

## Decisions

### D1: Debounce onChange（500ms）

**选择**: `useDeferredValue` + `useEffect` 或 `useTimeout` 在输入停止 500ms 后才调用父级 `onChange`。

**理由**: 避免按键级 HTTP 请求竞态。500ms 足够用户感知即时，同时减少无效写入。

### D2: 客户端预校验

**选择**: 在前端用 `Intl.DateTimeFormat` 无法直接验证 Java 模式——前后端模式规范不同（Java vs CLDR）。因此前端采用"try format with sample date"策略：用 `dayjs` 格式化一个固定日期，如果模式包含 Java 保留字符（如 `Y`/`M`/`d`/`H`/`m`/`s`/`S` 等）则视为"可能有效"，不做精确校验。精确校验由后端负责。

**修正**: 前端只做基础检查（非空、非纯空白），后端做精确的 `DateTimeFormatter.ofPattern()` 校验并返回 400 + 具体错误信息。前端展示后端返回的错误。

### D3: 后端校验

**选择**: `PreferencesController` 在 `updateDateFormat` 前调用 `DateTimeFormatter.ofPattern(newDateFormat)`，catch `IllegalArgumentException` 返回 400。

**理由**: 与 timezone 校验（`ZoneId.of()` catch `DateTimeException`）保持一致模式。

### D4: JdbcResultValueNormalizer 防御性降级

**选择**: 在 `DateTimeFormatter.ofPattern(dateFormat)` 外加 try-catch，失败时降级到 `DateTimeFormatter.ISO_LOCAL_DATE_TIME`。

**理由**: 即使数据库中存储了无效格式（极端场景：手动 DB 编辑），查询结果不应整体失败。

## Risks

- Debounce 期间用户可能切换到其他页面，最后一次 mutation 未完成——可接受，staleTime 5min + optimistic update 已覆盖
