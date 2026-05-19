## Context

报告查看器是 `report_viewer` 类型的 stage tab，内嵌 iframe 加载由 `ReportRenderer` 生成的自包含 HTML。当前存在三个缺陷：

1. **滚动条样式不一致**：iframe 内 HTML 通过 `ledger.css` 控制样式，该文件未定义滚动条伪元素，浏览器默认渲染正三角/倒三角按钮。
2. **iframe 重复加载**：`ReportViewerTab` 在 JSX 中直接内联 `Date.now()` 到 iframe src，每次组件 render 都产生新 URL。
3. **刷新后空白页（双环死锁）**：
   - `stage-tab-content.tsx:172-173` 有 payload 门控——`if (!payload.reportId) return null`
   - Ctrl+R 后 `__hydrateAll` → `toStageTab` 将 payload 重置为 `{}` → `reportId` 为 `undefined` → 门控返回 null → `ReportViewerTab` 永远不挂载 → `ensureHydrated` 永远不被调用 → payload 永远不恢复

## Goals / Non-Goals

**Goals:**
- 报告 iframe 内滚动条样式与主应用一致（无三角按钮、圆角 thumb）
- 打开报告 tab 时 iframe 仅加载 1 次
- Ctrl+R 刷新后报告 tab 恢复正常显示内容

**Non-Goals:**
- 不改变 report tab 的创建/去重逻辑
- 不改变 `beforeunload` 的 sendBeacon 持久化逻辑
- 不引入 iframe 内容的增量更新或局部刷新

## Decisions

### D1: iframe 时间戳绑定 reportId 而非每次 render

`useMemo(() => ..., [reportId])` 包装 URL 计算，确保同一 reportId 在组件生命周期内仅生成一次时间戳。

**备选方案**：`useRef` 存储初始时间戳。`useMemo` 语义更清晰——"此值依赖于 reportId"。

### D2: 按 dashboard-tab 模式重构——拆门控 + 组件自 hydrated

问题根因是 `stage-tab-content.tsx:172-173` 在组件挂载之前就拦截了空 payload：

```
if (tab.type === 'report_viewer') {
    const payload = (tab.payload ?? {}) as { reportId?: string }
    if (!payload.reportId) return null   // ← 死锁：不挂载就无法 ensureHydrated
    return <ReportViewerTab ... />
}
```

正确范式来自 `dashboard-tab.tsx:13-51`：
- wrapper 不做数据门控，直接传 `tab: StageTab` 对象
- 组件内部判 payload 缺失时调用 `coordinator.ensureHydrated(tab.tabId)`，渲染 `<TabContentLoader />`
- payload 到齐后渲染正常内容

修改方案：

| 文件 | 变更 |
|------|------|
| `stage-tab-content.tsx` | 删除 `if (!payload.reportId) return null`，改为传 `tab={tab}` |
| `report-viewer-tab.tsx` | 签名从 `({ reportId })` 改为 `({ tab })`，添加 `useEffect` 调用 `ensureHydrated`，缺 payload 时渲染 `<TabContentLoader />` |

**备选方案**：在 `stage-tab-content.tsx` 层调用 `ensureHydrated` 再渲染。这会把 hydration 逻辑分散到 wrapper 里，所有 tab 类型都得加，不如让组件自管理——与其他所有持久化 tab 一致。

### D3: ledger.css 追加滚动条样式

在 `ledger.css` 末尾追加 `::-webkit-scrollbar-*` 规则集，与主应用 `globals.css` 保持一致：透明 track、`var(--ledger-text-faint)` 底色圆角 thumb（带 3px transparent border）、hover 变暗、隐藏按钮。

## Risks / Trade-offs

- **ledger.css 滚动条样式仅影响 Webkit/Blink**：Firefox 用 `scrollbar-width` / `scrollbar-color`。当前 Tauri 桌面端基于 Webkit，风险低。
- **ensureHydrated 是异步调用**：hydration 完成前用户看到 `<TabContentLoader />`（骨架 loading），行为与 dashboard-tab 一致。
- **useMemo 依赖 [reportId]**：同一 tab 实例 reportId 固定不变，无风险。
