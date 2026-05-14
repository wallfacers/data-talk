# Fix Date Format Selector

## Why

`DateFormatSelector` 组件有两个阻断性 bug：

1. **Custom 按钮无效**：点击 Custom 调用 `onChange(value)` 传入当前值，值不变导致 `isPreset` 仍为 true，Custom 永远不会被选中，自定义输入框也不会出现
2. **切换卡顿**：每次点击 preset 都触发 `PUT /api/preferences` + `invalidateQueries` + `GET refetch`，双次网络往返导致 UI 明显卡顿

## What

修复 DateFormatSelector 的交互逻辑和 GeneralPanel 的 mutation 策略：

- Custom 按钮改为纯 UI 状态切换（不触发 mutation），选中后显示输入框
- 自定义格式输入框变更时才触发 onChange
- GeneralPanel 的 prefsMutation 改用 optimistic update，消除 refetch 延迟

## Scope

- `client/src/features/settings/general/date-format-selector.tsx` — 组件逻辑重写
- `client/src/features/settings/general/general-panel.tsx` — mutation optimistic update
- 不涉及后端变更

## Impact

- 用户可以正常使用 Custom 日期格式
- preset 切换即时响应，无感知延迟
