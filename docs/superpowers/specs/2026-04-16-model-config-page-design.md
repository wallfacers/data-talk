# 模型配置页面设计

> 日期：2026-04-16
> 状态：待评审
> 参考：opencode 模型配置页面风格（settings-providers.tsx / settings-models.tsx）

---

## 0. 摘要

本 spec 定义 data-talk 桌面端的模型配置页面，包含：
1. **提供商管理** — 连接/断开 AI 提供商，配置 API Key
2. **模型管理** — 按提供商分组的模型可见性开关
3. **自定义提供商** — 用户自定义 API 端点、模型列表、请求头

风格与 opencode 保持一致，使用 data-talk 现有的 shadcn/ui 组件实现。

### 成功标准

1. Sidebar Footer 用户菜单点击后弹出下拉菜单，包含"设置"入口
2. 设置页面包含三个 Tab：Providers / Models / General
3. Providers Tab 显示已连接提供商 + 可连接的热门提供商列表
4. Models Tab 按提供商分组显示模型列表，每个模型有可见性开关
5. 点击"Connect"弹出连接对话框，输入 API Key 后连接成功
6. 自定义提供商表单支持 Provider ID、名称、Base URL、模型列表、Headers
7. 图标使用框架主题色（text-foreground），深色模式自动切换

### 非目标

- 后端对接（MVP 使用 Mock 数据）
- 用户认证 / 多用户
- 模型使用统计 / 费用追踪
- 模型参数配置（temperature、max_tokens 等）

---

## 1. 页面入口与布局

### 1.1 入口：Sidebar Footer 用户菜单

用户点击 Sidebar Footer 的用户头像区域，弹出下拉菜单：

```
┌─────────────────────────┐
│  👤 用户名               │
│  ├── ⚙️ 设置            │
│  ├── 🔑 模型配置        │  ← 快捷入口
│  ├── 📝 会话历史        │
│  └── ─────────────      │
│  └── 退出登录           │
└─────────────────────────┘
```

**组件位置**：`client/src/features/workspace/components/nav-user.tsx`

**改造内容**：
- 当前 `NavUser` 组件已有 DropdownMenu
- 新增"设置"和"模型配置"菜单项
- 点击"设置"导航到 `/settings` 路由
- 点击"模型配置"直接打开设置页面的 Models Tab

### 1.2 设置页面布局

```
┌─────────────────────────────────────────────────┐
│  设置                                           │
│  ┌───────────────────────────────────────────┐  │
│  │ [Providers] [Models] [General]            │  │ ← Tabs
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ Tab Content                               │  │
│  │                                           │  │
│  │  （Providers / Models / General 内容）    │  │
│  │                                           │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**路由**：`/settings`

**文件位置**：`client/src/routes/settings.tsx`

---

## 2. 数据模型（Mock）

### 2.1 TypeScript 类型定义

```typescript
// client/src/features/model-config/types.ts

export interface Provider {
  id: string                    // "anthropic", "openai", "google", "custom-xxx"
  name: string                  // "Anthropic", "OpenAI", "Google Gemini"
  type: 'builtin' | 'custom'
  apiKey?: string               // 用户配置的 API Key（存储在 Tauri keyring）
  baseURL?: string              // 自定义提供商的 Base URL
  headers?: Record<string, string>  // 自定义请求头
  models: Model[]
  source?: 'env' | 'api' | 'config'  // 配置来源
  connected: boolean            // 是否已连接
}

export interface Model {
  id: string                    // "claude-4-opus", "gpt-4o"
  name: string                  // "Claude 4 Opus", "GPT-4o"
  providerId: string            // 所属提供商
  visible: boolean              // 是否在列表中显示
  latest?: boolean              // 是否是最新版本
}

export interface ModelConfigState {
  providers: Provider[]
  currentModel?: { providerId: string, modelId: string }
}
```

### 2.2 Mock 数据

```typescript
// client/src/features/model-config/mock-data.ts

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
    ]
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
    ]
  },
  {
    id: 'google',
    name: 'Google',
    type: 'builtin',
    connected: false,
    models: [
      { id: 'gemini-2-flash', name: 'Gemini 2.0 Flash', providerId: 'google', visible: true, latest: true },
      { id: 'gemini-1-5-pro', name: 'Gemini 1.5 Pro', providerId: 'google', visible: false },
    ]
  },
]

export const POPULAR_PROVIDER_ORDER = ['anthropic', 'openai', 'google', 'openrouter', 'vercel']
```

### 2.3 Zustand Store

```typescript
// client/src/features/model-config/store.ts

import { create } from 'zustand'
import { MOCK_PROVIDERS } from './mock-data'

interface ModelConfigStore {
  providers: Provider[]
  currentModel?: { providerId: string, modelId: string }
  
  // Actions
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
        p.id === providerId ? { ...p, connected: true, apiKey, source: 'api' } : p
      )
    })),
  
  disconnectProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId ? { ...p, connected: false, apiKey: undefined, source: undefined } : p
      )
    })),
  
  setModelVisibility: (providerId, modelId, visible) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId
          ? {
              ...p,
              models: p.models.map((m) =>
                m.id === modelId ? { ...m, visible } : m
              )
            }
          : p
      )
    })),
  
  setCurrentModel: (providerId, modelId) =>
    set({ currentModel: { providerId, modelId } }),
  
  addCustomProvider: (provider) =>
    set((state) => ({
      providers: [...state.providers, provider]
    })),
  
  removeCustomProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.filter((p) => p.id !== providerId)
    })),
}))
```

---

## 3. 组件清单

### 3.1 新增组件

| 组件 | 位置 | 描述 |
|------|------|------|
| `SettingsPage` | `routes/settings.tsx` | 设置页面容器，Tab 切换 |
| `ProvidersPanel` | `features/model-config/providers-panel.tsx` | 提供商管理面板 |
| `ProviderItem` | `features/model-config/provider-item.tsx` | 提供商列表项（已连接/未连接） |
| `ConnectProviderDialog` | `features/model-config/connect-provider-dialog.tsx` | 连接提供商对话框（API Key 输入） |
| `CustomProviderDialog` | `features/model-config/custom-provider-dialog.tsx` | 自定义提供商配置表单 |
| `ModelsPanel` | `features/model-config/models-panel.tsx` | 模型管理面板 |
| `ModelItem` | `features/model-config/model-item.tsx` | 模型列表项 + Switch |
| `ProviderIcon` | `features/model-config/provider-icon.tsx` | 提供商图标（单色） |
| `UserMenu` | `features/workspace/components/nav-user.tsx` | 改造：添加设置入口 |

### 3.2 使用的 shadcn/ui 组件

| 组件 | 状态 | 用途 |
|------|------|------|
| `Tabs` | 已有 | 设置页面 Tab 切换 |
| `Button` | 已有 | Connect/Disconnect/Add Model |
| `Input` | 已有 | API Key 输入、搜索框 |
| `Badge` | 已有 | 提供商类型标签（API Key/Environment/Custom） |
| `Card` | 已有 | 列表容器（替代 opencode 的 SettingsList） |
| `Switch` | **需添加** | 模型可见性开关 |
| `Dialog` | **需添加** | 连接/自定义提供商对话框 |
| `Label` | 已有 | 表单字段标签 |
| `Separator` | 已有 | 分隔线 |
| `DropdownMenu` | 已有 | Sidebar 用户菜单 |

### 3.3 需添加的 shadcn/ui 组件

```bash
npx shadcn@latest add switch dialog
```

---

## 4. 提供商管理面板（ProvidersPanel）

### 4.1 布局结构

```
┌─────────────────────────────────────────────────┐
│  Providers                                      │
│                                                 │
│  Connected                                      │
│  ┌───────────────────────────────────────────┐  │
│  │ [A] Anthropic [API Key]    [Disconnect]   │  │
│  │ [O] OpenAI [Environment]   System config  │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  Popular Providers                              │
│  ┌───────────────────────────────────────────┐  │
│  │ [G] Google Gemini models via API          │  │
│  │                            [+ Connect]    │  │
│  │───────────────────────────────────────────│  │
│  │ [C] Custom Provider [Custom]              │  │
│  │     Bring your own API endpoint           │  │
│  │                            [+ Connect]    │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  [View all providers]                           │
└─────────────────────────────────────────────────┘
```

### 4.2 组件实现要点

- **粘性头部**：标题固定在顶部，滚动时渐变遮罩
- **分组卡片**：Connected 和 Popular 两个分组，使用 Card 组件
- **提供商图标**：ProviderIcon 组件，20x20px，使用 `text-foreground`
- **标签类型**：
  - `API Key` — 用户手动输入
  - `Environment` — 系统环境变量配置
  - `Custom` — 自定义提供商
- **连接按钮**：`+ Connect` 使用 Button variant="secondary"
- **断开按钮**：`Disconnect` 使用 Button variant="ghost"

### 4.3 ProviderIcon 设计

```tsx
// client/src/features/model-config/provider-icon.tsx

import { cn } from '@/lib/utils'

const PROVIDER_ICONS: Record<string, React.ReactNode> = {
  anthropic: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="10"/>
      <path d="M12 6v6l4 2"/>
    </svg>
  ),
  openai: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
    </svg>
  ),
  google: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 2L2 7l10 5 10-5-10-5z"/>
      <path d="M2 17l10 5 10-5"/>
      <path d="M2 12l10 5 10-5"/>
    </svg>
  ),
  custom: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="3" width="18" height="18" rx="2"/>
      <path d="M9 9h6v6H9z"/>
    </svg>
  ),
}

export function ProviderIcon({ 
  id, 
  className 
}: { 
  id: string
  className?: string 
}) {
  const icon = PROVIDER_ICONS[id] ?? PROVIDER_ICONS.custom
  return (
    <div className={cn('size-5 text-foreground', className)}>
      {icon}
    </div>
  )
}
```

---

## 5. 模型管理面板（ModelsPanel）

### 5.1 布局结构

```
┌─────────────────────────────────────────────────┐
│  Models                                         │
│  ┌───────────────────────────────────────────┐  │
│  │ 🔍 [Search models...            ] [✕]    │  │ ← Sticky Search
│  └───────────────────────────────────────────┘  │
│                                                 │
│  [A] Anthropic                                  │
│  ┌───────────────────────────────────────────┐  │
│  │ Claude 4 Opus                    [ON]     │  │
│  │ Claude 4 Sonnet                  [OFF]    │  │
│  │ Claude 3.5 Haiku                 [ON]     │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  [O] OpenAI                                     │
│  ┌───────────────────────────────────────────┐  │
│  │ GPT-4o                           [ON]     │  │
│  │ GPT-4 Turbo                      [OFF]    │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 5.2 组件实现要点

- **粘性搜索框**：固定在顶部，渐变遮罩背景
- **分组列表**：按提供商分组，每组有图标和名称
- **Switch 开关**：控制模型可见性，使用 shadcn Switch
- **搜索过滤**：支持按模型名称、提供商名称搜索
- **空状态**：无匹配结果时显示"没有找到模型"

---

## 6. 连接提供商对话框（ConnectProviderDialog）

### 6.1 布局

```
┌─────────────────────────────────────────────────┐
│  [←] Connect Anthropic                    [✕]  │
│                                                 │
│  Enter your Anthropic API key to connect.       │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ API Key                                   │  │
│  │ [sk-ant-...                          ]    │  │
│  │                                           │  │
│  │ Your API key will be stored securely in   │  │
│  │ your system keyring.                      │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  [Connect]                                      │
└─────────────────────────────────────────────────┘
```

### 6.2 实现要点

- **Dialog 标题**：提供商图标 + "Connect {Provider Name}"
- **返回按钮**：返回到提供商选择列表（针对从 Provider 列表进入的情况）
- **API Key 输入**：Input type="password"，支持显示/隐藏
- **存储说明**：提示 API Key 存储在 Tauri keyring
- **Connect 按钮**：点击后调用 `connectProvider(providerId, apiKey)`
- **Toast 提示**：连接成功后显示 success toast

---

## 7. 自定义提供商对话框（CustomProviderDialog）

### 7.1 布局

```
┌─────────────────────────────────────────────────┐
│  [←] Custom Provider                     [✕]  │
│                                                 │
│  Configure your own API endpoint.               │
│  [Learn more ↗]                                │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ Provider ID                               │  │
│  │ [my-custom-provider                  ]    │  │
│  │ Unique identifier for this provider        │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ Name                                      │  │
│  │ [My Custom Provider                  ]    │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ Base URL                                  │  │
│  │ [https://api.example.com/v1           ]   │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  ┌───────────────────────────────────────────┐  │
│  │ API Key                                   │  │
│  │ [your-api-key                         ]   │  │
│  │ Optional if endpoint requires no auth      │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  Models                                         │
│  ├─ [model-id-1    ] [Model Name 1    ] [🗑]   │
│  ├─ [model-id-2    ] [Model Name 2    ] [🗑]   │
│  ├─ [+ Add Model]                              │
│                                                 │
│  Headers (Optional)                            │
│  ├─ [Header-Key   ] [Header-Value    ] [🗑]    │
│  ├─ [+ Add Header]                             │
│                                                 │
│  [Connect]                                      │
└─────────────────────────────────────────────────┘
```

### 7.2 实现要点

- **表单验证**：Provider ID 必填且唯一，Name 必填，Base URL 必填
- **动态列表**：模型列表和 Headers 列表可动态添加/删除
- **最小限制**：至少保留一个模型条目
- **Provider ID 校验**：只能是字母、数字、下划线、连字符
- **Connect 按钮**：点击后调用 `addCustomProvider(provider)`
- **返回按钮**：返回到提供商选择列表

---

## 8. 目录结构

```
client/src/
  routes/
    settings.tsx                    # 新增：设置页面路由
  features/
    model-config/                   # 新增：模型配置功能模块
      types.ts                      # TypeScript 类型定义
      mock-data.ts                  # Mock 数据
      store.ts                      # Zustand store
      providers-panel.tsx           # 提供商管理面板
      provider-item.tsx             # 提供商列表项
      provider-icon.tsx             # 提供商图标组件
      connect-provider-dialog.tsx   # 连接提供商对话框
      custom-provider-dialog.tsx    # 自定义提供商对话框
      models-panel.tsx              # 模型管理面板
      model-item.tsx                # 模型列表项
    workspace/components/
      nav-user.tsx                  # 改造：添加设置入口
```

---

## 9. 实现顺序

1. **添加 shadcn/ui 组件**
   - `npx shadcn@latest add switch dialog`

2. **创建类型和 Mock 数据**
   - `types.ts`、`mock-data.ts`、`store.ts`

3. **创建 ProviderIcon**
   - 单色图标，使用 `text-foreground`

4. **改造 NavUser**
   - 添加设置菜单入口

5. **创建设置页面路由**
   - `routes/settings.tsx` + Tabs

6. **实现 ProvidersPanel**
   - 提供商列表 + 连接/断开

7. **实现 ModelsPanel**
   - 模型列表 + Switch 开关

8. **实现 ConnectProviderDialog**
   - API Key 输入 + 连接逻辑

9. **实现 CustomProviderDialog**
   - 自定义提供商表单

---

## 10. 后端对接预留

后端对接时需要：
1. `GET /api/providers` — 获取提供商列表
2. `POST /api/providers/:id/connect` — 连接提供商（API Key 存入 keyring）
3. `DELETE /api/providers/:id` — 断开提供商
4. `GET /api/models` — 获取模型列表
5. `POST /api/models/:id/visibility` — 设置模型可见性
6. `POST /api/providers/custom` — 添加自定义提供商

Store 改造为调用 API，Mock 数据仅用于 MVP 验证 UI。