## 1. Fix DateFormatSelector Custom button

- [x] 1.1 重构 `date-format-selector.tsx`：引入 `mode` state (`'preset' | 'custom'`)，Custom 按钮 onClick 改为 `setMode('custom')`（不调 onChange）；preset 按钮 onClick 改为 `setMode('preset')` + `onChange(preset.value)`；`mode === 'custom'` 时显示 Input，Input onChange 才调外部 onChange
- [x] 1.2 添加 mode 与外部 value 同步：当 `isPreset` 从 true 变为 false 时自动 `setMode('custom')`，反之可选同步

## 2. Optimistic update for prefsMutation

- [x] 2.1 修改 `general-panel.tsx` 的 `prefsMutation`：添加 `onMutate`（cancelQueries + 快照 + setQueryData）、`onError`（回滚 + toast）、移除 `onSuccess` 的 `invalidateQueries`

## 3. Verification

- [x] 3.1 运行 `npx tsc --noEmit` 确认零类型错误
- [x] 3.2 运行 `npx vitest run client/src/features/settings/general/` 确认既有测试通过
- [ ] 3.3 浏览器端到端验证：preset 切换即时响应、Custom 按钮可点击进入编辑模式、输入自定义格式可保存
