## ADDED Requirements

### Requirement: ReportLibraryTab header 样式统一
ReportLibraryTab 的 header 区域 SHALL 使用与 SQL 编辑器 header 一致的视觉规格：`bg-bg-soft` 背景、`border-border/50` 边框色、`min-h-11` 最小高度、`px-3 py-2` 内边距、`flex items-center` 布局。

#### Scenario: ReportLibraryTab header 渲染
- **WHEN** ReportLibraryTab 渲染到 Stage 面板中
- **THEN** header 元素的 CSS 类 SHALL 包含 `bg-bg-soft`、`border-border/50`、`min-h-11`、`px-3 py-2`、`flex items-center`

### Requirement: ReportViewerTab header 样式统一
ReportViewerTab 的 header 区域 SHALL 使用与 SQL 编辑器 header 一致的视觉规格：`bg-bg-soft` 背景、`border-border/50` 边框色、`min-h-11` 最小高度、`px-3 py-2` 内边距。

#### Scenario: ReportViewerTab header 渲染
- **WHEN** ReportViewerTab 渲染到 Stage 面板中
- **THEN** header 元素的 CSS 类 SHALL 包含 `bg-bg-soft`、`border-border/50`、`min-h-11`、`px-3 py-2`
