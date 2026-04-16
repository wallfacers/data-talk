# 前端开发指南

## 技术栈

| 技术 | 用途 |
|------|------|
| Tauri v2 | 桌面应用容器 |
| React 19 | UI 框架 |
| Vite 5 | 构建工具 |
| TanStack Router | 文件式路由 |
| TanStack Query | 服务端状态管理 |
| Zustand | 客户端状态管理 |
| shadcn/ui | UI 组件库 |
| Tailwind CSS v4 | 样式方案 |
| Recharts | 数据可视化 |
| Zod v4 | 运行时类型校验 |

## 项目结构

```
client/
├── src/
│   ├── features/              # 按功能模块组织
│   │   ├── chat/              # 聊天界面
│   │   │   ├── components/    # chat-panel, chat-input, message-list, message-item
│   │   │   ├── hooks/         # use-chat
│   │   │   ├── store.ts       # Zustand store
│   │   │   └── types.ts       # 类型定义
│   │   ├── session/           # 会话管理
│   │   ├── connection/        # 数据库连接
│   │   ├── workspace/         # 右侧工作区 (tab bar + 内容区)
│   │   ├── data-grid/         # 数据表格
│   │   └── dashboard/         # 仪表盘 (示例页面)
│   ├── components/ui/         # shadcn/ui 基础组件
│   ├── types/api.ts           # API 类型定义
│   ├── lib/                   # 工具函数
│   └── main.tsx               # 入口
├── src-tauri/                 # Tauri Rust 后端
└── package.json
```

## 命令

```bash
npm install              # 安装依赖
npm run dev              # Vite 开发服务器
npm run tauri dev        # Tauri 开发模式（含热重载）
npm run build            # 生产构建
npm run typecheck        # TypeScript 类型检查
npm run lint             # ESLint 检查
npm run format           # Prettier 格式化
npm run gen:routes       # 重新生成 TanStack Router 路由树
```

## 开发约定

### Feature 模块结构

每个 feature 是一个自包含目录：

```
features/xxx/
├── components/        # React 组件（kebab-case 文件名）
├── hooks/             # 自定义 hooks（use-xxx.ts）
├── store.ts           # Zustand store（客户端状态）
└── types.ts           # 类型定义
```

### 状态管理原则

- **服务端状态**（API 数据）→ TanStack Query（自动缓存、重试、失效）
- **客户端状态**（UI 状态、临时状态）→ Zustand store
- 不混用：Zustand store 不缓存 API 响应

### 组件约定

- 组件文件名 kebab-case：`chat-input.tsx`
- 组件导出名 PascalCase：`export function ChatInput()`
- 使用 shadcn/ui 组件，避免重新造轮子
- 响应式布局使用 `react-resizable-panels`

### 与后端通信

- HTTP 客户端：`ky`（轻量 fetch 封装）
- SSE 流：通过 `EventSource` 或 fetch streaming 接收 `DtEvent`
- API 类型定义集中在 `src/types/api.ts`
