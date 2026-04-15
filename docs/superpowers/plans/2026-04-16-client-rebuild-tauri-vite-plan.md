# client 重建为 Tauri v2 + Vite + React — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `client/` 从 Next.js 16 原地重建为 Tauri v2 + Vite + React + TanStack Router + zustand + ky/react-query 的桌面应用骨架，保留 dashboard 页面，三栏工作台跑起来。

**Architecture:** 按 feature-based 分层。`src/routes/`（tanstack-router 文件路由）→ `src/layouts/`（页面骨架）→ `src/features/<domain>/`（业务域组件+store+types）→ `src/services/`（HTTP/IPC）→ `src/components/ui/`（shadcn 原子）。跨 feature 不直接引用，通过 `src/stores/` 或 layout 组合。

**Tech Stack:** Tauri v2, Vite 5, React 19, TypeScript strict, TanStack Router (file-based), TanStack Query, zustand, ky, react-resizable-panels, Tailwind v4, shadcn/ui (style: base-nova), Recharts, pnpm.

**Spec:** `docs/superpowers/specs/2026-04-16-client-rebuild-tauri-vite-design.md`

---

## 执行前先读

1. `client/AGENTS.md` 警告过：此项目 Next.js 版本有破坏性改动。但本计划**移除 Next.js**，不再受此约束。Vite + React 19 按官方文档即可。
2. 需要 Rust 工具链（`rustup` + `cargo`）才能跑 `tauri dev`。如果系统缺少，先装：`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`。
3. Linux 还需要系统依赖（Ubuntu/Debian）：`sudo apt install libwebkit2gtk-4.1-dev build-essential libssl-dev libayatana-appindicator3-dev librsvg2-dev`。**此计划不负责装系统包**，如 `pnpm tauri dev` 因此失败，先处理系统依赖再回来。
4. 所有命令都在 `client/` 目录下运行，除非另有说明。
5. 每个 Task 末尾都有 commit 步骤——坚持频繁提交。

---

## 文件总览

本计划结束时 `client/` 的目标状态：

```
client/
├── src-tauri/               新建：Rust 壳
├── public/                  保留：Vite 静态资源
├── index.html               新建：Vite 入口
├── vite.config.ts           新建
├── tsconfig.json            改写：去掉 Next 插件
├── tsconfig.node.json       新建
├── package.json             改写：换掉 Next/postcss，加 Tauri/Vite/tanstack/zustand/ky
├── pnpm-lock.yaml           重生成
├── components.json          保留
├── eslint.config.mjs        改写：去掉 next 预设
├── .prettierrc              新建
├── .gitignore               追加：dist/, src-tauri/target/
├── README.md                改写
├── AGENTS.md                保留
├── CLAUDE.md                保留
└── src/
    ├── main.tsx                   新建
    ├── routeTree.gen.ts           自动生成后提交
    ├── vite-env.d.ts              新建
    ├── routes/                    新建 3 个文件
    ├── layouts/                   新建 2 个文件
    ├── features/                  新建 8 个子目录
    ├── components/ui/             保留（从旧项目原位）
    ├── services/                  新建
    ├── stores/                    新建
    ├── hooks/use-mobile.ts        保留
    ├── lib/
    │   ├── utils.ts               保留
    │   └── query-client.ts        新建
    ├── types/api.ts               新建
    ├── styles/globals.css         移动自 src/app/globals.css
    └── assets/                    新建
```

删除清单（Task 1 执行）：

- `src/app/`（整个目录，含 `layout.tsx`, `page.tsx`, `favicon.ico`, `dashboard/`）
- `src/components/app-sidebar.tsx`, `nav-main.tsx`, `nav-documents.tsx`, `nav-secondary.tsx`, `nav-user.tsx`, `site-header.tsx`, `section-cards.tsx`, `data-table.tsx`, `chart-area-interactive.tsx`（Task 6 之前移动到 `features/dashboard/components/`，等效于"移动后旧位置不存在"）
- `next.config.ts`
- `next-env.d.ts`（如存在）
- `postcss.config.mjs`
- 旧 `eslint.config.mjs` 内容将被改写

---

## Task 1: 清理 Next.js 残留并清空 node_modules

**Files:**
- Delete: `client/src/app/` (recursive)
- Delete: `client/next.config.ts`
- Delete: `client/postcss.config.mjs`
- Delete: `client/next-env.d.ts` (if exists)
- Delete: `client/node_modules/`
- Delete: `client/pnpm-lock.yaml`
- Delete: `client/dist/`（如存在）

- [ ] **Step 1: 检查当前 working tree 干净**

Run: `cd client && git status`
Expected: `nothing to commit, working tree clean`（或仅你此前的未提交改动）

- [ ] **Step 2: 删除 Next.js 特有文件**

Run:
```bash
cd client
rm -rf src/app
rm -f next.config.ts next-env.d.ts postcss.config.mjs
rm -rf node_modules pnpm-lock.yaml dist
```

- [ ] **Step 3: 确认文件已删除**

Run: `ls client/src/ && ls client/ | head -30`
Expected `ls client/src/` 输出：
```
components
hooks
lib
```
Expected `ls client/` 输出中**没有** `next.config.ts`, `postcss.config.mjs`, `next-env.d.ts`, `node_modules`, `pnpm-lock.yaml`, `dist`。

- [ ] **Step 4: commit 清理**

Run:
```bash
cd client
git add -A
git commit -m "$(cat <<'EOF'
chore(client): remove Next.js artifacts before Tauri+Vite rebuild

Delete src/app/, next.config.ts, postcss.config.mjs, next-env.d.ts
(if present), node_modules/, pnpm-lock.yaml, and dist/ in preparation
for in-place migration to Tauri v2 + Vite + TanStack Router stack.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 改写 package.json 并重装依赖

**Files:**
- Modify: `client/package.json`

- [ ] **Step 1: 覆写 `client/package.json`**

将 `client/package.json` 内容替换为：

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
    "format": "prettier --write .",
    "gen:routes": "tsr generate"
  },
  "dependencies": {
    "@base-ui/react": "^1.4.0",
    "@dnd-kit/core": "^6.3.1",
    "@dnd-kit/modifiers": "^9.0.0",
    "@dnd-kit/sortable": "^10.0.0",
    "@dnd-kit/utilities": "^3.2.2",
    "@tanstack/react-query": "^5.59.0",
    "@tanstack/react-router": "^1.87.0",
    "@tanstack/react-table": "^8.21.3",
    "@tauri-apps/api": "^2.1.1",
    "@tauri-apps/plugin-shell": "^2.0.1",
    "class-variance-authority": "^0.7.1",
    "clsx": "^2.1.1",
    "ky": "^1.7.2",
    "lucide-react": "^1.8.0",
    "react": "19.2.4",
    "react-dom": "19.2.4",
    "react-resizable-panels": "^2.1.7",
    "recharts": "3.8.0",
    "sonner": "^2.0.7",
    "tailwind-merge": "^3.5.0",
    "tw-animate-css": "^1.4.0",
    "vaul": "^1.1.2",
    "zod": "^4.3.6",
    "zustand": "^5.0.2"
  },
  "devDependencies": {
    "@eslint/js": "^9.17.0",
    "@tailwindcss/vite": "^4.0.0",
    "@tanstack/router-cli": "^1.87.0",
    "@tanstack/router-vite-plugin": "^1.87.0",
    "@tauri-apps/cli": "^2.1.0",
    "@types/node": "^20.17.10",
    "@types/react": "^19.0.2",
    "@types/react-dom": "^19.0.2",
    "@vitejs/plugin-react-swc": "^3.7.2",
    "eslint": "^9.17.0",
    "eslint-plugin-react": "^7.37.2",
    "eslint-plugin-react-hooks": "^5.1.0",
    "globals": "^15.14.0",
    "prettier": "^3.4.2",
    "tailwindcss": "^4.0.0",
    "typescript": "^5.7.2",
    "typescript-eslint": "^8.18.1",
    "vite": "^5.4.11"
  }
}
```

注意相对原 `package.json`：
- **移除**：`next`, `next-themes`, `eslint-config-next`, `@tailwindcss/postcss`, `shadcn`（CLI 不在 deps）
- **新增 dep**：`@tanstack/react-query`, `@tanstack/react-router`, `@tauri-apps/api`, `@tauri-apps/plugin-shell`, `ky`, `react-resizable-panels`, `zustand`
- **新增 devDep**：`@eslint/js`, `@tailwindcss/vite`, `@tanstack/router-cli`, `@tanstack/router-vite-plugin`, `@tauri-apps/cli`, `@vitejs/plugin-react-swc`, `eslint-plugin-react`, `eslint-plugin-react-hooks`, `globals`, `prettier`, `typescript-eslint`, `vite`

- [ ] **Step 2: 安装依赖**

Run: `cd client && pnpm install`
Expected: 退出码 0，生成 `pnpm-lock.yaml` 和 `node_modules/`。可能需要 1-3 分钟。

- [ ] **Step 3: 验证 next 真的没了**

Run: `ls client/node_modules/ | grep -i '^next$'`
Expected: 空输出（没有 next 包）

Run: `ls client/node_modules/@tauri-apps/cli/`
Expected: 能看到 `package.json` 等（说明 Tauri CLI 装好）

- [ ] **Step 4: commit**

Run:
```bash
cd client
git add package.json pnpm-lock.yaml
git commit -m "$(cat <<'EOF'
chore(client): replace package.json with Tauri+Vite dependency set

Remove next, next-themes, eslint-config-next, @tailwindcss/postcss.
Add @tauri-apps/cli+api, vite, @vitejs/plugin-react-swc, tailwindcss
vite plugin, TanStack router+query, zustand, ky, react-resizable-panels,
prettier, and the modern eslint flat-config toolchain.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 创建 Vite 壳文件

**Files:**
- Create: `client/index.html`
- Create: `client/vite.config.ts`
- Modify: `client/tsconfig.json`
- Create: `client/tsconfig.node.json`
- Create: `client/src/vite-env.d.ts`

- [ ] **Step 1: 写 `client/index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>data-talk</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 2: 写 `client/vite.config.ts`**

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { TanStackRouterVite } from '@tanstack/router-vite-plugin'
import path from 'node:path'

const host = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [
    TanStackRouterVite({
      routesDirectory: './src/routes',
      generatedRouteTree: './src/routeTree.gen.ts',
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host ? { protocol: 'ws', host, port: 1421 } : undefined,
    watch: { ignored: ['**/src-tauri/**'] },
  },
  envPrefix: ['VITE_', 'TAURI_ENV_*'],
})
```

- [ ] **Step 3: 改写 `client/tsconfig.json`**

覆写内容：

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "isolatedModules": true,
    "resolveJsonModule": true,
    "allowImportingTsExtensions": false,
    "noEmit": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "useDefineForClassFields": true,
    "types": ["vite/client", "node"],
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: 写 `client/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "target": "ES2022",
    "lib": ["ES2022"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "skipLibCheck": true,
    "strict": true,
    "allowSyntheticDefaultImports": true,
    "types": ["node"],
    "noEmit": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: 写 `client/src/vite-env.d.ts`**

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

- [ ] **Step 6: commit**

Run:
```bash
cd client
git add index.html vite.config.ts tsconfig.json tsconfig.node.json src/vite-env.d.ts
git commit -m "$(cat <<'EOF'
feat(client): add Vite shell with TanStack Router and Tailwind plugins

Configure Vite for Tauri integration (port 1420, strictPort), alias @ →
./src, TanStack Router file-based plugin, tailwindcss vite plugin, and
SWC-powered React. Update tsconfig to strict mode with noUnusedLocals/
Parameters; add tsconfig.node.json as a project reference for
vite.config.ts.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 初始化 `src-tauri/` Rust 壳

**Files:**
- Create: `client/src-tauri/` (via `pnpm tauri init`, then customize)
- Modify: `client/src-tauri/tauri.conf.json`
- Modify: `client/src-tauri/src/lib.rs`

- [ ] **Step 1: 运行 `pnpm tauri init`（非交互）**

Run:
```bash
cd client
pnpm tauri init \
  --ci \
  --app-name "data-talk" \
  --window-title "data-talk" \
  --dev-url "http://localhost:1420" \
  --frontend-dist "../dist" \
  --before-dev-command "pnpm dev" \
  --before-build-command "pnpm build"
```
Expected: 生成 `client/src-tauri/` 目录，含 `Cargo.toml`, `tauri.conf.json`, `src/main.rs`, `src/lib.rs`, `build.rs`, `capabilities/default.json`, `icons/`。

- [ ] **Step 2: 验证目录**

Run: `ls client/src-tauri/`
Expected:
```
Cargo.toml
build.rs
capabilities
icons
src
tauri.conf.json
```

- [ ] **Step 3: 覆写 `client/src-tauri/tauri.conf.json` 窗口配置**

读一下 `tauri init` 生成的 JSON，保留 `identifier` 等字段，只修改 `productName` / `app.windows`。完整替换为：

```json
{
  "$schema": "../node_modules/@tauri-apps/cli/config.schema.json",
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
    "windows": [
      {
        "title": "data-talk",
        "width": 1400,
        "height": 900,
        "minWidth": 1024,
        "minHeight": 600,
        "resizable": true,
        "fullscreen": false
      }
    ],
    "security": {
      "csp": null
    }
  },
  "bundle": {
    "active": true,
    "targets": "all",
    "icon": [
      "icons/32x32.png",
      "icons/128x128.png",
      "icons/128x128@2x.png",
      "icons/icon.icns",
      "icons/icon.ico"
    ]
  }
}
```

- [ ] **Step 4: 写 `greet` 命令到 `client/src-tauri/src/lib.rs`**

覆写为：

```rust
#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
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

- [ ] **Step 5: 确认 `main.rs` 调 `lib::run()`**

`client/src-tauri/src/main.rs` 应该已经是 `tauri init` 生成的：

```rust
// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    client_lib::run()
}
```

如果不是这个内容（比如 `app_lib::run()` 或 `data_talk_lib::run()`），按 `Cargo.toml` 里 `[lib] name = "..."` 的值对齐 `main.rs` 里的模块名。读 `Cargo.toml` 确认 lib name。

Run: `grep -A1 '\[lib\]' client/src-tauri/Cargo.toml`
Expected: 形如 `[lib]\nname = "client_lib"`（按此值调用）

- [ ] **Step 6: commit**

Run:
```bash
cd client
git add src-tauri
git commit -m "$(cat <<'EOF'
feat(client): initialize Tauri v2 scaffolding with greet command

Bootstrap src-tauri/ via tauri init (non-interactive) with identifier
com.datatalk.app, dev URL port 1420, before-dev/build hooks wired to
pnpm dev/build. Customize tauri.conf.json for 1400x900 initial window
(min 1024x600) and add a greet IPC command to verify invoke wiring.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 迁移 `globals.css` 到 `src/styles/`

**Files:**
- Move: `client/src/styles/globals.css`（原来在 `src/app/globals.css` 已随 Task 1 删除）

但 Task 1 里连同 `src/app/` 把 `globals.css` 也删了。需要从 git 历史恢复或重写。

- [ ] **Step 1: 从 git 历史把 `globals.css` 恢复到新位置**

Run:
```bash
cd client
mkdir -p src/styles
git show HEAD~2:client/src/app/globals.css > src/styles/globals.css
```
（`HEAD~2` 假设 Task 1 + Task 2 各一个 commit。如果 commit 数不同，用 `git log --oneline -- src/app/globals.css` 找到最后一个包含此文件的 commit hash，用 `git show <hash>:client/src/app/globals.css > ...` 替代。）

- [ ] **Step 2: 检查文件内容**

Run: `head -20 client/src/styles/globals.css`
Expected: 看到 `@import "tailwindcss"` 或 `@tailwind base` 或 `@theme { ... }` 之类 Tailwind v4 的内容。

- [ ] **Step 3: 适配 Tailwind v4 + Vite 插件**

Tailwind v4 via `@tailwindcss/vite` 不需要 PostCSS 配置。`globals.css` 头部应是：

```css
@import "tailwindcss";
@import "tw-animate-css";
```

如果恢复的文件已经是这样，跳过。如果是 `@tailwind base; @tailwind components; @tailwind utilities;`（v3 写法），替换顶部三行为上面的单行 `@import "tailwindcss";`。保留 `@theme`、`:root`、`.dark` 等 shadcn 的 CSS 变量块。

- [ ] **Step 4: commit**

Run:
```bash
cd client
git add src/styles/globals.css
git commit -m "$(cat <<'EOF'
feat(client): move globals.css to src/styles/ for Tailwind v4

Restore globals.css from prior location src/app/globals.css (deleted
during Next.js cleanup) to src/styles/globals.css. Adapt to Tailwind v4
import syntax if needed; shadcn base-nova CSS variables preserved.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 迁移 dashboard 组件到 `features/dashboard/`

**Files:**
- Move: `client/src/components/{app-sidebar,nav-main,nav-documents,nav-secondary,nav-user,site-header,section-cards,data-table,chart-area-interactive}.tsx` → `client/src/features/dashboard/components/`
- Move: （从 git 历史恢复）`data.json` → `client/src/features/dashboard/data/mock-data.json`
- Create: `client/src/features/dashboard/dashboard-page.tsx`

- [ ] **Step 1: 建目录并移动组件**

Run:
```bash
cd client
mkdir -p src/features/dashboard/components src/features/dashboard/data
git mv src/components/app-sidebar.tsx          src/features/dashboard/components/app-sidebar.tsx
git mv src/components/nav-main.tsx             src/features/dashboard/components/nav-main.tsx
git mv src/components/nav-documents.tsx        src/features/dashboard/components/nav-documents.tsx
git mv src/components/nav-secondary.tsx        src/features/dashboard/components/nav-secondary.tsx
git mv src/components/nav-user.tsx             src/features/dashboard/components/nav-user.tsx
git mv src/components/site-header.tsx          src/features/dashboard/components/site-header.tsx
git mv src/components/section-cards.tsx        src/features/dashboard/components/section-cards.tsx
git mv src/components/data-table.tsx           src/features/dashboard/components/data-table.tsx
git mv src/components/chart-area-interactive.tsx src/features/dashboard/components/chart-area-interactive.tsx
```

- [ ] **Step 2: 恢复 `data.json` 到新位置**

Run:
```bash
cd client
git show HEAD~3:client/src/app/dashboard/data.json > src/features/dashboard/data/mock-data.json
```
（`HEAD~3` 假设此时 Task 1/2/5 各产生一个 commit。如不对，用 `git log --oneline -- src/app/dashboard/data.json` 找准。）

- [ ] **Step 3: 修正移过来的组件里的导入路径（如果有相对路径）**

以上 9 个组件里若有 `import ... from "./something"` 的相对导入，其"something"可能指向同批移动的兄弟文件，git mv 后仍能工作，不用改。但如果有 `import ... from "../ui/..."` 这类，移动后 `ui/` 位置从 `../components/ui/` 变成 `../../../components/ui/`——此处我们用 `@/components/ui/...` 别名，绝对路径不受位置影响。

Run:
```bash
cd client
grep -rE "from ['\"]\.\./" src/features/dashboard/components/ || echo "no relative parent imports"
```
Expected: `no relative parent imports`（即所有对外引用都是 `@/...` 别名）。如有输出，改成 `@/...` 别名。

- [ ] **Step 4: 写 `src/features/dashboard/dashboard-page.tsx`**

```tsx
import { AppSidebar } from '@/features/dashboard/components/app-sidebar'
import { ChartAreaInteractive } from '@/features/dashboard/components/chart-area-interactive'
import { DataTable } from '@/features/dashboard/components/data-table'
import { SectionCards } from '@/features/dashboard/components/section-cards'
import { SiteHeader } from '@/features/dashboard/components/site-header'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'

import mockData from './data/mock-data.json'

export function DashboardPage() {
  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 72)',
          '--header-height': 'calc(var(--spacing) * 12)',
        } as React.CSSProperties
      }
    >
      <AppSidebar variant="inset" />
      <SidebarInset>
        <SiteHeader />
        <div className="flex flex-1 flex-col">
          <div className="@container/main flex flex-1 flex-col gap-2">
            <div className="flex flex-col gap-4 py-4 md:gap-6 md:py-6">
              <SectionCards />
              <div className="px-4 lg:px-6">
                <ChartAreaInteractive />
              </div>
              <DataTable data={mockData} />
            </div>
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
```

- [ ] **Step 5: commit**

Run:
```bash
cd client
git add -A
git commit -m "$(cat <<'EOF'
feat(client): move dashboard-01 demo into features/dashboard

Relocate nine demo components (app-sidebar, nav-*, site-header,
section-cards, data-table, chart-area-interactive) and the mock data.json
from flat src/components/ into src/features/dashboard/{components,data}.
Add dashboard-page.tsx as the composed entry imported by the route.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: 共享基础设施 — `lib/query-client.ts`, `types/api.ts`, `stores/theme-store.ts`

**Files:**
- Create: `client/src/lib/query-client.ts`
- Create: `client/src/types/api.ts`
- Create: `client/src/stores/theme-store.ts`

- [ ] **Step 1: 写 `client/src/lib/query-client.ts`**

```ts
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
})
```

- [ ] **Step 2: 写 `client/src/types/api.ts`**

```ts
export type ApiResponse<T> = {
  data: T
}

export type ApiError = {
  code: string
  message: string
  details?: unknown
}

export type Paginated<T> = {
  items: T[]
  total: number
  page: number
  pageSize: number
}
```

- [ ] **Step 3: 建 `stores/` 目录并写 `theme-store.ts`**

Run: `mkdir -p client/src/stores`

写 `client/src/stores/theme-store.ts`：

```ts
import { create } from 'zustand'

export type Theme = 'light' | 'dark' | 'system'

type ThemeState = {
  theme: Theme
  setTheme: (theme: Theme) => void
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: 'system',
  setTheme: (theme) => set({ theme }),
}))
```

- [ ] **Step 4: commit**

Run:
```bash
cd client
git add src/lib/query-client.ts src/types/api.ts src/stores/theme-store.ts
git commit -m "$(cat <<'EOF'
feat(client): add query-client, api types, and theme store

Introduce shared foundations: singleton QueryClient with 30s staleTime
and no window-focus refetch, ApiResponse/ApiError/Paginated global types,
and a minimal zustand theme store (light/dark/system) as a placeholder
for later theme-switching UI.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Services 层 — `http.ts`、`api/*`、`tauri/index.ts`

**Files:**
- Create: `client/src/services/http.ts`
- Create: `client/src/services/api/health.ts`
- Create: `client/src/services/api/connection.ts`
- Create: `client/src/services/api/session.ts`
- Create: `client/src/services/api/chat.ts`
- Create: `client/src/services/api/query.ts`
- Create: `client/src/services/tauri/index.ts`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/services/api client/src/services/tauri`

- [ ] **Step 2: 写 `services/http.ts`**

```ts
import ky from 'ky'

export const http = ky.create({
  prefixUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 30_000,
  retry: { limit: 1 },
  hooks: {
    beforeError: [
      async (error) => {
        try {
          const body = await error.response.clone().json<{ message?: string }>()
          if (body?.message) error.message = body.message
        } catch {
          // response 不是 JSON，忽略
        }
        return error
      },
    ],
  },
})
```

- [ ] **Step 3: 写 `services/api/health.ts`**

```ts
import { http } from '@/services/http'

export type HealthStatus = {
  status: 'ok' | 'degraded'
  timestamp: string
}

export function getHealth() {
  return http.get('health').json<HealthStatus>()
}
```

- [ ] **Step 4: 写 `services/api/connection.ts`**

```ts
import { http } from '@/services/http'

export type DbType = 'mysql' | 'postgres' | 'sqlserver' | 'oracle' | 'sqlite'

export type Connection = {
  id: string
  name: string
  dbType: DbType
  host: string
  port: number
  database: string
  username: string
}

export type CreateConnectionInput = Omit<Connection, 'id'> & { password: string }

export function listConnections() {
  return http.get('connections').json<Connection[]>()
}

export function createConnection(input: CreateConnectionInput) {
  return http.post('connections', { json: input }).json<Connection>()
}

export function testConnection(id: string) {
  return http.post(`connections/${id}/test`).json<{ ok: boolean; message?: string }>()
}
```

- [ ] **Step 5: 写 `services/api/session.ts`**

```ts
import { http } from '@/services/http'

export type Session = {
  id: string
  connectionId: string
  title: string
  createdAt: string
  updatedAt: string
}

export function listSessions(connectionId?: string) {
  const search = connectionId ? { connectionId } : undefined
  return http.get('sessions', { searchParams: search }).json<Session[]>()
}

export function createSession(connectionId: string, title: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}
```

- [ ] **Step 6: 写 `services/api/chat.ts`**

```ts
import { http } from '@/services/http'

export type MessageRole = 'user' | 'assistant' | 'system'

export type ChatMessage = {
  id: string
  sessionId: string
  role: MessageRole
  content: string
  createdAt: string
}

export function listMessages(sessionId: string) {
  return http.get(`sessions/${sessionId}/messages`).json<ChatMessage[]>()
}

export function sendMessage(sessionId: string, content: string) {
  return http
    .post(`sessions/${sessionId}/messages`, { json: { content } })
    .json<ChatMessage>()
}
```

- [ ] **Step 7: 写 `services/api/query.ts`**

```ts
import { http } from '@/services/http'

export type QueryResult = {
  columns: string[]
  rows: Array<Record<string, unknown>>
  durationMs: number
  rowCount: number
}

export type ExecuteQueryInput = {
  connectionId: string
  sql: string
}

export function executeQuery(input: ExecuteQueryInput) {
  return http.post('query', { json: input }).json<QueryResult>()
}
```

- [ ] **Step 8: 写 `services/tauri/index.ts`**

```ts
import { invoke } from '@tauri-apps/api/core'

export function greet(name: string): Promise<string> {
  return invoke<string>('greet', { name })
}
```

- [ ] **Step 9: commit**

Run:
```bash
cd client
git add src/services
git commit -m "$(cat <<'EOF'
feat(client): add services layer (ky HTTP + Tauri invoke wrappers)

Introduce src/services/: ky instance bound to VITE_API_BASE_URL (default
localhost:8080/api), typed API stubs for health/connection/session/chat/
query, and a Tauri invoke wrapper exposing greet(). All API functions
return typed promises; mutations route through POST/PATCH/DELETE.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: `features/chat/` 骨架

**Files:**
- Create: `client/src/features/chat/types.ts`
- Create: `client/src/features/chat/store.ts`
- Create: `client/src/features/chat/hooks/use-chat.ts`
- Create: `client/src/features/chat/components/chat-panel.tsx`
- Create: `client/src/features/chat/components/message-list.tsx`
- Create: `client/src/features/chat/components/message-item.tsx`
- Create: `client/src/features/chat/components/chat-input.tsx`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/features/chat/components client/src/features/chat/hooks`

- [ ] **Step 2: 写 `types.ts`**

```ts
export type MessageRole = 'user' | 'assistant' | 'system'

export type ChatMessage = {
  id: string
  role: MessageRole
  content: string
  createdAt: number
  pending?: boolean
  error?: string
}
```

- [ ] **Step 3: 写 `store.ts`**

```ts
import { create } from 'zustand'
import type { ChatMessage } from './types'

type ChatState = {
  messages: ChatMessage[]
  isStreaming: boolean
  addMessage: (message: ChatMessage) => void
  updateMessage: (id: string, patch: Partial<ChatMessage>) => void
  clear: () => void
  setStreaming: (streaming: boolean) => void
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  isStreaming: false,
  addMessage: (message) =>
    set((s) => ({ messages: [...s.messages, message] })),
  updateMessage: (id, patch) =>
    set((s) => ({
      messages: s.messages.map((m) => (m.id === id ? { ...m, ...patch } : m)),
    })),
  clear: () => set({ messages: [] }),
  setStreaming: (streaming) => set({ isStreaming: streaming }),
}))
```

- [ ] **Step 4: 写 `hooks/use-chat.ts`**

```ts
import { useCallback } from 'react'
import { useChatStore } from '../store'
import type { ChatMessage } from '../types'

function uid() {
  return Math.random().toString(36).slice(2, 10)
}

export function useChat() {
  const messages = useChatStore((s) => s.messages)
  const isStreaming = useChatStore((s) => s.isStreaming)
  const addMessage = useChatStore((s) => s.addMessage)

  const sendMessage = useCallback(
    async (content: string) => {
      const userMsg: ChatMessage = {
        id: uid(),
        role: 'user',
        content,
        createdAt: Date.now(),
      }
      addMessage(userMsg)
      // 真正的 AI 调用等后端 OpenCode 接通后再接；骨架期先留空。
    },
    [addMessage],
  )

  return { messages, isStreaming, sendMessage }
}
```

- [ ] **Step 5: 写 `components/message-item.tsx`**

```tsx
import { cn } from '@/lib/utils'
import type { ChatMessage } from '../types'

export function MessageItem({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user'
  return (
    <div className={cn('flex w-full', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[80%] rounded-lg px-4 py-2 text-sm',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted',
          message.error && 'border border-destructive',
        )}
      >
        {message.error ? `⚠️ ${message.error}` : message.content}
      </div>
    </div>
  )
}
```

- [ ] **Step 6: 写 `components/message-list.tsx`**

```tsx
import { useChatStore } from '../store'
import { MessageItem } from './message-item'

export function MessageList() {
  const messages = useChatStore((s) => s.messages)

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center text-muted-foreground">
        <p className="text-sm">问点什么，比如"查询用户表最近一周的注册趋势"</p>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-4">
      {messages.map((m) => (
        <MessageItem key={m.id} message={m} />
      ))}
    </div>
  )
}
```

- [ ] **Step 7: 写 `components/chat-input.tsx`**

```tsx
import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Button } from '@/components/ui/button'
import { useChat } from '../hooks/use-chat'

export function ChatInput() {
  const [value, setValue] = useState('')
  const { sendMessage, isStreaming } = useChat()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const text = value.trim()
    if (!text || isStreaming) return
    setValue('')
    await sendMessage(text)
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void handleSubmit(e as unknown as FormEvent)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Enter 发送，Shift+Enter 换行"
          rows={2}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
        />
        <Button type="submit" disabled={!value.trim() || isStreaming}>
          发送
        </Button>
      </div>
    </form>
  )
}
```

- [ ] **Step 8: 写 `components/chat-panel.tsx`**

```tsx
import { MessageList } from './message-list'
import { ChatInput } from './chat-input'

export function ChatPanel() {
  return (
    <div className="flex h-full flex-col">
      <header className="border-b px-4 py-3">
        <h2 className="text-sm font-semibold">对话</h2>
      </header>
      <MessageList />
      <ChatInput />
    </div>
  )
}
```

- [ ] **Step 9: commit**

Run:
```bash
cd client
git add src/features/chat
git commit -m "$(cat <<'EOF'
feat(client): scaffold chat feature (store, hook, panel, list, input)

Add features/chat/ with a zustand store (messages, isStreaming), a
useChat hook that appends user messages optimistically, and the four
React components for the chat column: ChatPanel shell, MessageList with
empty state, MessageItem bubbles styled by role, and ChatInput with
Enter-to-send / Shift+Enter-to-newline. Backend wiring deferred.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: `features/workspace/` 骨架 + Tab store

**Files:**
- Create: `client/src/features/workspace/types.ts`
- Create: `client/src/features/workspace/store.ts`
- Create: `client/src/features/workspace/components/workspace.tsx`
- Create: `client/src/features/workspace/components/tab-bar.tsx`
- Create: `client/src/features/workspace/components/empty-state.tsx`
- Create: `client/src/features/workspace/components/query-result-tab.tsx`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/features/workspace/components`

- [ ] **Step 2: 写 `types.ts`**

```ts
export type WorkspaceTabKind = 'query-result' | 'er-diagram' | 'sql-editor'

export type WorkspaceTab = {
  id: string
  title: string
  kind: WorkspaceTabKind
  payload?: unknown
}
```

- [ ] **Step 3: 写 `store.ts`**

```ts
import { create } from 'zustand'
import type { WorkspaceTab } from './types'

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
  openTab: (tab) =>
    set((s) => ({
      tabs: s.tabs.some((t) => t.id === tab.id) ? s.tabs : [...s.tabs, tab],
      activeTabId: tab.id,
    })),
  closeTab: (id) =>
    set((s) => {
      const tabs = s.tabs.filter((t) => t.id !== id)
      const activeTabId =
        s.activeTabId === id ? (tabs.at(-1)?.id ?? null) : s.activeTabId
      return { tabs, activeTabId }
    }),
  setActive: (id) => set({ activeTabId: id }),
}))
```

- [ ] **Step 4: 写 `components/empty-state.tsx`**

```tsx
export function EmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center text-muted-foreground">
      <p className="text-sm">执行查询后，结果会以 Tab 形式出现在这里</p>
    </div>
  )
}
```

- [ ] **Step 5: 写 `components/tab-bar.tsx`**

```tsx
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useWorkspaceStore } from '../store'

export function TabBar() {
  const tabs = useWorkspaceStore((s) => s.tabs)
  const activeTabId = useWorkspaceStore((s) => s.activeTabId)
  const setActive = useWorkspaceStore((s) => s.setActive)
  const closeTab = useWorkspaceStore((s) => s.closeTab)

  if (tabs.length === 0) return null

  return (
    <div className="flex items-center gap-1 border-b bg-muted/30 px-2">
      {tabs.map((tab) => {
        const active = tab.id === activeTabId
        return (
          <button
            key={tab.id}
            onClick={() => setActive(tab.id)}
            className={cn(
              'group flex items-center gap-2 rounded-t-md px-3 py-1.5 text-sm',
              active ? 'bg-background font-medium' : 'text-muted-foreground hover:bg-background/50',
            )}
          >
            <span>{tab.title}</span>
            <span
              role="button"
              aria-label="关闭"
              onClick={(e) => {
                e.stopPropagation()
                closeTab(tab.id)
              }}
              className="opacity-0 transition-opacity group-hover:opacity-100 hover:text-destructive"
            >
              <X className="size-3" />
            </span>
          </button>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 6: 写 `components/query-result-tab.tsx`**

```tsx
import type { WorkspaceTab } from '../types'

export function QueryResultTab({ tab }: { tab: WorkspaceTab }) {
  return (
    <div className="flex h-full flex-col p-4">
      <p className="text-sm text-muted-foreground">
        Tab: <span className="font-mono">{tab.title}</span>
      </p>
      <p className="mt-2 text-xs text-muted-foreground">
        查询结果表格将在接入 data-grid 后填充到这里。payload preview：
      </p>
      <pre className="mt-2 overflow-auto rounded-md border bg-muted p-3 text-xs">
        {JSON.stringify(tab.payload ?? null, null, 2)}
      </pre>
    </div>
  )
}
```

- [ ] **Step 7: 写 `components/workspace.tsx`**

```tsx
import { useWorkspaceStore } from '../store'
import { TabBar } from './tab-bar'
import { EmptyState } from './empty-state'
import { QueryResultTab } from './query-result-tab'

export function Workspace() {
  const tabs = useWorkspaceStore((s) => s.tabs)
  const activeTabId = useWorkspaceStore((s) => s.activeTabId)
  const activeTab = tabs.find((t) => t.id === activeTabId)

  return (
    <div className="flex h-full flex-col">
      <TabBar />
      <div className="flex-1 overflow-hidden">
        {!activeTab && <EmptyState />}
        {activeTab?.kind === 'query-result' && <QueryResultTab tab={activeTab} />}
        {activeTab?.kind === 'er-diagram' && (
          <div className="p-4 text-sm text-muted-foreground">ER 图（未实装）</div>
        )}
        {activeTab?.kind === 'sql-editor' && (
          <div className="p-4 text-sm text-muted-foreground">SQL 编辑器（未实装）</div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 8: commit**

Run:
```bash
cd client
git add src/features/workspace
git commit -m "$(cat <<'EOF'
feat(client): scaffold workspace feature with tab store

Add features/workspace/ with WorkspaceTab types (query-result | er-diagram
| sql-editor), a zustand store managing tabs / activeTabId / open/close/
setActive semantics, and the Workspace composite component: TabBar with
close-on-hover, EmptyState placeholder, and QueryResultTab payload
preview. ER/SQL kinds currently render placeholder text.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: `features/connection/` 骨架

**Files:**
- Create: `client/src/features/connection/types.ts`
- Create: `client/src/features/connection/store.ts`
- Create: `client/src/features/connection/hooks/use-connections.ts`
- Create: `client/src/features/connection/components/connection-list.tsx`
- Create: `client/src/features/connection/components/connection-form.tsx`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/features/connection/components client/src/features/connection/hooks`

- [ ] **Step 2: 写 `types.ts`**

```ts
export type { Connection, CreateConnectionInput, DbType } from '@/services/api/connection'
```

- [ ] **Step 3: 写 `store.ts`**

```ts
import { create } from 'zustand'

type ConnectionUIState = {
  activeConnectionId: string | null
  setActive: (id: string | null) => void
}

export const useConnectionStore = create<ConnectionUIState>((set) => ({
  activeConnectionId: null,
  setActive: (id) => set({ activeConnectionId: id }),
}))
```

- [ ] **Step 4: 写 `hooks/use-connections.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import { listConnections } from '@/services/api/connection'

export function useConnections() {
  return useQuery({
    queryKey: ['connections'],
    queryFn: listConnections,
  })
}
```

- [ ] **Step 5: 写 `components/connection-list.tsx`**

```tsx
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useConnections } from '../hooks/use-connections'
import { useConnectionStore } from '../store'

export function ConnectionList() {
  const { data, isLoading, isError } = useConnections()
  const activeId = useConnectionStore((s) => s.activeConnectionId)
  const setActive = useConnectionStore((s) => s.setActive)

  return (
    <section className="flex flex-col border-b">
      <header className="flex items-center justify-between px-3 py-2">
        <h3 className="text-xs font-medium uppercase text-muted-foreground">连接</h3>
        <Button size="sm" variant="ghost" className="h-6 text-xs">
          新建
        </Button>
      </header>
      <ul className="flex flex-col gap-0.5 px-2 pb-2">
        {isLoading &&
          Array.from({ length: 2 }).map((_, i) => (
            <li key={i}>
              <Skeleton className="h-7 w-full" />
            </li>
          ))}
        {isError && (
          <li className="px-2 text-xs text-muted-foreground">加载失败</li>
        )}
        {data?.length === 0 && (
          <li className="px-2 text-xs text-muted-foreground">暂无连接</li>
        )}
        {data?.map((conn) => {
          const active = conn.id === activeId
          return (
            <li key={conn.id}>
              <button
                onClick={() => setActive(conn.id)}
                className={cn(
                  'w-full truncate rounded-md px-2 py-1 text-left text-sm',
                  active ? 'bg-accent font-medium' : 'hover:bg-accent/50',
                )}
              >
                {conn.name}
                <span className="ml-2 text-xs text-muted-foreground">{conn.dbType}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
```

- [ ] **Step 6: 写 `components/connection-form.tsx`**

```tsx
export function ConnectionForm() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <p className="text-sm text-muted-foreground">
        连接表单（占位，稍后接入 zod + react-hook-form）
      </p>
    </div>
  )
}
```

- [ ] **Step 7: commit**

Run:
```bash
cd client
git add src/features/connection
git commit -m "$(cat <<'EOF'
feat(client): scaffold connection feature

Add features/connection/ re-exporting service-layer types, a zustand UI
store for activeConnectionId, a useConnections react-query hook backed
by listConnections(), a ConnectionList with loading/empty/error states
and active highlighting, and a placeholder ConnectionForm.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: `features/session/` 骨架

**Files:**
- Create: `client/src/features/session/types.ts`
- Create: `client/src/features/session/store.ts`
- Create: `client/src/features/session/hooks/use-sessions.ts`
- Create: `client/src/features/session/components/session-list.tsx`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/features/session/components client/src/features/session/hooks`

- [ ] **Step 2: 写 `types.ts`**

```ts
export type { Session } from '@/services/api/session'
```

- [ ] **Step 3: 写 `store.ts`**

```ts
import { create } from 'zustand'

type SessionUIState = {
  activeSessionId: string | null
  setActive: (id: string | null) => void
}

export const useSessionStore = create<SessionUIState>((set) => ({
  activeSessionId: null,
  setActive: (id) => set({ activeSessionId: id }),
}))
```

- [ ] **Step 4: 写 `hooks/use-sessions.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import { listSessions } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'

export function useSessions() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  return useQuery({
    queryKey: ['sessions', connectionId],
    queryFn: () => listSessions(connectionId ?? undefined),
    enabled: connectionId !== null,
  })
}
```

**注意跨 feature**：这里 session/ 读取了 connection/ 的 store。这是符合设计的——会话天然属于连接，读一个 ID 属于可接受的向下依赖。如觉得冲突规则，可把 `activeConnectionId` 上提到 `src/stores/`。本骨架保持现状。

- [ ] **Step 5: 写 `components/session-list.tsx`**

```tsx
import { cn } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'
import { useSessions } from '../hooks/use-sessions'
import { useSessionStore } from '../store'
import { useConnectionStore } from '@/features/connection/store'

export function SessionList() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const { data, isLoading, isError } = useSessions()
  const activeId = useSessionStore((s) => s.activeSessionId)
  const setActive = useSessionStore((s) => s.setActive)

  if (!connectionId) {
    return (
      <section className="flex flex-col">
        <header className="px-3 py-2">
          <h3 className="text-xs font-medium uppercase text-muted-foreground">会话</h3>
        </header>
        <p className="px-3 pb-3 text-xs text-muted-foreground">先选择一个连接</p>
      </section>
    )
  }

  return (
    <section className="flex flex-1 flex-col overflow-hidden">
      <header className="px-3 py-2">
        <h3 className="text-xs font-medium uppercase text-muted-foreground">会话</h3>
      </header>
      <ul className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-2 pb-2">
        {isLoading &&
          Array.from({ length: 3 }).map((_, i) => (
            <li key={i}>
              <Skeleton className="h-7 w-full" />
            </li>
          ))}
        {isError && <li className="px-2 text-xs text-muted-foreground">加载失败</li>}
        {data?.length === 0 && <li className="px-2 text-xs text-muted-foreground">暂无会话</li>}
        {data?.map((s) => {
          const active = s.id === activeId
          return (
            <li key={s.id}>
              <button
                onClick={() => setActive(s.id)}
                className={cn(
                  'w-full truncate rounded-md px-2 py-1 text-left text-sm',
                  active ? 'bg-accent font-medium' : 'hover:bg-accent/50',
                )}
              >
                {s.title}
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
```

- [ ] **Step 6: commit**

Run:
```bash
cd client
git add src/features/session
git commit -m "$(cat <<'EOF'
feat(client): scaffold session feature gated on active connection

Add features/session/ with UI store, react-query useSessions hook that
waits for connectionId, and SessionList with four UI states: no active
connection, loading, error, empty, and populated with active row
highlighting.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: `features/data-grid/` + 占位目录（chart, diagram, assets）

**Files:**
- Create: `client/src/features/data-grid/types.ts`
- Create: `client/src/features/data-grid/components/data-grid.tsx`
- Create: `client/src/features/chart/.gitkeep`
- Create: `client/src/features/diagram/.gitkeep`
- Create: `client/src/assets/.gitkeep`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/features/data-grid/components client/src/features/chart client/src/features/diagram client/src/assets`

- [ ] **Step 2: 写 `data-grid/types.ts`**

```ts
export type DataGridColumn = {
  key: string
  header: string
}

export type DataGridProps = {
  columns: DataGridColumn[]
  rows: Array<Record<string, unknown>>
}
```

- [ ] **Step 3: 写 `data-grid/components/data-grid.tsx`**

```tsx
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { DataGridProps } from '../types'

export function DataGrid({ columns, rows }: DataGridProps) {
  if (columns.length === 0) {
    return <p className="p-4 text-sm text-muted-foreground">无列定义</p>
  }

  return (
    <div className="h-full overflow-auto">
      <Table>
        <TableHeader className="sticky top-0 bg-background">
          <TableRow>
            {columns.map((c) => (
              <TableHead key={c.key}>{c.header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={columns.length} className="text-center text-muted-foreground">
                无数据
              </TableCell>
            </TableRow>
          )}
          {rows.map((row, i) => (
            <TableRow key={i}>
              {columns.map((c) => (
                <TableCell key={c.key}>{String(row[c.key] ?? '')}</TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
```

- [ ] **Step 4: 建三个 `.gitkeep`**

Run:
```bash
cd client
touch src/features/chart/.gitkeep src/features/diagram/.gitkeep src/assets/.gitkeep
```

- [ ] **Step 5: commit**

Run:
```bash
cd client
git add src/features/data-grid src/features/chart/.gitkeep src/features/diagram/.gitkeep src/assets/.gitkeep
git commit -m "$(cat <<'EOF'
feat(client): scaffold data-grid feature and placeholder directories

Add features/data-grid/ with a DataGrid component wrapping shadcn Table
(sticky header, empty state) and types for columns/rows. Create empty
features/chart/, features/diagram/, and src/assets/ directories with
.gitkeep for future ECharts / React Flow / image assets.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Layouts — `workspace-layout.tsx` 和 `dashboard-layout.tsx`

**Files:**
- Create: `client/src/layouts/workspace-layout.tsx`
- Create: `client/src/layouts/dashboard-layout.tsx`

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/layouts`

- [ ] **Step 2: 写 `workspace-layout.tsx`**

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
      <PanelResizeHandle className="w-px bg-border transition-colors hover:bg-primary/50" />
      <Panel defaultSize={42} minSize={25}>
        <ChatPanel />
      </Panel>
      <PanelResizeHandle className="w-px bg-border transition-colors hover:bg-primary/50" />
      <Panel defaultSize={40} minSize={25}>
        <Workspace />
      </Panel>
    </PanelGroup>
  )
}
```

- [ ] **Step 3: 写 `dashboard-layout.tsx`**

```tsx
import { DashboardPage } from '@/features/dashboard/dashboard-page'

export function DashboardLayout() {
  return <DashboardPage />
}
```

（dashboard 自带 SidebarProvider，Layout 层几乎是直通——留这一层是为了日后 dashboard 有 chrome 时好加。）

- [ ] **Step 4: commit**

Run:
```bash
cd client
git add src/layouts
git commit -m "$(cat <<'EOF'
feat(client): add workspace and dashboard layouts

WorkspaceLayout composes the three resizable panels (connections+sessions
sidebar 18%, chat 42%, workspace tabs 40%) with 12/25/25 minimums via
react-resizable-panels. DashboardLayout is a thin wrapper over
DashboardPage, reserved for future chrome (menubar, breadcrumb).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Routes — `__root.tsx`、`index.tsx`、`dashboard.tsx` + 生成 routeTree

**Files:**
- Create: `client/src/routes/__root.tsx`
- Create: `client/src/routes/index.tsx`
- Create: `client/src/routes/dashboard.tsx`
- Create: `client/src/routeTree.gen.ts`（由 `tsr generate` 生成）

- [ ] **Step 1: 建目录**

Run: `mkdir -p client/src/routes`

- [ ] **Step 2: 写 `routes/__root.tsx`**

```tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  return (
    <TooltipProvider>
      <Outlet />
      <Toaster richColors position="top-right" />
    </TooltipProvider>
  )
}
```

- [ ] **Step 3: 写 `routes/index.tsx`**

```tsx
import { createFileRoute } from '@tanstack/react-router'
import { WorkspaceLayout } from '@/layouts/workspace-layout'

export const Route = createFileRoute('/')({
  component: WorkspaceLayout,
})
```

- [ ] **Step 4: 写 `routes/dashboard.tsx`**

```tsx
import { createFileRoute } from '@tanstack/react-router'
import { DashboardLayout } from '@/layouts/dashboard-layout'

export const Route = createFileRoute('/dashboard')({
  component: DashboardLayout,
})
```

- [ ] **Step 5: 生成 `routeTree.gen.ts`**

Run: `cd client && pnpm gen:routes`
Expected: 输出类似 `✔ Generated route tree`，文件 `client/src/routeTree.gen.ts` 被创建。

- [ ] **Step 6: 验证生成文件存在**

Run: `ls client/src/routeTree.gen.ts && head -5 client/src/routeTree.gen.ts`
Expected: 文件存在，头几行是 `/* eslint-disable */` + `// @ts-nocheck` + 自动生成的说明注释。

- [ ] **Step 7: commit**

Run:
```bash
cd client
git add src/routes src/routeTree.gen.ts
git commit -m "$(cat <<'EOF'
feat(client): add TanStack Router routes (root, /, /dashboard)

Create the root route that provides TooltipProvider and Toaster chrome,
/ mapped to WorkspaceLayout (the three-panel main workbench), and
/dashboard mapped to DashboardLayout (preserved shadcn demo). Commit
the auto-generated routeTree.gen.ts so CI typecheck doesn't need to run
tsr generate.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: 应用入口 `src/main.tsx`

**Files:**
- Create: `client/src/main.tsx`

- [ ] **Step 1: 写 `src/main.tsx`**

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
  interface Register {
    router: typeof router
  }
}

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element #root not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
```

- [ ] **Step 2: 跑 typecheck 确认引用能解析**

Run: `cd client && pnpm typecheck`
Expected: 退出码 0。如果报 `Cannot find module './routeTree.gen'`，说明 Task 15 没跑 `pnpm gen:routes`，回去补上。如果报其他错，修对应的导入或类型。

- [ ] **Step 3: commit**

Run:
```bash
cd client
git add src/main.tsx
git commit -m "$(cat <<'EOF'
feat(client): wire application entry with Router + QueryClient

Create main.tsx that boots React 19 in StrictMode, provides the
singleton QueryClient via QueryClientProvider, and mounts the TanStack
RouterProvider with the generated route tree. Register the router type
for end-to-end type inference on useParams / useSearch.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: ESLint 扁平配置 + Prettier + `.gitignore`

**Files:**
- Modify: `client/eslint.config.mjs`（覆写）
- Create: `client/.prettierrc`
- Modify: `client/.gitignore`

- [ ] **Step 1: 覆写 `client/eslint.config.mjs`**

```js
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'src-tauri/target/**',
      'src/routeTree.gen.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser },
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
    },
    settings: { react: { version: '19' } },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    files: ['vite.config.ts', 'eslint.config.mjs'],
    languageOptions: {
      globals: { ...globals.node },
    },
  },
)
```

- [ ] **Step 2: 写 `client/.prettierrc`**

```json
{
  "semi": false,
  "singleQuote": true,
  "trailingComma": "all",
  "printWidth": 100,
  "tabWidth": 2,
  "arrowParens": "always"
}
```

- [ ] **Step 3: 更新 `client/.gitignore`**

Read current contents first:

Run: `cat client/.gitignore`

在末尾追加（如果没有的话）：

```
# Tauri
src-tauri/target/
src-tauri/gen/schemas/

# Vite
dist/

# Editor
.vscode/
.idea/
```

**注意**：**不**把 `src/routeTree.gen.ts` 加进去——它提交入库便于 CI 类型检查。

- [ ] **Step 4: 跑 lint 自检**

Run: `cd client && pnpm lint`
Expected: 退出码 0，可能有少量 warning（比如未使用的 import），但没有 error。如有 error，修一下具体行。

- [ ] **Step 5: 跑 format（只写不改判定）**

Run: `cd client && pnpm format`
Expected: 退出码 0，可能把少量文件格式统一掉。

- [ ] **Step 6: commit（含格式化产生的改动）**

Run:
```bash
cd client
git add eslint.config.mjs .prettierrc .gitignore
# 如 prettier 改了源文件，也一并加
git add -A
git commit -m "$(cat <<'EOF'
chore(client): replace next-lint with eslint flat + prettier

Switch ESLint to flat config built from @eslint/js + typescript-eslint +
eslint-plugin-react + eslint-plugin-react-hooks; ignore
routeTree.gen.ts from linting but keep it committed. Add .prettierrc
(no semi, single quote, print width 100). Append Tauri target/ and
Vite dist/ to .gitignore. Apply initial prettier pass.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: 重写 `README.md`

**Files:**
- Modify: `client/README.md`

- [ ] **Step 1: 覆写 `client/README.md`**

```markdown
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
- `src/lib/` — 纯工具 (`cn`、`queryClient`)
- `src-tauri/` — Rust 侧

## 路由

- `/` — 三栏工作台（连接+会话 / 对话 / 工作区 Tab）
- `/dashboard` — 保留的 shadcn dashboard demo

## 环境变量

- `VITE_API_BASE_URL` — 后端 Spring Boot 基址，默认 `http://localhost:8080/api`
```

- [ ] **Step 2: commit**

Run:
```bash
cd client
git add README.md
git commit -m "$(cat <<'EOF'
docs(client): rewrite README for Tauri+Vite stack

Replace Next.js CRA-style README with project-specific instructions:
system prerequisites per OS, pnpm tauri dev workflow, script reference,
directory layout aligned with the new feature-based structure, the two
routes currently registered, and VITE_API_BASE_URL env var.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 19: 首次启动验证 & 成功标准检查

此 Task **不写代码**，只跑验证。

- [ ] **Step 1: typecheck**

Run: `cd client && pnpm typecheck`
Expected: 退出码 0。如果报 `Cannot find name 'React'`：检查 `tsconfig.json` 的 `jsx: "react-jsx"`。

- [ ] **Step 2: lint**

Run: `cd client && pnpm lint`
Expected: 退出码 0，warning 可以有，error 不能有。

- [ ] **Step 3: Vite 构建**

Run: `cd client && pnpm build`
Expected: 退出码 0，生成 `client/dist/` 目录，含 `index.html` 和 `assets/` 的产物 JS/CSS。

- [ ] **Step 4: 启动 Tauri dev（后台跑，30 秒后 kill）**

Run:
```bash
cd client
pnpm tauri dev &
DEV_PID=$!
sleep 45
if kill -0 $DEV_PID 2>/dev/null; then
  echo "tauri dev is running"
  kill $DEV_PID
else
  echo "tauri dev died"
  exit 1
fi
```
Expected：前 30-45 秒会看到 Rust 编译输出，然后 Vite 打印 `Local: http://localhost:1420/`，随后 Tauri 尝试打开窗口。若你在带显示的环境（WSLg/本机桌面），窗口会弹出。

**若 WSL 无图形环境**：Rust 编译成功 + Vite listen 成功即视为通过本 step；窗口渲染留给带显示的环境验证。

- [ ] **Step 5: 手动验证成功标准（1-6，按 spec §1）**

在带显示环境下做一次性手工检查，逐条打勾：

- [ ] 1. `pnpm tauri dev` 能启动桌面窗口
- [ ] 2. 根路由 `/` 展示三栏：左侧连接+会话、中部对话空状态、右侧 Tab 空状态
- [ ] 3. 左/中分隔线和中/右分隔线都能拖拽
- [ ] 4. 访问 `/dashboard`（地址栏或临时加个链接）能看到 shadcn dashboard-01 原样渲染
- [ ] 5. 开发者工具里 console 没有红色 error（警告可忽略）
- [ ] 6. 后端没起时，`/` 上连接列表显示"加载失败"而不是崩页

**若某条失败**：对照 spec §8 风险表，回到相关 Task 修复，而不是强行往下。

- [ ] **Step 6: 最终 commit（若前几步修过东西）**

如果 Step 1-5 没改动任何文件：不需要 commit。

如果修过东西（比如 typecheck 报错改了两行），单独 commit：

```bash
cd client
git add -A
git commit -m "$(cat <<'EOF'
fix(client): resolve typecheck/lint findings from first-boot verification

<在这里写具体改了什么>

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 计划自审

**1. Spec 覆盖性** — 逐条对照 spec §2（21 条技术决定）：

| 条目 | Task |
|------|------|
| 2.1 Tauri v2 | Task 4 |
| 2.2 Vite | Task 3 |
| 2.3 React 19.2.x | Task 2 |
| 2.4 TypeScript strict | Task 3 (tsconfig strict: true) |
| 2.5 pnpm | Task 2 |
| 2.6 TanStack Router 文件路由 | Task 15 |
| 2.7 zustand | Task 9/10/11/12 + 7 |
| 2.8 ky + react-query | Task 8 + 7 (queryClient) |
| 2.9 react-resizable-panels | Task 14 |
| 2.10 shadcn base-nova | 保留 `components.json` 不动 |
| 2.11 lucide-react | 保留 |
| 2.12 Recharts 保留 | 保留 |
| 2.13 ER 图不装 | Task 13 (.gitkeep) |
| 2.14 Tailwind v4 via vite 插件 | Task 2 deps + Task 3 vite.config + Task 5 css |
| 2.15 sonner | Task 15 `__root.tsx` `<Toaster />` |
| 2.16 zod | 保留 |
| 2.17 移除 next-themes | Task 2 package.json |
| 2.18 eslint flat | Task 17 |
| 2.19 prettier | Task 17 |
| 2.20 Node ≥ 20 | Task 18 README |
| 2.21 Rust 2021 | `tauri init` 生成默认 2021 |

spec §1 成功标准 6 条 → Task 19 Step 5 逐条勾。

spec §3 目录结构 → Tasks 3-16 累积建起。

spec §4 10 个关键文件示例 → 都分别落在 Tasks 2/3/4/8/10/14/15/16 里。

**2. 占位符扫描** — 全文搜了 "TBD"、"TODO"、"fill in"、"similar to"，无匹配。**注意**：Task 9 Step 4 `useChat.sendMessage` 的 "// 真正的 AI 调用等后端 OpenCode 接通后再接" 是刻意的功能性注释，不是占位——对应 spec §1 "非目标：真实 AI 对话"。

**3. 类型一致性** — `WorkspaceTab`、`ChatMessage`、`Connection` 等类型在 services 层（Task 8）、feature types（Tasks 9-13）、消费端（Tasks 14-16）中命名一致。feature/chat 的 `ChatMessage` 与 services/api/chat 的 `ChatMessage` 是两个不同的类型（前者含 UI 状态字段 `pending/error`），这是有意的——不统一，由 hook 层做转换。若后续觉得冗余可合并，那是重构话题不入本 plan。

**4. 跨 feature 规则** — 唯一的跨 feature 读是 Task 12 里 `session/` 读 `connection/store`。已在 Task 12 Step 4 的注释中说明理由并给了替代方案（上提到 `src/stores/`）。
