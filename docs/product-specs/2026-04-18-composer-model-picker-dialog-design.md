# Composer 模型选择器：Popover 改为对话框

**状态**：待实施
**日期**：2026-04-18
**作者**：wallfacers
**关联代码**：`client/src/features/session/model-picker/`

## 1. 背景

Composer 输入框下方的模型选择器当前使用 `Popover`（`ModelPickerPopover`，280px 宽，搜索 + 单栏启用模型列表）。设置中心的"模型"页是一个大型对话框（960×540，带侧边栏 + 主内容区）。

两者视觉风格差异大，用户希望 composer 的模型切换也走"和设置页一样的大对话框"，以获得一致的观感和更充裕的浏览空间；但 composer 上触发按钮（图标 + 模型名 + 倒三角）不变。

同时，当前的 `Popover` 只承担"选当前模型"这一职责，而设置页承担"启用/禁用 + 管理"。这两件事不能合并——合并会让"点一下就切换"的快速动作被 Switch、管理入口干扰，增加误触风险。

## 2. 目标与非目标

**目标**：
- 用对话框替代 `Popover`，视觉上与 `SettingsDialog` 对齐（960×540 尺寸、同级圆角阴影）
- 对话框内部采用**双栏**：左侧提供商导航、右侧该提供商的模型列表
- 打开时默认定位到**当前模型所属的提供商**；无当前模型时定位到侧边栏第一项
- 支持**全局搜索**，同时过滤侧边栏和右侧列表
- 触发按钮视觉完全保留（`ProviderIcon` + 模型名 truncate + `ChevronDownIcon`）
- 无可选模型时提供空态指引 + 一键跳转到设置页的"模型"段

**非目标**：
- 不在此对话框里做模型启用/禁用（那是设置页的职责）
- 不支持拖拽排序、收藏、最近使用等额外能力
- 不改动 `prompt-composer.tsx` 的其它行为
- 不改 `SettingsDialog` / `ModelsPage` 自身
- 不处理"提供商未连接"情形（左侧只显示已连接 + 有启用模型的提供商）

## 3. 交互与视觉规范

### 3.1 触发器（零视觉变化）

```
[ProviderIcon] [模型名(max-w-[140px] truncate)] [ChevronDownIcon]
```
- 高度 `h-7`，`px-2`，`text-xs`，hover 态 `bg-accent/50`
- 未选中模型时显示"选择模型"占位文字（`text-muted-foreground`）
- 点击打开对话框（替换掉原 Popover）
- 显式 `type="button"`，避免嵌在 form 内触发提交

### 3.2 对话框骨架

- 尺寸：**960×540**，与 `SettingsDialog` 完全一致（`w-[960px] h-[540px] max-w-[960px] max-h-[540px] sm:max-w-[960px]`）
- 结构：
  - `DialogHeader`：左侧标题"选择模型"，右侧搜索输入框（`SearchIcon` + `Input`，宽约 240px）
  - Body：`flex flex-1 overflow-hidden`
    - 左侧 `ProviderNav`：宽约 200px，带分隔线
    - 右侧主区：`flex-1 overflow-y-auto p-6`
- 按 Esc / 点击遮罩 / 点击右上关闭按钮 → 关闭，**不**改当前模型

### 3.3 左侧 `ProviderNav`

- 仅展示**已连接（`connected: true`）且至少有一个启用模型（`enabled: true`）的提供商**
- 每项：`ProviderIcon size-4` + 提供商名称，`px-3 py-2`，选中态使用 `bg-accent` / `text-foreground`，非选中态 `text-muted-foreground hover:bg-accent/50`
- 有搜索查询时：仅保留"至少包含一个匹配模型"的提供商

### 3.4 右侧模型列表

- 垂直列表，每项一行：模型名（`text-sm`）、`px-3 py-2`、圆角 hover
- **当前已选模型**用 `bg-accent` 高亮
- 点击模型 → `onPick(formatModelId(providerId, modelId))` → 触发 `setCurrentModel` mutation → 关闭对话框
- 有搜索查询时：仅显示当前提供商下匹配的模型

### 3.5 默认定位与联动

- 组件内部维护 `activeProviderId`（本地 state）：
  - 首次打开时通过 `parseModelId(currentModelId)` 推导；无当前模型时取过滤后 `providers[0].id`
  - 对话框每次从关闭转为打开时重新计算（即"关闭再打开"会回到默认定位）
- 搜索输入 `q` 变化时：
  - 派生"过滤后仍有匹配的 providers 列表"
  - 若 `activeProviderId` 不在这个列表中，自动修正为列表第一个
- 关闭对话框（`onOpenChange(false)`）时清空 `q`（避免下次打开还记得旧搜索）

### 3.6 空态

两种空态按"搜索串是否为空"区分，布局在所有情况下保持 960×540（左栏框架始终存在，内容可空）：

1. **完全没有可选模型**（`q === ''` 且过滤后 provider 列表为空）
   - 左栏：空
   - 右栏居中渲染：
     ```
     尚未启用任何模型，请先在设置 → 模型中启用
     [前往设置]
     ```
   - "前往设置"按钮点击逻辑：先 `onOpenChange(false)` 关闭本对话框，再调用 `useSettingsDialogStore.getState().openDialog('models')`。顺序不能反，否则设置对话框会被叠在本对话框之下
2. **搜索无命中**（`q !== ''` 且过滤后 provider 列表为空）
   - 左栏：空
   - 右栏：`没有匹配的模型`（纯文本，**不**渲染"前往设置"按钮，避免把"搜错词"误导成"没启用模型"）

### 3.7 打开/关闭时机

- `ModelPicker` 保留原有的 `const [open, setOpen] = useState(false)`，把 `<Popover>` 改为 `<button onClick={() => setOpen(true)}>` + `<ModelPickerDialog open={open} onOpenChange={setOpen}>`
- mutation 成功后（`onSuccess`）保持对话框已关（`onPick` 已提前调 `onOpenChange(false)`；无需在 onSuccess 里再关一次）

## 4. 数据与依赖

- 数据来源与原 `ModelPicker` 一致：`useQuery(aiQueryKeys.models, fetchModels)` + `useQuery(aiQueryKeys.currentModel, getCurrentModel)` + `useMutation(setCurrentModel)`，不新增请求
- 过滤复用 `filterProvidersBySearch(providers, q, { enabledOnly: true })`
- ID 格式化 / 解析复用 `formatModelId` / `parseModelId`
- 对话框组件复用项目现有 shadcn/ui：`Dialog`, `DialogContent`, `DialogHeader`, `DialogTitle`, `Input`
- 跳转设置：`useSettingsDialogStore`（已有）

## 5. 文件与组件结构

**新增**
- `client/src/features/session/model-picker/model-picker-dialog.tsx`
  - 默认导出 `ModelPickerDialog`，Props：
    ```ts
    type Props = {
      open: boolean
      onOpenChange: (open: boolean) => void
      providers: ProviderDto[]
      currentModelId: string | null
      onPick: (modelId: string) => void
    }
    ```
  - 内部状态：`activeProviderId: string | null`、`q: string`
  - 内部派生：`filteredProviders`（过滤后的 provider 列表）、`activeProvider`（`filteredProviders.find(p => p.id === activeProviderId) ?? filteredProviders[0] ?? null`）
  - `useEffect`：`open` 由 `false` 变 `true` 时，重置 `q = ''` 并按 currentModelId 推导 `activeProviderId`

**修改**
- `client/src/features/session/model-picker/model-picker.tsx`
  - 移除 `Popover/PopoverTrigger/PopoverContent` 与 `ModelPickerPopover` 引入
  - Trigger 改为 `<button type="button" onClick={() => setOpen(true)}>`，视觉类名和结构**一字不变**
  - 渲染 `<ModelPickerDialog open={open} onOpenChange={setOpen} providers={...} currentModelId={...} onPick={(id) => { mut.mutate(id); setOpen(false) }} />`

**删除**
- `client/src/features/session/model-picker/model-picker-popover.tsx`（仅 `model-picker.tsx` 在用，验证无其它引用后删除）

## 6. 测试规划

**更新** `client/src/features/session/model-picker/__tests__/model-picker.test.tsx`：
- 保留"未选中时显示占位符 + 点击后能看到模型"的断言；触发方式由 `PopoverTrigger` 改为 `button`，断言改为查询对话框内文本
- 追加断言：点击模型后 `setCurrentModel` 被正确参数调用且对话框关闭

**新增** `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`：
- `打开时默认选中当前模型所属的 provider`：设置 `currentModelId = openai/gpt-5`，渲染 `<ModelPickerDialog open>`，断言右侧列出 OpenAI 的模型且 `GPT-5` 项带高亮类
- `搜索联动`：输入与 Anthropic 模型名匹配的字串，断言侧边栏只保留 Anthropic，右栏只保留命中项；清空后恢复
- `当前 provider 无匹配时自动切换`：选中 OpenAI 后输入只在 Anthropic 能命中的串，断言右栏切到 Anthropic 结果
- `点击模型触发 onPick 并关闭`：点击模型项后 `onPick` 参数为 `providerId/modelId`，`onOpenChange(false)` 被调用
- `空态`：构造 providers 全部 `connected=false` 或无启用模型 → 右栏显示引导文案；点击"前往设置"按钮后 `onOpenChange(false)` 被调用（不直接断言全局 store，只断关闭）

测试栈沿用 vitest + Testing Library，mock `@/features/settings/shared/api`（与现有测试一致）。

## 7. 迁移与风险

- **快捷键/焦点**：`Dialog` 会自动 focus trap + Esc 关闭，比 Popover 更严格。在对话框关闭前不要让 Composer 的"Enter 发送"意外生效——`Dialog` 默认会拦截 Esc/Enter 在对话框内部，不向外冒泡到 Composer 的 `textarea`。需人工验证一次
- **测试正则**：`model-picker.test.tsx` 的 `fireEvent.click(screen.getByText('选择模型'))` 需确认新的 trigger 仍带 `选择模型` 文案（未选模型时）
- **空态跳转**：`useSettingsDialogStore.openDialog('models')` 打开设置对话框时会叠在本对话框之上；必须先 `onOpenChange(false)` 再开设置，顺序不能反

## 8. 验收清单

- [ ] 触发按钮视觉与改造前逐像素一致
- [ ] 点击触发器打开 960×540 对话框
- [ ] 默认定位到当前模型所属提供商；无当前模型时取第一项
- [ ] 全局搜索同时过滤左右两栏
- [ ] 当前左侧项被过滤掉时自动切到新的第一个
- [ ] 点击模型立即切换并关闭对话框
- [ ] 无可选模型时显示空态 + "前往设置"按钮且能打开设置"模型"段
- [ ] `client/` 下 `npx tsc --noEmit` 零错
- [ ] 新增及更新的 vitest 全部通过
