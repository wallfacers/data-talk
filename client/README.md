# client

data-talk 桌面端：Tauri v2 + Vite + React 19 + TypeScript，界面用 shadcn/ui。

## 依赖

- Node.js ≥ 20
- pnpm ≥ 9
- Rust ≥ 1.77（`rustup` 安装）
- Linux 还需：`sudo apt install libwebkit2gtk-4.1-dev build-essential libssl-dev libayatana-appindicator3-dev librsvg2-dev`
- macOS 需要 Xcode Command Line Tools
- Windows 需要 Microsoft Edge WebView2（Windows 10+ 自带）

## 开发

```bash
pnpm install
pnpm tauri dev
```

首次启动会编译 Rust 侧，耗时较长。之后热重载在秒级。

## 脚本

- `pnpm dev` — 仅启动 Vite 开发服务器（端口 1420）
- `pnpm tauri dev` — Vite + Tauri 同时起，打开桌面窗口
- `pnpm build` — 前端生产构建
- `pnpm tauri build` — 打包桌面应用（.dmg/.msi/.deb）
- `pnpm typecheck` — TypeScript 检查
- `pnpm lint` — ESLint
- `pnpm format` — Prettier 格式化
- `pnpm gen:routes` — 重新生成 `src/routeTree.gen.ts`（一般由 Vite 插件自动跑）

## 目录

- `src/routes/` — TanStack Router 文件路由（`__root.tsx`, `index.tsx`, `dashboard.tsx`）
- `src/layouts/` — 页面级骨架（三栏工作台、dashboard）
- `src/features/<domain>/` — 业务域：chat / connection / session / workspace / data-grid / dashboard / (chart / diagram 预留)
- `src/components/ui/` — shadcn 原子组件
- `src/services/` — HTTP (ky) + Tauri IPC 封装
- `src/stores/` — 跨 feature 的全局 zustand store
- `src/lib/` — 纯工具（`cn`、`queryClient`）
- `src-tauri/` — Rust 侧

## 路由

- `/` — 三栏工作台（连接+会话 / 对话 / 工作区 Tab）
- `/dashboard` — 保留的 shadcn dashboard demo

## 环境变量

- `VITE_API_BASE_URL` — 后端 Spring Boot origin（仅协议+主机+端口，**不含 `/api`**），默认 `http://localhost:8080`。`/api` 路径前缀由 `src/services/api-prefix.ts` 的 `API_PREFIX` 统一管理
