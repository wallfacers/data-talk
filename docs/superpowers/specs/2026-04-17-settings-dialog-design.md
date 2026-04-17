# Settings 弹窗化设计

> 日期：2026-04-17
> 状态：待实现

---

## 0. 摘要

将当前嵌入页面的 `/settings` 路由改回弹窗模式。用户通过 Sidebar Footer 用户菜单中的"设置"项打开 SettingsDialog 弹窗。删除 `/settings` 路由。弹窗内部采用左侧导航 + 右侧内容的布局。

### 成功标准

1. 用户菜单点击"设置" → 弹出 SettingsDialog
2. 弹窗内左侧导航（桌面：通用 / 服务器：数据源、提供商、模型）
3. 弹窗尺寸 960×540，内容区可滚动
4. 删除 `/settings` 路由及相关引用
5. 各页面功能不变（CRUD、测试连接、模型开关等）

### 非目标

- 不改变各页面内部逻辑
- 不改变后端 API
- 不改变 ModelPicker（session 侧的模型选择器保持 Popover）

---

## 1. 弹窗结构

```
┌───────────────────────────────────────────────┐  960×540
│  设置                                       ✕ │  Header
├──────────────┬────────────────────────────────┤
│  桌面         │  通用设置内容                    │
│  ├── 通用     │                                 │
│              │  ...滚动区域...                   │
│  服务器       │                                 │
│  ├── 数据源   │                                 │
│  ├── 提供商   │                                 │
│  └── 模型     │                                 │
└──────────────┴────────────────────────────────┘
    192px               768px
```

### 1.1 弹窗尺寸

- 宽度：`max-w-[960px]`
- 高度：`max-h-[540px]`
- 理由：数据源页包含表格（ID/类型/地址/数据库/用户/操作），780px 原始尺寸会导致表格列拥挤。960px 宽度分配 192px 给导航，剩余 768px 足够展示表格内容。540px 高度比原始 500px 略高，减少垂直滚动。

---

## 2. 组件架构

### 2.1 新增

| 组件 | 路径 | 描述 |
|------|------|------|
| `SettingsDialog` | `features/settings/settings-dialog.tsx` | Dialog 容器，管理 open state 和 activeSection state |
| `settings-dialog-store` | `features/settings/settings-dialog-store.ts` | Zustand store，管理弹窗开关和当前导航项 |

### 2.2 修改

| 组件 | 路径 | 改动 |
|------|------|------|
| `SettingsNav` | `features/settings/settings-nav.tsx` | `<Link>` 改为 `<button>`，从 URL search params 改为接收 `activeSection` + `onSectionChange` props |
| `SettingsLayout` | `features/settings/settings-layout.tsx` | 从读取 URL search params 改为接收 `activeSection` prop |
| `nav-user` | `features/workspace/components/nav-user.tsx` | `onClick` 从 `navigate({ to: '/settings' })` 改为 `openSettingsDialog()` |
| `__root.tsx` | `routes/__root.tsx` | 添加 `<SettingsDialog />` 全局渲染 |

### 2.3 删除

| 文件 | 原因 |
|------|------|
| `routes/settings.tsx` | 不再需要路由 |

### 2.4 保留不动

| 组件 | 路径 | 原因 |
|------|------|------|
| `GeneralPage` | `features/settings/general/` | 弹窗内容不变 |
| `DataSourcesPage` | `features/settings/data-sources/` | 弹窗内容不变 |
| `ProvidersPage` | `features/settings/providers/` | 弹窗内容不变 |
| `ModelsPage` | `features/settings/models/` | 弹窗内容不变 |
| `shared/api.ts` | `features/settings/shared/` | 数据层不变 |

---

## 3. 状态管理

### 3.1 settings-dialog-store

```ts
// features/settings/settings-dialog-store.ts
import { create } from 'zustand'

type Section = 'general' | 'data-sources' | 'providers' | 'models'

interface SettingsDialogState {
  open: boolean
  activeSection: Section
  openDialog: (section?: Section) => void
  closeDialog: () => void
  setActiveSection: (section: Section) => void
}

export const useSettingsDialogStore = create<SettingsDialogState>((set) => ({
  open: false,
  activeSection: 'general',
  openDialog: (section) => set({ open: true, activeSection: section ?? 'general' }),
  closeDialog: () => set({ open: false }),
  setActiveSection: (section) => set({ activeSection: section }),
}))
```

### 3.2 nav-user 集成

```tsx
// nav-user.tsx 中的设置菜单项
import { useSettingsDialogStore } from '@/features/settings/settings-dialog-store'

const openSettingsDialog = useSettingsDialogStore((s) => s.openDialog)

<DropdownMenuItem onClick={() => openSettingsDialog()}>
  <SettingsIcon />
  <span>设置</span>
</DropdownMenuItem>
```

---

## 4. 组件实现

### 4.1 SettingsDialog

```tsx
// features/settings/settings-dialog.tsx
import { useEffect } from 'react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { SettingsNav } from './settings-nav'
import { GeneralPage } from './general/general-page'
import { DataSourcesPage } from './data-sources/data-sources-page'
import { ProvidersPage } from './providers/providers-page'
import { ModelsPage } from './models/models-page'
import { useSettingsDialogStore } from './settings-dialog-store'

const PAGE_BY_SECTION = {
  'general': <GeneralPage />,
  'data-sources': <DataSourcesPage />,
  'providers': <ProvidersPage />,
  'models': <ModelsPage />,
} as const

export function SettingsDialog() {
  const { open, activeSection, closeDialog, setActiveSection } = useSettingsDialogStore()

  // ESC 关闭时重置状态
  useEffect(() => {
    if (!open) return
    return () => setActiveSection('general')
  }, [open])

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && closeDialog()}>
      <DialogContent className="flex flex-col !p-0 overflow-hidden max-w-[960px] max-h-[540px] sm:max-w-[960px]">
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle className="text-lg font-medium">设置</DialogTitle>
        </DialogHeader>
        <div className="flex flex-1 overflow-hidden" style={{ height: '500px' }}>
          <SettingsNav activeSection={activeSection} onSectionChange={setActiveSection} />
          <main className="flex-1 overflow-y-auto p-6">
            {PAGE_BY_SECTION[activeSection]}
          </main>
        </div>
      </DialogContent>
    </Dialog>
  )
}
```

### 4.2 SettingsNav 改造

```tsx
// features/settings/settings-nav.tsx（改造后）
import { cn } from '@/lib/utils'
import { Settings, Database, Box, Sparkles } from 'lucide-react'

type Section = 'general' | 'data-sources' | 'providers' | 'models'

const GROUPS: { title: string; items: { key: Section; label: string; icon: React.ComponentType<{ className?: string }> }[] }[] = [
  { title: '桌面', items: [
    { key: 'general', label: '通用', icon: Settings },
  ]},
  { title: '服务器', items: [
    { key: 'data-sources', label: '数据源', icon: Database },
    { key: 'providers', label: '提供商', icon: Box },
    { key: 'models', label: '模型', icon: Sparkles },
  ]},
]

interface SettingsNavProps {
  activeSection: Section
  onSectionChange: (section: Section) => void
}

export function SettingsNav({ activeSection, onSectionChange }: SettingsNavProps) {
  return (
    <nav className="flex w-48 flex-col gap-4 p-4 text-sm overflow-y-auto">
      {GROUPS.map(g => (
        <div key={g.title}>
          <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
          {g.items.map(it => {
            const Icon = it.icon
            return (
              <button
                key={it.key}
                onClick={() => onSectionChange(it.key)}
                className={cn(
                  'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left',
                  activeSection === it.key && 'bg-accent font-medium',
                )}
              >
                <Icon className="size-4" />{it.label}
              </button>
            )
          })}
        </div>
      ))}
    </nav>
  )
}
```

---

## 5. 错误处理

- Dialog 关闭后，`activeSection` 重置为 `general`，下次打开从通用页开始
- 各页面内部的 QueryClient 状态保持独立，不受弹窗开关影响
- TanStack Query 缓存不受影响（QueryClient 在 App 层级）

---

## 6. 测试

- `settings-dialog.test.tsx`：验证弹窗打开/关闭、导航切换、各页面渲染
- `settings-nav.test.tsx`：验证 `<button>` 点击触发 `onSectionChange`
- 现有各页面测试不变
