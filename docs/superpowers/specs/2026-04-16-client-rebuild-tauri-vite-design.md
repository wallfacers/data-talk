# client 目录重建为 Tauri v2 + Vite + React

> 日期：2026-04-16
> 状态：待评审
> 上一轮讨论：本文档已作废并替换 2026-04-15 的两份旧 spec

---

## 1. 背景与目标

`client/` 当前是 Next.js 16 + React 19 的脚手架（shadcn `dashboard-01` 模板原样塞进来），但 `project_doc.md` 的原始架构是 **Tauri v2 + React + Vite** 桌面端。两者路线不一致，本次选择**切回 Tauri + Vite**，理由：

- 项目形态是桌面工具（连本地/内网数据库、管理密码），Next.js 的 SSR / Server Components 在这个场景是负担
- 后端 Spring Boot 已规划走 HTTP，前端无需 Next.js 的 API Routes
- Tauri 提供原生菜单、托盘、凭据管理、文件对话框等桌面能力

### 成功标准

1. `cd client && pnpm tauri dev` 能启动桌面窗口
2. 根路由 `/` 展示**三栏骨架**（连接/会话列表 · 聊天区 · 工作区 Tab），可拖拽调整分栏宽度
3. `/dashboard` 路由保留现有 shadcn `dashboard-01` 页面可访问（后续由用户自行改造）
4. `src/services/http.ts` 有 `ky` 实例，能发起一次到 `http://localhost:8080/api/health` 的请求（后端未起时返回错误，不崩）
5. `pnpm lint` 和 `pnpm typecheck` 0 错误
6. Rust 侧有一个 `greet` 命令通过 `invoke("greet", { name })` 能回显，验证 IPC 通路

### 非目标（本次不做）

- 真实连接数据库、真实 AI 对话
- ECharts 切换（保留 Recharts）
- ER 图（React Flow / `@xyflow/react`）
- 单元测试 / e2e
- husky / lint-staged / commitlint
- 主题切换交互（但 CSS 变量会就位，`next-themes` 移除）
- Tauri 凭据管理、托盘、菜单

---

## 2. 技术决定

| # | 话题 | 决定 | 备注 |
|---|------|------|------|
| 2.1 | 桌面壳 | Tauri v2 | 对应 `src-tauri/` |
| 2.2 | 构建 | Vite | 替换 Next.js |
| 2.3 | React | 19.2.x | 保留 |
| 2.4 | 语言 | TypeScript strict | 保留 `tsconfig.json` 的 `strict: true` |
| 2.5 | 包管理 | pnpm | 继续 |
| 2.6 | 路由 | `@tanstack/react-router` + `@tanstack/router-vite-plugin`（文件路由） | `routeTree.gen.ts` 提交仓库便于 CI 类型检查 |
| 2.7 | 状态 | `zustand` | 每个 feature 内部 slice，必要时在 `src/stores/` 放跨 feature 的 |
| 2.8 | 数据层 | `ky` + `@tanstack/react-query` | `ky` 做 HTTP 实例，`react-query` 管缓存 |
| 2.9 | 分栏 | `react-resizable-panels` | shadcn `resizable` 组件底层一致 |
| 2.10 | UI 库 | shadcn/ui，style `base-nova` | `components.json` 不动 |
| 2.11 | 图标 | `lucide-react` | 已装，保留 |
| 2.12 | 图表 | Recharts | 保留；ECharts 切换列入后续任务 |
| 2.13 | ER 图 | 不装 | `features/diagram/` 仅留空目录 |
| 2.14 | 样式 | Tailwind v4 | `@tailwindcss/vite` 插件，`globals.css` 移到 `src/styles/` |
| 2.15 | 通知 | `sonner` | 已装，保留 |
| 2.16 | 表单校验 | `zod` | 已装，保留 |
| 2.17 | 主题 | **移除 `next-themes`** | Next.js 专有，后续自己实现或换 `@base-ui/react` 的 theme 原子 |
| 2.18 | Lint | `eslint` + `typescript-eslint` + `eslint-plugin-react` + `eslint-plugin-react-hooks` | 移除 `eslint-config-next` |
| 2.19 | Format | `prettier` | 新增 `.prettierrc` |
| 2.20 | Node 版本 | ≥ 20 | 现有要求 |
| 2.21 | Rust Edition | 2021 | Tauri v2 默认 |

---

## 3. 目录结构

```
client/
├── src-tauri/                         # Rust 侧（新建）
│   ├── Cargo.toml
│   ├── tauri.conf.json
│   ├── build.rs
│   ├── icons/                         # 从 Tauri 模板拷入
│   ├── capabilities/
│   │   └── default.json
│   └── src/
│       ├── main.rs                    # 入口，调用 lib::run()
│       └── lib.rs                     # greet 命令 + plugin 注册
│
├── public/                             # Vite 静态资源（favicon 等）
│
├── index.html                          # Vite 入口
├── vite.config.ts                      # vite + tanstack-router + tailwind 插件
├── tsconfig.json                       # 更新 paths，去掉 next 插件
├── tsconfig.node.json                  # 给 vite.config.ts 单独的 tsconfig
├── package.json
├── pnpm-lock.yaml                      # 重新生成
├── pnpm-workspace.yaml
├── components.json                     # shadcn 配置，不动
├── eslint.config.mjs                   # Vite 版 eslint flat config
├── .prettierrc
├── .gitignore                          # 增加 dist/ target/ .tauri/
├── AGENTS.md                           # 不动
├── CLAUDE.md                           # 不动
├── README.md                           # 重写：Tauri + Vite 启动方式
└── src/
    ├── main.tsx                        # 入口：RouterProvider + QueryClientProvider
    ├── routeTree.gen.ts                # tanstack-router 自动生成，提交
    ├── vite-env.d.ts                   # Vite 类型声明
    │
    ├── routes/                         # tanstack-router 文件路由
    │   ├── __root.tsx                  # 根路由：TooltipProvider + Toaster + <Outlet />
    │   ├── index.tsx                   # `/` → 三栏工作台
    │   └── dashboard.tsx               # `/dashboard` → 保留的 shadcn demo
    │
    ├── layouts/
    │   ├── workspace-layout.tsx        # 三栏骨架（resizable-panels）
    │   └── dashboard-layout.tsx        # dashboard 原布局（SidebarProvider）
    │
    ├── features/                       # 按业务域划分
    │   ├── chat/
    │   │   ├── components/
    │   │   │   ├── chat-panel.tsx      # 聊天主面板（组装子组件）
    │   │   │   ├── message-list.tsx    # 消息列表 + ScrollArea
    │   │   │   ├── message-item.tsx    # 单条消息（user / ai）
    │   │   │   └── chat-input.tsx      # 输入框（Enter 发，Shift+Enter 换行）
    │   │   ├── hooks/
    │   │   │   └── use-chat.ts         # messages / sendMessage
    │   │   ├── store.ts                # zustand: messages, isStreaming
    │   │   └── types.ts                # Message, MessageRole
    │   │
    │   ├── connection/                 # 数据库连接
    │   │   ├── components/
    │   │   │   ├── connection-list.tsx
    │   │   │   └── connection-form.tsx # 占位表单
    │   │   ├── hooks/
    │   │   │   └── use-connections.ts  # react-query hook
    │   │   ├── store.ts                # zustand: activeConnectionId
    │   │   └── types.ts                # Connection, DbType
    │   │
    │   ├── session/                    # 会话
    │   │   ├── components/
    │   │   │   └── session-list.tsx
    │   │   ├── hooks/
    │   │   │   └── use-sessions.ts
    │   │   ├── store.ts                # zustand: activeSessionId
    │   │   └── types.ts
    │   │
    │   ├── workspace/                  # 右侧 Tab 工作区
    │   │   ├── components/
    │   │   │   ├── workspace.tsx       # Tabs 容器
    │   │   │   ├── tab-bar.tsx         # 可关闭的 tab 标签条
    │   │   │   ├── empty-state.tsx     # 无 tab 时占位
    │   │   │   └── query-result-tab.tsx # 查询结果 tab 内容
    │   │   ├── store.ts                # zustand: tabs, activeTabId, openTab, closeTab
    │   │   └── types.ts                # WorkspaceTab (kind: 'query-result' | 'er-diagram' | 'sql-editor')
    │   │
    │   ├── data-grid/                  # 查询结果表格
    │   │   ├── components/
    │   │   │   └── data-grid.tsx       # 包 @tanstack/react-table
    │   │   └── types.ts
    │   │
    │   ├── dashboard/                  # 保留的 shadcn dashboard demo
    │   │   ├── components/
    │   │   │   ├── app-sidebar.tsx     # 从旧 src/components/ 搬
    │   │   │   ├── nav-main.tsx
    │   │   │   ├── nav-documents.tsx
    │   │   │   ├── nav-secondary.tsx
    │   │   │   ├── nav-user.tsx
    │   │   │   ├── site-header.tsx
    │   │   │   ├── section-cards.tsx
    │   │   │   ├── data-table.tsx
    │   │   │   └── chart-area-interactive.tsx
    │   │   ├── data/
    │   │   │   └── mock-data.json      # 原 src/app/dashboard/data.json
    │   │   └── dashboard-page.tsx      # 聚合入口（由 routes/dashboard.tsx 引用）
    │   │
    │   ├── chart/                      # ECharts 预留（MVP 不加）
    │   │   └── .gitkeep
    │   │
    │   └── diagram/                    # ER 图预留（MVP 不加）
    │       └── .gitkeep
    │
    ├── components/                     # 通用 UI（无业务逻辑）
    │   └── ui/                         # shadcn 原子组件（从旧项目整体搬）
    │       ├── avatar.tsx
    │       ├── badge.tsx
    │       ├── breadcrumb.tsx
    │       ├── button.tsx
    │       ├── card.tsx
    │       ├── chart.tsx               # shadcn 的 recharts 封装
    │       ├── checkbox.tsx
    │       ├── drawer.tsx
    │       ├── dropdown-menu.tsx
    │       ├── input.tsx
    │       ├── label.tsx
    │       ├── select.tsx
    │       ├── separator.tsx
    │       ├── sheet.tsx
    │       ├── sidebar.tsx
    │       ├── skeleton.tsx
    │       ├── sonner.tsx
    │       ├── table.tsx
    │       ├── tabs.tsx
    │       ├── toggle.tsx
    │       ├── toggle-group.tsx
    │       └── tooltip.tsx
    │
    ├── services/                       # 服务层（不依赖 React）
    │   ├── http.ts                     # ky 实例（baseUrl 从 env 读）
    │   ├── api/
    │   │   ├── connection.ts           # listConnections, createConnection, testConnection
    │   │   ├── session.ts              # listSessions, createSession
    │   │   ├── chat.ts                 # sendMessage（SSE/流式预留接口）
    │   │   ├── query.ts                # executeQuery
    │   │   └── health.ts               # getHealth
    │   └── tauri/
    │       └── index.ts                # invoke 包装（MVP 只有 greet）
    │
    ├── stores/                         # 跨 feature 的全局 zustand
    │   └── theme-store.ts              # dark/light 预留（不做切换交互）
    │
    ├── hooks/                          # 全局 hooks
    │   └── use-mobile.ts               # 从旧项目搬
    │
    ├── lib/                            # 工具
    │   ├── utils.ts                    # cn()（从旧项目搬）
    │   └── query-client.ts             # react-query 全局实例
    │
    ├── types/                          # 全局类型
    │   └── api.ts                      # ApiResponse<T>, ApiError
    │
    ├── styles/
    │   └── globals.css                 # 从旧 src/app/globals.css 搬
    │
    └── assets/
        └── .gitkeep
```

### 分层原则

| 层级 | 职责 | 依赖方向 |
|------|------|----------|
| `components/ui/` | 无业务的 shadcn 原子组件 | 被所有层引用 |
| `layouts/` | 页面级布局骨架（三栏、dashboard） | 引用 `features/*` 和 `components/ui` |
| `features/<domain>/` | 单一业务域的 components/hooks/store/types | 只能引用 `components/ui`、`services`、`lib`、`hooks`、`types`。**不跨 feature 横向引用**——需要就上提到 `stores/` 或 `layouts/` 组合。 |
| `services/` | HTTP/IPC 调用，无 React | 只依赖 `types/`、`lib/` |
| `stores/` | 跨 feature 的全局状态 | 只依赖 `types/`、`services/` |
| `hooks/` | 可复用的、无领域的 React hooks | - |
| `lib/` | 纯函数工具 | 无依赖 |
| `types/` | 全局类型 | 无依赖 |
| `routes/` | tanstack-router 路由节点 | 引用 `layouts/` 和 `features/*` |

---

## 4. 关键文件内容概要

### 4.1 `package.json`（目标状态）

```json
{
  "name": "client",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "tauri": "tauri",
    "lint": "eslint .",
    "typecheck": "tsc -b --noEmit",
    "format": "prettier --write ."
  },
  "dependencies": {
    "@base-ui/react": "^1.4.0",
    "@dnd-kit/core": "^6.3.1",
    "@dnd-kit/modifiers": "^9.0.0",
    "@dnd-kit/sortable": "^10.0.0",
    "@dnd-kit/utilities": "^3.2.2",
    "@tanstack/react-query": "^5.x",
    "@tanstack/react-router": "^1.x",
    "@tanstack/react-table": "^8.21.3",
    "@tauri-apps/api": "^2.x",
    "@tauri-apps/plugin-shell": "^2.x",
    "class-variance-authority": "^0.7.1",
    "clsx": "^2.1.1",
    "ky": "^1.x",
    "lucide-react": "^1.8.0",
    "react": "19.2.4",
    "react-dom": "19.2.4",
    "react-resizable-panels": "^2.x",
    "recharts": "3.8.0",
    "sonner": "^2.0.7",
    "tailwind-merge": "^3.5.0",
    "tw-animate-css": "^1.4.0",
    "vaul": "^1.1.2",
    "zod": "^4.3.6",
    "zustand": "^5.x"
  },
  "devDependencies": {
    "@eslint/js": "^9.x",
    "@tailwindcss/vite": "^4.x",
    "@tanstack/router-vite-plugin": "^1.x",
    "@tauri-apps/cli": "^2.x",
    "@types/node": "^20",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "@vitejs/plugin-react-swc": "^3.x",
    "eslint": "^9",
    "eslint-plugin-react": "^7.x",
    "eslint-plugin-react-hooks": "^5.x",
    "prettier": "^3.x",
    "tailwindcss": "^4",
    "typescript": "^5",
    "typescript-eslint": "^8.x",
    "vite": "^5.x"
  }
}
```

**移除**：`next`、`next-themes`、`eslint-config-next`、`@tailwindcss/postcss`、`shadcn`（CLI 不需要在 deps）

### 4.2 `vite.config.ts`

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { TanStackRouterVite } from '@tanstack/router-vite-plugin'
import path from 'node:path'

// Tauri v2 约定：开发端口固定 1420
const host = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [
    TanStackRouterVite({ routesDirectory: './src/routes', generatedRouteTree: './src/routeTree.gen.ts' }),
    react(),
    tailwindcss(),
  ],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host ? { protocol: 'ws', host, port: 1421 } : undefined,
    watch: { ignored: ['**/src-tauri/**'] },
  },
})
```

### 4.3 `src/main.tsx`

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { routeTree } from './routeTree.gen'
import { queryClient } from './lib/query-client'
import './styles/globals.css'

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register { router: typeof router }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
```

### 4.4 `src/routes/__root.tsx`

```tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'

export const Route = createRootRoute({
  component: () => (
    <TooltipProvider>
      <Outlet />
      <Toaster richColors />
    </TooltipProvider>
  ),
})
```

### 4.5 `src/routes/index.tsx`

```tsx
import { createFileRoute } from '@tanstack/react-router'
import { WorkspaceLayout } from '@/layouts/workspace-layout'

export const Route = createFileRoute('/')({ component: WorkspaceLayout })
```

### 4.6 `src/layouts/workspace-layout.tsx`

三栏骨架：

```tsx
import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { ConnectionList } from '@/features/connection/components/connection-list'
import { SessionList } from '@/features/session/components/session-list'
import { ChatPanel } from '@/features/chat/components/chat-panel'
import { Workspace } from '@/features/workspace/components/workspace'

export function WorkspaceLayout() {
  return (
    <PanelGroup direction="horizontal" className="h-screen w-screen">
      <Panel defaultSize={18} minSize={12} maxSize={30}>
        <aside className="flex h-full flex-col border-r">
          <ConnectionList />
          <SessionList />
        </aside>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel defaultSize={42} minSize={25}>
        <ChatPanel />
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel defaultSize={40} minSize={25}>
        <Workspace />
      </Panel>
    </PanelGroup>
  )
}
```

### 4.7 `src/services/http.ts`

```ts
import ky from 'ky'

export const http = ky.create({
  prefixUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 30_000,
  retry: { limit: 1 },
})
```

### 4.8 `src-tauri/src/lib.rs`

```rust
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}!", name)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![greet])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 4.9 `src-tauri/tauri.conf.json`（关键字段）

```json
{
  "productName": "data-talk",
  "version": "0.1.0",
  "identifier": "com.datatalk.app",
  "build": {
    "devUrl": "http://localhost:1420",
    "frontendDist": "../dist",
    "beforeDevCommand": "pnpm dev",
    "beforeBuildCommand": "pnpm build"
  },
  "app": {
    "windows": [{
      "title": "data-talk",
      "width": 1400,
      "height": 900,
      "minWidth": 1024,
      "minHeight": 600,
      "resizable": true
    }],
    "security": { "csp": null }
  }
}
```

### 4.10 `src/features/workspace/store.ts`（zustand 示意）

```ts
import { create } from 'zustand'

export type WorkspaceTab = {
  id: string
  title: string
  kind: 'query-result' | 'er-diagram' | 'sql-editor'
  payload?: unknown
}

type WorkspaceState = {
  tabs: WorkspaceTab[]
  activeTabId: string | null
  openTab: (tab: WorkspaceTab) => void
  closeTab: (id: string) => void
  setActive: (id: string) => void
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  tabs: [],
  activeTabId: null,
  openTab: (tab) => set((s) => ({
    tabs: s.tabs.some((t) => t.id === tab.id) ? s.tabs : [...s.tabs, tab],
    activeTabId: tab.id,
  })),
  closeTab: (id) => set((s) => {
    const tabs = s.tabs.filter((t) => t.id !== id)
    const activeTabId = s.activeTabId === id ? (tabs.at(-1)?.id ?? null) : s.activeTabId
    return { tabs, activeTabId }
  }),
  setActive: (id) => set({ activeTabId: id }),
}))
```

---

## 5. 数据流

### 5.1 聊天发送

```
ChatInput (onSubmit)
  → useChat.sendMessage(text)
      · 本地 messages 立即追加 user 消息（optimistic）
      · 调 services/api/chat.sendMessage(sessionId, text)
      · react-query mutation 处理 pending/error
      · 成功后追加 ai 消息（或流式增量）
  → MessageList 订阅 useChatStore 自动重渲染
```

### 5.2 查询结果 → Workspace Tab

```
用户在聊天里确认执行 SQL
  → useChat 调 services/api/query.executeQuery
  → 成功后调 useWorkspaceStore.openTab({ kind: 'query-result', payload: result })
  → Workspace 组件按 activeTabId 渲染 QueryResultTab
  → QueryResultTab 把 payload 传给 data-grid/components/data-grid.tsx
```

### 5.3 跨 feature 通信规则

- 不允许 `features/a` 直接 `import` `features/b`
- 跨 feature 的状态放在 `src/stores/`，或由 `layouts/` 作为组合点
- 例外：`features/workspace/` 的 `openTab` 可以被 `features/chat/` 调用——因为 workspace 就是被设计为"其他 feature 的结果接收者"，这是合理的依赖方向（chat → workspace，单向）

---

## 6. 错误处理

- `services/http.ts` 统一抛 `ky` 的 `HTTPError`，上层 react-query mutation 收到后走 `onError`
- `onError` 里调 `toast.error(...)`（`sonner`）
- 聊天类错误在对话气泡中显示（AI 回复变成"⚠️ 请求失败：..."），不弹 toast 避免干扰
- 路由级 error 用 tanstack-router 的 `errorComponent` 统一兜底
- Tauri IPC 错误（`invoke` 失败）统一走 `services/tauri/index.ts` 的包装函数，内部捕获后转成 `Error` 抛出

---

## 7. 迁移步骤（宏观，详细写在 plan 里）

1. **装依赖**：删 `next*` 系列，装 Tauri + Vite + tanstack + zustand + ky 等
2. **初始化 Tauri**：`pnpm tauri init` 生成 `src-tauri/`，手工把 `productName`、窗口尺寸写进 `tauri.conf.json`
3. **新建 Vite 基础文件**：`index.html`、`vite.config.ts`、`tsconfig.node.json`，改 `tsconfig.json`
4. **搬运保留资产**：
   - `src/components/ui/**` → 原位保留
   - `src/hooks/use-mobile.ts` → 原位保留
   - `src/lib/utils.ts` → 原位保留
   - `src/app/globals.css` → `src/styles/globals.css`
   - dashboard 相关组件（`app-sidebar.tsx` / `nav-*.tsx` / `site-header.tsx` / `section-cards.tsx` / `data-table.tsx` / `chart-area-interactive.tsx`）→ `src/features/dashboard/components/`
   - `src/app/dashboard/data.json` → `src/features/dashboard/data/mock-data.json`
5. **删除 Next.js 残留**：
   - `src/app/layout.tsx`、`src/app/page.tsx`
   - `next.config.ts`、`next-env.d.ts`（如果有）
   - `postcss.config.mjs`（Tailwind v4 不再需要）
   - 旧 `eslint.config.mjs`
6. **写新入口**：`src/main.tsx`、`src/routes/__root.tsx`、`src/routes/index.tsx`、`src/routes/dashboard.tsx`
7. **搭 layouts**：`workspace-layout.tsx`、`dashboard-layout.tsx`
8. **搭 features 骨架**：每个 feature 建 `components/`、`hooks/`、`store.ts`、`types.ts`，每个 component 先返回占位 JSX（`<div>Chat panel placeholder</div>`）
9. **services 层**：写 `http.ts`、`api/health.ts`，其余 api 文件先建好签名
10. **Rust 侧**：`greet` 命令、`tauri.conf.json` 窗口配置
11. **lint / prettier 配置**：新写 flat config
12. **更新 `.gitignore`**：加 `dist/`、`src-tauri/target/`、`.tauri/`。`src/routeTree.gen.ts` **提交入库**（不加到 ignore），便于 CI 类型检查
13. **README 重写**：`pnpm install` → `pnpm tauri dev` → 端口 1420
14. **验证成功标准 1-6**

---

## 8. 风险与对策

| 风险 | 对策 |
|------|------|
| `next` 没装成功说明 `pnpm install` 本就有问题（lockfile 可能损坏） | 重建前先 `rm -rf node_modules pnpm-lock.yaml`，`package.json` 清完再重装 |
| Tauri v2 刚发布不久，shadcn/base-ui 在 Tauri WebView 下可能有 CSS 兼容问题 | 完成后手动测试一次 dashboard 页渲染；如遇 `backdrop-filter` 等高级属性问题，记入后续 |
| `@tanstack/router-vite-plugin` 生成 `routeTree.gen.ts` 可能和手写路由冲突 | 严格遵循文件路由约定，不手写 router 配置；`routeTree.gen.ts` 不手改 |
| Tailwind v4 + Vite 插件 + shadcn base-nova CSS 变量 三者组合没有验证过 | `globals.css` 直接搬；如 base-nova 变量因插件替换失效，对照 shadcn 最新文档修 `@theme` 块 |
| `eslint-config-next` 移除后 React hooks 规则缺失 | 显式装 `eslint-plugin-react-hooks` 并启用 `recommended` |
| Rust 工具链缺失导致 `tauri dev` 失败 | 在 README 写明依赖：`rustup`、`cargo`、Linux 下 `libwebkit2gtk-4.1-dev` 等 |

---

## 9. 开放问题

以下不阻塞本次实施，记录待后续决策：

- **ECharts 切换时机**：第二阶段"AI 生成图表配置"才需要，留到那时再 `pnpm remove recharts && pnpm add echarts echarts-for-react`
- **凭据存储**：数据库密码怎么存？候选：`tauri-plugin-stronghold`、`keyring-rs` via 自建命令、加密后存 SQLite。等后端 `server/` 确认密码走哪端再定
- **流式响应协议**：后端 OpenCode 走 SSE 还是 WebSocket？影响 `services/api/chat.ts` 实现
- **主题切换**：移除 `next-themes` 后需要一个轻量替代，可用 `@base-ui/react` 的 theming 或 `usehooks-ts` 的 `useDarkMode`
- **i18n**：目前全中文硬编码，后续如需多语言再引 `i18next`
