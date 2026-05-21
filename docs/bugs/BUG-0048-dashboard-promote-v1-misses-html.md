---
id: BUG-0048
title: v1 dashboard promote 链路不透传 HTML，stage iframe 永远显示 missing 占位
status: fixed
priority: P1
source: e2e-playwright
modules: [dashboard, stage]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

bezel skill 的产物本应是 dashboard.json + dashboard.html 一起 promote 落库，stage 通过 `GET /api/dashboards/{id}/html` 拿 HTML 渲染 iframe。当前 `client/src/features/dashboard/services/dashboard-api.ts:13` 的 `promoteDashboard` 只把 dashboard JSON 传给 `POST /api/dashboards/promote`，没有透传 HTML；`DashboardBlock.promoteDashboard()`(chat 端) 同样不接收 HTML 字段。结果 stage 打开 dashboard tab 后 `DashboardIframeShell` 永远命中 `status='missing'`，UI 显示占位 "v1 dashboard — 在 chat 中说『重新生成视觉』生成新版 HTML"，用户看不到任何图表。

## Reproduction Steps

1. 任意路径触发 dashboard 进 stage（fenced block "在工作台打开"按钮、或 `/api/dashboards/promote` + stage tab persist）
2. stage 显示 "电商运营实时监控中心" tab，激活后 iframe 区域显示 "v1 dashboard — 在 chat 中说『重新生成视觉』生成新版 HTML" 占位
3. 不论 AI 在 chat 中发再多次 dashboard JSON，HTML 字段都不会进入后端，占位永远存在

## Expected vs Actual

- **Expected**: AI 按 bezel `compile-rules.md` 把 JSON 编译为自包含 HTML，跟 JSON 一起 promote；stage iframe 通过 srcDoc 加载 HTML，能看到 ECharts 渲染的大屏（KPI / 趋势 / 漏斗 / 环形图 / 热力图等）
- **Actual**: HTML 从未离开 AI 端；后端 `dashboard_html` 列空；stage iframe 永远显示 v1 missing 占位

## Environment

- Backend commit: 3f67eb44
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium via playwright-cli

## Evidence

- `client/src/features/dashboard/services/dashboard-api.ts:13-21` `promoteDashboard` 只传 `{ dashboard }`，无 `html` 字段
- `client/src/features/chat/components/markdown/dashboard-block.tsx:52-64` `promoteDashboard()` 同样不接 HTML
- `server/data-talk-adapter/.../DashboardController.java:32-62` `/promote` 路由能接收 `request.html()`，但前端从不传
- 截图 `tmp/dashboard-maximized.png`：iframe 区域居中显示 "v1 dashboard — 在 chat 中说『重新生成视觉』生成新版 HTML"
- 触发链路：`iframe-shell.tsx:46-50` 在 `fetchDashboardHtml` 返回 null（后端 `loadHtml(id).isEmpty()`）时进入 missing 分支

## Root Cause

bezel skill 的端到端契约设计上是 "JSON + HTML 一起 promote"，但前端从未实装 HTML 透传：
- 没有把 dashboard fenced block 之外的 ```` ```dashboard-html ```` 旁路代码块或 metadata 通道接进来
- `promoteDashboard` 服务函数签名只接 `payload: unknown`，没有可选 HTML
- 后端虽然支持 `request.html()` 但被现状闲置

## Fix

走"双 fenced block"链路，端到端打通 AI → markdown → store → backend → iframe：

1. **bezel SKILL.md** 的 Delivery contract 改为强制输出**两个**相邻 fenced block：先 ```` ```dashboard ```` (JSON)，紧跟 ```` ```dashboard-html ```` (按 `compile-rules.md` + `assets/templates/NN-<industry>.html` 编译出的 self-contained HTML)。文档里明确"只发 JSON 会看到 v1 missing 占位"。
2. **`markdown.tsx`** `decorateDashboardBlocks` 一次扫两种 fence：JSON block 创建 mount div，紧跟的 HTML block 以 base64 写入同一 mount 的 `data-dashboard-html-b64` 属性，并 `removeChild` 原 `<pre>` 避免在 chat 流显示原始 HTML。
3. **`DashboardBlock`** props 增加 `html?: string`，挂载时通过 `entry.root.render(<I18nProvider>...)` 透传。
4. **`promoteDashboard(dashboard, html)`** 改为先 await `promoteDashboardApi(dashboard, html)` 拿到 server 重分配的 `{id, version}`，然后用**最终 id** hydrate dashboard-tabs-store 并 openTab。避免之前用 client 端旧 id `hydrateTab` → iframe-shell `fetchDashboardHtml(clientId)` 404 → 又落回 missing 占位的链路 bug。
5. **`dashboard-api.ts`** `promoteDashboard` 签名扩为 `(payload, html?)`，POST body 仅在 html 非空时附 `html` 字段（后端 `DashboardController.promote` 早已支持 `request.html()`）。

## Verification

playwright-cli E2E（2026-05-15，截图 `tmp/dashboard-live.png` / `tmp/dashboard-fullscreen.png`）：

1. 重启 backend（mvn install + spring-boot:run）+ vite HMR
2. chat 输入"做一个 ecommerce 大屏，按 bezel skill 的 Delivery contract，输出 dashboard 和 dashboard-html 两个 fenced block ... HTML 部分基于 assets/templates/02-ecommerce.html"
3. AI 用 ~7 分钟生成 11 widget dashboard + 30KB HTML，chat 中 mount div 同时持有 `data-dashboard-json-b64` (25KB) + `data-dashboard-html-b64` (30KB)
4. 点击 preview 卡片"Open to workbench" → `promoteDashboardApi(dashboard, html)` → server 分配 `dash_at7l2yfp` 并落库 JSON + HTML
5. `GET /api/dashboards/dash_at7l2yfp/html` → 200, 23524 字节
6. stage tab "电商运营实时监控中心" 打开，`DashboardIframeShell` 渲染 `<iframe sandbox="allow-scripts" srcDoc=...>`, `data-status="loading" → "ready"`
7. 截图证据：黑色暗色主题大屏，4 个 KPI 卡片 + 红/橙/黄柱状图 + 网格 widget 布局，**iframe 内 ECharts 实际渲染**

## Notes

- iframe 内中文字符显示乱码（widget 标题、KPI 描述）。已确认 mount 的 `data-dashboard-html-b64` 编解码走 `encodeUtf8Base64` / `decodeUtf8Base64`（兼容多字节），但 AI 生成的 HTML 内嵌字符可能没经过严格 UTF-8 序列化 — 可能是 OpenCode SSE 传输或 AI 端模板替换时的字符宽度问题。本 BUG 范围内 iframe 已成功加载真实 HTML 不再 missing 占位，**编码乱码作为独立 cosmetic 问题留待后续**（建议新开 BUG-0049 收口）。
- AI 一次生成 dashboard.html 仍较慢（本次 ~407s）。后续若想缩短可考虑：a) 在后端实现 JSON→HTML 编译器把 HTML 生成从 AI 端迁到 server；b) 把 polling scheduler IIFE 抽到 server 静态资源由 promote 时拼接。但这都是 v2 优化，与本 BUG 修复正交。

## Notes

本 BUG 在 [BUG-0047](BUG-0047-bezel-dashboard-unreachable-from-ai-and-block-render-fails.md) 的 E2E 验证过程中被暴露，但属于独立的链路缺口，不在 BUG-0047 的修复范围。
