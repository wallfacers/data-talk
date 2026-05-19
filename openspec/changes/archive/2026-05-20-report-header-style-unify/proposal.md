## Why

报告库（ReportLibraryTab）和报告查看器（ReportViewerTab）的 header 样式与 SQL 编辑器（SqlEditorHeader / SqlEditorToolbar）不一致：背景色、边框色、最小高度、内边距均不同，导致视觉割裂。需要统一为 SQL 编辑器的 header 规格。

## What Changes

- 统一 ReportLibraryTab header 的 CSS 类：背景 `bg-bg-soft`、边框 `border-border/50`、最小高度 `min-h-11`、内边距 `px-3 py-2`，补充 `flex items-center` 布局
- 统一 ReportViewerTab header 的 CSS 类：补充背景 `bg-bg-soft`、边框 `border-border/50`、最小高度 `min-h-11`、内边距 `px-3 py-2`

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `report-tab-ui`: 报告 Tab header 的背景色、边框色、最小高度、内边距统一到 SQL 编辑器 header 规格

## Impact

- `client/src/features/report/components/report-library-tab.tsx` — header className 变更
- `client/src/features/report/components/report-viewer-tab.tsx` — header className 变更
- 纯视觉样式变更，无 API / 数据 / 逻辑影响
