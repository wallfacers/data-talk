## 1. Header 样式统一

- [x] 1.1 修改 `report-library-tab.tsx:56` header className：`sticky top-0 z-10 px-4 py-3 border-b border-border-subtle bg-bg-canvas` → `sticky top-0 z-10 flex items-center min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft`
- [x] 1.2 修改 `report-viewer-tab.tsx:82` header className：`flex items-center justify-between gap-3 px-4 py-3 border-b border-border-subtle` → `flex items-center justify-between gap-3 min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft`

## 2. 验证

- [x] 2.1 运行 `cd client && npx tsc --noEmit` 确认零类型错误
