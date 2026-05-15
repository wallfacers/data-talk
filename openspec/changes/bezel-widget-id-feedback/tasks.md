## 1. bezel SKILL.md prompt 同步 schema（S1）

- [ ] 1.1 修 `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md` 第 58 行（"JSON skeleton — minimum required fields" 段内 widget id 描述），把 `<lowercase>_w_<4-16 alphanumerics>` 改为与真实 schema 一致的 `<lowercase>+_w_<4-32 alphanumerics-or-underscores>`，并在同一行紧跟附加正则原文 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`
- [ ] 1.2 在 SKILL.md "JSON skeleton — minimum required fields" 段尾追加 ✓/✗ widget id 列表：✓ `kpi_w_orders01`、`kpi_w_total_gmv`、`chart_w_funnel01`；✗ `kpi_w_gmv`（标注"后缀仅 3 字符 < 4"）、`kpi_w_a`（标注"后缀仅 1 字符 < 4"）
- [ ] 1.3 在 SKILL.md "Pre-emit checklist for the data-context block" 末尾追加新一项："对每个 `widget.id` 跑一遍 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`——后缀必须 ≥4 字符；不要使用短英文缩写（如 `gmv`/`cpu`/`qps`）作为后缀，改用 `gmv01`/`total_gmv` 这类有信息量的形式"
- [ ] 1.4 grep `SKILL.md` 确认无残留 `4-16` 字符串（避免改一处漏一处）

## 2. zod-issue-humanizer 实现（S4 核心）

- [ ] 2.1 新增 `client/src/features/dashboard/zod-issue-humanizer.ts`，导出 `humanizeZodIssue(issue: z.ZodIssue, t: TranslationFn): { path: string; friendly: string }`
- [ ] 2.2 实现 `pathToHuman(path: PropertyKey[]): string` 工具：把 `["widgets", 0, "id"]` 转为 `widgets[0].id`
- [ ] 2.3 实现已知映射规则（按 `path 末段 + issue.code + issue.validation/regex` 匹配）：
  - widget id 正则失败 → i18n key `dashboard.errorDetail.widgetIdRegex`，传 `value` + `suffixLength`
  - dashboard id 正则失败 → `dashboard.errorDetail.dashboardIdRegex`，传 `value`
  - patternId 正则失败 → `dashboard.errorDetail.patternIdRegex`，传 `value`
  - theme 正则失败 → `dashboard.errorDetail.themeRegex`，传 `value`
- [ ] 2.4 实现 fallback 路径：未命中任何已知规则的 issue 直接返回 `friendly = issue.message` 原文（保留信息，不吞）
- [ ] 2.5 顺手覆盖一个 stretch case：路径含 `id` / `theme` / `patternId` 但 issue.code 是 `invalid_type`（字段类型错或缺失）时给出对应"字段缺失或类型错"的中文 friendly（不强制——design.md Open Questions 列为可选）

## 3. DashboardBlock 错误卡重构（S4 集成）

- [ ] 3.1 在 `dashboard-block.tsx` 修改 `parseDashboard` 返回类型：错误分支从 `{ ok: false; error: string }` 改为 `{ ok: false; issues: Array<{ path: string; friendly: string }> }`
- [ ] 3.2 错误状态渲染：把现有"折叠区单 `<pre>{json}`"改为两段式 — 第一段是结构化条目列表（每条一行：path 用 mono-sm 字体，friendly 用 ui-sm 字体），第二段是可独立展开的 raw JSON `<pre>` 区域
- [ ] 3.3 顶部 errMsg（折叠头部那一行）从 `error: string` 拼接改为：用 `issues.length` 显示"N 项错误"汇总（具体内容在折叠区）
- [ ] 3.4 严格使用 client/DESIGN.md token：`status.danger` / `status.dangerSurface` / `text.base` / `text.muted` / `typography.mono-sm` / `typography.ui-sm` / `motion.fast`；不引入 raw 颜色字面量
- [ ] 3.5 保留 a11y：`role="alert"`、键盘可聚焦折叠按钮、内容可选中可复制

## 4. i18n 字符串落地

- [ ] 4.1 在 `client/src/i18n/locales/zh-CN.json` 的 `dashboard` 命名空间下新增 `errorDetail` 子对象，含 4 个 keys：`widgetIdRegex` / `dashboardIdRegex` / `patternIdRegex` / `themeRegex`，文案见 design.md D4
- [ ] 4.2 在 `client/src/i18n/locales/en-US.json` 同步对应英文：`widgetIdRegex: "Widget id \"{value}\" violates naming rule: must match `<lowercase>_w_<≥4 chars>`, current suffix length {suffixLength}"` 等同结构
- [ ] 4.3 grep `client/src/i18n/locales/` 确认两份 locale 文件 keys 数与缩进对齐

## 5. 测试

- [ ] 5.1 在 `client/src/features/chat/components/markdown/__tests__/dashboard-block.test.tsx` 新增 case："widget id 后缀过短显示中文人话" — 注入含 `widgets[0].id = "kpi_w_gmv"` 的 JSON，断言错误卡含 path `widgets[0].id` 与字符串 `kpi_w_gmv`、且 friendly 含 "3" 或 "≥4"
- [ ] 5.2 新增 case："dashboard.id 格式错显示中文人话" — 注入 `id = "my-dashboard"`，断言条目 path 为 `id` 或 `dashboard.id`、friendly 含 `dash_`
- [ ] 5.3 新增 case："多 issue 同时存在全部展示" — 注入同时违反 widget id 与 theme 的 JSON，断言条目数 ≥ 2 且 path 与 Zod 真实 path 对齐
- [ ] 5.4 新增 case："未知 Zod issue 回退原文" — 注入 `version = "1"`（字符串而非 number）触发 `invalid_type`，断言 friendly 等于（或包含）issue.message 原文
- [ ] 5.5 新增 case："原始 JSON 仍可复制" — 注入失败 JSON，渲染后展开 raw JSON 区域，断言区域内含原始 JSON 字符串且 `<pre>` 元素无 `user-select: none`
- [ ] 5.6 新增 case：humanizer 单测（`zod-issue-humanizer.test.ts`，与组件解耦），覆盖 4 类已知映射 + 1 类 fallback
- [ ] 5.7 跑 `cd client && npm test -- dashboard-block` + `npm test -- zod-issue-humanizer`，全部通过；如有旧 case 因结构变更被破坏，同步修

## 6. 编译/类型校验（合并验证关）

- [ ] 6.1 `cd client && npx tsc --noEmit` — 零 type error
- [ ] 6.2 `cd client && npm test` — 全量 vitest 通过（含本次新增 case + 既有 dashboard-block / schema 相关测试）
- [ ] 6.3 后端无代码改动，只动 SKILL.md 资源；保险起见跑 `cd server && mvn -pl data-talk-adapter compile -q` 确认资源拷贝无误（SKILL.md 在 `src/main/resources/skills/bezel/`）

## 7. 手动 E2E 验证（chat 注入）

- [ ] 7.1 启动 backend (`mvn spring-boot:run -pl data-talk-adapter`) + 前端 (`npm run tauri dev`)
- [ ] 7.2 在任一 chat session 里粘贴 BUG-0053 reproduction 中的原始 buggy ```dashboard``` JSON（含 `kpi_w_gmv`）
- [ ] 7.3 验证：错误卡折叠头部显示 "N 项错误"，展开后第一条 path 为 `widgets[0].id`、friendly 含 `kpi_w_gmv` 与 "≥4"；展开 "原始 JSON" 区域可复制
- [ ] 7.4 让 AI 重新生成同主题大屏（"切换到 test_store，给我创建一个大屏"），观察 widget id 是否全部 ≥4 字符（应满足）
- [ ] 7.5 截图新错误卡保存到 `docs/bugs/assets/BUG-0053/screenshot-01-after-fix.png`（PNG ≤500KB）

## 8. BUG 收尾

- [ ] 8.1 编辑 `docs/bugs/BUG-0053-bezel-ai-widget-id-too-short-and-zod-error-unhelpful.md`：`status: investigating` → `status: fixed`，回填 `fixCommit` 短 SHA，Verification 章节填 task 7 截图证据路径
- [ ] 8.2 编辑 `docs/bugs/index.md`：BUG-0053 行的 Status 列从 `investigating` 改为 `fixed`，Owner 列加上短 SHA
- [ ] 8.3 commit 时 message 关联 BUG-0053（如 `fix(dashboard): humanize zod errors + sync bezel SKILL widget id rule (BUG-0053)`）

## 9. 收尾归档

- [ ] 9.1 全部任务完成后，运行 `/opsx:archive bezel-widget-id-feedback`，让 delta spec 合入 `openspec/specs/dashboard-emit-feedback/spec.md`，change 目录移至 `openspec/changes/archive/YYYY-MM-DD-bezel-widget-id-feedback/`
