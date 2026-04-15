# 前端全面重构设计文档

> 日期：2026-04-15
> 状态：待评审

## 概述

将前端项目从零重构为 Feature-based 分层架构，所有 UI 组件必须使用 shadcn/ui，通过 `npx shadcn@latest add` 命令安装。布局保留 sidebar + 双面板风格，但全面采用 shadcn dashboard-01 设计规范。

**核心约束：**
- 所有前端组件必须删除重建，不保留任何旧组件
- UI 组件 100% 使用 shadcn/ui（通过 `npx shadcn@latest add` 安装）
- `InputGroup` 和 `AutosizeTextarea` 不在 shadcn 官方组件中，从社区 registry（originui.com）安装
- 输入框必须使用 HeroPrompt 风格：InputGroup + AutosizeTextarea + 圆形 ArrowUp 提交按钮
- 布局保留 sidebar + 左右双面板（聊天 | 查询结果）

---

## 架构设计

### 分层结构

```
client/src/
├── main.tsx                          # 应用入口
├── App.tsx                           # 布局入口
├── vite-env.d.ts                     # Vite 类型声明
│
├── app/                              # 应用层（布局配置）
│   └── layout.tsx                    # SidebarProvider 全局布局
│
├── components/                       # 通用 UI 组件（无业务逻辑）
│   ├── ui/                           # shadcn 组件（npx 安装）
│   │   ├── button.tsx
│   │   ├── card.tsx
│   │   ├── badge.tsx
│   │   ├── table.tsx
│   │   ├── tabs.tsx
│   │   ├── scroll-area.tsx
│   │   ├── alert.tsx
│   │   ├── skeleton.tsx
│   │   ├── separator.tsx
│   │   ├── tooltip.tsx
│   │   ├── avatar.tsx
│   │   ├── dropdown-menu.tsx
│   │   ├── collapsible.tsx
│   │   ├── sheet.tsx
│   │   ├── breadcrumb.tsx
│   │   ├── input.tsx
│   │   └── textarea.tsx
│   ├── input-group.tsx               # 社区 registry 安装
│   └── autosize-textarea.tsx         # 社区 registry 安装
│
├── features/                         # 业务功能模块
│   ├── chat/                         # 聊天功能
│   │   ├── components/
│   │   │   ├── chat-area.tsx
│   │   │   ├── message-item.tsx
│   │   │   └── chat-input.tsx
│   │   ├── hooks/
│   │   │   └── use-chat.ts
│   │   └── types.ts
│   │
│   ├── query-result/                 # 查询结果功能
│   │   ├── components/
│   │   │   ├── query-result.tsx
│   │   │   └── result-table.tsx
│   │   └── types.ts
│   │
│   └── sidebar/                      # 侧边栏功能
│       └── components/
│           ├── app-sidebar.tsx
│           ├── nav-main.tsx
│           ├── nav-projects.tsx
│           ├── nav-user.tsx
│           └── team-switcher.tsx
│
├── hooks/                            # 全局共享 hooks
│   └── use-mobile.ts
│
├── lib/                              # 工具函数
│   └── utils.ts                      # cn()
│
├── services/                         # API 服务层
│   └── api.ts                        # Tauri/HTTP 调用
│
└── types/                            # 全局类型定义
    └── index.ts
```

### 分层原则

| 层级 | 职责 | 示例 |
|------|------|------|
| `components/ui/` | 纯 UI，无业务逻辑，全部 shadcn | Button, Card, Table |
| `components/` | 项目级通用组件，无业务逻辑 | InputGroup, AutosizeTextarea |
| `features/` | 按功能划分，含 components + hooks + types | chat, query-result, sidebar |
| `services/` | 纯 API 调用层，不依赖 React | executeQuery |
| `types/` | 全局共享类型 | QueryResponse, Message |
| `hooks/` | 全局共享 hooks | use-mobile |
| `app/` | 布局配置，路由（如需） | layout.tsx |

---

## 核心组件设计

### ChatInput (`features/chat/components/chat-input.tsx`)

- 包裹在 `<form>` 中，Enter 提交，Shift+Enter 换行
- 使用 `InputGroup` + `AutosizeTextarea` + 圆形 ArrowUp 按钮
- 无内容时提交按钮禁用
- 无状态管理，仅接收 `onSubmit` 和 `disabled` props

### MessageItem (`features/chat/components/message-item.tsx`)

- 使用 shadcn `Card` 包裹消息
- 用户消息：Card 靠右对齐，primary 背景
- AI 消息：Card 靠左对齐，muted 背景
- 支持 loading 状态（Skeleton 动画）

### ChatArea (`features/chat/components/chat-area.tsx`)

- 使用 shadcn `ScrollArea` 包裹消息列表
- 底部固定 ChatInput
- 通过 `useChat` hook 获取状态
- 不直接调用 API

### useChat (`features/chat/hooks/use-chat.ts`)

- 管理：messages 列表、loading 状态、error 状态
- 提供：`sendMessage(sql: string)` 方法
- 内部调用 `services/api.executeQuery`
- 返回：`{ messages, isLoading, error, sendMessage }`

### ResultTable (`features/query-result/components/result-table.tsx`)

- 使用 shadcn `Table` 组件
- 表头固定（sticky header）
- 外层使用 `ScrollArea` 包裹实现滚动

### QueryResult (`features/query-result/components/query-result.tsx`)

- 顶部：Badge 显示行数 + 耗时
- 中部：ResultTable 数据表格
- 空状态：居中提示"执行查询后结果将显示在这里"

### Sidebar 组件

- 保留 nav-main, nav-projects, nav-user, team-switcher 结构
- 清理 mock 数据，预留真实项目数据接口
- 全部使用 shadcn sidebar 组件体系

### Layout (`app/layout.tsx`)

- 包裹 `SidebarProvider`
- 顶部 header 使用 shadcn `Separator` 分隔
- 主内容区：左右 50/50 双面板

---

## 数据流

```
用户输入
  → ChatInput (onSubmit)
    → useChat.sendMessage(sql)
      → services/api.executeQuery()
        → useChat 更新 messages + 返回 queryResult
          → App.tsx 传递 queryResult 到 QueryResult
            → ResultTable 渲染
```

- 状态通过 props 提升：App.tsx 持有 queryResult
- ChatInput 通过 onSubmit 回调与 useChat 通信
- 组件不直接调用 API

---

## 错误处理

- `useChat` 捕获 API 错误，以 AI 消息气泡展示错误信息
- 查询无数据：QueryResult 显示空状态提示
- 网络错误：显示"查询失败: {错误信息}"
- 侧边栏加载失败：使用 Skeleton 加载态

---

## shadcn 组件安装清单

### 已有组件（不重装）

button, textarea, input, sidebar, table, tabs, tooltip, skeleton, avatar, dropdown-menu, collapsible, sheet, breadcrumb, separator

### 新增安装

```bash
npx shadcn@latest add card
npx shadcn@latest add badge
npx shadcn@latest add scroll-area
npx shadcn@latest add alert
```

### 社区组件

```bash
npx shadcn@latest add https://originui.com/input-group
npx shadcn@latest add https://originui.com/autosize-textarea
```

---

## 实施顺序

1. 安装所有 shadcn 组件（card, badge, scroll-area, alert + 社区组件）
2. 删除所有旧前端组件文件
3. 创建 `features/`、`types/`、`app/` 目录结构
4. 实现 `useChat` hook
5. 实现 `ChatInput`、`MessageItem`、`ChatArea`
6. 实现 `ResultTable`、`QueryResult`
7. 重构 `sidebar/` 组件（保留结构，清理 mock 数据）
8. 实现 `app/layout.tsx`
9. 重写 `App.tsx` 使用新架构
10. 更新 `main.tsx` import 路径
