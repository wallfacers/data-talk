## Why

用户在设置页 Custom 模式输入日期格式（如 `yyyy-MM-dd HH:mm:ss.SSS`）后，格式未生效。根因：`DateFormatSelector` 的自定义输入框在每次按键时都触发 `onChange` → `prefsMutation.mutate()`，连续 20+ 次 HTTP PUT 请求导致竞态条件——后写入的中间值可能覆盖最终值。同时，前后端均无格式合法性校验：用户输入无效模式（如 `abc`）会被静默保存，直到执行 SQL 时在 `JdbcResultValueNormalizer` 中抛出未捕获的 `IllegalArgumentException`，导致查询结果整体失败。

## What Changes

- `DateFormatSelector` 自定义输入改为 debounce（500ms），避免按键级写入
- 后端 `PreferencesController` 对 `dateFormat` 做格式校验：尝试 `DateTimeFormatter.ofPattern()`，非法模式返回 400 + 错误提示
- 前端 `DateFormatSelector` 在提交前做客户端预校验，非法格式在输入框下方展示错误提示
- `JdbcResultValueNormalizer.normalize()` 对 `DateTimeFormatter.ofPattern()` 加 try-catch，防御性降级到默认格式

## Capabilities

### Modified Capabilities
- `timezone-settings-ui`: 日期格式自定义模式增加 debounce 和客户端/服务端双重校验

## Impact

- **前端**: `date-format-selector.tsx` 增加 debounce 逻辑和校验错误提示
- **后端 adapter**: `PreferencesController` 增加格式校验（类比 timezone 校验）
- **后端 application**: `JdbcResultValueNormalizer` 增加防御性 catch
- **测试**: `PreferencesControllerTest` 增加无效格式测试；`JdbcResultValueNormalizerTest` 增加非法格式降级测试

## Risks

- docs/bugs/index.md 中无相关 open BUG
