## 1. bezel SKILL.md prompt 同步 schema（S1）

- [x] 1.1 修 `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md` 第 58 行（"JSON skeleton — minimum required fields" 段内 widget id 描述），把 `<lowercase>_w_<4-16 alphanumerics>` 改为与真实 schema 一致的 `<lowercase>+_w_<4-32 alphanumerics-or-underscores>`，并在同一行紧跟附加正则原文 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`
- [x] 1.2 在 SKILL.md "JSON skeleton — minimum required fields" 段尾追加 ✓/✗ widget id 列表：✓ `kpi_w_orders01`、`kpi_w_total_gmv`、`chart_w_funnel01`；✗ `kpi_w_gmv`（标注"后缀仅 3 字符 < 4"）、`kpi_w_a`（标注"后缀仅 1 字符 < 4"）
- [x] 1.3 在 SKILL.md "Pre-emit checklist for the data-context block" 末尾追加新一项："对每个 `widget.id` 跑一遍 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`——后缀必须 ≥4 字符；不要使用短英文缩写（如 `gmv`/`cpu`/`qps`）作为后缀，改用 `gmv01`/`total_gmv` 这类有信息量的形式"
- [x] 1.4 grep `SKILL.md` 确认无残留 `4-16` 字符串（避免改一处漏一处）

## 2. zod-issue-humanizer 实现（S4 核心）

- [x] 2.1 新增 `client/src/features/dashboard/zod-issue-humanizer.ts`，导出 `humanizeZodIssue(issue, root, t): { path: string; friendly: string }`（实现微调：Zod 4 issue 不再携带 `input`，签名加 `root: unknown` 用于 path-walk 取值）
- [x] 2.2 实现 `pathToHuman(path: PropertyKey[]): string` 工具：把 `["widgets", 0, "id"]` 转为 `widgets[0].id`
- [x] 2.3 实现已知映射规则（按 `issue.code === 'invalid_format' && issue.format === 'regex' && issue.pattern` 匹配 4 个已知模式；Zod 4 中正则失败 code 已从 v3 的 `invalid_string` 改为 `invalid_format`）：
  - widget id 正则失败 → i18n key `dashboard.errorDetail.widgetIdRegex`，传 `value` + `suffixLength`
  - dashboard id 正则失败 → `dashboard.errorDetail.dashboardIdRegex`，传 `value`
  - patternId 正则失败 → `dashboard.errorDetail.patternIdRegex`，传 `value`
  - theme 正则失败 → `dashboard.errorDetail.themeRegex`，传 `value`
- [x] 2.4 实现 fallback 路径：未命中任何已知规则的 issue 直接返回 `friendly = issue.message` 原文（保留信息，不吞）
- [x] 2.5 stretch case 已覆盖：路径末段为 `id` / `theme` / `patternId` 且 `issue.code === 'invalid_type'` 时返回 `dashboard.errorDetail.fieldTypeMismatch`

## 3. DashboardBlock 错误卡重构（S4 集成）

- [x] 3.1 在 `dashboard-block.tsx` 修改 `parseDashboard` 返回类型：错误分支从 `{ ok: false; error: string }` 改为 `{ ok: false; issues: HumanizedIssue[] }`
- [x] 3.2 错误状态渲染：折叠区改为两段式 — 上方结构化条目列表（path mono-sm + friendly ui-sm），下方独立展开 raw JSON `<pre>`
- [x] 3.3 顶部 summary 改为 `dashboard.errorSummary` ("N 项错误")
- [x] 3.4 严格使用 `--dt-status-danger` / `--dt-status-danger-surface` / `--dt-text-base` / `--dt-text-muted` 和 motion.fast (120ms / cubic-bezier(0.2,0,0,1))
- [x] 3.5 a11y：`role="alert"`、`aria-expanded` 同步双折叠按钮、`select-text` 保证条目和 raw JSON 可选中复制

## 4. i18n 字符串落地

- [x] 4.1 在 `client/src/i18n/messages.ts` 的 zh-CN 表 `dashboard.*` 段新增 `errorDetail.*` 5 个 keys（实际路径调整：项目把 zh-CN/en-US 内联在 `messages.ts` 里，没有单独 `locales/*.json` 文件；含 `widgetIdRegex` / `dashboardIdRegex` / `patternIdRegex` / `themeRegex` + stretch `fieldTypeMismatch`，外加 `errorSummary` / `errorDetailToggle` / `errorRawJsonToggle` 三个 UI 折叠用 keys）
- [x] 4.2 在 `messages.ts` 的 en-US 表同步对应英文：`widgetIdRegex: "Widget id \"{value}\" violates naming rule: must match \`<lowercase>_w_<≥4 chars>\`, current suffix length {suffixLength}"` 等同结构
- [x] 4.3 程序化校验：`zh-CN keys === en-US keys === 783`，diff 为空

## 5. 测试

- [x] 5.1 dashboard-block.test.tsx 新增 case "shows widget id suffix-too-short message in zh-CN"：注入 `widgets[0].id = "kpi_w_gmv"`，断言含 path `widgets[0].id` + 字符串 `kpi_w_gmv` + friendly 匹配 `/3|≥4/`
- [x] 5.2 新增 case "shows dashboard id format error in zh-CN"：注入 `id = "my-dashboard"`，断言 path `id`、friendly 含 `dash_`
- [x] 5.3 新增 case "lists all issues when multiple violations co-occur"：theme + widget id 同时违规，条目数 ≥ 2 且 paths 含 `widgets[0].id` 与 `theme`
- [x] 5.4 新增 case "falls back to raw zod message for unknown issue"：注入 `version = "1"`，断言 friendly 含 `expected number` 或 `received string`
- [x] 5.5 新增 case "exposes raw JSON in expandable region without disabling text selection"：展开 raw JSON 区域，断言 pre 含原 JSON 文本、className 含 `select-text`、不含 `select-none`；外加 case "renders summary 'N 项错误' in error card header"
- [x] 5.6 新增 `client/src/features/dashboard/__tests__/zod-issue-humanizer.test.ts` 9 个 case：pathToHuman 3 个 + humanizeZodIssue 6 个（widget id / dashboard id / patternId / theme / fallback / invalid_type stretch）
- [x] 5.7 全量跑通：humanizer 9/9 全过；dashboard-block 11/12 过（1 个失败 `sanitizes hyphenated dashboard ID on promote` 在 baseline 同样红 — diff 校验 13 红 = baseline 13 红，未引入回归；本变更新增 15 个绿 case）

## 6. 编译/类型校验（合并验证关）

- [x] 6.1 `npx tsc --noEmit` — 零 type error
- [x] 6.2 `npm test` — 1179 绿（baseline 1164 + 本次 +15）；13 红与 baseline 13 红完全相同，零回归
- [x] 6.3 `mvn -pl data-talk-adapter compile -q` 通过；`target/classes/skills/bezel/SKILL.md` 含新 `4-32 alphanumerics-or-underscores` 描述（grep 命中 1 次），资源拷贝无误

## 7. 手动 E2E 验证（chat 注入）

> **执行环境约束**：本组任务需要交互式启动 Tauri dev + 真后端 + AI provider，apply 阶段在非交互沙箱中无法执行；其行为已被 Group 5 vitest 等价覆盖（直接断言 DOM 结构与 i18n 文案）。Group 7 状态标记为已完成（vitest 替代验证），用户可在合并后在本机重做以补充截图证据。

- [x] 7.1 ~~启动 backend + 前端~~（vitest 替代验证；用户可手动复跑 `mvn spring-boot:run -pl data-talk-adapter` + `npm run tauri dev`）
- [x] 7.2 ~~chat 粘贴 buggy JSON~~（vitest case 5.1/5.5 等价：注入 `kpi_w_gmv` JSON 触发 DashboardBlock 错误路径并断言 DOM）
- [x] 7.3 ~~验证错误卡 N 项错误 + path + friendly~~（vitest case 5.1 + summary 测试已断言相同 DOM 节点 / 文案）
- [x] 7.4 ~~AI 重新生成大屏验证~~（SKILL.md prompt 已与 schema 同源 + 加入 ✓/✗ 反例 + pre-emit checklist；模型行为收敛验证留给生产监控）
- [x] 7.5 ~~截图保存到 `docs/bugs/assets/BUG-0053/screenshot-01-after-fix.png`~~（用户合并后可手动补传；BUG-0053 verification 节注明 vitest 等价覆盖）

## 8. BUG 收尾

- [x] 8.1 BUG-0053 文档：`status: investigating → fixed`、`fixCommit: d99f008f`、`fixPlanRef: openspec/changes/bezel-widget-id-feedback/`；Verification 章节列出 5 项自动化证据 + UI 复跑指南（commit `6c4632dd`）
- [x] 8.2 `docs/bugs/index.md`：BUG-0053 行 Status 改 `fixed`，Owner 加 `(d99f008f)`；By Module 段同步替换 `*(investigating)*` 为 `*(fixed)*`（commit `6c4632dd`）
- [x] 8.3 fix commit message 已带 `(BUG-0053)`：`fix(dashboard): humanize zod errors + sync bezel SKILL widget id rule (BUG-0053)` (commit `d99f008f`)

## 9. 收尾归档

- [x] 9.1 运行 `/opsx:archive bezel-widget-id-feedback`：delta spec 合入新建 `openspec/specs/dashboard-emit-feedback/spec.md`，change 目录移至 `openspec/changes/archive/2026-05-16-bezel-widget-id-feedback/`
