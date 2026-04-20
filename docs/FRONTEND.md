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

### 国际化约定

- 全局国际化入口位于 `src/i18n/`，通过 `I18nProvider` + `useI18n()` 提供 `t()`。
- 当前支持 `zh-CN` 和 `en-US`；语言状态持久化在 `ui-settings-store`。
- 用户可见文案优先写成 message key；provider 名、model 名、数据库对象名保持原样，不做翻译。
- HTTP 请求统一附带 `Accept-Language`，用于驱动后端返回同语言错误和默认文案。

### 样式调试经验

- **"整体发灰"先查 `opacity`，不是 `color`**：若一组元素（文字、图标、开关、按钮）**同时**显灰且对比度一致降低，大概率是父级被 `opacity` 降调，不是文字颜色继承。`opacity` 作为合成层属性会让所有后代一起半透明，伪装成"颜色都变了"
- **警惕 `has-*` 的连锁效应**：shadcn 基础组件（`InputGroup`、`Form` 等）常带 `has-disabled:opacity-50`、`has-[...]:...` 这类 `&:has()` 选择器规则——**容器内任意子元素带 `disabled` 属性，整个容器被拖累**。排查时先看容器的完整 class 串，再排查内部谁带了 `disabled`
- **按钮禁用优先用 `aria-disabled` + `aria-disabled:*` class**，而不是 HTML `disabled` 属性。`disabled` 会触发父级 `has-disabled` 连锁；`aria-disabled` 保留无障碍语义和视觉（配合 `aria-disabled:opacity-50 aria-disabled:cursor-not-allowed`）但不污染父级。点击行为在 handler 里手动早退即可
- **视觉症状对不上单一变量解释时，先开 DevTools 看 Computed**：不要凭代码推理反复改 class。例如 Switch 的 thumb 位置（右=checked）和底色（灰=unchecked）互相矛盾 → 立刻查父级 `opacity`，比猜 10 次快
