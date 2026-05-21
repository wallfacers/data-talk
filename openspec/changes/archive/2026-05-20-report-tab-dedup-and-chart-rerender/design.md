## Context

Report viewer 是只读 iframe 嵌入，加载服务端生成的自包含 HTML（含 ECharts 图表）。当前三个组件（`report-library-tab`、`report-card`、`report-viewer-tab`）缺乏 tab 去重和 iframe 重载保障，导致用户可见的两种缺陷。

Stage store 已有去重模式：`openArtifactPreviewTab()` 先查 `tabs.find()` 再决定 `focusTab` 还是 `openTab`。本 change 复用该模式。

## Goals / Non-Goals

**Goals:**
- 同一报告只能有一个打开的 tab；重复点击聚焦已有 tab
- 关闭后重新打开，iframe 内 ECharts 图表完整渲染
- chart 容器在 CSS 未就绪时仍有 fallback 高度

**Non-Goals:**
- 不修改 stage store 核心 `openTab`/`focusTab`/`trashTab` 逻辑
- 不引入新的 tab 类型或 stage 机制
- 不改动 `ledger.css` 的 chart 高度规则（保留 CSS 作为主尺寸源）

## Decisions

### D1: 去重放在调用侧，不放进 stage store

**选择**：在 `report-library-tab.tsx` 和 `report-card.tsx` 的 `onOpen` 中直接查 `tabs`。

**理由**：与 `openArtifactPreviewTab` 模式一致；不需要新增 store 方法；report viewer 的匹配条件简单（只需匹配 tabId 前缀 `report-viewer:` + reportId）。

**替代方案**：在 `openTab` 内部加通用去重——影响所有 tab 类型，风险大且不必要。

### D2: iframe src 加 cache-busting 时间戳

**选择**：`reportDownloadUrl(reportId, 'html')` 后追加 `?_t=${Date.now()}`。

**理由**：最小侵入，确保每次 mount 都触发完整 HTTP 请求；服务端忽略未知 query param，无副作用。

**替代方案**：
- 改 `key` 为含时间戳的值——会破坏持久化（tabId 变化导致 persistence 不一致）
- 用 `iframe.contentWindow.location.reload()`——iframe sandbox 限制跨域访问
- 去掉 `sandbox`——降低安全性

### D3: chart 容器内联 height 作为 fallback

**选择**：`ReportRenderer.renderChart` 输出 `<div data-ledger-chart-id="..." style="height:360px">`。

**理由**：与 ECharts `ChartRenderer` 组件默认高度一致（chart-renderer.tsx 默认 360px）；内联 style 权重低于 CSS class（如有 `!important` 可覆盖），但作为 fallback 在 CSS 未加载时提供尺寸。

**替代方案**：
- 在 HTML `<style>` 中写 `.ledger-chart div { height: 360px }`——可行但需改 ledger.css，影响 PDF 渲染
- 在 bootstrap script 中加 `setTimeout` 延迟初始化——脆弱，不可靠

## Risks / Trade-offs

- **[cache-busting 增加请求量]** 每次重新打开都发起完整 HTTP 请求而非用缓存。→ 报告 HTML 通常 <100KB，影响可忽略；且图表正确性比节省一次请求更重要。
- **[内联 height 与 CSS 冲突]** 若未来 `ledger.css` 对 chart 容器设不同高度，内联 style 优先级可能冲突。→ 内联 style 不加 `!important`，CSS 可用 `!important` 覆盖；或改用 CSS inline block 同时输出。
