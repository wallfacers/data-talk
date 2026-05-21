# Blank Session List Actions Design

## Background

会话列表当前始终显示空白会话（`hasEverSent=false`），且空白会话与普通会话共享同一组列表操作。用户删除当前会话后，前端会自动切到一个空白会话作为兜底，因此列表里会立刻出现一个新的“新会话”项；如果该项继续暴露删除和重命名，会形成“刚删完又出现一个可删的新会话”的困惑交互。

## Goal

将空白会话在会话列表中降级为“仅可进入，不可管理”的占位会话：

- 继续显示在列表中，用户可以点击进入
- 不显示右侧“更多”按钮
- 不允许通过列表触发重命名
- 不允许通过列表触发删除

## Non-Goals

- 不修改后端 session API 权限或约束
- 不改变“删除当前会话后自动跳到空白会话”的现有兜底机制
- 不隐藏空白会话本身

## Design

### 1. UI Rule

在 `client/src/features/workspace/components/nav-sessions.tsx` 中，将 `hasEverSent === false` 的 session 视为 blank session。

- blank session：只渲染 `SidebarMenuButton`
- normal session：继续渲染 `SidebarMenuButton + DropdownMenu`

这样列表视觉上不会再为 blank session 暴露“更多”按钮，直接消除用户误操作入口。

### 2. Interaction Guard

除了隐藏菜单，还在组件内部加一层保护：

- 仅 normal session 可进入 rename editing 态
- 仅 normal session 可设置 delete target

即使未来有人误恢复菜单 UI，也不会让 blank session 进入现有删改流程。

### 3. Testing

补充前端组件测试覆盖两类行为：

- blank session 不渲染“更多”按钮
- normal session 仍然保留“更多”按钮，并且保留删除入口

## Error Handling

本次不新增错误提示；因为对 blank session 的删改入口直接不展示，用户不会进入失败流。

## Files

- Modify: `client/src/features/workspace/components/nav-sessions.tsx`
- Modify: `client/src/features/workspace/components/__tests__/nav-sessions.test.tsx`

## Acceptance Criteria

1. 会话列表中的 blank session 不显示“更多”按钮
2. blank session 仍可点击进入
3. normal session 仍可通过“更多”菜单看到 rename/delete
4. 现有“删除当前会话后切到空白会话”的行为不回归
