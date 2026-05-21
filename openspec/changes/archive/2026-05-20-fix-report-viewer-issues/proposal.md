## Why

报告查看器（report viewer tab）存在三个影响用户体验的缺陷：iframe 内滚动条保留了浏览器默认的正三角/倒三角按钮与主应用风格不统一；打开报告时 iframe 因 `Date.now()` 在每次 render 中重新计算导致重复加载；Ctrl+R 刷新后报告 tab 丢失 payload 显示空白页。这三个问题都需要修复。

## What Changes

- **ledger.css 添加滚动条样式**：隐藏 `::-webkit-scrollbar-button`，添加与主应用一致的圆角 thumb 样式，使报告内嵌 iframe 的滚动条与主应用视觉统一
- **report-viewer-tab.tsx 稳定化 iframe URL**：用 `useMemo` 将缓存破坏参数 `_t` 绑定到 `reportId` 而非每次 render，消除 `useReport` / `useSystemStatus` 数据返回触发的多余 iframe 重载
- **stage-tab-content.tsx 移除 report_viewer 的 payload 门控**：删除 `if (!payload.reportId) return null` 分支，改为将整个 `tab` 对象传入 `ReportViewerTab`，组件内部自行调用 `ensureHydrated` 并显示 loading——与 `dashboard-tab.tsx` 模式一致
- **report-viewer-tab.tsx 重构为接收 tab 对象**：组件签名从 `({ reportId })` 改为 `({ tab })`，内部判断 payload 缺失时调用 `coordinator.ensureHydrated(tab.tabId)` 并渲染 `<TabContentLoader />`，payload 就绪后再渲染 iframe

## Capabilities

### Modified Capabilities

- `report-viewer-tab`: 细化 iframe 重载时机（仅 remount 而非每次 render），新增 payload 持久化恢复行为，并将滚动条样式纳入报告 HTML 的渲染规范

## Impact

- **前端**: `client/src/features/report/components/report-viewer-tab.tsx`（iframe URL 稳定化 + 重构为接收 tab）、`client/src/features/stage/components/stage-tab-content.tsx`（移除 report_viewer 门控）
- **后端**: `server/data-talk-adapter/src/main/resources/skills/ledger/assets/styles/ledger.css`（滚动条样式）
- **无**数据库 schema 变更、无 API 变更、无新增依赖
