---
id: BUG-0047
title: bezel 大屏 skill 从 AI 端不可触达，且 chat 中 DashboardBlock 渲染抛 i18n 错误
status: fixed
priority: P1
source: e2e-playwright
modules: [chat, dashboard, stage, opencode-agents]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

用户在 chat 里请求"做一个电商运营大屏"，AI 不走 bezel skill 也不输出 dashboard fenced block，而是用 OpenCode 默认 `write` 工具把 HTML 落到 `~/.data-talk/opencode/ecommerce-dashboard.html`。即使绕过 AI 在 chat 里手动塞合规 dashboard JSON，`DashboardBlock` 也因 React `createRoot` 脱离 `I18nProvider` 上下文而抛 `useI18n must be used within I18nProvider`，整个交付链路断开。

## Reproduction Steps

1. 启动 backend + frontend，浏览器打开 `http://localhost:1420/`
2. chat 输入"做一个电商运营大屏"并提交
3. 观察 AI 工具调用：未触发 bezel skill 相关参考文件 read，最终用 `write` 工具把 HTML 写到磁盘
4. 在工作台中查找该大屏 → 看不见任何 dashboard tab
5. 即便手工构造合规 dashboard fenced block，chat 中 `DashboardBlock` 也直接报错（控制台 `useI18n must be used within I18nProvider`），preview 卡片不出现 → 用户无路径把大屏带进 stage

## Expected vs Actual

- **Expected**: 用户说"做一个大屏" → AI 路由到 bezel skill → chat 里输出 `dashboard` fenced block → `DashboardBlock` 预览卡片出现 → 用户点"在工作台打开"→ stage 显示 dashboard tab + iframe
- **Actual**:
  - **路由**：AI 命中 `charts-and-dashboards` 硬路由，bezel 不在 Trigger Gate，永远不被激活
  - **契约**：bezel SKILL.md 没有 Delivery Contract 章节，AI 误以为产物是文件，遂调用 `write`
  - **渲染**：`markdown.tsx` 用 `createRoot(mountPoint).render(<DashboardBlock />)` 创建独立 React root，未包裹 `I18nProvider`，组件内 `useI18n()` 直接抛错（`ChartBlock` 同样受影响）

## Environment

- Backend commit: 3f67eb44（develop, 含本次 schema 放宽）
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium via playwright-cli
- Data source: N/A（AI 流程不触达 DB）

## Evidence

- 控制台错误片段：
  ```
  Error: useI18n must be used within I18nProvider
      at useI18n (use-i18n.ts:6:15)
      at DashboardBlock (dashboard-block.tsx:119:19)
  ```
- 修复前 AI 自述行为："文件位置：/home/wushengzhou/.data-talk/opencode/ecommerce-dashboard.html"

## Root Cause

三层叠加：

1. **AGENTS.md Trigger Gate** 仅把 `dashboard / KPI / monitoring screen` 一锅端硬路由到 `charts-and-dashboards`，`bezel` 不在硬路由中。`charts-and-dashboards.SKILL.md` 第 18 行虽有"premium 大屏让给 bezel"但只在自身被加载后生效，已经太晚。
2. **bezel SKILL.md** 没有"Delivery contract"章节，"Two artifacts per request: dashboard.json + dashboard.html" 措辞把 AI 引导成产出文件，且没明确禁用 `write` / chat 输出的 fenced block 形态。
3. **markdown.tsx** 第 580 / 537 行 `createRoot(mountPoint)` 创建脱离主 React 树的独立 root，未包裹 `I18nProvider`，所有调 `useI18n` 的 chart/dashboard 子组件都会崩。

## Fix

提交于 develop branch：

1. `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
   - Trigger Gate 第 99 行拆为 charts-and-dashboards / bezel 两行，bezel 触发关键词覆盖"大屏 / 监控大屏 / 驾驶舱 / 数据墙 / data wall / TV-wall / control center / ops cockpit / industry-specific visualization"
   - Identity & Hard Constraints 新增条目：dashboard 产物必须以 `dashboard` fenced block 在 chat 中交付，禁用 `write` 工具任何文件落盘
2. `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md`
   - 重写 "What it produces" 区分"chat 交付物（JSON）"与"内部编译产物（HTML）"
   - 新增 "Delivery contract — MUST follow" 章节：列正确通道、禁止通道、最小 JSON skeleton、emit 后的管线说明
   - Key invariants 顶部新增 "Delivery is a fenced `dashboard` block in chat — never a file write"
3. `client/src/features/chat/components/markdown/markdown.tsx`
   - import `I18nProvider`
   - ChartBlock / DashboardBlock 的 `entry.root.render(...)` 均用 `<I18nProvider>...</I18nProvider>` 包裹
4. `client/src/features/dashboard/schema.ts` + `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json`
   - widget id 正则 `^[a-z]+_w_[a-zA-Z0-9]{4,16}$` 放宽为 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`（允许第三段含下划线、长度上限放宽到 32），缓解 AI 命名习惯（`kpi_w_gmv_total`）与 schema 的冲突

## Verification

playwright-cli E2E 回归（2026-05-15）：

1. 重启后端（mvn install application + spring-boot:run）+ vite HMR 前端
2. chat 输入"做一个电商运营大屏"
3. 观察 AI 工具链：连续 `skill` / `read` 拉取 bezel skill `patterns-catalog.md` / `industries/*` / `compile-rules.md` 等参考文件 → bezel 路径成功激活
4. AI 最终输出 ```` ```dashboard ```` fenced block，body 是 schemaVersion 2 + `renderer:"bezel"` + `theme:"industry-ecommerce"` 的 dashboard JSON（解码自 mount div `data-dashboard-json-b64`）
5. `DashboardBlock` 不再抛 i18n 错误，render 出 `dashboard-error` 或 `dashboard-preview` 状态（取决于 widget id 是否合规）
6. 用合规 widget id 做最小修补后 `POST /api/dashboards/promote` 返回 201，`PUT /api/stage/tabs/{tabId}` 持久化 dashboard 类型 stage tab，reload 后工作台显示 "电商运营实时监控中心" tab（截图：`tmp/dashboard-final.png` / `tmp/dashboard-maximized.png`）

## Notes

- iframe 内部目前显示 `iframe-shell.tsx:46-50` 的 v1 missing 占位 "v1 dashboard — 在 chat 中说『重新生成视觉』生成新版 HTML"。这是 v1 promote API 不传 HTML 的独立链路缺口，见 BUG-0048。
- AI 输出的 dashboard JSON 仍偶有违反 schema（widget id 含下划线、`paramRefs` 缺失）。已通过放宽 schema 缓解；进一步收敛建议在 bezel SKILL.md 里增加更强约束 / 示例 emphasis 或在 promote 端补一层 sanitize（如自动注入空 `paramRefs`）。
