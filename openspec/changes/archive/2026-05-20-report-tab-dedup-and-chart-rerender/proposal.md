## Why

Report viewer tab 存在两个用户感知缺陷：
1. **重复打开** — 同一报告在 Tab 列表中可以创建多个完全相同的 tab，关闭时还会误删所有同名 tab。
2. **重开图表空白** — 关闭报告 tab 后再次打开同一报告，iframe 内 ECharts 图表区域为空，无法正常渲染。

## What Changes

- **报告 tab 去重**：`report-library-tab.tsx` 和 `report-card.tsx` 的 `onOpen` 逻辑改为"先查再开"——若 `tabs[]` 中已存在同 `tabId` 的 report_viewer tab，调用 `focusTab()` 聚焦；否则才 `openTab()` 新建。与现有 `openArtifactPreviewTab()` 模式一致。
- **iframe 重载保障**：给 `ReportViewerTab` 的 iframe `src` 添加 cache-busting 查询参数 `?_t=${Date.now()}`，确保每次组件 mount 都触发完整加载，绕过浏览器缓存。
- **chart 容器 fallback 高度**：`ReportRenderer` 生成的 `<div data-ledger-chart-id="...">` 添加内联 `style="height:360px"`，在 `ledger.css` 尚未加载完成时提供 fallback 尺寸，避免 ECharts 对零高度容器初始化。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。本 change 修正的是实现缺陷（去重逻辑缺失、iframe 缓存、容器高度 fallback），不改变任何 spec-level 的行为要求。前端报告 tab 的交互行为（打开、关闭、聚焦）保持与 `openArtifactPreviewTab` 等已有 tab 类型一致。

## Impact

**前端代码**：
- `client/src/features/report/components/report-library-tab.tsx` — `onOpen` 加去重
- `client/src/features/report/components/report-card.tsx` — `onOpen` 加去重
- `client/src/features/report/components/report-viewer-tab.tsx` — iframe src 加 cache-busting

**后端代码**：
- `server/data-talk-application/src/main/java/com/datatalk/application/report/ReportRenderer.java` — chart 容器 div 加内联高度

**测试**：
- `ReportRendererTest` — 验证 chart 容器含内联 height style
- 前端 vitest（如有 report 相关测试需同步更新）

**无 API 变更、无数据源类型变更、无 spec 变更。**

**已检 docs/bugs/ 关联**：BUG-0073（字体 CORS）已由 `ledger-report-quality-fixes` 修复，与本次 chart 渲染问题无关。无其他 open BUG 与报告 tab 去重/图表渲染重叠。
