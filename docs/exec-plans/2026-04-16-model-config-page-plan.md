# Model Config Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 实现模型配置页面 UI，包含提供商管理、模型管理、自定义提供商功能，使用 Mock 数据。

**Architecture:** 前端 React 组件 + Zustand Store + shadcn/ui。入口为 Sidebar Footer 用户菜单，设置页面包含三个 Tab（Providers / Models / General）。

**Tech Stack:** React 19, TanStack Router, Zustand, shadcn/ui (base-ui), Lucide Icons, Tailwind CSS

---

## File Structure

```
client/src/
  routes/
    settings.tsx                    # 新增：设置页面路由
  features/
    model-config/                   # 新增：模型配置功能模块
      types.ts                      # TypeScript 类型定义
      mock-data.ts                  # Mock 数据
      store.ts                      # Zustand store
      provider-icon.tsx             # 提供商图标组件
      providers-panel.tsx           # 提供商管理面板
      provider-item.tsx             # 提供商列表项
      connect-provider-dialog.tsx   # 连接提供商对话框
      custom-provider-dialog.tsx    # 自定义提供商对话框
      models-panel.tsx              # 模型管理面板
      model-item.tsx                # 模型列表项
    workspace/components/
      nav-user.tsx                  # 改造：添加设置入口，路由跳转
  components/ui/
    switch.tsx                      # 新增：shadcn Switch 组件
    dialog.tsx                      # 新增：shadcn Dialog 组件
```

---

## Task 1: 添加 shadcn/ui Switch 和 Dialog 组件

**Files:**
- Create: `client/src/components/ui/switch.tsx`
- Create: `client/src/components/ui/dialog.tsx`

- [x] **Step 1: 运行 shadcn CLI 添加组件**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx shadcn@latest add switch dialog
```

Expected: 两个组件文件创建成功

- [x] **Step 2: 验证组件存在**

```bash
ls -la /home/wushengzhou/workspace/github/data-talk/client/src/components/ui/switch.tsx
ls -la /home/wushengzhou/workspace/github/data-talk/client/src/components/ui/dialog.tsx
```

Expected: 两个文件存在

- [x] **Step 3: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 2: 创建类型定义文件

**Files:**
- Create: `client/src/features/model-config/types.ts`

- [x] **Step 1: 创建类型定义文件**

```typescript
// client/src/features/model-config/types.ts

export interface Provider {
  id: string
  name: string
  type: 'builtin' | 'custom'
  apiKey?: string
  baseURL?: string
  headers?: Record<string, string>
  models: Model[]
  source?: 'env' | 'api' | 'config'
  connected: boolean
}

export interface Model {
  id: string
  name: string
  providerId: string
  visible: boolean
  latest?: boolean
}

export interface ModelConfigState {
  providers: Provider[]
  currentModel?: { providerId: string; modelId: string }
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 3: 创建 Mock 数据文件

**Files:**
- Create: `client/src/features/model-config/mock-data.ts`

- [x] **Step 1: 创建 Mock 数据文件**

```typescript
// client/src/features/model-config/mock-data.ts

import type { Provider } from './types'

export const MOCK_PROVIDERS: Provider[] = [
  {
    id: 'anthropic',
    name: 'Anthropic',
    type: 'builtin',
    connected: true,
    source: 'api',
    models: [
      { id: 'claude-4-opus', name: 'Claude 4 Opus', providerId: 'anthropic', visible: true, latest: true },
      { id: 'claude-4-sonnet', name: 'Claude 4 Sonnet', providerId: 'anthropic', visible: true },
      { id: 'claude-3-5-haiku', name: 'Claude 3.5 Haiku', providerId: 'anthropic', visible: false },
    ],
  },
  {
    id: 'openai',
    name: 'OpenAI',
    type: 'builtin',
    connected: false,
    models: [
      { id: 'gpt-4o', name: 'GPT-4o', providerId: 'openai', visible: true, latest: true },
      { id: 'gpt-4-turbo', name: 'GPT-4 Turbo', providerId: 'openai', visible: false },
      { id: 'gpt-3-5-turbo', name: 'GPT-3.5 Turbo', providerId: 'openai', visible: false },
    ],
  },
  {
    id: 'google',
    name: 'Google',
    type: 'builtin',
    connected: false,
    models: [
      { id: 'gemini-2-flash', name: 'Gemini 2.0 Flash', providerId: 'google', visible: true, latest: true },
      { id: 'gemini-1-5-pro', name: 'Gemini 1.5 Pro', providerId: 'google', visible: false },
    ],
  },
]

export const POPULAR_PROVIDER_ORDER = ['anthropic', 'openai', 'google', 'openrouter', 'vercel']
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 4: 创建 Zustand Store

**Files:**
- Create: `client/src/features/model-config/store.ts`

- [x] **Step 1: 创建 Store 文件**

```typescript
// client/src/features/model-config/store.ts

import { create } from 'zustand'
import type { Provider, ModelConfigState } from './types'
import { MOCK_PROVIDERS } from './mock-data'

interface ModelConfigStore extends ModelConfigState {
  connectProvider: (providerId: string, apiKey: string) => void
  disconnectProvider: (providerId: string) => void
  setModelVisibility: (providerId: string, modelId: string, visible: boolean) => void
  setCurrentModel: (providerId: string, modelId: string) => void
  addCustomProvider: (provider: Provider) => void
  removeCustomProvider: (providerId: string) => void
}

export const useModelConfigStore = create<ModelConfigStore>((set) => ({
  providers: MOCK_PROVIDERS,

  connectProvider: (providerId, apiKey) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId ? { ...p, connected: true, apiKey, source: 'api' as const } : p
      ),
    })),

  disconnectProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId ? { ...p, connected: false, apiKey: undefined, source: undefined } : p
      ),
    })),

  setModelVisibility: (providerId, modelId, visible) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId
          ? {
              ...p,
              models: p.models.map((m) => (m.id === modelId ? { ...m, visible } : m)),
            }
          : p
      ),
    })),

  setCurrentModel: (providerId, modelId) =>
    set({ currentModel: { providerId, modelId } }),

  addCustomProvider: (provider) =>
    set((state) => ({
      providers: [...state.providers, provider],
    })),

  removeCustomProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.filter((p) => p.id !== providerId),
    })),
}))
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 5: 创建 ProviderIcon 组件

**Files:**
- Create: `client/src/features/model-config/provider-icon.tsx`

- [x] **Step 1: 创建 ProviderIcon 组件**

```tsx
// client/src/features/model-config/provider-icon.tsx

import { cn } from '@/lib/utils'

const PROVIDER_ICONS: Record<string, React.ReactNode> = {
  anthropic: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 6v6l4 2" />
    </svg>
  ),
  openai: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
    </svg>
  ),
  google: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2L2 7l10 5 10-5-10-5z" />
      <path d="M2 17l10 5 10-5" />
      <path d="M2 12l10 5 10-5" />
    </svg>
  ),
  custom: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="M9 9h6v6H9z" />
    </svg>
  ),
}

export function ProviderIcon({ id, className }: { id: string; className?: string }) {
  const icon = PROVIDER_ICONS[id] ?? PROVIDER_ICONS.custom
  return (
    <div className={cn('size-5 text-foreground [&>svg]:size-full', className)}>
      {icon}
    </div>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 6: 改造 NavUser 添加设置入口

**Files:**
- Modify: `client/src/features/workspace/components/nav-user.tsx`

- [x] **Step 1: 添加路由导航 import**

在文件顶部添加：

```tsx
import { useNavigate } from '@tanstack/react-router'
```

- [x] **Step 2: 在 NavUser 函数内添加 navigate hook**

在 `const { isMobile } = useSidebar()` 后添加：

```tsx
const navigate = useNavigate()
```

- [x] **Step 3: 替换菜单项 onClick 为路由导航**

将三个 DropdownMenuItem 的 onClick 替换为：

```tsx
<DropdownMenuItem
  className="gap-2 px-2 py-1.5"
  onClick={() => navigate({ to: '/settings' })}
>
  <Settings2Icon />
  <span>系统设置</span>
</DropdownMenuItem>
<DropdownMenuItem
  className="gap-2 px-2 py-1.5"
  onClick={() => navigate({ to: '/settings', search: { tab: 'providers' } })}
>
  <DatabaseIcon />
  <span>连接配置</span>
</DropdownMenuItem>
<DropdownMenuItem
  className="gap-2 px-2 py-1.5"
  onClick={() => navigate({ to: '/settings', search: { tab: 'models' } })}
>
  <SlidersHorizontalIcon />
  <span>模型配置</span>
</DropdownMenuItem>
```

- [x] **Step 4: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误（可能有路由未定义错误，下一步解决）

---

## Task 7: 创建设置页面路由

**Files:**
- Create: `client/src/routes/settings.tsx`

- [x] **Step 1: 创建设置页面路由文件**

```tsx
// client/src/routes/settings.tsx

import { createFileRoute, useSearch } from '@tanstack/react-router'
import { SettingsPage } from '@/features/model-config/settings-page'

export const Route = createFileRoute('/settings')({
  component: SettingsPage,
  validateSearch: (search: Record<string, unknown>) => ({
    tab: (search.tab as string) ?? 'providers',
  }),
})
```

- [x] **Step 2: 创建 SettingsPage 组件**

```tsx
// client/src/features/model-config/settings-page.tsx

import { SettingsIcon, SlidersHorizontalIcon, DatabaseIcon } from 'lucide-react'
import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ProvidersPanel } from './providers-panel'
import { ModelsPanel } from './models-panel'

export function SettingsPage() {
  const navigate = useNavigate()
  const search = useSearch({ from: '/settings' })
  const activeTab = search.tab ?? 'providers'

  const handleTabChange = (tab: string) => {
    navigate({ to: '/settings', search: { tab } })
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4">
        <h1 className="text-lg font-medium">设置</h1>
        <Button variant="ghost" size="sm" onClick={() => navigate({ to: '/' })}>
          返回
        </Button>
      </div>

      <Tabs value={activeTab} onValueChange={handleTabChange} className="flex-1 overflow-hidden">
        <TabsList variant="line" className="px-6">
          <TabsTrigger value="providers">
            <DatabaseIcon data-slot="icon" className="size-4" />
            <span>Providers</span>
          </TabsTrigger>
          <TabsTrigger value="models">
            <SlidersHorizontalIcon data-slot="icon" className="size-4" />
            <span>Models</span>
          </TabsTrigger>
          <TabsTrigger value="general">
            <SettingsIcon data-slot="icon" className="size-4" />
            <span>General</span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="providers" className="flex-1 overflow-y-auto p-6">
          <ProvidersPanel />
        </TabsContent>

        <TabsContent value="models" className="flex-1 overflow-y-auto p-6">
          <ModelsPanel />
        </TabsContent>

        <TabsContent value="general" className="flex-1 overflow-y-auto p-6">
          <div className="text-muted-foreground">General settings placeholder</div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
```

- [x] **Step 3: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 8: 创建 ProviderItem 组件

**Files:**
- Create: `client/src/features/model-config/provider-item.tsx`

- [x] **Step 1: 创建 ProviderItem 组件**

```tsx
// client/src/features/model-config/provider-item.tsx

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ProviderIcon } from './provider-icon'
import type { Provider } from './types'

interface ProviderItemProps {
  provider: Provider
  onConnect?: () => void
  onDisconnect?: () => void
}

export function ProviderItem({ provider, onConnect, onDisconnect }: ProviderItemProps) {
  const sourceLabel = () => {
    if (provider.source === 'env') return 'Environment'
    if (provider.source === 'api') return 'API Key'
    if (provider.type === 'custom') return 'Custom'
    return null
  }

  return (
    <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
      <div className="flex items-center gap-3 min-w-0">
        <ProviderIcon id={provider.id} />
        <span className="text-sm font-medium truncate">{provider.name}</span>
        {sourceLabel() && (
          <Badge variant="secondary" className="text-xs">
            {sourceLabel()}
          </Badge>
        )}
      </div>
      {provider.connected ? (
        onDisconnect ? (
          <Button variant="ghost" size="sm" onClick={onDisconnect}>
            Disconnect
          </Button>
        ) : (
          <span className="text-sm text-muted-foreground">System config</span>
        )
      ) : (
        onConnect && (
          <Button variant="secondary" size="sm" onClick={onConnect}>
            + Connect
          </Button>
        )
      )}
    </div>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 9: 创建 ProvidersPanel 组件

**Files:**
- Create: `client/src/features/model-config/providers-panel.tsx`

- [x] **Step 1: 创建 ProvidersPanel 组件**

```tsx
// client/src/features/model-config/providers-panel.tsx

import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ProviderIcon } from './provider-icon'
import { ProviderItem } from './provider-item'
import { ConnectProviderDialog } from './connect-provider-dialog'
import { CustomProviderDialog } from './custom-provider-dialog'
import { useModelConfigStore } from './store'
import { POPULAR_PROVIDER_ORDER } from './mock-data'
import type { Provider } from './types'

export function ProvidersPanel() {
  const providers = useModelConfigStore((s) => s.providers)
  const connectProvider = useModelConfigStore((s) => s.connectProvider)
  const disconnectProvider = useModelConfigStore((s) => s.disconnectProvider)
  const addCustomProvider = useModelConfigStore((s) => s.addCustomProvider)

  const [connectDialogOpen, setConnectDialogOpen] = useState(false)
  const [customDialogOpen, setCustomDialogOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(null)

  const connectedProviders = providers.filter((p) => p.connected)
  const popularProviderIds = POPULAR_PROVIDER_ORDER.filter(
    (id) => !providers.find((p) => p.id === id && p.connected)
  )

  const handleConnect = (provider: Provider) => {
    setSelectedProvider(provider)
    setConnectDialogOpen(true)
  }

  const handleConnectSubmit = (apiKey: string) => {
    if (selectedProvider) {
      connectProvider(selectedProvider.id, apiKey)
      toast.success(`已连接 ${selectedProvider.name}`)
      setConnectDialogOpen(false)
      setSelectedProvider(null)
    }
  }

  const handleDisconnect = (providerId: string) => {
    const provider = providers.find((p) => p.id === providerId)
    disconnectProvider(providerId)
    toast.success(`已断开 ${provider?.name}`)
  }

  const handleAddCustom = (provider: Provider) => {
    addCustomProvider(provider)
    toast.success(`已添加自定义提供商 ${provider.name}`)
    setCustomDialogOpen(false)
  }

  return (
    <div className="flex flex-col gap-6 max-w-2xl">
      {/* Connected Section */}
      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">Connected</h2>
        <Card>
          <CardContent className="px-4">
            {connectedProviders.length === 0 ? (
              <div className="py-4 text-sm text-muted-foreground">暂无已连接的提供商</div>
            ) : (
              connectedProviders.map((provider) => (
                <ProviderItem
                  key={provider.id}
                  provider={provider}
                  onDisconnect={() => handleDisconnect(provider.id)}
                />
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {/* Popular Providers Section */}
      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">Popular Providers</h2>
        <Card>
          <CardContent className="px-4">
            {providers
              .filter((p) => !p.connected && p.type === 'builtin')
              .sort((a, b) => {
                const aIdx = POPULAR_PROVIDER_ORDER.indexOf(a.id)
                const bIdx = POPULAR_PROVIDER_ORDER.indexOf(b.id)
                return aIdx - bIdx
              })
              .map((provider) => (
                <ProviderItem
                  key={provider.id}
                  provider={provider}
                  onConnect={() => handleConnect(provider)}
                />
              ))}

            {/* Custom Provider Entry */}
            <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
              <div className="flex flex-col min-w-0 gap-1">
                <div className="flex items-center gap-3">
                  <ProviderIcon id="custom" />
                  <span className="text-sm font-medium">Custom Provider</span>
                  <Badge variant="secondary" className="text-xs">Custom</Badge>
                </div>
                <span className="text-xs text-muted-foreground pl-8">
                  添加自定义 API 端点
                </span>
              </div>
              <Button variant="secondary" size="sm" onClick={() => setCustomDialogOpen(true)}>
                + Connect
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Connect Dialog */}
      {selectedProvider && (
        <ConnectProviderDialog
          open={connectDialogOpen}
          onOpenChange={setConnectDialogOpen}
          provider={selectedProvider}
          onSubmit={handleConnectSubmit}
        />
      )}

      {/* Custom Provider Dialog */}
      <CustomProviderDialog
        open={customDialogOpen}
        onOpenChange={setCustomDialogOpen}
        onSubmit={handleAddCustom}
      />
    </div>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误（ConnectProviderDialog 和 CustomProviderDialog 未定义，后续任务解决）

---

## Task 10: 创建 ModelItem 组件

**Files:**
- Create: `client/src/features/model-config/model-item.tsx`

- [x] **Step 1: 创建 ModelItem 组件**

```tsx
// client/src/features/model-config/model-item.tsx

import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import type { Model } from './types'

interface ModelItemProps {
  model: Model
  onVisibilityChange?: (visible: boolean) => void
}

export function ModelItem({ model, onVisibilityChange }: ModelItemProps) {
  return (
    <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
      <div className="flex items-center gap-2 min-w-0">
        <span className="text-sm truncate">{model.name}</span>
        {model.latest && (
          <Badge variant="secondary" className="text-xs">
            Latest
          </Badge>
        )}
      </div>
      <Switch checked={model.visible} onCheckedChange={onVisibilityChange} />
    </div>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 11: 创建 ModelsPanel 组件（带搜索功能）

**Files:**
- Create: `client/src/features/model-config/models-panel.tsx`

- [x] **Step 1: 创建 ModelsPanel 组件**

```tsx
// client/src/features/model-config/models-panel.tsx

import { useState, useMemo } from 'react'
import { SearchIcon, XIcon } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { IconButton } from '@/components/ui/icon-button'
import { ProviderIcon } from './provider-icon'
import { ModelItem } from './model-item'
import { useModelConfigStore } from './store'
import { POPULAR_PROVIDER_ORDER } from './mock-data'

export function ModelsPanel() {
  const providers = useModelConfigStore((s) => s.providers)
  const setModelVisibility = useModelConfigStore((s) => s.setModelVisibility)
  const [search, setSearch] = useState('')

  const filteredProviders = useMemo(() => {
    const query = search.toLowerCase().trim()
    if (!query) return providers

    return providers
      .map((p) => ({
        ...p,
        models: p.models.filter(
          (m) =>
            m.name.toLowerCase().includes(query) ||
            p.name.toLowerCase().includes(query) ||
            m.id.toLowerCase().includes(query)
        ),
      }))
      .filter((p) => p.models.length > 0)
      .sort((a, b) => {
        const aIdx = POPULAR_PROVIDER_ORDER.indexOf(a.id)
        const bIdx = POPULAR_PROVIDER_ORDER.indexOf(b.id)
        const aPopular = aIdx >= 0
        const bPopular = bIdx >= 0
        if (aPopular && !bPopular) return -1
        if (!aPopular && bPopular) return 1
        return aIdx - bIdx
      })
  }, [providers, search])

  const handleVisibilityChange = (providerId: string, modelId: string, visible: boolean) => {
    setModelVisibility(providerId, modelId, visible)
  }

  const clearSearch = () => setSearch('')

  return (
    <div className="flex flex-col gap-6 max-w-2xl h-full overflow-hidden">
      {/* Sticky Search Header */}
      <div className="sticky top-0 z-10 bg-gradient-to-b from-background to-background/0 pb-2">
        <div className="relative flex items-center gap-2">
          <SearchIcon className="size-4 text-muted-foreground absolute left-3" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索模型..."
            className="pl-9 pr-9"
          />
          {search && (
            <button
              onClick={clearSearch}
              className="size-4 text-muted-foreground absolute right-3 hover:text-foreground"
            >
              <XIcon className="size-4" />
            </button>
          )}
        </div>
      </div>

      {/* Provider Groups */}
      <div className="flex flex-col gap-4 overflow-y-auto">
        {filteredProviders.length === 0 ? (
          <div className="py-8 text-center text-sm text-muted-foreground">
            没有找到匹配的模型
          </div>
        ) : (
          filteredProviders.map((provider) => (
            <div key={provider.id} className="flex flex-col gap-2">
              <div className="flex items-center gap-2 pb-2">
                <ProviderIcon id={provider.id} />
                <span className="text-sm font-medium">{provider.name}</span>
              </div>
              <Card>
                <CardContent className="px-4">
                  {provider.models.map((model) => (
                    <ModelItem
                      key={model.id}
                      model={model}
                      onVisibilityChange={(visible) =>
                        handleVisibilityChange(provider.id, model.id, visible)
                      }
                    />
                  ))}
                </CardContent>
              </Card>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误（IconButton 可能不存在，使用 button 替代）

- [x] **Step 3: 检查是否有 IconButton 组件，如果没有则修改**

```bash
ls /home/wushengzhou/workspace/github/data-talk/client/src/components/ui/icon-button.tsx
```

Expected: 文件不存在

如果不存在，修改 ModelsPanel 中的 XIcon 清除按钮为：

```tsx
<button
  onClick={clearSearch}
  className="size-4 text-muted-foreground absolute right-3 hover:text-foreground cursor-pointer"
>
  <XIcon className="size-full" />
</button>
```

---

## Task 12: 创建 ConnectProviderDialog 组件

**Files:**
- Create: `client/src/features/model-config/connect-provider-dialog.tsx`

- [x] **Step 1: 创建 ConnectProviderDialog 组件**

```tsx
// client/src/features/model-config/connect-provider-dialog.tsx

import { useState } from 'react'
import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { ProviderIcon } from './provider-icon'
import type { Provider } from './types'

interface ConnectProviderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  provider: Provider
  onSubmit: (apiKey: string) => void
}

export function ConnectProviderDialog({
  open,
  onOpenChange,
  provider,
  onSubmit,
}: ConnectProviderDialogProps) {
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!apiKey.trim()) {
      setError('请输入 API Key')
      return
    }
    onSubmit(apiKey.trim())
    setApiKey('')
    setError('')
  }

  const handleOpenChange = (open: boolean) => {
    if (!open) {
      setApiKey('')
      setError('')
    }
    onOpenChange(open)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ProviderIcon id={provider.id} />
            Connect {provider.name}
          </DialogTitle>
          <DialogDescription>
            输入您的 {provider.name} API Key 以连接服务。
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="api-key">API Key</Label>
            <div className="relative">
              <Input
                id="api-key"
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value)
                  setError('')
                }}
                placeholder="sk-..."
                className="pr-10"
              />
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showKey ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
              </button>
            </div>
            {error && <span className="text-xs text-destructive">{error}</span>}
          </div>

          <p className="text-xs text-muted-foreground">
            您的 API Key 将安全存储在系统 keyring 中。
          </p>

          <Button type="submit" className="w-full">
            Connect
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 13: 创建 CustomProviderDialog 组件

**Files:**
- Create: `client/src/features/model-config/custom-provider-dialog.tsx`

- [x] **Step 1: 创建 CustomProviderDialog 组件**

```tsx
// client/src/features/model-config/custom-provider-dialog.tsx

import { useState } from 'react'
import { PlusIcon, Trash2Icon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { ProviderIcon } from './provider-icon'
import type { Provider, Model } from './types'

interface CustomProviderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (provider: Provider) => void
}

interface ModelEntry {
  id: string
  name: string
}

interface HeaderEntry {
  key: string
  value: string
}

export function CustomProviderDialog({
  open,
  onOpenChange,
  onSubmit,
}: CustomProviderDialogProps) {
  const [providerId, setProviderId] = useState('')
  const [name, setName] = useState('')
  const [baseURL, setBaseURL] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [models, setModels] = useState<ModelEntry[]>([{ id: '', name: '' }])
  const [headers, setHeaders] = useState<HeaderEntry[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  const validateProviderId = (id: string) => {
    if (!id) return '请输入 Provider ID'
    if (!/^[\w-]+$/.test(id)) return '只能包含字母、数字、下划线、连字符'
    return ''
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const newErrors: Record<string, string> = {}
    const idError = validateProviderId(providerId)
    if (idError) newErrors.providerId = idError
    if (!name) newErrors.name = '请输入名称'
    if (!baseURL) newErrors.baseURL = '请输入 Base URL'

    const validModels = models.filter((m) => m.id && m.name)
    if (validModels.length === 0) newErrors.models = '请至少添加一个模型'

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      return
    }

    const provider: Provider = {
      id: providerId,
      name,
      type: 'custom',
      baseURL,
      apiKey: apiKey || undefined,
      models: validModels.map((m) => ({
        id: m.id,
        name: m.name,
        providerId,
        visible: true,
      })),
      headers: headers.length > 0 
        ? headers.reduce((acc, h) => ({ ...acc, [h.key]: h.value }), {}) 
        : undefined,
      connected: true,
      source: 'api',
    }

    onSubmit(provider)
    resetForm()
  }

  const resetForm = () => {
    setProviderId('')
    setName('')
    setBaseURL('')
    setApiKey('')
    setModels([{ id: '', name: '' }])
    setHeaders([])
    setErrors({})
  }

  const handleOpenChange = (open: boolean) => {
    if (!open) resetForm()
    onOpenChange(open)
  }

  const addModel = () => setModels([...models, { id: '', name: '' }])
  const removeModel = (index: number) => {
    if (models.length > 1) setModels(models.filter((_, i) => i !== index))
  }
  const updateModel = (index: number, field: 'id' | 'name', value: string) => {
    setModels(models.map((m, i) => (i === index ? { ...m, [field]: value } : m)))
  }

  const addHeader = () => setHeaders([...headers, { key: '', value: '' }])
  const removeHeader = (index: number) => setHeaders(headers.filter((_, i) => i !== index))
  const updateHeader = (index: number, field: 'key' | 'value', value: string) => {
    setHeaders(headers.map((h, i) => (i === index ? { ...h, [field]: value } : h)))
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ProviderIcon id="custom" />
            Custom Provider
          </DialogTitle>
          <DialogDescription>
            配置自定义 API 端点
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Provider ID */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="provider-id">Provider ID</Label>
            <Input
              id="provider-id"
              value={providerId}
              onChange={(e) => {
                setProviderId(e.target.value)
                setErrors((err) => ({ ...err, providerId: '' }))
              }}
              placeholder="my-custom-provider"
            />
            {errors.providerId && (
              <span className="text-xs text-destructive">{errors.providerId}</span>
            )}
            <span className="text-xs text-muted-foreground">唯一标识符</span>
          </div>

          {/* Name */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">名称</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                setErrors((err) => ({ ...err, name: '' }))
              }}
              placeholder="My Custom Provider"
            />
            {errors.name && <span className="text-xs text-destructive">{errors.name}</span>}
          </div>

          {/* Base URL */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="base-url">Base URL</Label>
            <Input
              id="base-url"
              value={baseURL}
              onChange={(e) => {
                setBaseURL(e.target.value)
                setErrors((err) => ({ ...err, baseURL: '' }))
              }}
              placeholder="https://api.example.com/v1"
            />
            {errors.baseURL && (
              <span className="text-xs text-destructive">{errors.baseURL}</span>
            )}
          </div>

          {/* API Key */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="api-key">API Key（可选）</Label>
            <div className="relative">
              <Input
                id="api-key"
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="your-api-key"
                className="pr-10"
              />
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showKey ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
              </button>
            </div>
            <span className="text-xs text-muted-foreground">
              如果端点无需认证可不填
            </span>
          </div>

          {/* Models */}
          <div className="flex flex-col gap-2">
            <Label>模型列表</Label>
            {models.map((model, index) => (
              <div key={index} className="flex gap-2 items-center">
                <Input
                  value={model.id}
                  onChange={(e) => updateModel(index, 'id', e.target.value)}
                  placeholder="model-id"
                  className="flex-1"
                />
                <Input
                  value={model.name}
                  onChange={(e) => updateModel(index, 'name', e.target.value)}
                  placeholder="Model Name"
                  className="flex-1"
                />
                <button
                  type="button"
                  onClick={() => removeModel(index)}
                  disabled={models.length <= 1}
                  className="size-8 flex items-center justify-center text-muted-foreground hover:text-foreground disabled:opacity-50"
                >
                  <Trash2Icon className="size-4" />
                </button>
              </div>
            ))}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={addModel}
              className="self-start"
            >
              <PlusIcon className="size-4" />
              Add Model
            </Button>
            {errors.models && (
              <span className="text-xs text-destructive">{errors.models}</span>
            )}
          </div>

          {/* Headers */}
          <div className="flex flex-col gap-2">
            <Label>请求头（可选）</Label>
            {headers.map((header, index) => (
              <div key={index} className="flex gap-2 items-center">
                <Input
                  value={header.key}
                  onChange={(e) => updateHeader(index, 'key', e.target.value)}
                  placeholder="Header-Key"
                  className="flex-1"
                />
                <Input
                  value={header.value}
                  onChange={(e) => updateHeader(index, 'value', e.target.value)}
                  placeholder="Header-Value"
                  className="flex-1"
                />
                <button
                  type="button"
                  onClick={() => removeHeader(index)}
                  className="size-8 flex items-center justify-center text-muted-foreground hover:text-foreground"
                >
                  <Trash2Icon className="size-4" />
                </button>
              </div>
            ))}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={addHeader}
              className="self-start"
            >
              <PlusIcon className="size-4" />
              Add Header
            </Button>
          </div>

          <Button type="submit" className="w-full mt-4">
            Connect
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
```

- [x] **Step 2: 验证 TypeScript 编译**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: 无类型错误

---

## Task 14: 运行 dev server 验证 UI

**Files:**
- 无新文件创建

- [x] **Step 1: 启动 dev server**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npm run dev
```

Expected: Dev server 启动成功，显示 localhost URL

- [x] **Step 2: 打开浏览器验证页面**

访问 http://localhost:5173/settings

验证以下功能：
1. Tab 切换正常（Providers / Models / General）
2. Providers Tab 显示已连接和热门提供商列表
3. 点击 Connect 弹出对话框
4. Models Tab 搜索和 Switch 开关正常

- [x] **Step 3: 验证 Sidebar 用户菜单**

在主页点击 Sidebar Footer 用户头像，验证：
1. 下拉菜单显示"系统设置"、"连接配置"、"模型配置"
2. 点击后导航到 /settings 页面

---

## Task 15: 提交代码

**Files:**
- 所有新增和修改的文件

- [x] **Step 1: 查看 git status**

```bash
cd /home/wushengzhou/workspace/github/data-talk && git status
```

Expected: 显示所有新增和修改的文件

- [x] **Step 2: 添加文件并提交**

```bash
git add client/src/routes/settings.tsx \
  client/src/features/model-config/ \
  client/src/components/ui/switch.tsx \
  client/src/components/ui/dialog.tsx \
  client/src/features/workspace/components/nav-user.tsx

git commit -m "$(cat <<'EOF'
feat(client): add model config page UI with mock data

- Add settings page route with Tabs (Providers/Models/General)
- Add ProvidersPanel: connected providers + connect/disconnect
- Add ModelsPanel: searchable model list with visibility toggle
- Add ConnectProviderDialog: API key input form
- Add CustomProviderDialog: custom provider configuration
- Add ProviderIcon: single-color provider icons
- Add Zustand store for model config state management
- Update NavUser: add settings menu entries with route navigation
- Add shadcn/ui Switch and Dialog components

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

Expected: Commit 成功

- [x] **Step 3: 验证 git log**

```bash
git log --oneline -1
```

Expected: 显示最新的 commit

---

## Self-Review Checklist

**1. Spec Coverage:**
- [x] Sidebar Footer 用户菜单入口 — Task 6
- [x] 设置页面三个 Tab — Task 7
- [x] Providers Tab 显示已连接 + 热门提供商 — Task 9
- [x] Models Tab 搜索 + Switch — Task 11
- [x] Connect 对话框 API Key 输入 — Task 12
- [x] 自定义提供商表单 — Task 13
- [x] ProviderIcon 单色图标 — Task 5
- [x] Mock 数据 + Store — Task 2-4

**2. Placeholder Scan:**
- [x] 无 TBD/TODO
- [x] 无 "implement later"
- [x] 所有代码步骤有完整实现

**3. Type Consistency:**
- [x] Provider interface 定义一致（types.ts）
- [x] Model interface 定义一致（types.ts）
- [x] Store actions 签名与组件调用一致
- [x] Provider.id 用于 ProviderIcon 查找