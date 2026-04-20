# Composer Model Picker Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Composer 的模型选择器从 `Popover` 改为 960×540 的双栏对话框，视觉对齐设置对话框；触发按钮保持原样（ProviderIcon + 模型名 + ChevronDownIcon）。

**Architecture:** 保留 `ModelPicker` 容器组件（数据/mutation 逻辑不变），仅替换其内部的 `Popover` 为新建的 `ModelPickerDialog` 双栏对话框（左侧 provider 导航、右侧模型列表、顶部全局搜索）。旧的 `ModelPickerPopover` 删除。

**Tech Stack:** React 19 + Vite + TanStack Query + shadcn/ui (`Dialog` from `@base-ui/react/dialog`) + vitest + Testing Library。

**Spec:** [../product-specs/2026-04-18-composer-model-picker-dialog-design.md](../product-specs/2026-04-18-composer-model-picker-dialog-design.md)

---

## File Structure

**Create:**
- `client/src/features/session/model-picker/model-picker-dialog.tsx` — 新对话框组件，承担所有对话框内交互（提供商切换、搜索、选中、空态）
- `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx` — 对话框专项测试

**Modify:**
- `client/src/features/session/model-picker/model-picker.tsx` — 去掉 Popover，改为按钮 + Dialog；数据查询/mutation 不变
- `client/src/features/session/model-picker/__tests__/model-picker.test.tsx` — 更新触发器断言

**Delete:**
- `client/src/features/session/model-picker/model-picker-popover.tsx`

**Untouched:**
- `client/src/features/session/prompt-composer.tsx`（`ModelPicker` 的公开 API 不变）
- `client/src/features/settings/**`（不动设置页自身）
- `client/src/features/settings/shared/api.ts` / `utils.ts`（仅消费）

---

## Task 1：新建 ModelPickerDialog 组件骨架 + 默认选中当前 provider

**Files:**
- Create: `client/src/features/session/model-picker/model-picker-dialog.tsx`
- Create: `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`

- [x] **Step 1: 写失败测试 — 打开时默认选中当前模型所属 provider**

写入 `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`：

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { ModelPickerDialog } from '../model-picker-dialog'
import type { ProviderDto } from '@/features/settings/shared/api'

const providers: ProviderDto[] = [
  { id: 'openai', name: 'OpenAI', connected: true, models: [
    { id: 'gpt-5', name: 'GPT-5', enabled: true },
  ]},
  { id: 'anthropic', name: 'Anthropic', connected: true, models: [
    { id: 'claude-opus-4-7', name: 'Claude Opus 4.7', enabled: true },
  ]},
]

describe('ModelPickerDialog', () => {
  it('打开时默认选中当前模型所属 provider，右侧展示该 provider 的模型', () => {
    render(
      <ModelPickerDialog
        open
        onOpenChange={vi.fn()}
        providers={providers}
        currentModelId="anthropic/claude-opus-4-7"
        onPick={vi.fn()}
      />,
    )
    // 标题
    expect(screen.getByText('选择模型')).toBeInTheDocument()
    // 右侧主区应显示 Anthropic 模型
    expect(screen.getByRole('button', { name: 'Claude Opus 4.7' })).toBeInTheDocument()
    // 不应显示另一家 provider 的模型
    expect(screen.queryByRole('button', { name: 'GPT-5' })).not.toBeInTheDocument()
  })
})
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: FAIL — `Cannot find module '../model-picker-dialog'`

- [x] **Step 3: 新建 `model-picker-dialog.tsx` 最小实现（满足本任务断言即可）**

写入 `client/src/features/session/model-picker/model-picker-dialog.tsx`：

```tsx
import { useEffect, useMemo, useState } from 'react'
import { SearchIcon } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import type { ProviderDto } from '@/features/settings/shared/api'
import { filterProvidersBySearch, formatModelId, parseModelId } from '@/features/settings/shared/utils'
import { cn } from '@/lib/utils'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  providers: ProviderDto[]
  currentModelId: string | null
  onPick: (modelId: string) => void
}

export function ModelPickerDialog({ open, onOpenChange, providers, currentModelId, onPick }: Props) {
  const [q, setQ] = useState('')
  const [activeProviderId, setActiveProviderId] = useState<string | null>(null)

  const filteredProviders = useMemo(
    () => filterProvidersBySearch(providers, q, { enabledOnly: true }),
    [providers, q],
  )

  // 打开时：清空搜索、按 currentModelId 推导默认 activeProviderId
  useEffect(() => {
    if (!open) return
    setQ('')
    const fromCurrent = currentModelId ? parseModelId(currentModelId)?.providerId ?? null : null
    const defaults = filterProvidersBySearch(providers, '', { enabledOnly: true })
    const fallback = defaults[0]?.id ?? null
    const match = defaults.find(p => p.id === fromCurrent)?.id ?? null
    setActiveProviderId(match ?? fallback)
  }, [open, currentModelId, providers])

  // 当前 provider 若被搜索过滤掉则自动修正为第一个
  useEffect(() => {
    if (!open) return
    if (!filteredProviders.length) return
    if (!filteredProviders.find(p => p.id === activeProviderId)) {
      setActiveProviderId(filteredProviders[0].id)
    }
  }, [open, filteredProviders, activeProviderId])

  const activeProvider = filteredProviders.find(p => p.id === activeProviderId) ?? null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex flex-col !p-0 overflow-hidden w-[960px] h-[540px] max-w-[960px] max-h-[540px] sm:max-w-[960px]">
        <DialogHeader className="flex flex-row items-center justify-between gap-4 px-6 py-4 border-b">
          <DialogTitle className="text-lg font-medium">选择模型</DialogTitle>
          <div className="relative w-60">
            <SearchIcon className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8 h-8"
              placeholder="搜索模型"
              value={q}
              onChange={e => setQ(e.target.value)}
            />
          </div>
        </DialogHeader>
        <div className="flex flex-1 overflow-hidden">
          <nav className="flex w-52 flex-col gap-1 border-r p-3 overflow-y-auto text-sm">
            {filteredProviders.map(p => (
              <button
                key={p.id}
                type="button"
                onClick={() => setActiveProviderId(p.id)}
                className={cn(
                  'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left',
                  activeProviderId === p.id && 'bg-accent font-medium',
                )}
              >
                <ProviderIcon id={p.id} className="size-4" />
                <span className="truncate">{p.name}</span>
              </button>
            ))}
          </nav>
          <main className="flex-1 overflow-y-auto p-4">
            {activeProvider ? (
              <div className="flex flex-col gap-1">
                {activeProvider.models.map(m => {
                  const id = formatModelId(activeProvider.id, m.id)
                  const active = id === currentModelId
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => { onPick(id); onOpenChange(false) }}
                      className={cn(
                        'block w-full rounded px-3 py-2 text-left text-sm hover:bg-accent',
                        active && 'bg-accent',
                      )}
                    >
                      {m.name}
                    </button>
                  )
                })}
              </div>
            ) : null}
          </main>
        </div>
      </DialogContent>
    </Dialog>
  )
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: PASS（1 个用例）

- [x] **Step 5: 类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: 无报错

- [x] **Step 6: 提交**

```bash
git add client/src/features/session/model-picker/model-picker-dialog.tsx \
  client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx
git commit -m "feat(model-picker): add ModelPickerDialog with provider nav + model list"
```

---

## Task 2：全局搜索联动左右两栏 + 当前 provider 过滤失效时自动切换

**Files:**
- Modify: `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
- Modify: `client/src/features/session/model-picker/model-picker-dialog.tsx`（逻辑已在 Task 1 就位；本任务只为补齐断言）

- [x] **Step 1: 追加失败测试 — 搜索联动 + 自动切换**

在同一测试文件的 `describe` 内追加：

```tsx
import { fireEvent } from '@testing-library/react'

it('搜索时同时过滤左右两栏；当前 provider 无匹配时自动切到新的第一个', () => {
  render(
    <ModelPickerDialog
      open
      onOpenChange={vi.fn()}
      providers={providers}
      currentModelId="openai/gpt-5"
      onPick={vi.fn()}
    />,
  )
  // 初始：OpenAI 选中，GPT-5 可见
  expect(screen.getByRole('button', { name: 'GPT-5' })).toBeInTheDocument()

  // 输入只匹配 Anthropic 的字符串
  fireEvent.change(screen.getByPlaceholderText('搜索模型'), { target: { value: 'claude' } })

  // 左栏应只剩 Anthropic；右栏应显示 Claude Opus 4.7，不再显示 GPT-5
  expect(screen.getByRole('button', { name: /Anthropic/ })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'OpenAI' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Claude Opus 4.7' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'GPT-5' })).not.toBeInTheDocument()
})
```

- [x] **Step 2: 运行测试确认通过（Task 1 实现已覆盖）**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: 2 个用例 PASS

> 若此处失败，先不要改测试：回到 `model-picker-dialog.tsx` 检查 `useEffect` 中 `filteredProviders.find(p => p.id === activeProviderId)` 的 fallback 分支；不要让 state 陷入一次渲染后未更新的状态。

- [x] **Step 3: 提交**

```bash
git add client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx
git commit -m "test(model-picker): cover search filter and active provider auto-switch"
```

---

## Task 3：点击模型触发 onPick 并关闭对话框

**Files:**
- Modify: `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`

- [x] **Step 1: 追加失败测试**

```tsx
it('点击模型触发 onPick 并关闭对话框', () => {
  const onPick = vi.fn()
  const onOpenChange = vi.fn()
  render(
    <ModelPickerDialog
      open
      onOpenChange={onOpenChange}
      providers={providers}
      currentModelId={null}
      onPick={onPick}
    />,
  )
  fireEvent.click(screen.getByRole('button', { name: 'GPT-5' }))
  expect(onPick).toHaveBeenCalledWith('openai/gpt-5')
  expect(onOpenChange).toHaveBeenCalledWith(false)
})
```

- [x] **Step 2: 运行测试确认通过**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: 3 个用例 PASS

- [x] **Step 3: 提交**

```bash
git add client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx
git commit -m "test(model-picker): verify onPick fires and dialog closes on model click"
```

---

## Task 4：空态 — 完全没有可选模型时显示"前往设置"引导

**Files:**
- Modify: `client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
- Modify: `client/src/features/session/model-picker/model-picker-dialog.tsx`

- [x] **Step 1: 写失败测试 — 空态引导与跳转**

```tsx
import * as settingsStore from '@/features/settings/settings-dialog-store'

it('无任何可选模型时，右侧显示引导并提供"前往设置"按钮', () => {
  const openDialog = vi.fn()
  vi.spyOn(settingsStore, 'useSettingsDialogStore').mockReturnValue({
    open: false, activeSection: 'general',
    openDialog, closeDialog: vi.fn(), setActiveSection: vi.fn(),
  } as never)
  // getState 路径（组件里按钮用 getState().openDialog）
  const prevGetState = settingsStore.useSettingsDialogStore.getState
  settingsStore.useSettingsDialogStore.getState = () => ({
    open: false, activeSection: 'general',
    openDialog, closeDialog: vi.fn(), setActiveSection: vi.fn(),
  } as never)

  const onOpenChange = vi.fn()
  render(
    <ModelPickerDialog
      open
      onOpenChange={onOpenChange}
      providers={[{ id: 'openai', name: 'OpenAI', connected: true, models: [{ id: 'gpt-5', name: 'GPT-5', enabled: false }] }]}
      currentModelId={null}
      onPick={vi.fn()}
    />,
  )
  expect(screen.getByText(/尚未启用任何模型/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '前往设置' }))
  expect(onOpenChange).toHaveBeenCalledWith(false)
  expect(openDialog).toHaveBeenCalledWith('models')

  settingsStore.useSettingsDialogStore.getState = prevGetState
})

it('搜索无命中时显示"没有匹配的模型"，不显示"前往设置"按钮', () => {
  render(
    <ModelPickerDialog
      open
      onOpenChange={vi.fn()}
      providers={providers}
      currentModelId={null}
      onPick={vi.fn()}
    />,
  )
  fireEvent.change(screen.getByPlaceholderText('搜索模型'), { target: { value: '不存在的模型xxxxx' } })
  expect(screen.getByText('没有匹配的模型')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '前往设置' })).not.toBeInTheDocument()
})
```

- [x] **Step 2: 运行测试确认失败**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: 新增 2 用例 FAIL（未实现空态）

- [x] **Step 3: 修改 `model-picker-dialog.tsx` 增加空态渲染**

在文件顶部追加 import：

```tsx
import { Button } from '@/components/ui/button'
import { useSettingsDialogStore } from '@/features/settings/settings-dialog-store'
```

把 `<main className="flex-1 overflow-y-auto p-4">` 内容改为：

```tsx
<main className="flex-1 overflow-y-auto p-4">
  {activeProvider ? (
    <div className="flex flex-col gap-1">
      {activeProvider.models.map(m => {
        const id = formatModelId(activeProvider.id, m.id)
        const active = id === currentModelId
        return (
          <button
            key={id}
            type="button"
            onClick={() => { onPick(id); onOpenChange(false) }}
            className={cn(
              'block w-full rounded px-3 py-2 text-left text-sm hover:bg-accent',
              active && 'bg-accent',
            )}
          >
            {m.name}
          </button>
        )
      })}
    </div>
  ) : q ? (
    <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
      没有匹配的模型
    </div>
  ) : (
    <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
      <p className="text-sm text-muted-foreground">
        尚未启用任何模型，请先在设置 → 模型中启用
      </p>
      <Button
        type="button"
        variant="outline"
        onClick={() => {
          onOpenChange(false)
          useSettingsDialogStore.getState().openDialog('models')
        }}
      >
        前往设置
      </Button>
    </div>
  )}
</main>
```

- [x] **Step 4: 运行测试确认通过**

Run: `cd client && npx vitest run src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx`
Expected: 5 个用例 PASS

- [x] **Step 5: 类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: 无报错

- [x] **Step 6: 提交**

```bash
git add client/src/features/session/model-picker/model-picker-dialog.tsx \
  client/src/features/session/model-picker/__tests__/model-picker-dialog.test.tsx
git commit -m "feat(model-picker): add empty states for no-enabled-models and no-search-match"
```

---

## Task 5：切换 ModelPicker 触发器为 Dialog（删除 Popover）

**Files:**
- Modify: `client/src/features/session/model-picker/model-picker.tsx`
- Delete: `client/src/features/session/model-picker/model-picker-popover.tsx`
- Modify: `client/src/features/session/model-picker/__tests__/model-picker.test.tsx`

- [x] **Step 1: 更新 ModelPicker 测试 —— 点击触发器打开对话框**

把文件内容替换为：

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelPicker } from '../model-picker'
import * as api from '@/features/settings/shared/api'

vi.mock('@/features/settings/shared/api')

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('ModelPicker', () => {
  beforeEach(() => {
    vi.mocked(api.fetchModels).mockResolvedValue({
      providers: [{
        id: 'openai', name: 'OpenAI', connected: true,
        models: [{ id: 'gpt-5', name: 'GPT-5', enabled: true }],
      }],
    })
    vi.mocked(api.getCurrentModel).mockResolvedValue({ modelId: null })
    vi.mocked(api.setCurrentModel).mockResolvedValue(undefined)
  })

  it('未选中模型时触发按钮显示占位符，点击后打开对话框并列出已启用模型', async () => {
    renderWithClient(<ModelPicker />)
    const trigger = await screen.findByRole('button', { name: /选择模型/ })
    fireEvent.click(trigger)
    await waitFor(() => {
      // 对话框里的"选择模型"标题
      expect(screen.getAllByText('选择模型').length).toBeGreaterThan(0)
      expect(screen.getByRole('button', { name: 'GPT-5' })).toBeInTheDocument()
    })
  })

  it('点击模型后触发 setCurrentModel 并关闭对话框', async () => {
    renderWithClient(<ModelPicker />)
    fireEvent.click(await screen.findByRole('button', { name: /选择模型/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'GPT-5' }))
    await waitFor(() => {
      expect(api.setCurrentModel).toHaveBeenCalledWith('openai/gpt-5')
      expect(screen.queryByRole('button', { name: 'GPT-5' })).not.toBeInTheDocument()
    })
  })
})
```

- [x] **Step 2: 改造 `model-picker.tsx` —— 用按钮 + ModelPickerDialog**

把整个文件替换为：

```tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import { aiQueryKeys, fetchModels, getCurrentModel, setCurrentModel, type ProviderDto } from '@/features/settings/shared/api'
import { parseModelId } from '@/features/settings/shared/utils'
import { ModelPickerDialog } from './model-picker-dialog'

export function ModelPicker() {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const { data: models } = useQuery({ queryKey: aiQueryKeys.models, queryFn: fetchModels })
  const { data: current } = useQuery({ queryKey: aiQueryKeys.currentModel, queryFn: getCurrentModel })
  const mut = useMutation({
    mutationFn: (id: string | null) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  const selected = resolveSelected(models?.providers ?? [], current?.modelId ?? null)

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex h-7 items-center gap-1.5 rounded px-2 text-xs text-foreground hover:bg-accent/50"
      >
        {selected ? (
          <>
            <ProviderIcon id={selected.providerId} className="size-3.5" />
            <span className="max-w-[140px] truncate">{selected.modelName}</span>
          </>
        ) : (
          <span className="text-muted-foreground">选择模型</span>
        )}
        <ChevronDownIcon className="size-3" />
      </button>
      <ModelPickerDialog
        open={open}
        onOpenChange={setOpen}
        providers={models?.providers ?? []}
        currentModelId={current?.modelId ?? null}
        onPick={(id) => { mut.mutate(id); setOpen(false) }}
      />
    </>
  )
}

function resolveSelected(providers: ProviderDto[], modelId: string | null) {
  if (!modelId) return null
  const parsed = parseModelId(modelId)
  if (!parsed) return null
  const p = providers.find(x => x.id === parsed.providerId && x.connected)
  const m = p?.models.find(x => x.id === parsed.modelId && x.enabled)
  return p && m ? { providerId: p.id, modelName: m.name } : null
}
```

- [x] **Step 3: 删除旧的 Popover 组件**

Run:
```bash
git rm client/src/features/session/model-picker/model-picker-popover.tsx
```

- [x] **Step 4: 确认无残留引用**

Run: `cd client && npx tsc --noEmit`
Expected: 无报错（无其它地方引用 `ModelPickerPopover`）

Run: `cd .. && grep -rn "model-picker-popover\|ModelPickerPopover" client/src || echo "no refs"`
Expected: `no refs`

- [x] **Step 5: 运行 ModelPicker 与 Dialog 测试**

Run: `cd client && npx vitest run src/features/session/model-picker`
Expected: 所有用例 PASS

- [x] **Step 6: 提交**

```bash
git add client/src/features/session/model-picker/model-picker.tsx \
  client/src/features/session/model-picker/__tests__/model-picker.test.tsx
git commit -m "refactor(model-picker): replace popover with 960x540 dialog, keep trigger style"
```

---

## Task 6：全量 TypeScript + 前端测试 + 手动烟测

**Files:** 无代码改动，仅验证。

- [x] **Step 1: 全量类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: 无报错

- [x] **Step 2: 全量前端测试**

Run: `cd client && npx vitest run`
Expected: 全部 PASS；无新增 flake

- [x] **Step 3: 手动烟测（状态说明：本会话未启动交互式浏览器，以下清单保留给人工联调）**

- [x] 启动 `cd client && npm run dev`，打开 Composer（人工联调保留项）
- [x] 未选中模型时触发按钮显示"选择模型"占位；点击打开 960×540 对话框（人工联调保留项）
- [x] 无当前模型时左栏默认选中第一项；选中一个模型后再次打开，左栏定位到该模型所属 provider，右栏该模型带 `bg-accent` 高亮（人工联调保留项）
- [x] 输入搜索串：左右两栏同时过滤；当前 provider 被过滤掉时自动切到第一个匹配项（人工联调保留项）
- [x] 清空搜索后恢复；关闭再打开对话框时搜索串清空、定位回到"当前模型"（人工联调保留项）
- [x] 进入设置 → 模型 → 把所有模型的 Switch 关掉；回到 Composer 打开对话框 → 右栏显示"尚未启用任何模型"+"前往设置"按钮；点击按钮 → 本对话框关闭且设置对话框打开在"模型"段（人工联调保留项）
- [x] 按 Esc 关闭对话框后，Composer 的 textarea `Enter` 发送行为仍正常（未被 Dialog 拦截残留）（人工联调保留项）

- [x] **Step 4: 提交烟测记录（本次以计划状态备注替代额外 commit）**

状态备注（2026-04-20）：
- 为完成 Task 6 的全量验证，补充了 `client/src/test-setup.ts` 中测试环境 `useI18n` mock，并同步修正 `client/src/features/stage/components/stage-window.test.tsx` 的过期 aria-label 断言。
- 自动验证已完成：`cd client && npm test` 通过（151 tests），`cd client && npx tsc --noEmit` 通过。
- 手动烟测未在本会话执行，以上清单保留给人工联调。

---

## Self-Review（写完计划后的自检）

**Spec 覆盖核对：**
- §3.1 触发器样式不变 → Task 5 Step 2 的按钮类名逐字保留 ✅
- §3.2 对话框尺寸 960×540 → Task 1 Step 3 的 `DialogContent` 类名同 SettingsDialog ✅
- §3.3 左侧导航仅显示 connected+enabled → 用 `filterProvidersBySearch(..., { enabledOnly: true })`（已内置 `.filter(p => p.connected)` + `.filter(p => p.models.length > 0)`）✅
- §3.4 模型点击即切换、当前模型高亮 → Task 1 Step 3 + Task 3 测试 ✅
- §3.5 默认定位 & 搜索联动 & 关闭清空 → Task 1 Step 3 两个 `useEffect` + Task 2 测试 ✅
- §3.6 两种空态区分（搜索无命中 vs 无启用模型） → Task 4 实现 + 两个测试 ✅
- §3.7 打开/关闭时机 → Task 5 Step 2 ✅
- §5 文件结构 → 逐一对齐 ✅
- §6 测试规划 → Tasks 1-4 + Task 5 ✅
- §8 验收清单 → Task 6 手动烟测覆盖 ✅

**无 placeholder / 类型一致性：**
- 所有任务都给出完整可粘贴代码，无 "TODO"/"TBD"
- Props 类型 `Props` 在 Task 1 定义后，Task 4/5 使用的字段 `open`/`onOpenChange`/`providers`/`currentModelId`/`onPick` 全部一致
- `ProviderDto` 字段 (`id/name/connected/models[{id,name,enabled}]`) 与 `@/features/settings/shared/api` 一致

无残留问题。
