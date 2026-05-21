# Design: Fix Date Format Selector

## Decision 1: Custom 模式使用组件内 state

**选择**: `DateFormatSelector` 内部维护 `mode: 'preset' | 'custom'` state

**原因**: Custom 按钮点击是纯 UI 行为（展示输入框），不应触发外部 onChange/API mutation。只有当用户在输入框中输入自定义格式时才应调用 onChange。

**实现**:
- 新增 `const [mode, setMode] = useState<'preset' | 'custom'>(isPreset ? 'preset' : 'custom')`
- Custom 按钮 onClick → `setMode('custom')`，不调 onChange
- preset 按钮 onClick → `setMode('preset')` + `onChange(preset.value)`
- mode 为 custom 时显示 Input，Input onChange 才调外部 onChange
- 当外部 value 从 preset 变为非 preset 时，mode 自动同步为 custom（通过 useEffect 或派生逻辑）

## Decision 2: Optimistic Update 替代 write-then-refetch

**选择**: `prefsMutation` 使用 TanStack Query optimistic update

**原因**: 当前流程 `PUT → invalidate → GET refetch` 产生双次网络往返。Optimistic update 在 `onMutate` 中直接写入 queryCache，UI 即时更新，PUT 请求在后台进行。

**实现**:
```
onMutate:
  - 取消进行中的 query (cancelQueries)
  - 快照当前 cache (getQueryData)
  - 写入新值 (setQueryData)
  - 返回 snapshot 作为 context

onError:
  - 回滚 cache (setQueryData(snapshot))

onSuccess:
  - 不 invalidate，因为 optimistic 已经是正确值
```

## Risk

- Optimistic update 写入的值可能与后端实际持久化的值不同步（如后端校验失败）。缓解：onError 回滚 cache + toast 提示
- mode state 与外部 value 的同步边界情况：如果外部 value 变化（如另一个组件更新了偏好），mode 需要响应。通过 `useEffect` 监听 `isPreset` 变化来同步

## Files Changed

| File | Change |
|------|--------|
| `date-format-selector.tsx` | 引入 mode state，重构 Custom 按钮逻辑 |
| `general-panel.tsx` | prefsMutation 改用 optimistic update |
