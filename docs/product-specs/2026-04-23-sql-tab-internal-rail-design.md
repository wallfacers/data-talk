# SQL 编辑器 Tab 内置 Activity Rail 设计

- **日期**：2026-04-23
- **状态**：待实施
- **作者**：wallfacers
- **关联代码**：`client/src/features/stage/components/stage-window.tsx`、`client/src/features/stage/components/sql-workbench-tab.tsx`、`client/src/features/stage/components/activity-rail/`

## 1. 背景

当前 Stage 窗口的右侧 `StageActivityRail`（28px 图标列 + 280px 滑出面板，承载 Schema / 历史 / 大纲）渲染在 `StageWindow` 顶层，与 `StageTabContent` 同级——也就是说，无论激活的是 SQL 编辑器、文件预览、还是未来的 Dashboard / 报表，rail 都会显示。

但这套面板的内容**只对 SQL 编辑器（以及未来的 ER 图设计器）有意义**：

- Schema 面板需要 `query_editor` Tab 的 connection 上下文
- 历史面板读 `useSqlWorkbenchStore` 中按 tabId 维护的执行历史
- 大纲面板解析当前 tab 的 SQL 文本

文件预览 Tab 看到这些图标是噪声、点开是空状态。

## 2. 目标

把 Activity Rail 从 Stage 窗口层下移到「SQL 编辑器 Tab」内部：

- **保留 rail 形态**：右侧 28px 图标列 + 280px 单选滑出面板；内容、交互、状态作用域、i18n 文案均不动
- **视觉硬约束**：rail 严格落在 Tab 内容矩形内，不能与上方 Tab 标签栏齐平、不能盖到标题栏
- **作用范围**：仅在 `query_editor` Tab 内渲染；未来 `er_designer` Tab 复用同一组件
- **不渲染场景**：`file_preview` Tab、未来的 dashboard / report Tab、空 Tab 占位

非目标：

- 不重做 rail 内 Schema / History / Outline 三个面板的内部交互
- 不实现 ER 图设计器（ER Tab 接入只是预留位）
- 不动 i18n 文案
- 不改 SQL 执行链路、不改 result panel

## 3. 当前结构

```
StageWindow
├── 顶部窗口标题栏 (10px 高，含 maximize/close)
└── 主体 (flex row, 高度铺满)
    ├── 主区 (flex column, flex-1)
    │   ├── StageTabBar (顶部 Tab 标签)
    │   └── StageTabContent
    │       └── 按 tab.type 渲染：
    │           ├── SqlWorkbenchTab     → 编辑器 + 结果面板（纵向 split）
    │           └── FilePreviewTab      → 只读 monaco 预览
    └── StageActivityRail ← 当前位置：所有 tab 类型都看得到
        ├── 280px 滑出面板（按需）
        └── 28px 图标列（Schema / History / Outline）
```

## 4. 目标结构

```
StageWindow
├── 顶部窗口标题栏
└── 主体 (flex row)
    └── 主区 (flex column, flex-1)
        ├── StageTabBar
        └── StageTabContent
            └── 按 tab.type 渲染：
                ├── SqlWorkbenchTab (flex row, 占满 Tab 矩形)
                │   ├── 主区 (flex column, flex-1, min-w-0)
                │   │   ├── SqlEditorToolbar
                │   │   ├── SqlMonacoEditor
                │   │   └── SqlResultPanel (按需, 下方)
                │   └── StageActivityRail (shrink-0)  ← 新位置
                ├── FilePreviewTab → 不挂 rail
                └── (未来) ErDesignerTab → 主区 + StageActivityRail
```

要点：

- `StageWindow` 不再渲染 `<StageActivityRail>`；外层 flex row 退化为单列
- rail 顶边贴 `SqlEditorToolbar`、底边贴 Tab 底；左右分隔由 rail 自身的 `border-l` 提供
- 由于 rail 现在是 SQL Tab 的子元素，自然被 Tab 内容矩形裁剪，不会越界到标题栏 / Tab 标签栏

## 5. 关键决策

### 5.1 状态作用域：保持按 session 记忆

`useStageStore.activeRailPanelBySession`（key: `sessionId | 'workspace'`）**不动**。理由：

- **UX 与 IDE 一致**：用户打开 outline 后切到下一个 SQL Tab，期望它仍开着、内容自动换成新 Tab 的——VS Code / JetBrains 的侧栏选择是跨文件持久的
- **状态零清理**：按 tab 记忆需要在 tab 关闭时清理 `activeRailPanelByTab`（类似现有 `cleanupTabs` 那一套）；按 session 记忆不需要
- **面板内容已是按 tab**：rail 内部三个面板的数据源都从「当前激活 tab」实时读取，「内容随 tab 切换更新」天然成立，无需把「面板选择」也按 tab 拆

### 5.2 组件签名：不改

`StageActivityRail` 组件保持现有 props（`sessionId?: string | null` + `className?: string`），只是渲染位置从 `StageWindow` 下移到 `SqlWorkbenchTab` 内部。

理由：组件已经从内部读 active tab、active tab state、`activeRailPanelBySession`，它只需要知道 sessionId（用于 rail panel 选择 key）。SQL Tab 内部把 `tab.originSessionId ?? null` 透传给它即可。

### 5.3 Tab 类型分流：在 `StageTabContent` 之外承担

`StageTabContent` 仍按 `tab.type` 分流到具体 Tab 组件，但**不**在自己这层加判断「要不要挂 rail」。Rail 由各 Tab 组件**自行决定**是否挂——`SqlWorkbenchTab` 挂；`FilePreviewTab` 不挂；未来 `ErDesignerTab` 挂；Dashboard / 报表不挂。

理由：把「我需要哪些侧栏」的决定权交给 Tab 自己，符合「Tab 是自包含单元」的层次原则；避免在 `StageTabContent` 形成大 switch 列举所有 Tab 类型与 rail 的对应关系。

## 6. 改动清单

| 文件 | 改动 |
|------|------|
| `client/src/features/stage/components/stage-window.tsx` | 删除 `<StageActivityRail sessionId={…} />` 渲染；外层主体改为单列 flex |
| `client/src/features/stage/components/sql-workbench-tab.tsx` | 在最外层包横向 flex；左侧主区放原有「编辑器 + 结果」纵向 split；右侧挂 `<StageActivityRail sessionId={tab.originSessionId ?? null} />` |
| `client/src/features/stage/components/file-preview-tab.tsx` | 不变（本来就没挂 rail）|
| `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx` | 不动（保持现有 props 与逻辑）|
| `client/src/stores/stage-store.ts` | 不动（`activeRailPanelBySession` 保留）|

## 7. 测试

| 测试文件 | 调整 |
|----------|------|
| `stage-window.test.tsx` | 移除「`StageActivityRail` 在窗口层渲染」的断言；新增「窗口主体内不再出现 `data-testid="stage-activity-rail"`」 |
| `sql-workbench-tab.test.tsx` | 新增「SQL Tab 内部包含 `data-testid="stage-activity-rail"`」与「rail 顶边不超过 toolbar」的层级断言 |
| `file-preview-tab.test.tsx` | 新增「文件预览 Tab 内部不出现 `data-testid="stage-activity-rail"`」 |
| `stage-activity-rail.test.tsx` | 不动（组件本身行为未变） |

## 8. 风险

| 风险 | 缓解 |
|------|------|
| rail 横向占位影响编辑器宽度 | 主区加 `min-w-0`，rail `shrink-0`；Monaco 编辑器横向自适应 |
| 同一 session 内多个 SQL Tab 切换时面板内容闪烁 | 现有实现已经按 active tab 读取数据，切 tab 即重渲染；无新闪烁源 |
| 文件预览 Tab 用户找不到 Schema / 历史 | 这是设计意图——Schema / 历史 / 大纲对文件预览无意义，移除即去噪 |
| 未来 ER Tab 接入需要改 rail | 不需要——ER Tab 自己挂 `<StageActivityRail>` 即可 |

## 9. 验证

- 视觉：rail 上沿严格不超过 SQL 编辑器 toolbar 顶边；切换到文件预览 Tab，rail 完全消失
- 行为：在 session A 打开两个 SQL Tab，rail 上点开 outline；在两个 Tab 间切换，outline 始终保持打开，但内容跟随当前 Tab 的 SQL 变化
- 类型：`cd client && npx tsc --noEmit` 零错误
- 测试：`cd client && npm test` 全绿
