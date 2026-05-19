## Why

SQL 编辑器当前仅依赖 Monaco 原生右键菜单（Cut/Copy/Paste），缺少 SQL 专属操作入口。用户执行 SQL、格式化、注释切换等高频操作被迫在键盘快捷键和工具栏之间切换，效率低且对新用户不友好。引入自定义右键菜单统一操作入口，提升编辑体验。

## What Changes

- 禁用 Monaco 原生右键菜单，替换为基于 `@base-ui/react/context-menu` 的自定义菜单
- 新增右键菜单项：运行所有、运行当前语句、运行选中的 SQL、取消执行、格式化 SQL、切换注释、剪切、复制、粘贴、撤销、重做、全选
- 新增键盘快捷键：`Ctrl+Shift+Enter` 运行当前语句、`Ctrl+/` 切换行注释
- 菜单项根据编辑器状态上下文感知显示/隐藏（有选中文本时显示"运行选中"，执行中时显示"取消执行"，无可撤销时禁用撤销等）

## Capabilities

### New Capabilities

- `sql-editor-context-menu`: SQL 编辑器右键上下文菜单，提供执行、编辑、格式化三类操作的快捷入口

### Modified Capabilities

<!-- None -->

## Impact

- `client/src/features/stage/components/sql-monaco-editor.tsx` — 添加右键菜单组件、新快捷键注册、新增 props
- `client/src/features/stage/components/sql-workbench-tab.tsx` — 添加 `handleRunCurrentStatement` 回调、传递新 props
- `client/src/components/ui/context-menu.tsx` — 已有组件，直接复用
- 无后端变更、无 API 变更、无数据库变更
