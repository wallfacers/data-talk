## Context

报告库（ReportLibraryTab）和报告查看器（ReportViewerTab）的 header 样式与 SQL 编辑器（SqlEditorHeader / SqlEditorToolbar）不一致。当前差异：

| 属性 | SQL 编辑器 | 报告 Tab |
|------|-----------|---------|
| background | `bg-bg-soft` | `bg-bg-canvas` / 无 |
| border-color | `border-border/50` | `border-border-subtle` |
| min-height | `min-h-11` (44px) | 无 |
| padding | `px-3 py-2` | `px-4 py-3` |
| flex layout | `flex items-center justify-between` | ReportLibrary 无 flex / ReportViewer 有 |

## Goals / Non-Goals

**Goals:**
- 报告两个 Tab 的 header 视觉规格与 SQL 编辑器完全一致

**Non-Goals:**
- 不改变 header 内容、功能或交互逻辑
- 不涉及 SQL 编辑器本身的样式变更
- 不引入新的共享 header 组件（纯 className 对齐）

## Decisions

**直接对齐 SQL 编辑器 header 的 className**

SQL 编辑器 header 已是项目中 toolbar/header 的标准规格（`bg-bg-soft` + `min-h-11` + `border-border/50` + `px-3 py-2`）。报告 header 直接采用相同 class 即可，无需抽取共享组件。

变更映射：

- `report-library-tab.tsx:56` header: `sticky top-0 z-10 px-4 py-3 border-b border-border-subtle bg-bg-canvas` → `sticky top-0 z-10 flex items-center min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft`
- `report-viewer-tab.tsx:82` header: `flex items-center justify-between gap-3 px-4 py-3 border-b border-border-subtle` → `flex items-center justify-between gap-3 min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft`

## Risks / Trade-offs

- ReportLibraryTab 的 sticky 定位保留不变，仅对齐视觉属性，无风险
- ReportViewerTab header 无背景色 → 加 `bg-bg-soft` 后可能在某些主题下略有色差，但与 SQL 编辑器一致故可接受
