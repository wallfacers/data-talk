---
id: BUG-0053
title: bezel AI 生成 widget id 后缀过短被前端 Zod 拒，且错误提示无法定位字段
status: fixed
priority: P1
source: manual-report
modules: [dashboard, chat, opencode]
discovered: 2026-05-16
discoveredBy: human
testRunId: null
fixCommit: d99f008f
fixPlanRef: openspec/changes/bezel-widget-id-feedback/
duplicateOf: null
regression: false
---

## Summary

用户在 chat 让 AI "切换到 test_store，基于 test_store 库下的表创建一个大屏"，AI 走 bezel skill 输出 ```dashboard``` + ```dashboard-html``` 两个 fenced block，但 JSON 中第一个 widget id 写成 `kpi_w_gmv`（`_w_` 后缀仅 3 字符），违反 `dashboardSchema` 与后端 `dashboard-schema.json` 共同定义的 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 正则，前端 `DashboardBlock` 直接渲染成红色 "Dashboard 解析失败" 卡片。错误文本是 Zod 原始 issue.message 拼接（英文，且不告诉用户具体是哪个 widget 哪条规则违反），用户无从修起，且根本没机会点 "在工作台打开"。

是 BUG-0047 Notes 明确点过的遗留风险（"AI 输出仍偶有违反 schema … 建议 SKILL.md 增加更强约束 / 示例 emphasis"）的真实复现。

## Reproduction Steps

1. 启动 backend + frontend，浏览器打开 chat
2. 在 chat 里建立一个绑定到含 `test_store` 库的 MySQL 连接的会话
3. 输入 "切换到 test_store，基于 test_store 库下的表，给我创建一个大屏"
4. AI 命中 bezel skill，输出 ```dashboard``` + ```dashboard-html``` fenced 块
5. 观察 chat 里的 dashboard 区块：渲染成红色 "Dashboard 解析失败" 卡片，**没有 "在工作台打开" 按钮**
6. 展开错误详情：看到一长串 Zod 英文 issue 拼接文本（含 `Invalid string: must match pattern ^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`），但**不告诉用户**：
   - 是哪个 widget（widgets[0] 还是 widgets[5]）
   - 是哪条字段（id 还是 patternId）
   - 用人话说违反了什么（"`_w_` 后的后缀至少 4 个字符"）
7. 用户除了重新让 AI 生成（结果可能再犯）外没有其他出路；后端 promote / iframe 完全走不到

## Expected vs Actual

- **Expected**:
  - bezel SKILL.md 应该精确反映 schema 真实约束；现在写的是 `<lowercase>_w_<4-16 alphanumerics>`（既错了下限语义又漏了下划线允许），AI 受误导
  - 即使 AI 偶尔犯规，`DashboardBlock` 的错误卡应该用人话告诉用户：哪个 widget、哪个字段、违反什么规则、怎么改
- **Actual**:
  - SKILL.md 笔误（4-16 vs 真实 4-32）放任 AI 误学
  - Zod 错误直接 `issues.map(i => i.message).join('; ')` 输出，对中文用户和不熟 Zod regex 语法的人没有任何指导价值

## Environment

- Backend commit: 52342ebe（develop, 含 BUG-0047 + BUG-0048 + BUG-0049 + BUG-0050 + BUG-0051 + BUG-0052 修复）
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Tauri webview
- Data source: MySQL（test_store，连接 id `cb2d0259-edd5-4091-873b-6cfee09eec1a`）

## Evidence

- 用户原始 chat 产物（JSON+HTML）见会话上下文；关键证据：JSON 中 `"id": "kpi_w_gmv"`（widgets[0]），`gmv` 长度 3 < 4 触发正则失败
- 受影响代码：
  - `client/src/features/dashboard/schema.ts:37` — `id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9_]{4,32}$/)`
  - `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json:49` — 同正则
  - `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md:58` — `<lowercase>_w_<4-16 alphanumerics>`（**笔误，应为 4-32 且允许 `_`**）
  - `client/src/features/chat/components/markdown/dashboard-block.tsx:54` — `error: result.error.issues.map((i) => i.message).join('; ')`（错误信息直拼，无 path/翻译/操作指引）

## Root Cause

两层叠加：

1. **AI 训练面契约模糊**：bezel SKILL.md 第 58 行写 `<lowercase>_w_<4-16 alphanumerics>`，与真实 `client/src/features/dashboard/schema.ts` + `server/.../dashboard-schema.json` 同时持有的 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 不一致：
   - 上限错（16 vs 32）—— 不会触发拒收，但 AI 会无谓裁短；
   - 下限语义不显式（用 "4-16" 让 AI 误以为"约 4-16 都行"，没有明示 `≥4` 是硬约束）；
   - "alphanumerics" 漏了下划线（实际正则允许 `_`）；
   - 没有 "示例错例" 让 AI 直观感知 `kpi_w_gmv` / `kpi_w_a` 都会失败。

2. **前端反馈链路对人不友好**：`DashboardBlock.parseDashboard` 把 Zod `result.error.issues` 直接 `.map(i => i.message).join('; ')` 显示。Zod v4 的 `i.message` 通常是英文 `Invalid string: must match pattern …`，且**丢失了 `i.path`**（`["widgets", 0, "id"]`），用户既不知道是哪个 widget 哪个字段，也不知道实际违反的是 "`_w_` 后缀长度 ≥4" 这个语义规则。

下游链路（promote / iframe / SQL execution）全部正常，根因严格在"emit 时和 emit 后第一公里"。

## Fix

待 OpenSpec change 落地（预计名 `bezel-widget-id-feedback`）。范围：

- **S1（skill 合同清晰化）**：修 `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md`：
  - 第 58 行 `<lowercase>_w_<4-16 alphanumerics>` → 与真实 schema 一致：`<lowercase>+_w_<4-32 alphanumerics-or-underscores>`，配实际正则文本一并展示
  - "Pre-emit checklist" 新增第 6 条："对每个 widget.id 跑一遍 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`，后缀必须 ≥4 字符；不要使用 `kpi_w_gmv` / `kpi_w_a` 这类短后缀，改用 `kpi_w_gmv01` / `kpi_w_total_gmv`"
  - 在 "JSON skeleton — minimum required fields" 节给出 ✓/✗ widget id 例
- **S4（前端错误 UX 中文化 + 字段定位）**：改 `client/src/features/chat/components/markdown/dashboard-block.tsx` 的 `parseDashboard` / 错误展示：
  - 把 Zod issue 转成 `{path: "widgets[0].id", code: "regex_not_match", friendly: "widget id「kpi_w_gmv」不符合命名规则：…"}` 的结构化条目
  - 错误卡片折叠区按结构化条目逐项展示，便于定位
  - i18n 字符串走 `client/src/i18n/`，遵循 `client/DESIGN.md` 的 `status.danger / status.dangerSurface` 配色与 `text.muted` 注释配色

不动 widget id 正则本身（S5 已在 design.md 的 Alternatives Considered 中拒绝，理由：BUG-0047 已经放过一次，再放宽会让 `_w_` 后缀失去语义约束）。

## Verification

固定 commit：`d99f008f` (`fix(dashboard): humanize zod errors + sync bezel SKILL widget id rule (BUG-0053)`)

**自动化验证（已完成 — 等价 E2E）：**

1. `cd client && npx tsc --noEmit` — 零 type error
2. `cd client && npm test` — 1179 绿（baseline 1164 + 本次 +15 全部通过），13 红与 baseline 13 红集合完全相同（diff 为空），零回归
3. 关键 vitest case（`client/src/features/chat/components/markdown/__tests__/dashboard-block.test.tsx`）已直接断言用户在 chat 中看到的 DOM：
   - "shows widget id suffix-too-short message in zh-CN"：注入 `widgets[0].id = "kpi_w_gmv"`，断言错误卡含 path `widgets[0].id` + 字符串 `kpi_w_gmv` + friendly 匹配 `/3|≥4/`
   - "shows dashboard id format error in zh-CN"：注入 `id = "my-dashboard"`，断言条目含 `dash_`
   - "lists all issues when multiple violations co-occur"：theme + widget id 同时违规列出 ≥ 2 条
   - "falls back to raw zod message for unknown issue"：注入 `version = "1"` 走 fallback
   - "exposes raw JSON in expandable region without disabling text selection"：raw JSON 可展开 / 可选中
   - "renders summary 'N 项错误' in error card header"：顶部摘要走 `dashboard.errorSummary`
4. Humanizer 单测 9/9 全过：`client/src/features/dashboard/__tests__/zod-issue-humanizer.test.ts`
5. 后端 `cd server && mvn -pl data-talk-adapter compile -q` 通过；`target/classes/skills/bezel/SKILL.md` 含新 `4-32 alphanumerics-or-underscores` 描述（grep 1 命中），资源拷贝无误

**手动 UI 复跑（用户合并后可补）：**

- 启动 `mvn spring-boot:run -pl data-talk-adapter` + `npm run tauri dev`，在 chat 注入 reproduction 中的 buggy JSON，截图保存到 `docs/bugs/assets/BUG-0053/screenshot-01-after-fix.png`
- 重新让 AI "切换到 test_store，给我创建一个大屏"，观察 widget id 是否全部 ≥4 字符（SKILL.md prompt 已收紧 + 加 ✓/✗ 反例 + pre-emit checklist）

## Notes

- 与 BUG-0047 同源（同一组 schema 放宽决策的延伸）。BUG-0047 选择"放宽 schema"的方向，本次选择"加紧 prompt + 改善反馈"，方向相反但互补。
- 不需要 BUG 升级为 exec-plan：实施工作量小（2 文件 + 测试），通过 OpenSpec change 直接落地即可。
