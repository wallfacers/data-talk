## 1. Frontend: Debounce + 校验错误展示

- [x] 1.1 `date-format-selector.tsx` 自定义输入 onChange 改为 debounce（500ms），仅在输入停止后调用父级 `onChange`
- [x] 1.2 `date-format-selector.tsx` 接收 `error?: string` prop，在自定义输入框下方展示错误提示（红色文字）
- [x] 1.3 `general-panel.tsx` 从 mutation error 中提取后端校验错误，传递给 `DateFormatSelector` 的 `error` prop
- [x] 1.4 添加 i18n key: `general.dateFormatInvalid`（zh: "无效的日期格式", en: "Invalid date format"）
- [x] 1.5 `npx tsc --noEmit` 验证类型无误

## 2. Backend: 格式校验

- [x] 2.1 `PreferencesController.updatePreferences()` 对 `dateFormat` 增加 `DateTimeFormatter.ofPattern()` 校验，catch `IllegalArgumentException` 返回 400 + 错误信息
- [x] 2.2 `PreferencesControllerTest` 新增测试：无效格式返回 400；有效格式（含 `SSS`）正常保存
- [x] 2.3 `mvn compile -pl data-talk-adapter` 验证编译通过

## 3. Backend: 防御性降级

- [x] 3.1 `JdbcResultValueNormalizer.normalize()` 对 `DateTimeFormatter.ofPattern(dateFormat)` 加 try-catch，失败时降级到 `DateTimeFormatter.ISO_LOCAL_DATE_TIME`
- [x] 3.2 `JdbcResultValueNormalizerTest` 新增测试：非法格式降级到 ISO 格式而非抛异常
- [x] 3.3 `mvn compile -pl data-talk-application` 验证编译通过

## 4. Consolidated verification

- [x] 4.1 `cd server && mvn compile -q` — 全量编译通过
- [x] 4.2 `cd server && mvn test -pl data-talk-adapter,data-talk-application -q` — 受影响模块测试通过
- [x] 4.3 `cd client && npx tsc --noEmit` — 前端类型检查通过
- [x] 4.4 `cd client && npx vitest run` — 前端测试通过
