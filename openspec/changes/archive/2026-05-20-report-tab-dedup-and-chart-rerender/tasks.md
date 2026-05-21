## 1. 报告 tab 去重

- [x] 1.1 `report-library-tab.tsx` — `onOpen` 中先查 `useStageStore tabs` 是否已有 `tabId = "report-viewer:{reportId}"` 的 tab，有则 `focusTab`，无则 `openTab`
- [x] 1.2 `report-card.tsx` — `onOpen` 加同样去重逻辑（与 1.1 模式一致）

## 2. iframe 重载保障

- [x] 2.1 `report-viewer-tab.tsx` — iframe `src` 追加 `?_t=${Date.now()}` cache-busting 参数

## 3. chart 容器 fallback 高度

- [x] 3.1 `ReportRenderer.java` `renderChart` — chart 容器 `<div>` 加内联 `style="height:360px"`
- [x] 3.2 `ReportRendererTest` — 补充断言验证 chart 容器含 `style="height:360px"`

## 4. 验证

- [x] 4.1 前端编译通过：`cd client && npx tsc --noEmit`
- [x] 4.2 后端编译通过：`cd server && mvn compile -q`
- [x] 4.3 `ReportRendererTest` 全部通过
