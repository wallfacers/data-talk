## Context

`SqlMonacoEditor` 使用 Monaco Editor，当前启用原生右键菜单（仅 Cut/Copy/Paste）。项目已有 `@base-ui/react/context-menu` 组件库，在 SQL 结果表格、Stage 标签栏等多处使用。需要替换原生菜单为自定义菜单，加入 SQL 专属操作。

已有基础设施：
- `parseSqlOutline()` / `resolveCurrentSqlOutlineStatement()` — 按 `;` 分割 SQL 语句并定位光标所在语句
- `getSelectedSqlText()` — 获取当前选中的 SQL 文本
- `handleRun` — 已有"运行所有/运行选中"的统一入口（传递 `sqlOverride`）
- 键盘快捷键：`Ctrl+Enter`（运行）、`Ctrl+Shift+F`（格式化）、`Escape`（取消）

## Design Inputs

来自 `client/DESIGN.md` 的约束：
- 上下文菜单使用语义 token，不做组件本地颜色值
- Motion: 菜单进出动画使用 `fast` (120ms) / `normal` (180ms)，当前 `context-menu.tsx` 已内置 `animate-in fade-in-0 zoom-in-95`
- Keyboard: 菜单项必须支持键盘导航（`@base-ui/react/context-menu` 原生支持）
- State: 禁用状态不可仅靠颜色区分（结合 `data-disabled` + `opacity-50`，已有）
- 图标操作需要 accessible name

## Goals / Non-Goals

**Goals:**
- 替换 Monaco 原生右键菜单为 React 自定义菜单
- 支持 12 个菜单项：运行所有、运行当前语句、运行选中 SQL、取消执行、格式化、切换注释、剪切、复制、粘贴、撤销、重做、全选
- 菜单项根据编辑器状态上下文感知显示/隐藏
- 新增 `Ctrl+Shift+Enter`（运行当前语句）、`Ctrl+/`（切换注释）快捷键
- 显示已有快捷键提示

**Non-Goals:**
- AI 辅助菜单项（AI 解释/优化/修复）— 后续迭代
- 复制为 CSV/Markdown — 后续迭代
- 运行当前行（逐行执行，忽略语句边界）— 用户未选

## Decisions

### Decision 1: 替换 Monaco 原生菜单

**选择**：禁用 Monaco `contextmenu`，用 `@base-ui/react/context-menu` 包裹编辑器容器。

**理由**：Monaco 原生菜单布局受限，无法添加分组、图标、子菜单，与项目其他上下文菜单 UI 风格不统一。替换后完全控制布局和交互。

**替代方案**：通过 Monaco `IActionDescriptor` 的 `contextMenuGroupId` 扩展原生菜单 — 无法分组、无图标、样式不可控。

### Decision 2: 上下文菜单组件位置

**选择**：在 `SqlMonacoEditor` 组件内部渲染 `ContextMenu`，通过 props 接收 `onRunCurrentStatement`、`onCancel`、`isRunning`。

**理由**：`SqlMonacoEditor` 持有 Monaco editor 实例引用，可直接调用 `editor.trigger()` 执行 Monaco 内置操作（cut/copy/paste/undo/redo/selectAll/toggleComment），无需通过 ref 向上暴露。

### Decision 3: 运行当前语句的实现

**选择**：在 `SqlWorkbenchTab` 中添加 `handleRunCurrentStatement`，利用已有的 `parseSqlOutline` + `resolveCurrentSqlOutlineStatement` 定位光标所在语句，提取文本后通过 `runQueryEditorSql({ sqlOverride })` 执行。

**理由**：复用已有的语句解析和 SQL 执行基础设施，`SqlMonacoEditor` 已通过 `onCursorChange` 跟踪光标位置。

### Decision 4: Monaco 内置操作的触发方式

**选择**：通过 `editor.trigger('keyboard', '<actionId>')` 调用 Monaco 内置 action。

涉及的 action ID：
| 操作 | Action ID |
|------|-----------|
| Cut | `editor.action.clipboardCutAction` |
| Copy | `editor.action.clipboardCopyAction` |
| Paste | `editor.action.clipboardPasteAction` |
| Undo | `undo` |
| Redo | `redo` |
| Select All | `editor.action.selectAll` |
| Toggle Comment | `editor.action.commentLine` |

### Decision 5: 上下文感知逻辑

| 菜单项 | 显示/启用条件 |
|--------|--------------|
| 运行所有 | 始终显示，无选中文本时为主操作 |
| 运行当前语句 | 始终显示（非执行中） |
| 运行选中 SQL | 仅当有非空选中文本 |
| 取消执行 | 仅当 `isRunning === true` |
| 格式化 | 始终显示 |
| 切换注释 | 始终显示 |
| 剪切 | 有选中文本时启用 |
| 复制 | 有选中文本时启用 |
| 粘贴 | 始终启用（Monaco 自行判断剪贴板） |
| 撤销 | 编辑器有可撤销历史时启用 |
| 重做 | 编辑器有可重做历史时启用 |
| 全选 | 始终显示 |

选中状态和撤销/重做状态在菜单打开时通过 `editorRef.current` 实时查询，不通过 React state 持续跟踪（避免不必要的重渲染）。

### Decision 6: 菜单分组结构

```
  ▶ 运行所有                Ctrl+Enter
  ▶ 运行当前语句          Ctrl+Shift+Enter
  ▶ 运行选中的 SQL              ← 仅当有选中时显示
  ■ 取消执行                    ← 仅当执行中时显示
  ────────────────────────────
  🔧 格式化 SQL            Ctrl+Shift+F
  💬 切换注释                  Ctrl+/
  ────────────────────────────
  ✂ 剪切                      Ctrl+X
  📋 复制                      Ctrl+C
  📄 粘贴                      Ctrl+V
  ────────────────────────────
  ↩ 撤销                       Ctrl+Z
  ↪ 重做                   Ctrl+Shift+Z
  📝 全选                       Ctrl+A
```

分为「执行」「格式化」「剪贴板」「历史」四个分组，用 `ContextMenuSeparator` 分隔。

## Risks / Trade-offs

- **Monaco 原生 "Go to Definition" 等丢失** → SQL 场景几乎用不到，影响极小
- **右键菜单打开时需查询编辑器状态** → 查询是同步的，无性能问题
- **`contextmenu: false` 可能影响 Monaco 内部右键行为** → 测试确认，Monaco 仅禁用弹出菜单，不影响其他右键事件
