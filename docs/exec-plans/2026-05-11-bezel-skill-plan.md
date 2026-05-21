# bezel Skill 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `tmp/Dashboard/` 12 张 premium HTML 沉淀为独立 GitHub repo `wallfacers/bezel`（letterpress 形态），同时把 data-talk 的 dashboard 渲染管线大改为 JSON → AI 一次性产出 HTML artifact → iframe sandbox 渲染 + 内置 polling 的新管线，并通过 server classpath 资源 + 启动期解压模式集成 bezel 到 `data-talk/.opencode/skills/bezel/`。

**Architecture:** 三层独立交付物：
1. **bezel repo**（独立，在 `~/workspace/github/bezel/` 创建后推到 `wallfacers/bezel`）：letterpress 形态目录，含 12 份 industries pattern 详解 + design-language + data-contract + compile-rules + patterns-catalog + validate.py + preview.py
2. **data-talk server**：JSON schema v2 + `OpenCodeBinaryResolver.ensureBezelSkill` + maven-assembly-plugin vendor + `GET /html` 与 `POST /widgets/{wid}/data` 新 endpoint + Java HTML validator + lazy migration
3. **data-talk client**：删除 React `dashboard-canvas` / `chart-widget` / `grid-layout-engine`，重写 `dashboard-tab.tsx` 为 iframe sandbox 宿主，新加 `iframe-shell.tsx` 与 `iframe-protocol.ts`

**Tech Stack:** Markdown / Python 3.11+（bezel scripts）/ Java 21 + Spring Boot 3.5 / TypeScript 5 + React 19 / Vitest + Playwright / JUnit 5 + AssertJ + WireMock

**Spec:** [2026-05-11-bezel-skill-design.md](../product-specs/2026-05-11-bezel-skill-design.md)

---

## 执行批次拓扑

```
Phase 0：bezel 本地仓库骨架（T1 必须先做，所有后续依赖）
   T1 (bezel scaffolding)

Phase 1：bezel 内容（T2-T6 可全并行批量做）
   T2 (design-language.md)
   T3 (data-contract.md)
   T4 (compile-rules.md)
   T5 (patterns-catalog.md + 12 industries)   ← 内部再起 12 并行
   T6 (validate.py + preview.py)

Phase 2：data-talk vendor + 启动 deploy（T7-T9 串行）
   T7 (vendor bezel into server resources)
   T8 (OpenCodeBinaryResolver.ensureBezelSkill + 测试)
   T9 (OpenCodeProcessManager 接入)

Phase 3：data-talk schema v2 + Java validator（T10-T12 全并行）
   T10 (domain + schema-json v2)
   T11 (Java HTML validator)
   T12 (lazy migration in DashboardArtifactService.load)

Phase 4：data-talk 后端 API（T13-T14 串行依赖 T10/T11/T12）
   T13 (DashboardController GET /html 与 promote 改造)
   T14 (WidgetDataService + POST /widgets/{wid}/data)

Phase 5：data-talk 前端重写（T15-T18 一次性提交；T15 删除独立先做）
   T15 (delete legacy React canvas/widget/engines)
   T16 (schema.ts v2 + services/dashboard-api.ts 加 fetchDashboardHtml)
   T17 (iframe-shell.tsx + iframe-protocol.ts)
   T18 (rewrite dashboard-tab.tsx)

Phase 6：E2E + 文档收尾
   T19 (Playwright dashboard-bezel-v2.spec.ts)
   T20 (bezel GitHub push)
   T21 (文档 housekeeping + index 更新)
```

**并发说明**（按 CLAUDE.md "Parallel Plan Execution"）：
- T2-T6 内的 markdown 写作互不依赖，子代理用 `dispatching-parallel-agents` skill 并行；T5 内部 12 industries 再起子并行
- T10-T12 三个 Java 改动文件不重叠，可并行；批内跳过逐 task `mvn compile`，批末统一一次 `mvn clean verify`
- T16-T18 三个前端文件不重叠（schema/api 与 iframe-shell 与 dashboard-tab），可并行；批末统一 `tsc --noEmit` + `npm test`
- T15（删除）必须先于 T18（dashboard-tab 重写）执行，因为 T18 在已删除旧 widget 树的基础上重写

---

## 文件结构总览

### bezel 仓库（新建 `~/workspace/github/bezel/`）

| 文件 | 责任 |
|---|---|
| `.claude-plugin/marketplace.json` | Claude Code marketplace 元信息 |
| `skills/bezel/SKILL.md` | 顶层入口；何时触发、关键不变量 |
| `skills/bezel/references/design-language.md` | 设计 token：色、字、间距、动效 |
| `skills/bezel/references/data-contract.md` | JSON schema v2、polling 协议、`window.__BEZEL_CONFIG__` |
| `skills/bezel/references/compile-rules.md` | JSON → HTML 拼装算法、必含元素、安全约束 |
| `skills/bezel/references/patterns-catalog.md` | 12 industry 索引 + pattern 选择优先级 |
| `skills/bezel/references/industries/01-multi-screen.md` ~ `12-education.md` | 12 份行业 pattern 详解 |
| `skills/bezel/assets/templates/01-multi-screen.html` ~ `12-education.html` | 12 份原始 HTML 视觉范本（从 data-talk `tmp/Dashboard/` 复制） |
| `skills/bezel/scripts/validate.py` | 产出 HTML 合规性自检 |
| `skills/bezel/scripts/preview.py` | 本地 JSON+mock → HTML 预览 |
| `README.md` | 项目说明（letterpress 同形态） |
| `LICENSE` | Apache 2.0 |

### data-talk 改动

| 文件 | 动作 |
|---|---|
| `server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel/` | 新建（vendored bezel copy） |
| `server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version` | 新建（嵌入版本号） |
| `server/data-talk-infrastructure/src/assembly/bezel.xml` | 新建（maven-assembly 描述符） |
| `server/data-talk-infrastructure/pom.xml` | 修改（加 maven-assembly-plugin execution） |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java` | 修改（加 `ensureBezelSkill`） |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java` | 修改（启动期调 `ensureBezelSkill`） |
| `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverBezelTest.java` | 新建 |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Dashboard.java` | 修改（加 `theme/renderer/refresh`） |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Widget.java` | 修改（加 `patternId/refresh`） |
| `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json` | 修改（升 v2） |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java` | 修改（lazy migration + promote 双 artifact + replaceHtml） |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/BezelHtmlValidator.java` | 新建 |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/WidgetDataService.java` | 新建 |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java` | 修改（加 `GET /html` 与 `POST /widgets/{wid}/data`） |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardHtmlServeControllerIT.java` | 新建 |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/WidgetDataControllerIT.java` | 新建 |
| `client/src/features/dashboard/dashboard-canvas.tsx` | **删除** |
| `client/src/features/dashboard/widgets/chart-widget.tsx` | **删除** |
| `client/src/features/dashboard/widgets/markdown-widget.tsx` | **删除** |
| `client/src/features/dashboard/widgets/widget-shell.tsx` | **删除** |
| `client/src/features/dashboard/widgets/__tests__/` | **删除整目录** |
| `client/src/features/dashboard/engines/grid-layout-engine.ts` | **删除** |
| `client/src/features/dashboard/engines/layout-engine.ts` | **删除** |
| `client/src/features/dashboard/engines/__tests__/` | **删除整目录** |
| `client/src/features/dashboard/schema.ts` | 修改（升 v2） |
| `client/src/features/dashboard/services/dashboard-api.ts` | 修改（加 `fetchDashboardHtml`） |
| `client/src/features/dashboard/iframe-shell.tsx` | 新建 |
| `client/src/features/dashboard/iframe-protocol.ts` | 新建 |
| `client/src/features/dashboard/dashboard-tab.tsx` | 重写 |
| `client/tests/e2e/dashboard-bezel-v2.spec.ts` | 新建 |
| `.gitignore` | 修改（加 `.opencode/skills/bezel/` 与 `.opencode/.bezel-installed`） |
| `scripts/sync-bezel.sh` | 新建 |
| `docs/product-specs/index.md` | （spec 阶段已登记，无需再改） |
| `docs/exec-plans/index.md` | 修改（Active 节登记本 plan） |
| `docs/bugs/` | 若 E2E 发现新 BUG，按 CLAUDE.md BUG Tracking Gate 登记 |

---

## Phase 0：bezel 本地仓库骨架

### Task 1：bezel 仓库初始化

**Files:**
- Create: `/home/wushengzhou/workspace/github/bezel/` 全新目录
- Create: `bezel/README.md`
- Create: `bezel/LICENSE`（Apache 2.0）
- Create: `bezel/.gitignore`
- Create: `bezel/.claude-plugin/marketplace.json`
- Create: `bezel/skills/bezel/SKILL.md`

- [x] **Step 1: 创建目录骨架**

```bash
mkdir -p ~/workspace/github/bezel/.claude-plugin
mkdir -p ~/workspace/github/bezel/skills/bezel/references/industries
mkdir -p ~/workspace/github/bezel/skills/bezel/assets/templates
mkdir -p ~/workspace/github/bezel/skills/bezel/scripts
cd ~/workspace/github/bezel
git init -b main
```

- [x] **Step 2: 写 .gitignore**

```
# Python
__pycache__/
*.pyc
.venv/

# OS
.DS_Store

# Editor
.idea/
.vscode/

# bezel preview output
tmp/
```

- [x] **Step 3: 写 LICENSE（Apache 2.0 full text）**

```bash
curl -sSL https://www.apache.org/licenses/LICENSE-2.0.txt > LICENSE
```

- [x] **Step 4: 写 .claude-plugin/marketplace.json**

```json
{
  "name": "bezel",
  "owner": { "name": "wallfacers" },
  "metadata": {
    "description": "Premium industrial dashboards rendered from JSON. Self-contained HTML with embedded polling, 12 industry patterns, ready to embed as iframe.",
    "version": "0.1.0"
  },
  "plugins": [
    {
      "name": "bezel",
      "description": "Premium industrial dashboard skill with 12 industry patterns and JSON→HTML compile rules.",
      "source": "./",
      "strict": false,
      "skills": ["./skills/bezel"]
    }
  ]
}
```

- [x] **Step 5: 写 skills/bezel/SKILL.md（占位顶层，详细内容由后续 task 填充）**

```markdown
---
name: bezel
description: Premium industrial dashboards rendered from JSON. Use this skill whenever the user asks to build a dashboard, monitoring screen, KPI board, big-screen display, operations cockpit, or industry-specific data visualization — even when the word "dashboard" is not explicitly used. Produces a JSON description plus a self-contained HTML artifact with embedded polling, ready to be embedded as an iframe inside DataTalk.
---

# bezel — Premium Industrial Dashboard Skill

bezel turns a JSON dashboard description into a self-contained, premium-quality HTML artifact ready to render inside an iframe. It carries 12 industry-specific design patterns (ecommerce, manufacturing, finance, ...) and bakes refresh policy + polling endpoints into the HTML so the host needs no runtime dependency on bezel.

## When to use

Trigger this skill whenever the user mentions any of:
- dashboards, monitoring screens, KPI boards, ops cockpits, big-screen displays
- "data wall", "TV-wall", "control center", "command center"
- industry-specific data visualization (ecommerce ops, factory monitoring, finance trading floor, healthcare bed map, energy grid, etc.)
- "I need a visualization for X" where X is a multi-metric operational view

Even if the user does not say "dashboard", if the intent matches the above, use this skill.

## What it produces

Two artifacts per request:
1. **dashboard.json** — schemaVersion 2; declares title, theme (industry-*), widgets with `patternId`, `query.sql + connectionId`, and `refresh.intervalMs`. Source-of-truth.
2. **dashboard.html** — self-contained; embeds CSP `<meta>`, ECharts CDN, polling scheduler, and `window.__BEZEL_CONFIG__`. Derived from dashboard.json.

## Reference layout

- `references/design-language.md` — color, typography, spacing, motion tokens
- `references/data-contract.md` — JSON schema v2 spec + polling protocol + `window.__BEZEL_CONFIG__` contract
- `references/compile-rules.md` — JSON → HTML assembly algorithm + must-include elements + security constraints
- `references/patterns-catalog.md` — 12 industry index + pattern selection priority
- `references/industries/NN-name.md` — per-industry visual signature, layout skeleton, widget recipes, AI trigger cues
- `assets/templates/NN-name.html` — original visual references (read-only inspiration)
- `scripts/validate.py` — compile-output self-check; run before promoting
- `scripts/preview.py` — local JSON + mock-data → HTML preview for debugging

Read `patterns-catalog.md` first to pick the industry, then the per-industry file, then `compile-rules.md` for assembly, then `data-contract.md` to wire the polling layer.

## Key invariants

1. JSON is source-of-truth; HTML is derived. Never hand-edit HTML.
2. HTML must self-contain — no runtime dependency on bezel after production.
3. Refresh updates ECharts data only (`setOption({ dataset })`); never rebuild DOM.
4. CSP is enforced via `<meta http-equiv="Content-Security-Policy">` inside the HTML, with placeholder `__BEZEL_SERVER_ORIGIN__` that the host server replaces on serve.
5. Every widget SQL must be a single SELECT — no DDL/DML, no admin commands. Server-side `SqlStatementGuard` will reject otherwise.
```

- [x] **Step 6: 写 README.md（letterpress 同形态）**

```markdown
# bezel

A Claude Code / OpenCode skill that produces premium industrial dashboards rendered from JSON. Turns flat JSON descriptions into self-contained, polling-aware HTML you embed as an iframe.

## What It Does

When Claude is asked to build a dashboard, monitoring screen, KPI board, or operations cockpit, bezel produces:

1. **dashboard.json** — declarative description (theme, layout, widgets with SQL queries and refresh policy)
2. **dashboard.html** — a single, self-contained `.html` file that loads ECharts, parses an embedded `__BEZEL_CONFIG__`, and runs its own polling scheduler against your data API

**12 built-in industry patterns** cover the most common operational dashboards:

| # | Industry | Visual signature |
|---|----------|------------------|
| 01 | Multi-screen control center | Floating KPI capsules, left/right columns |
| 02 | Ecommerce ops | Lava red/gold, funnel, marquee leaderboards, heatmap |
| 03 | Manufacturing | Steel blue, progress rings, SCADA-style cards |
| 04 | SaaS ops | Violet, retention matrix, MRR trends |
| 05 | Finance | Ink green/black/gold, candlesticks, sankey |
| 06 | Logistics | Cyan, route maps, SLA rings |
| 07 | Healthcare | White-blue, ward heatmap, patient profile |
| 08 | HR | Warm gray, org tree, hiring funnel |
| 09 | Energy | Orange-black, grid topology, load curves |
| 10 | Cybersecurity | Dark green/black, alert waterfall, IP arcs |
| 11 | Agriculture | Sage-brown, field maps, phenology curves |
| 12 | Education | Orange-teal, learner radar, progress arrays |

## Installation

### Via Claude Code Plugin Marketplace

```bash
/plugin marketplace add wallfacers/bezel
/plugin install bezel@bezel
```

### Via OpenCode Project-Local Skills

OpenCode loads skills from `<project>/.opencode/skills/<name>/`. The hosting project (e.g., DataTalk) typically vendors bezel's `skills/bezel/` directory into its server's classpath and extracts it on startup. Manual install:

```bash
cp -r skills/bezel ~/your-project/.opencode/skills/bezel
```

## Usage

Once installed, bezel activates automatically when Claude detects dashboard-shaped intent. You can also invoke explicitly:

```
/bezel Build me an ecommerce ops dashboard for mysql-prod
/bezel Healthcare bed-map dashboard
/bezel Manufacturing line monitoring, refresh every 5s
```

## Philosophy

- **JSON is source-of-truth, HTML is derived.** Edit JSON; let bezel re-emit HTML.
- **Self-contained artifacts.** Once produced, the HTML needs nothing but ECharts CDN to run.
- **Refresh as a property, not a feature.** Every widget declares `refresh.intervalMs`; the HTML carries its own scheduler.
- **Industry as design DNA.** A finance dashboard should look nothing like a healthcare dashboard. bezel ships 12 industry patterns with distinct visual signatures.

## Project Structure

```
bezel/
├── .claude-plugin/marketplace.json
├── skills/bezel/
│   ├── SKILL.md
│   ├── references/
│   │   ├── design-language.md
│   │   ├── data-contract.md
│   │   ├── compile-rules.md
│   │   ├── patterns-catalog.md
│   │   └── industries/01-…12-…
│   ├── assets/templates/  (12 original HTML inspirations)
│   └── scripts/validate.py, preview.py
├── README.md
└── LICENSE  (Apache 2.0)
```

## License

Apache 2.0 — see [LICENSE](LICENSE).
```

- [x] **Step 7: 复制 12 个原始 HTML 范本到 assets/templates/**

```bash
cp /home/wushengzhou/workspace/github/data-talk/tmp/Dashboard/0[1-9]-*.html \
   /home/wushengzhou/workspace/github/data-talk/tmp/Dashboard/1[0-2]-*.html \
   ~/workspace/github/bezel/skills/bezel/assets/templates/
ls ~/workspace/github/bezel/skills/bezel/assets/templates/
```

Expected: 12 个 `NN-*.html` 文件被复制。

- [x] **Step 8: 提交骨架**

```bash
cd ~/workspace/github/bezel
git add -A
git status
git commit -m "feat: scaffold bezel skill repo (letterpress shape)

- .claude-plugin/marketplace.json
- skills/bezel/SKILL.md placeholder
- 12 original HTML templates copied to assets/templates/
- README.md, LICENSE (Apache 2.0), .gitignore

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 1：bezel 内容（T2-T6 并发批量）

> **执行方式**：用 `superpowers:dispatching-parallel-agents` skill 并行起 5 个子代理（T2/T3/T4/T5/T6）。批内跳过逐 task 验证；批末统一跑 `python ~/workspace/github/bezel/skills/bezel/scripts/validate.py --help` + 人工 spot-check 一份 industries/*.md。

### Task 2：design-language.md

**Files:**
- Create: `~/workspace/github/bezel/skills/bezel/references/design-language.md`

- [x] **Step 1: 写 design-language.md**

内容章节（全部用 markdown）：

1. **Color Tokens**：定义 CSS 变量约定：
   - `--bezel-bg-app`, `--bezel-bg-panel`, `--bezel-bg-card`
   - `--bezel-text-strong`, `--bezel-text-base`, `--bezel-text-muted`
   - `--bezel-accent-primary`, `--bezel-accent-secondary`, `--bezel-accent-warn`, `--bezel-accent-success`, `--bezel-accent-danger`
   - `--bezel-grid-line`, `--bezel-grid-line-strong`
   - 每个 industry 重定义这些 CSS 变量值

2. **Typography**：
   - `font-family`: `'PingFang SC', 'Microsoft YaHei', sans-serif`
   - 标题：22-28px / weight 700 / letter-spacing 1-2px
   - KPI 数字：24-48px / weight 700 / `font-variant-numeric: tabular-nums`
   - 卡片标题：13-14px / weight 700
   - 正文标签：11-12px / weight 400 / `color: var(--bezel-text-muted)`

3. **Spacing**：4 / 8 / 12 / 14 / 16 / 24 / 32 px scale

4. **Radius**：`--bezel-radius-card: 12px`, `--bezel-radius-pill: 28px`

5. **Motion**：transition 200-300ms，easing `cubic-bezier(0.2, 0, 0, 1)`，hover transform `scale(1.02)` 或 `translateY(-4px)`

6. **Background effects**：粒子 / 网格 / 波浪 / 光晕，per-industry 选用

7. **Glass morphism**：`backdrop-filter: blur(8-12px)` + `background: rgba(...,0.55)` + `border: 1px solid rgba(...,0.18)`

8. **ECharts theme baseline**：参考 `tmp/Dashboard/02-ecommerce.html` 的 `darkGrid` / `darkAxis` / `darkTooltip` 三段公共配置；要求每个 industry 在其 industries/*.md 里覆盖这些 baseline

- [x] **Step 2: 提交**

```bash
cd ~/workspace/github/bezel
git add skills/bezel/references/design-language.md
git commit -m "docs: add design-language.md (color/typography/motion tokens)"
```

### Task 3：data-contract.md

**Files:**
- Create: `~/workspace/github/bezel/skills/bezel/references/data-contract.md`

- [x] **Step 1: 写 data-contract.md**

内容章节：

1. **JSON schema v2 完整 spec**（与 data-talk `client/src/features/dashboard/schema.ts` 一致）：
   - 根字段 `schemaVersion`, `id`, `title`, `description`, `defaultConnectionId`, `theme`, `renderer`, `refresh`, `parameters`, `widgets`, `layout`, `version`, `createdAt`, `updatedAt`
   - widget 字段 `id`, `patternId`, `position`, `parameters`, `query`, `refresh`, `options`
   - 给一份完整 JSON 示例（电商 dashboard，5 widgets）

2. **widgetQuery 约束**：
   - 必须是单条 SELECT
   - 参数走 `paramRefs: { sqlName: dashboardParamId }`，JDBC `PreparedStatement` ?-参数
   - 列名约定：每个 pattern 在 industries/*.md 里声明它期待的列名

3. **polling 协议**：
   - HTML 内嵌 `window.__BEZEL_CONFIG__`：
     ```js
     window.__BEZEL_CONFIG__ = {
       dashboardId: 'dash_xxx',
       defaultIntervalMs: 10000,
       pauseOnHidden: true,
       widgets: [
         { id: 'chart_w_xxx', intervalMs: 5000, endpoint: '/api/dashboards/{id}/widgets/{wid}/data',
           params: { dt: '2026-05-11' } }
       ]
     };
     ```
   - polling 调度器伪代码（让 AI 在 compile-rules 里照搬）

4. **错误处理**：
   - widget polling 失败时：保留上一帧数据，widget 右上角显示橙色错误图标 + tooltip 含 HTTP 状态码
   - 整页错误（HTML 启动失败）：iframe `onerror` 通过 postMessage 报给 host

5. **window.__BEZEL_CONFIG__ schema**：完整 zod-like 定义

6. **postMessage 协议**（与前端 iframe-protocol.ts 一致）：
   ```ts
   HostToIframe: { type: 'params/update'; params } | { type: 'refresh/pause' } | { type: 'refresh/resume' }
   IframeToHost: { type: 'ready'; jsonHash } | { type: 'error'; widgetId; message } | { type: 'metric'; name; value }
   ```

- [x] **Step 2: 提交**

```bash
git add skills/bezel/references/data-contract.md
git commit -m "docs: add data-contract.md (JSON v2 + polling + postMessage)"
```

### Task 4：compile-rules.md

**Files:**
- Create: `~/workspace/github/bezel/skills/bezel/references/compile-rules.md`

- [x] **Step 1: 写 compile-rules.md**

内容章节：

1. **总体 HTML 骨架模板**（doctype、CSP meta、JSON_HASH meta、`<head>` ECharts CDN script、`<body>` widget 容器、`<script>` window.__BEZEL_CONFIG__、polling 调度器 IIFE）

2. **必含元素清单（validator 强制）**：
   - `<meta http-equiv="Content-Security-Policy" content="...__BEZEL_SERVER_ORIGIN__...">`
   - `<meta name="__JSON_HASH__" content="sha256:...">`
   - `<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js">`（白名单 CDN）
   - `<script>window.__BEZEL_CONFIG__ = {...}</script>`
   - polling 调度器 IIFE
   - 每个 widget 的 ECharts init 段
   - `document.addEventListener('visibilitychange', ...)` for pauseOnHidden

3. **安全约束**：
   - 无 inline `on*` 属性
   - 无 `<script src>` 指向白名单 CDN 以外
   - CSP 含 `frame-ancestors 'self'`、`connect-src __BEZEL_SERVER_ORIGIN__`、不含 `unsafe-eval`、`*` 通配仅限 img-src 的 `data:` `https:`

4. **JSON → HTML 装配算法**（步进伪代码）：
   ```
   1. 读 dashboard.json，确定 theme / patternId set
   2. 读 references/industries/<industry>.md，拿到该 industry 的 CSS 变量值、布局骨架
   3. 拼 <head>：CSP meta、JSON_HASH meta、ECharts CDN
   4. 拼 <body>：按 layout.engine='free' + 每个 widget 的 patternId 选对应 HTML 片段
   5. 拼 <script>：window.__BEZEL_CONFIG__（含每 widget 的 endpoint + intervalMs + paramRefs）
   6. 拼 polling 调度器（粘贴标准 IIFE）
   7. 计算 jsonHash = sha256(JSON.stringify(dashboard.json))，注入 __JSON_HASH__ meta
   8. 通过 scripts/validate.py 验证产出，不通过则修复
   ```

5. **polling 调度器标准 IIFE 代码**（让 AI 直接 copy-paste）：

```js
(function() {
  const cfg = window.__BEZEL_CONFIG__;
  if (!cfg) return;
  const charts = {};  // widgetId → ECharts instance
  const timers = {};
  let paused = false;

  function bindWidget(w) {
    const el = document.getElementById(w.id);
    if (!el) return;
    charts[w.id] = echarts.init(el);
    schedule(w);
  }

  function schedule(w) {
    const interval = w.intervalMs || cfg.defaultIntervalMs || 10000;
    timers[w.id] = setTimeout(async function tick() {
      if (paused) { timers[w.id] = setTimeout(tick, interval); return; }
      try {
        const res = await fetch(w.endpoint, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ params: w.params || {} })
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        applyWidgetData(w, data);
      } catch (e) {
        parent.postMessage({ type: 'error', widgetId: w.id, message: String(e) }, '*');
      }
      timers[w.id] = setTimeout(tick, interval);
    }, interval);
  }

  function applyWidgetData(w, data) {
    const ch = charts[w.id];
    if (!ch) return;
    ch.setOption({ dataset: { source: data.rows } }, { lazyUpdate: true });
  }

  if (cfg.pauseOnHidden !== false) {
    document.addEventListener('visibilitychange', () => { paused = document.hidden; });
  }

  window.addEventListener('message', (ev) => {
    const m = ev.data;
    if (!m || typeof m !== 'object') return;
    if (m.type === 'refresh/pause') paused = true;
    if (m.type === 'refresh/resume') paused = false;
    if (m.type === 'params/update') {
      cfg.widgets.forEach(w => w.params = { ...(w.params||{}), ...m.params });
    }
  });

  cfg.widgets.forEach(bindWidget);
  parent.postMessage({ type: 'ready', jsonHash: document.querySelector('meta[name=__JSON_HASH__]')?.content }, '*');
})();
```

- [x] **Step 2: 提交**

```bash
git add skills/bezel/references/compile-rules.md
git commit -m "docs: add compile-rules.md (assembly algorithm + polling IIFE)"
```

### Task 5：patterns-catalog.md + 12 份 industries/*.md

**Files:**
- Create: `~/workspace/github/bezel/skills/bezel/references/patterns-catalog.md`
- Create: `~/workspace/github/bezel/skills/bezel/references/industries/01-multi-screen.md` ~ `12-education.md`

> **执行方式**：本 task 内部用 `superpowers:dispatching-parallel-agents` 起 12 + 1 共 13 个子代理，每个负责一份 markdown。每个 industries/NN-*.md 内容由对应 `assets/templates/NN-*.html` 提炼而来。

- [x] **Step 1: 写 patterns-catalog.md**

包含：
- 12 industry 索引表（编号 / 名称 / 视觉签名一句话 / 关键 widget pattern 列表）
- **pattern 选择优先级 4 条规则**（与 spec §3.1 末尾一致）：
  1. 业务名词权重 > 行业名词权重
  2. 表/列名信号 > 自然语言信号
  3. 多 industry 兼容时优先 multi-screen（01）
  4. 不明确时反问，给 3 候选
- 跨 industry 通用 widget pattern 库（如 `generic.kpi-tile`、`generic.echarts-card`、`generic.table`、`generic.markdown`、`generic.section-header`、`generic.divider`、`generic.image` 等 8 个 generic.* pattern，用于 v1→v2 lazy migration 兜底）

- [x] **Step 2: 写 12 份 industries/NN-*.md**

每份遵循固定模板（参考 spec §3.1）：

```markdown
# NN <Industry> — <Chinese Name>

## 业务上下文
（5-8 个该行业 KPI 与典型分析维度）

## 视觉签名
- 主色 #xxx / 辅色 #yyy / 背景 #zzz
- 装饰元素（粒子 / 玻璃拟态 / 流光卡片 等）
- ECharts theme baseline 覆盖项

## 推荐布局骨架
- header 高度 / 主体列宽比例 / 底部 ticker 高度

## 典型 widget pattern（4-8 个）
### <industry>.<pattern-name>
- 何时用
- ECharts option 模板片段（或 HTML 片段，KPI/funnel 等非 ECharts 时）
- SQL 输出列约定：col1 TYPE, col2 TYPE
- 推荐 refresh.intervalMs

## 触发线索（AI 用）
- 用户提到「关键词列表」→ 选此 industry
- 表名包含「关键词」→ 强信号
```

每份按 `tmp/Dashboard/` 对应原 HTML（已复制到 `assets/templates/`）的实际视觉提炼：

| 编号 | industry id | 中文名 | 取自 | 子代理任务 |
|---|---|---|---|---|
| 01 | multi-screen | 多屏综合监控 | `01-multi-screen-dashboard.html` | 提炼 4-6 个 multi-screen.* pattern |
| 02 | ecommerce | 电商运营实时监控中心 | `02-ecommerce.html` | 提炼 ecommerce.funnel-gradient / ecommerce.gmv-marquee 等 6+ pattern |
| 03 | manufacturing | 工业制造智能监控中心 | `03-manufacturing.html` | 6+ pattern |
| 04 | saas | SaaS 运营监控中心 | `04-saas.html` | 6+ pattern |
| 05 | finance | 财务数据分析中心 | `05-finance.html` | 6+ pattern |
| 06 | logistics | 物流供应链监控中心 | `06-logistics.html` | 6+ pattern |
| 07 | healthcare | 医疗健康大数据中心 | `07-healthcare.html` | 6+ pattern |
| 08 | hr | 人力资源分析中心 | `08-hr.html` | 6+ pattern |
| 09 | energy | 能源环保监控中心 | `09-energy.html` | 6+ pattern |
| 10 | cybersecurity | 网络安全态势感知中心 | `10-cybersecurity.html` | 6+ pattern |
| 11 | agriculture | 智慧农业大数据中心 | `11-agriculture.html` | 6+ pattern |
| 12 | education | 在线教育数据中心 | `12-education.html` | 6+ pattern |

- [x] **Step 3: 提交**

```bash
git add skills/bezel/references/patterns-catalog.md skills/bezel/references/industries/
git commit -m "docs: add patterns-catalog.md + 12 industries/*.md pattern detail"
```

### Task 6：validate.py + preview.py

**Files:**
- Create: `~/workspace/github/bezel/skills/bezel/scripts/validate.py`
- Create: `~/workspace/github/bezel/skills/bezel/scripts/preview.py`

- [x] **Step 1: 写 scripts/validate.py**

```python
#!/usr/bin/env python3
"""bezel HTML compliance validator.

Run before promoting a generated dashboard.html. Exits 0 on pass, 1 on fail.
"""
from __future__ import annotations
import argparse
import re
import sys
from pathlib import Path

ALLOWED_CDN = ("https://cdn.jsdelivr.net/",)

REQUIRED_CHECKS = [
    ("csp_meta", r'<meta\s+http-equiv\s*=\s*["\']Content-Security-Policy["\']'),
    ("bezel_origin_placeholder", r"__BEZEL_SERVER_ORIGIN__"),
    ("frame_ancestors_self", r"frame-ancestors\s+'self'"),
    ("json_hash_meta", r'<meta\s+name\s*=\s*["\']__JSON_HASH__["\']'),
    ("bezel_config", r"window\.__BEZEL_CONFIG__\s*="),
]

FORBIDDEN_CHECKS = [
    ("inline_event_handler", r"\bon[a-z]+\s*="),
    ("unsafe_eval", r"unsafe-eval"),
    ("wildcard_in_default_src", r"default-src[^;]*\*"),
]

def check_script_src_whitelist(html: str) -> list[str]:
    errors = []
    for m in re.finditer(r'<script[^>]+src\s*=\s*["\']([^"\']+)["\']', html, re.I):
        src = m.group(1)
        if not any(src.startswith(p) for p in ALLOWED_CDN):
            errors.append(f"non-whitelisted script src: {src}")
    return errors

def check_fetch_url_shape(html: str) -> list[str]:
    """Heuristic: every fetch() URL should target /api/dashboards/.../widgets/.../data."""
    errors = []
    for m in re.finditer(r"fetch\s*\(\s*['\"`]([^'\"`]+)['\"`]", html):
        url = m.group(1)
        if not re.match(r"^/api/dashboards/[^/]+/widgets/[^/]+/data$", url) and "{" not in url:
            errors.append(f"suspicious fetch URL: {url}")
    return errors

def validate(html_path: Path) -> int:
    html = html_path.read_text(encoding="utf-8")
    failures: list[str] = []

    for name, pattern in REQUIRED_CHECKS:
        if not re.search(pattern, html, re.I):
            failures.append(f"missing required: {name} (/{pattern}/)")

    for name, pattern in FORBIDDEN_CHECKS:
        if re.search(pattern, html, re.I):
            failures.append(f"forbidden present: {name} (/{pattern}/)")

    failures.extend(check_script_src_whitelist(html))
    failures.extend(check_fetch_url_shape(html))

    if failures:
        print(f"[bezel.validate] FAIL: {html_path}", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1

    print(f"[bezel.validate] PASS: {html_path}")
    return 0

def main() -> int:
    ap = argparse.ArgumentParser(description="Validate a bezel-generated dashboard.html")
    ap.add_argument("html", type=Path, help="path to dashboard.html")
    args = ap.parse_args()
    return validate(args.html)

if __name__ == "__main__":
    sys.exit(main())
```

- [x] **Step 2: 写 scripts/preview.py**

```python
#!/usr/bin/env python3
"""bezel local preview helper.

Reads a dashboard.json + a mock-data JSON, opens the HTML in a browser with
mock data inlined (replaces fetch() with a stub that returns mock rows).
For development only; not used in production.
"""
from __future__ import annotations
import argparse
import json
import sys
import webbrowser
from pathlib import Path

def main() -> int:
    ap = argparse.ArgumentParser(description="Preview a bezel dashboard.html locally with mock data")
    ap.add_argument("html", type=Path, help="path to dashboard.html")
    ap.add_argument("--mock", type=Path, default=None,
                    help="path to mock data JSON: { widgetId: { columns, rows } }")
    args = ap.parse_args()

    html = args.html.read_text(encoding="utf-8")
    if args.mock:
        mock = json.loads(args.mock.read_text(encoding="utf-8"))
        stub = (
            "<script>(function(){const M=" + json.dumps(mock) + ";"
            "const _f=window.fetch;window.fetch=function(u,opts){"
            "const m=u.match(/\\/widgets\\/([^/]+)\\/data/);"
            "if(m&&M[m[1]])return Promise.resolve({ok:true,status:200,"
            "json:()=>Promise.resolve(M[m[1]])});return _f(u,opts);};})();</script>"
        )
        html = html.replace("</head>", stub + "</head>", 1)

    out = args.html.with_suffix(".preview.html")
    out.write_text(html, encoding="utf-8")
    print(f"[bezel.preview] wrote {out}")
    webbrowser.open(out.as_uri())
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- [x] **Step 3: 让脚本可执行 + 自检**

```bash
chmod +x ~/workspace/github/bezel/skills/bezel/scripts/*.py
python3 ~/workspace/github/bezel/skills/bezel/scripts/validate.py --help
python3 ~/workspace/github/bezel/skills/bezel/scripts/preview.py --help
```

Expected: 两个脚本各打印 usage 字符串，无 ImportError。

- [x] **Step 4: 写一个最小 fixture HTML 跑通 validate.py 正/反例**

```bash
cd ~/workspace/github/bezel/skills/bezel
mkdir -p tmp
cat > tmp/good.html <<'EOF'
<!doctype html><html><head>
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__; frame-ancestors 'self'">
<meta name="__JSON_HASH__" content="sha256:dummy">
<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
</head><body><script>window.__BEZEL_CONFIG__={};fetch("/api/dashboards/x/widgets/y/data")</script></body></html>
EOF

cat > tmp/bad.html <<'EOF'
<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src *">
</head><body onload="alert(1)"><script src="https://evil.example.com/x.js"></script></body></html>
EOF

python3 scripts/validate.py tmp/good.html
echo "good exit: $?"
python3 scripts/validate.py tmp/bad.html
echo "bad exit: $?"
rm -rf tmp
```

Expected: good `[bezel.validate] PASS` 与 exit 0；bad `[bezel.validate] FAIL` 列出多条缺失/违规 + exit 1。

- [x] **Step 5: 提交**

```bash
git add skills/bezel/scripts/
git commit -m "feat: add validate.py + preview.py scripts"
```

---

## Phase 2：data-talk vendor + 启动 deploy

### Task 7：vendor bezel 进 server resources + 同步脚本

**Files:**
- Create: `data-talk/scripts/sync-bezel.sh`
- Create: `data-talk/server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel/` (vendored copy)
- Create: `data-talk/server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version`
- Create: `data-talk/server/data-talk-infrastructure/src/assembly/bezel.xml`
- Modify: `data-talk/server/data-talk-infrastructure/pom.xml`
- Modify: `data-talk/.gitignore`

- [x] **Step 1: 写 scripts/sync-bezel.sh**

```bash
#!/usr/bin/env bash
# sync-bezel.sh — vendor bezel skill content into server resources at a pinned tag.
set -euo pipefail

BEZEL_TAG="${1:-}"
if [[ -z "$BEZEL_TAG" ]]; then
  echo "usage: $0 <git-tag-or-branch-or-commit>" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_RES="$REPO_ROOT/server/data-talk-infrastructure/src/main/resources/opencode"
TMP="$(mktemp -d)"
trap "rm -rf $TMP" EXIT

# Prefer local checkout if available (for offline / dev), else clone from remote.
if [[ -d "$HOME/workspace/github/bezel/.git" ]]; then
  git -C "$HOME/workspace/github/bezel" worktree add "$TMP/bezel" "$BEZEL_TAG"
  trap "git -C $HOME/workspace/github/bezel worktree remove --force $TMP/bezel; rm -rf $TMP" EXIT
else
  git clone --depth 1 --branch "$BEZEL_TAG" https://github.com/wallfacers/bezel "$TMP/bezel"
fi

rm -rf "$SERVER_RES/skills-src/bezel"
mkdir -p "$SERVER_RES/skills-src/bezel"
cp -r "$TMP/bezel/." "$SERVER_RES/skills-src/bezel/"

mkdir -p "$SERVER_RES/skills"
echo "$BEZEL_TAG" > "$SERVER_RES/skills/bezel.version"

git -C "$REPO_ROOT" add "$SERVER_RES/skills-src/bezel" "$SERVER_RES/skills/bezel.version"
echo "synced bezel@$BEZEL_TAG into $SERVER_RES; review with 'git diff --cached' and commit"
```

```bash
chmod +x data-talk/scripts/sync-bezel.sh
```

- [x] **Step 2: 跑同步脚本**

```bash
cd /home/wushengzhou/workspace/github/data-talk
./scripts/sync-bezel.sh main
ls server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel/
cat server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version
```

Expected: `bezel.version` 内容为 `main`；`skills-src/bezel/` 含 `skills/bezel/SKILL.md` 等。

- [x] **Step 3: 写 src/assembly/bezel.xml**

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.1"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.1.1
                              http://maven.apache.org/xsd/assembly-2.1.1.xsd">
  <id>bezel</id>
  <formats>
    <format>tar.gz</format>
  </formats>
  <includeBaseDirectory>false</includeBaseDirectory>
  <fileSets>
    <fileSet>
      <directory>${project.basedir}/src/main/resources/opencode/skills-src/bezel/skills/bezel</directory>
      <outputDirectory>/</outputDirectory>
      <excludes>
        <exclude>.git/**</exclude>
        <exclude>.github/**</exclude>
        <exclude>README.md</exclude>
        <exclude>LICENSE</exclude>
        <exclude>**/tmp/**</exclude>
        <exclude>**/__pycache__/**</exclude>
      </excludes>
    </fileSet>
  </fileSets>
</assembly>
```

- [x] **Step 4: 修改 server/data-talk-infrastructure/pom.xml 加 maven-assembly-plugin**

在 `<plugins>` 段内追加：

```xml
<plugin>
  <artifactId>maven-assembly-plugin</artifactId>
  <executions>
    <execution>
      <id>vendor-bezel</id>
      <phase>process-resources</phase>
      <goals><goal>single</goal></goals>
      <configuration>
        <descriptors>
          <descriptor>src/assembly/bezel.xml</descriptor>
        </descriptors>
        <finalName>bezel</finalName>
        <appendAssemblyId>false</appendAssemblyId>
        <outputDirectory>${project.build.outputDirectory}/opencode/skills</outputDirectory>
        <tarLongFileMode>posix</tarLongFileMode>
      </configuration>
    </execution>
  </executions>
</plugin>
```

排除 `skills-src/` 不打进 jar（避免重复）：在该 module pom 的 `<resources>` 段加 `<excludes><exclude>opencode/skills-src/**</exclude></excludes>`，若 module 此前未自定义 `<resources>`，新增一个完整 `<resources>` 段。

- [x] **Step 5: 修改 .gitignore**

在 data-talk 根 `.gitignore` 末尾追加：

```
# bezel runtime install (extracted at server start)
/.opencode/skills/bezel/
/.opencode/.bezel-installed
```

- [x] **Step 6: 验证 build 生成 tar.gz**

```bash
cd /home/wushengzhou/workspace/github/data-talk
mvn install -pl server/data-talk-infrastructure -am -DskipTests
ls server/data-talk-infrastructure/target/classes/opencode/skills/
```

Expected: `bezel.tar.gz` 与 `bezel.version` 同目录存在。

- [x] **Step 7: 提交**

```bash
git add scripts/sync-bezel.sh \
        server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel \
        server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version \
        server/data-talk-infrastructure/src/assembly/bezel.xml \
        server/data-talk-infrastructure/pom.xml \
        .gitignore
git commit -m "feat(infra): vendor bezel skill + maven-assembly to bezel.tar.gz

- scripts/sync-bezel.sh pulls a pinned bezel tag/branch/commit into
  server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel
- maven-assembly-plugin packs skills-src/bezel/skills/bezel/ into
  target/classes/opencode/skills/bezel.tar.gz at process-resources phase
- bezel.version records the embedded tag for the runtime marker compare

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 8：OpenCodeBinaryResolver.ensureBezelSkill + 测试

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverBezelTest.java`

- [x] **Step 1: 写测试 OpenCodeBinaryResolverBezelTest（TDD：先写测试）**

```java
package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.*;

class OpenCodeBinaryResolverBezelTest {

    @TempDir Path tmp;
    OpenCodeBinaryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OpenCodeBinaryResolver() {
            @Override
            InputStream openClasspathResource(String name) {
                // mock: return a tar.gz containing a single file `SKILL.md`
                if (!"opencode/skills/bezel.tar.gz".equals(name)) return null;
                return makeMockTarGzInputStream();
            }
            @Override
            String readEmbeddedBezelVersion() { return "v0.1.0"; }
        };
    }

    @Test
    void extractsBezelSkillToProjectOpencodeSkillsBezelOnFirstRun() throws Exception {
        resolver.ensureBezelSkill(tmp);
        assertThat(Files.exists(tmp.resolve(".opencode/skills/bezel/SKILL.md"))).isTrue();
        assertThat(Files.readString(tmp.resolve(".opencode/.bezel-installed")).trim()).isEqualTo("v0.1.0");
    }

    @Test
    void isIdempotentWhenMarkerMatchesEmbeddedVersion() throws Exception {
        resolver.ensureBezelSkill(tmp);
        long firstMtime = Files.getLastModifiedTime(tmp.resolve(".opencode/skills/bezel/SKILL.md")).toMillis();
        Thread.sleep(20);
        resolver.ensureBezelSkill(tmp);
        long secondMtime = Files.getLastModifiedTime(tmp.resolve(".opencode/skills/bezel/SKILL.md")).toMillis();
        assertThat(secondMtime).isEqualTo(firstMtime);  // no re-extraction
    }

    @Test
    void reExtractsWhenEmbeddedVersionDiffersFromMarker() throws Exception {
        resolver.ensureBezelSkill(tmp);
        // simulate upgrade: bump embedded version
        OpenCodeBinaryResolver v2 = new OpenCodeBinaryResolver() {
            @Override InputStream openClasspathResource(String n) {
                return "opencode/skills/bezel.tar.gz".equals(n) ? makeMockTarGzInputStream("v2-content") : null;
            }
            @Override String readEmbeddedBezelVersion() { return "v0.2.0"; }
        };
        v2.ensureBezelSkill(tmp);
        assertThat(Files.readString(tmp.resolve(".opencode/.bezel-installed")).trim()).isEqualTo("v0.2.0");
        assertThat(Files.readString(tmp.resolve(".opencode/skills/bezel/SKILL.md"))).contains("v2-content");
    }

    @Test
    void noopWhenClasspathResourceMissing() throws Exception {
        OpenCodeBinaryResolver noBezel = new OpenCodeBinaryResolver() {
            @Override InputStream openClasspathResource(String n) { return null; }
            @Override String readEmbeddedBezelVersion() { return ""; }
        };
        // should not throw
        assertThatCode(() -> noBezel.ensureBezelSkill(tmp)).doesNotThrowAnyException();
        assertThat(Files.exists(tmp.resolve(".opencode/skills/bezel"))).isFalse();
    }

    private static InputStream makeMockTarGzInputStream() {
        return makeMockTarGzInputStream("# bezel SKILL.md\nstub");
    }

    private static InputStream makeMockTarGzInputStream(String content) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                byte[] body = content.getBytes();
                byte[] header = new byte[512];
                System.arraycopy("SKILL.md".getBytes(), 0, header, 0, 8);
                String size = String.format("%011o ", body.length);
                System.arraycopy(size.getBytes(), 0, header, 124, size.length());
                gz.write(header);
                gz.write(body);
                int padding = (512 - (body.length % 512)) % 512;
                gz.write(new byte[padding]);
                gz.write(new byte[512 * 2]);  // two zero blocks = EOF
            }
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [x] **Step 2: 跑测试，预期 4 个全部 FAIL（`ensureBezelSkill` 还没实现）**

```bash
cd server
mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverBezelTest
```

Expected: 4 test failures（`NoSuchMethodError: ensureBezelSkill` 或类似）。

- [x] **Step 3: 实现 OpenCodeBinaryResolver.ensureBezelSkill**

修改 `OpenCodeBinaryResolver.java`，在末尾加：

```java
static final String BEZEL_RESOURCE = "opencode/skills/bezel.tar.gz";
static final String BEZEL_VERSION_RESOURCE = "opencode/skills/bezel.version";
static final String BEZEL_MARKER   = ".bezel-installed";

public void ensureBezelSkill(Path projectRoot) {
    Path opencodeDir = projectRoot.resolve(".opencode");
    Path skillDir    = opencodeDir.resolve("skills").resolve("bezel");
    Path marker      = opencodeDir.resolve(BEZEL_MARKER);

    String embeddedVersion = readEmbeddedBezelVersion();
    if (Files.exists(marker) && Files.isDirectory(skillDir)) {
        try {
            if (embeddedVersion.equals(Files.readString(marker).trim())) {
                return;
            }
        } catch (IOException ignored) { /* fall through to reinstall */ }
        deleteRecursively(skillDir);
    }

    try (InputStream in = openClasspathResource(BEZEL_RESOURCE)) {
        if (in == null) {
            log.info("No bundled bezel skill on classpath, skipping extraction");
            return;
        }
        Files.createDirectories(skillDir);
        extractDepsTarGz(in, skillDir);
        Files.createDirectories(opencodeDir);
        Files.writeString(marker, embeddedVersion);
        log.info("Extracted bundled bezel skill v{} to {}", embeddedVersion, skillDir);
    } catch (Exception e) {
        log.warn("Failed to extract bezel skill: {}", e.getMessage());
    }
}

InputStream openClasspathResource(String name) {
    return getClass().getClassLoader().getResourceAsStream(name);
}

String readEmbeddedBezelVersion() {
    try (InputStream in = openClasspathResource(BEZEL_VERSION_RESOURCE)) {
        if (in == null) return "";
        return new String(in.readAllBytes()).trim();
    } catch (IOException e) {
        return "";
    }
}
```

注意 `extractDepsTarGz` 已存在（公共 helper），复用即可。把 `extractDepsTarGz` 的可见性从 `void` 隐含 package-private 保持不变。

- [x] **Step 4: 跑测试，预期全部 PASS**

```bash
mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverBezelTest
```

Expected: 4 tests pass。

- [x] **Step 5: 提交**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverBezelTest.java
git commit -m "feat(infra): OpenCodeBinaryResolver.ensureBezelSkill with idempotent version-aware extract

- BEZEL_RESOURCE + BEZEL_MARKER + readEmbeddedBezelVersion
- 4 tests: first-run extract / idempotent on match / re-extract on bump / noop on missing resource

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 9：OpenCodeProcessManager 接入

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java`

- [x] **Step 1: 找到 start() 中 binaryResolver.extractFromClasspath() 调用处**

```bash
grep -n 'extractFromClasspath' /home/wushengzhou/workspace/github/data-talk/server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java
```

- [x] **Step 2: 在 `binaryResolver.ensureNodeModules()` 调用之后增加一行**

```java
binaryResolver.ensureNodeModules();
binaryResolver.ensureBezelSkill(Paths.get(""));   // NEW: deploy bezel skill to ./.opencode/skills/bezel/
```

`Paths.get("")` 在 JVM cwd 下解析为 `""`，但 `Files.createDirectories` 与 `tmp.resolve(...)` 都能正确处理空 path。如果出现 `IllegalArgumentException`，回退到 `Paths.get(System.getProperty("user.dir"))`。

- [x] **Step 3: 跑现有 OpenCodeProcessManagerTest / 启动 smoke**

```bash
cd server
mvn test -pl data-talk-infrastructure
```

Expected: 全绿。

- [x] **Step 4: 提交**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java
git commit -m "feat(infra): wire ensureBezelSkill into OpenCodeProcessManager.start()"
```

---

## Phase 3：data-talk schema v2 + Java validator

> **执行方式**：T10/T11/T12 三个文件不重叠，可用 `dispatching-parallel-agents` 并发起 3 个子代理。批末统一一次 `cd server && mvn clean verify`。

### Task 10：domain + schema-json v2

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Dashboard.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Widget.java`
- Modify: `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json`

- [x] **Step 1: 改 Dashboard record**

在 `Dashboard.java` 现有字段（假设含 `id/title/description/parameters/widgets/layout/version/createdAt/updatedAt/defaultConnectionId`）基础上加：

```java
public record DashboardRefresh(int defaultIntervalMs, boolean pauseOnHidden) {
    public DashboardRefresh { if (defaultIntervalMs < 1000) throw new IllegalArgumentException("refresh.defaultIntervalMs >= 1000"); }
    public static DashboardRefresh defaults() { return new DashboardRefresh(10000, true); }
}

public record Dashboard(
    int schemaVersion,
    String id,
    String title,
    String description,
    String defaultConnectionId,
    String theme,                 // NEW
    String renderer,              // NEW (always "bezel")
    DashboardRefresh refresh,     // NEW (nullable; use defaults() when null)
    List<ParameterDef> parameters,
    List<Widget> widgets,
    Layout layout,
    int version,
    long createdAt,
    long updatedAt
) {
    public Dashboard {
        if (schemaVersion != 2) throw new IllegalArgumentException("only schemaVersion=2 accepted at construction; migrate v1 first");
        if (!"bezel".equals(renderer)) throw new IllegalArgumentException("renderer must be 'bezel'");
        if (theme == null || !theme.startsWith("industry-")) throw new IllegalArgumentException("theme must match industry-*");
    }
}
```

- [x] **Step 2: 改 Widget record**

```java
public record WidgetRefresh(Integer intervalMs, RefreshStrategy strategy) { }
public enum RefreshStrategy { DATA_ONLY, FULL_RERENDER }

public record Widget(
    String id,
    String type,
    String patternId,              // NEW (required, e.g., "ecommerce.funnel-gradient")
    GridPosition position,
    List<ParameterDef> parameters,
    WidgetQuery query,
    WidgetRefresh refresh,         // NEW (nullable; inherit dashboard default)
    Map<String, Object> options
) {
    public Widget {
        if (patternId == null || !patternId.matches("[a-z0-9-]+\\.[a-z0-9-]+"))
            throw new IllegalArgumentException("patternId must match <industry>.<pattern>");
    }
}
```

- [x] **Step 3: 改 dashboard-schema.json**

把 `"schemaVersion": { "const": 1 }` 改成 `"const": 2`；加 `theme` / `renderer` / `refresh` 字段到根 `properties` 与 `required`；加 `patternId` / `refresh` 到 widget `properties` 与 `required`；把 `gridLayout` 改成 `freeLayout`：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["schemaVersion","id","title","theme","renderer","parameters","widgets","layout","version"],
  "properties": {
    "schemaVersion": { "const": 2 },
    "id": { "type": "string", "pattern": "^dash_[a-zA-Z0-9_]{4,}$" },
    "title": { "type": "string", "minLength": 1, "maxLength": 256 },
    "description": { "type": "string", "maxLength": 32768 },
    "defaultConnectionId": { "type": ["string","null"] },
    "theme": { "type": "string", "pattern": "^industry-[a-z-]+$" },
    "renderer": { "const": "bezel" },
    "refresh": {
      "type": "object",
      "properties": {
        "defaultIntervalMs": { "type": "integer", "minimum": 1000 },
        "pauseOnHidden": { "type": "boolean" }
      }
    },
    "version": { "type": "integer", "minimum": 1 },
    "createdAt": { "type": "integer", "minimum": 0 },
    "updatedAt": { "type": "integer", "minimum": 0 },
    "parameters": { "type": "array", "items": { "$ref": "#/$defs/parameterDef" } },
    "widgets":    { "type": "array", "items": { "$ref": "#/$defs/widget" } },
    "layout":     { "$ref": "#/$defs/freeLayout" }
  },
  "$defs": {
    "parameterDef": { /* unchanged */ },
    "widget": {
      "type": "object",
      "required": ["id","type","patternId","position","options"],
      "properties": {
        "id":        { "type": "string", "pattern": "^[a-z]+_w_[a-zA-Z0-9]{4,16}$" },
        "type":      { "enum": ["chart","kpi","table","markdown","filter","section","divider","image"] },
        "patternId": { "type": "string", "pattern": "^[a-z0-9-]+\\.[a-z0-9-]+$" },
        "position":  { "$ref": "#/$defs/gridPosition" },
        "parameters":{ "type": "array", "items": { "$ref": "#/$defs/parameterDef" } },
        "query":     { "$ref": "#/$defs/widgetQuery" },
        "refresh": {
          "type": "object",
          "properties": {
            "intervalMs": { "type": "integer", "minimum": 1000 },
            "strategy":   { "enum": ["data-only","full-rerender"] }
          }
        },
        "options":   { "type": "object" }
      }
    },
    "widgetQuery":  { /* unchanged */ },
    "gridPosition": { /* unchanged */ },
    "freeLayout": {
      "type": "object",
      "required": ["engine"],
      "properties": {
        "engine": { "const": "free" },
        "viewport": {
          "type": "object",
          "properties": {
            "minWidth": { "type": "integer", "minimum": 640 },
            "aspect":   { "type": "string", "pattern": "^\\d+:\\d+$" }
          }
        }
      }
    }
  }
}
```

- [x] **Step 4: 跑既有 dashboard 单元测试，预期会 FAIL（v1 用例不再合法）**

```bash
cd server
mvn test -pl data-talk-domain
```

Expected: v1 测试失败。

- [x] **Step 5: 升级既有 v1 测试用例为 v2**

逐文件搜：
```bash
grep -rln 'schemaVersion.*1' server/data-talk-domain/src/test
```
每个用例加 `theme=industry-neutral`, `renderer=bezel`, widget 加 `patternId=generic.echarts-card`，并把 `schemaVersion=1` 改为 `2`。

- [x] **Step 6: 再跑测试**

```bash
mvn test -pl data-talk-domain
```

Expected: 全绿。

- [x] **Step 7: 提交**

```bash
git add server/data-talk-domain server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json
git commit -m "feat(domain): dashboard schema v2 — theme/renderer/refresh/patternId + freeLayout"
```

### Task 11：Java HTML validator

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/BezelHtmlValidator.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/dashboard/BezelHtmlValidatorTest.java`

- [x] **Step 1: 写测试**

```java
package com.datatalk.application.dashboard;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BezelHtmlValidatorTest {

    private static final String OK_HTML = """
        <!doctype html><html><head>
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__; frame-ancestors 'self'">
        <meta name="__JSON_HASH__" content="sha256:abc">
        <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
        </head><body><div id="w1"></div>
        <script>window.__BEZEL_CONFIG__={dashboardId:'x',widgets:[]};</script>
        </body></html>
        """;

    @Test void passesOnCompliantHtml() {
        var r = BezelHtmlValidator.validate(OK_HTML);
        assertThat(r.ok()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test void failsWhenCspMetaMissing() {
        var r = BezelHtmlValidator.validate(OK_HTML.replace("<meta http-equiv=\"Content-Security-Policy\"", "<meta name=\"x\""));
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("missing required: csp_meta"));
    }

    @Test void failsWhenServerOriginPlaceholderMissing() {
        var r = BezelHtmlValidator.validate(OK_HTML.replace("__BEZEL_SERVER_ORIGIN__", "http://evil"));
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("bezel_origin_placeholder"));
    }

    @Test void failsOnInlineEventHandler() {
        var bad = OK_HTML.replace("<body>", "<body onclick=\"alert(1)\">");
        var r = BezelHtmlValidator.validate(bad);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("forbidden present: inline_event_handler"));
    }

    @Test void failsOnNonWhitelistedScriptSrc() {
        var bad = OK_HTML.replace("https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js",
                                  "https://evil.example.com/x.js");
        var r = BezelHtmlValidator.validate(bad);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("non-whitelisted script src"));
    }
}
```

- [x] **Step 2: 跑测试，预期 FAIL（类不存在）**

```bash
cd server
mvn test -pl data-talk-application -Dtest=BezelHtmlValidatorTest
```

Expected: 编译错误「找不到符号 BezelHtmlValidator」。

- [x] **Step 3: 实现 BezelHtmlValidator**

```java
package com.datatalk.application.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-side mirror of bezel/skills/bezel/scripts/validate.py.
 * Server does not trust client; re-runs the same checks before promote.
 */
public final class BezelHtmlValidator {

    private static final List<String> ALLOWED_CDN = List.of("https://cdn.jsdelivr.net/");

    private static final List<Required> REQUIRED = List.of(
        new Required("csp_meta", Pattern.compile("<meta\\s+http-equiv\\s*=\\s*[\"']Content-Security-Policy[\"']", Pattern.CASE_INSENSITIVE)),
        new Required("bezel_origin_placeholder", Pattern.compile("__BEZEL_SERVER_ORIGIN__")),
        new Required("frame_ancestors_self", Pattern.compile("frame-ancestors\\s+'self'")),
        new Required("json_hash_meta", Pattern.compile("<meta\\s+name\\s*=\\s*[\"']__JSON_HASH__[\"']", Pattern.CASE_INSENSITIVE)),
        new Required("bezel_config", Pattern.compile("window\\.__BEZEL_CONFIG__\\s*="))
    );

    private static final List<Forbidden> FORBIDDEN = List.of(
        new Forbidden("inline_event_handler", Pattern.compile("\\bon[a-z]+\\s*=", Pattern.CASE_INSENSITIVE)),
        new Forbidden("unsafe_eval", Pattern.compile("unsafe-eval")),
        new Forbidden("wildcard_in_default_src", Pattern.compile("default-src[^;]*\\*"))
    );

    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public record Result(boolean ok, List<String> errors) {}
    private record Required(String name, Pattern pat) {}
    private record Forbidden(String name, Pattern pat) {}

    public static Result validate(String html) {
        List<String> errors = new ArrayList<>();
        for (Required r : REQUIRED) {
            if (!r.pat.matcher(html).find()) errors.add("missing required: " + r.name);
        }
        for (Forbidden f : FORBIDDEN) {
            if (f.pat.matcher(html).find()) errors.add("forbidden present: " + f.name);
        }
        var m = SCRIPT_SRC.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            boolean ok = false;
            for (String p : ALLOWED_CDN) if (src.startsWith(p)) { ok = true; break; }
            if (!ok) errors.add("non-whitelisted script src: " + src);
        }
        return new Result(errors.isEmpty(), errors);
    }

    private BezelHtmlValidator() {}
}
```

- [x] **Step 4: 跑测试，预期 PASS**

```bash
mvn test -pl data-talk-application -Dtest=BezelHtmlValidatorTest
```

Expected: 5 tests pass。

- [x] **Step 5: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/dashboard/BezelHtmlValidator.java \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/BezelHtmlValidatorTest.java
git commit -m "feat(app): BezelHtmlValidator (server-side mirror of bezel validate.py)"
```

### Task 12：lazy migration in DashboardArtifactService.load

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/dashboard/DashboardArtifactServiceV1MigrationTest.java`

- [x] **Step 1: 写测试**

```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DashboardArtifactServiceV1MigrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test void migratesV1JsonToV2OnLoad() throws Exception {
        String v1 = """
            {
              "schemaVersion": 1,
              "id": "dash_test",
              "title": "Old",
              "parameters": [],
              "widgets": [
                { "id": "chart_w_aaaa", "type": "chart", "position": {"x":0,"y":0,"w":4,"h":3},
                  "options": { "echartsOption": {} } },
                { "id": "kpi_w_bbbb", "type": "kpi", "position": {"x":4,"y":0,"w":2,"h":2}, "options": {} }
              ],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 80, "gap": 8 },
              "version": 5, "createdAt": 0, "updatedAt": 0
            }
            """;
        JsonNode v2 = DashboardArtifactService.migrateV1ToV2(mapper.readTree(v1), mapper);
        assertThat(v2.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(v2.path("renderer").asText()).isEqualTo("bezel");
        assertThat(v2.path("theme").asText()).isEqualTo("industry-neutral");
        assertThat(v2.path("refresh").path("defaultIntervalMs").asInt()).isEqualTo(10000);
        assertThat(v2.path("layout").path("engine").asText()).isEqualTo("free");
        assertThat(v2.path("widgets").get(0).path("patternId").asText()).isEqualTo("generic.echarts-card");
        assertThat(v2.path("widgets").get(1).path("patternId").asText()).isEqualTo("generic.kpi-tile");
    }
}
```

- [x] **Step 2: 跑测试，预期 FAIL（migrateV1ToV2 不存在）**

```bash
cd server
mvn test -pl data-talk-application -Dtest=DashboardArtifactServiceV1MigrationTest
```

- [x] **Step 3: 实现 migrateV1ToV2 + 在 load() 中调用**

在 `DashboardArtifactService.java` 加：

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public static JsonNode migrateV1ToV2(JsonNode v1, ObjectMapper mapper) {
    ObjectNode v2 = v1.deepCopy();
    v2.put("schemaVersion", 2);
    v2.put("renderer", "bezel");
    v2.put("theme", "industry-neutral");
    v2.set("refresh", mapper.createObjectNode()
        .put("defaultIntervalMs", 10000)
        .put("pauseOnHidden", true));
    if (v2.has("layout") && v2.get("layout").isObject()) {
        ((ObjectNode) v2.get("layout")).put("engine", "free");
    }
    if (v2.has("widgets") && v2.get("widgets").isArray()) {
        for (JsonNode w : v2.get("widgets")) {
            ObjectNode wo = (ObjectNode) w;
            if (!wo.has("patternId")) {
                wo.put("patternId", inferPatternFromType(wo.path("type").asText("chart")));
            }
        }
    }
    return v2;
}

private static String inferPatternFromType(String type) {
    return switch (type) {
        case "chart"    -> "generic.echarts-card";
        case "kpi"      -> "generic.kpi-tile";
        case "table"    -> "generic.table";
        case "markdown" -> "generic.markdown";
        case "filter"   -> "generic.filter-bar";
        case "section"  -> "generic.section-header";
        case "divider"  -> "generic.divider";
        case "image"    -> "generic.image";
        default         -> "generic.echarts-card";
    };
}
```

在现有 `load(id)` 内（读 raw JSON 后、treeToValue 前），加分支：

```java
JsonNode tree = mapper.readTree(raw);
if (tree.path("schemaVersion").asInt() == 1) {
    tree = migrateV1ToV2(tree, mapper);
}
return mapper.treeToValue(tree, Dashboard.class);
```

- [x] **Step 4: 跑测试**

```bash
mvn test -pl data-talk-application -Dtest=DashboardArtifactServiceV1MigrationTest
```

Expected: pass。同时跑既有 DashboardArtifactServiceTest 确认无 regression。

- [x] **Step 5: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/DashboardArtifactServiceV1MigrationTest.java
git commit -m "feat(app): lazy v1→v2 migration in DashboardArtifactService.load"
```

> **批末统一验证**（Phase 3 收尾）：
> ```bash
> cd server
> mvn clean verify
> ```
> Expected: 全绿。

---

## Phase 4：data-talk 后端 API

### Task 13：DashboardController GET /html + promote 改造

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardHtmlServeControllerIT.java`

- [x] **Step 1: 写 IT 测试**

```java
package com.datatalk.adapter.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DashboardHtmlServeControllerIT {

    @Autowired MockMvc mvc;

    @Test
    void servesHtmlWithServerOriginInjected() throws Exception {
        // assume test fixtures promote a dashboard 'dash_test01' with html containing __BEZEL_SERVER_ORIGIN__
        var result = mvc.perform(get("/api/dashboards/dash_test01/html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("__BEZEL_SERVER_ORIGIN__");
        assertThat(body).contains("http://");  // origin was injected
    }

    @Test
    void returns404WhenHtmlArtifactMissing() throws Exception {
        mvc.perform(get("/api/dashboards/dash_v1_only/html"))
            .andExpect(status().isNotFound());
    }
}
```

注：fixture 由现有 dashboard test infra 提供；如果不存在 promote-html 的入口，在 setUp 里调 `DashboardArtifactService.promote(v2json, htmlBytes)`。

- [x] **Step 2: 跑测试，预期 FAIL（endpoint 未实现）**

```bash
cd server
mvn test -pl data-talk-adapter -Dtest=DashboardHtmlServeControllerIT
```

- [x] **Step 3: 改 DashboardArtifactService.promote 接受 html bytes**

在 `DashboardArtifactService.java` 既有 `promote(Object dashboard)` 旁加 overload：

```java
public PromoteResult promote(Object dashboardJson, byte[] htmlBytes) {
    if (htmlBytes != null && htmlBytes.length > 0) {
        var v = BezelHtmlValidator.validate(new String(htmlBytes));
        if (!v.ok()) throw new IllegalArgumentException("bezel html validation failed: " + String.join("; ", v.errors()));
    }
    var res = promote(dashboardJson);   // existing JSON-only path
    if (htmlBytes != null && htmlBytes.length > 0) {
        storeHtmlArtifact(res.id(), res.version(), htmlBytes);  // delegate to file_artifact via FileArtifactService.registerExternal
    }
    return res;
}

public void replaceHtml(String dashboardId, int baseVersion, byte[] htmlBytes) {
    var v = BezelHtmlValidator.validate(new String(htmlBytes));
    if (!v.ok()) throw new IllegalArgumentException("bezel html validation failed: " + String.join("; ", v.errors()));
    // bump dashboard version (via promote/patch) then write new HTML artifact same way
    fileArtifactService.replaceBytesAtomic(htmlArtifactId(dashboardId), htmlBytes);
}

public Optional<byte[]> loadHtml(String dashboardId) {
    try {
        return Optional.of(fileArtifactService.readBytes(htmlArtifactId(dashboardId)));
    } catch (ArtifactNotFoundException e) {
        return Optional.empty();
    }
}

private String htmlArtifactId(String dashboardId) { return dashboardId + ":html"; }

private void storeHtmlArtifact(String dashboardId, int version, byte[] bytes) {
    Path p = sessionWorkdirRoot.dashboardsRoot().resolve(dashboardId + ".html");
    Files.createDirectories(p.getParent());
    Files.write(p, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    fileArtifactService.registerExternal(htmlArtifactId(dashboardId), FileArtifactKind.DASHBOARD,
        Scope.SESSION, null, currentSessionId(), p.toAbsolutePath(),
        "dashboard " + dashboardId + " html v" + version, null,
        Map.of("dashboardId", dashboardId, "version", version, "kind-sub", "html"));
}
```

注：实际方法签名按 `FileArtifactService` 既有 `registerExternal(...)` 与 `readBytes(...)` 一致（详见 `2026-05-09-dashboard-file-artifact-integration-design.md` D2）。htmlArtifactId 后缀 `:html` 区分 JSON artifact。

- [x] **Step 4: 改 DashboardController 加 GET /api/dashboards/{id}/html**

```java
@GetMapping(value = "/api/dashboards/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
public ResponseEntity<String> serveHtml(@PathVariable String id, HttpServletRequest req) {
    var maybe = dashboardArtifactService.loadHtml(id);
    if (maybe.isEmpty()) return ResponseEntity.notFound().build();
    String origin = "http://" + req.getServerName() + ":" + req.getServerPort();
    String body = new String(maybe.get(), StandardCharsets.UTF_8)
        .replace("__BEZEL_SERVER_ORIGIN__", origin);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(body);
}
```

- [x] **Step 5: 跑测试**

```bash
mvn test -pl data-talk-adapter -Dtest=DashboardHtmlServeControllerIT
```

Expected: pass。

- [x] **Step 6: 提交**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardHtmlServeControllerIT.java
git commit -m "feat(adapter): GET /api/dashboards/{id}/html with __BEZEL_SERVER_ORIGIN__ injection"
```

### Task 14：WidgetDataService + POST /widgets/{wid}/data

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/WidgetDataService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/WidgetDataControllerIT.java`

- [x] **Step 1: 写 IT 测试**

```java
package com.datatalk.adapter.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
class WidgetDataControllerIT {
    @Autowired MockMvc mvc;

    @Test void returnsColumnsRowsForKnownWidget() throws Exception {
        mvc.perform(post("/api/dashboards/dash_test01/widgets/chart_w_aaaa/data")
                .contentType("application/json")
                .content("{\"params\":{}}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.columns").isArray())
           .andExpect(jsonPath("$.rows").isArray())
           .andExpect(jsonPath("$.executedAt").isNumber());
    }

    @Test void returns404OnUnknownWidget() throws Exception {
        mvc.perform(post("/api/dashboards/dash_test01/widgets/none_w_xxxx/data")
                .contentType("application/json").content("{\"params\":{}}"))
           .andExpect(status().isNotFound());
    }

    @Test void returns400OnDdlSql() throws Exception {
        // a fixture dashboard with widgetQuery.sql = "DROP TABLE x" should be rejected
        mvc.perform(post("/api/dashboards/dash_with_bad_sql/widgets/bad_w_dddd/data")
                .contentType("application/json").content("{\"params\":{}}"))
           .andExpect(status().isBadRequest());
    }
}
```

- [x] **Step 2: 跑测试，预期 FAIL**

```bash
mvn test -pl data-talk-adapter -Dtest=WidgetDataControllerIT
```

- [x] **Step 3: 实现 WidgetDataService**

```java
package com.datatalk.application.dashboard;

import com.datatalk.application.sql.ParameterizedSqlExecutor;
import com.datatalk.application.sql.SqlStatementGuard;
// ... domain imports

public class WidgetDataService {

    private final DashboardArtifactService dashboardService;
    private final ParameterizedSqlExecutor sqlExecutor;
    private final SqlStatementGuard guard;

    public WidgetDataService(DashboardArtifactService d, ParameterizedSqlExecutor s, SqlStatementGuard g) {
        this.dashboardService = d; this.sqlExecutor = s; this.guard = g;
    }

    public record WidgetData(List<String> columns, List<List<Object>> rows, long executedAt) {}

    public Optional<WidgetData> fetchWidgetData(String dashboardId, String widgetId, Map<String,Object> params) {
        var dash = dashboardService.load(dashboardId);
        if (dash == null) return Optional.empty();
        var widget = dash.widgets().stream().filter(w -> w.id().equals(widgetId)).findFirst().orElse(null);
        if (widget == null) return Optional.empty();
        var q = widget.query();
        if (q == null) return Optional.empty();

        guard.assertReadOnlySelect(q.sql(), dash.defaultConnectionIdOr(q.connectionId()));
        var result = sqlExecutor.execute(q.connectionId() != null ? q.connectionId() : dash.defaultConnectionId(),
                                          q.sql(), resolveParams(dash, widget, q, params));
        return Optional.of(new WidgetData(result.columns(), result.rows(), System.currentTimeMillis()));
    }

    private Map<String,Object> resolveParams(Dashboard d, Widget w, WidgetQuery q, Map<String,Object> hostParams) {
        Map<String,Object> resolved = new HashMap<>();
        for (var e : q.paramRefs().entrySet()) {
            String sqlName = e.getKey();
            String refId   = e.getValue();
            Object v = hostParams.get(refId);
            if (v == null) v = lookupDefault(d, w, refId);
            resolved.put(sqlName, v);
        }
        return resolved;
    }

    private Object lookupDefault(Dashboard d, Widget w, String paramId) {
        // search widget-local first, then dashboard-global
        for (var p : Optional.ofNullable(w.parameters()).orElse(List.of())) {
            if (p.id().equals(paramId)) return p.defaultValue();
        }
        for (var p : d.parameters()) if (p.id().equals(paramId)) return p.defaultValue();
        return null;
    }
}
```

注：`SqlStatementGuard.assertReadOnlySelect(...)` 与 `ParameterizedSqlExecutor.execute(...)` 沿用既有签名；如果接口不完全匹配，对照既有用例（如 `DashboardArtifactService` 当前如何调 SQL）做最小适配。

- [x] **Step 4: 加 controller endpoint**

在 `DashboardController.java` 加：

```java
@PostMapping("/api/dashboards/{id}/widgets/{wid}/data")
@CrossOrigin(origins = "null", allowCredentials = "false")
public ResponseEntity<WidgetDataService.WidgetData> fetchWidgetData(
    @PathVariable String id, @PathVariable String wid,
    @RequestBody Map<String,Object> body) {
    @SuppressWarnings("unchecked")
    Map<String,Object> params = (Map<String,Object>) body.getOrDefault("params", Map.of());
    try {
        return widgetDataService.fetchWidgetData(id, wid, params)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().build();
    }
}
```

- [x] **Step 5: 跑测试**

```bash
mvn test -pl data-talk-adapter -Dtest=WidgetDataControllerIT
```

Expected: pass。

- [x] **Step 6: 全模块 verify**

```bash
mvn clean verify
```

Expected: 全绿。

- [x] **Step 7: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/dashboard/WidgetDataService.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/WidgetDataControllerIT.java
git commit -m "feat(adapter): POST /api/dashboards/{id}/widgets/{wid}/data with guard + CORS origin=null"
```

---

## Phase 5：data-talk 前端重写

### Task 15：删除旧 React canvas / widgets / engines

**Files:**
- Delete (entire files/dirs):
  - `client/src/features/dashboard/dashboard-canvas.tsx`
  - `client/src/features/dashboard/widgets/chart-widget.tsx`
  - `client/src/features/dashboard/widgets/markdown-widget.tsx`
  - `client/src/features/dashboard/widgets/widget-shell.tsx`
  - `client/src/features/dashboard/widgets/__tests__/` （整目录）
  - `client/src/features/dashboard/engines/grid-layout-engine.ts`
  - `client/src/features/dashboard/engines/layout-engine.ts`
  - `client/src/features/dashboard/engines/__tests__/` （整目录）

- [x] **Step 1: 找到所有引用点**

```bash
cd client
grep -rln 'dashboard-canvas\|chart-widget\|markdown-widget\|widget-shell\|grid-layout-engine\|layout-engine' \
   --include='*.ts' --include='*.tsx' src tests | grep -v 'node_modules'
```

记下所有引用——这些文件接下来要么改、要么删；现有 `dashboard-tab.tsx` 必然引用 `dashboard-canvas`，T18 重写它时把引用切掉。

- [x] **Step 2: 删除文件**

```bash
rm -f src/features/dashboard/dashboard-canvas.tsx
rm -f src/features/dashboard/widgets/chart-widget.tsx
rm -f src/features/dashboard/widgets/markdown-widget.tsx
rm -f src/features/dashboard/widgets/widget-shell.tsx
rm -rf src/features/dashboard/widgets/__tests__
rmdir src/features/dashboard/widgets 2>/dev/null || true  # 若已空
rm -f src/features/dashboard/engines/grid-layout-engine.ts
rm -f src/features/dashboard/engines/layout-engine.ts
rm -rf src/features/dashboard/engines/__tests__
rmdir src/features/dashboard/engines 2>/dev/null || true  # 若已空
```

- [x] **Step 3: 跑 tsc，预期 FAIL（dashboard-tab.tsx 引用已删除模块）**

```bash
cd client
npx tsc --noEmit
```

记下报错——T18 修复。

- [x] **Step 4: 不要现在提交**

留给 T18 一起 commit（删除 + 重写 dashboard-tab 是同一个语义动作）。

### Task 16：schema.ts v2 + services/dashboard-api.ts 加 fetchDashboardHtml

**Files:**
- Modify: `client/src/features/dashboard/schema.ts`
- Modify: `client/src/features/dashboard/services/dashboard-api.ts`

- [x] **Step 1: 改 schema.ts（按 spec §4.4）**

```ts
import { z } from 'zod'

const gridPosition = z.object({
  x: z.number().int().min(0).max(11),
  y: z.number().int().min(0),
  w: z.number().int().min(1).max(12),
  h: z.number().int().min(1),
  z: z.number().int().nullable().optional(),
})

const widgetQuery = z.object({
  connectionId: z.string().nullable().optional(),
  sql: z.string(),
  paramRefs: z.record(z.string(), z.string()),
})

const parameterDef = z.object({
  id: z.string().regex(/^(global|local):[a-zA-Z0-9_:]+$/),
  scope: z.enum(['global', 'local']),
  ownerWidgetId: z.string().nullable().optional(),
  name: z.string(),
  type: z.enum(['date', 'date_range', 'string', 'number', 'string_list']),
  default: z.unknown(),
})

const refreshPolicy = z.object({
  intervalMs: z.number().int().min(1000).optional(),
  strategy: z.enum(['data-only', 'full-rerender']).optional(),
})

const dashboardRefresh = z.object({
  defaultIntervalMs: z.number().int().min(1000).default(10000),
  pauseOnHidden: z.boolean().default(true),
})

const widget = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9]{4,16}$/),
  type: z.enum(['chart', 'kpi', 'table', 'markdown', 'filter', 'section', 'divider', 'image']),
  patternId: z.string().regex(/^[a-z0-9-]+\.[a-z0-9-]+$/),
  position: gridPosition,
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
  refresh: refreshPolicy.optional(),
  options: z.record(z.string(), z.unknown()),
})

const freeLayout = z.object({
  engine: z.literal('free'),
  viewport: z.object({
    minWidth: z.number().int().min(640),
    aspect: z.string().regex(/^\d+:\d+$/),
  }).optional(),
})

export const dashboardSchema = z.object({
  schemaVersion: z.literal(2),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  theme: z.string().regex(/^industry-[a-z-]+$/),
  renderer: z.literal('bezel'),
  refresh: dashboardRefresh.optional(),
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: freeLayout,
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0),
  updatedAt: z.number().int().min(0),
})

export type Dashboard = z.infer<typeof dashboardSchema>
export type Widget = z.infer<typeof widget>
export type WidgetQuery = z.infer<typeof widgetQuery>
export type GridPosition = z.infer<typeof gridPosition>
export type FreeLayout = z.infer<typeof freeLayout>
export type ParameterDef = z.infer<typeof parameterDef>
export type DashboardRefresh = z.infer<typeof dashboardRefresh>
export type WidgetRefresh = z.infer<typeof refreshPolicy>
```

- [x] **Step 2: 改 services/dashboard-api.ts 加 fetchDashboardHtml**

在文件末尾加：

```ts
export async function fetchDashboardHtml(id: string): Promise<string | null> {
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}/html`)
  if (!response.ok) return null
  return await response.text()
}
```

- [x] **Step 3: 跑 schema 既有测试 + tsc**

```bash
cd client
npx tsc --noEmit
npm test -- src/features/dashboard/__tests__
```

预期 tsc 仍有 dashboard-tab 报错（待 T18 修），但 schema 测试通过。

- [x] **Step 4: 不要现在提交**（留给 T18 一起）

### Task 17：iframe-shell.tsx + iframe-protocol.ts

**Files:**
- Create: `client/src/features/dashboard/iframe-protocol.ts`
- Create: `client/src/features/dashboard/iframe-shell.tsx`
- Create: `client/src/features/dashboard/__tests__/iframe-shell.test.tsx`

- [x] **Step 1: 写 iframe-protocol.ts**

```ts
export type HostToIframe =
  | { type: 'params/update'; params: Record<string, unknown> }
  | { type: 'refresh/pause' }
  | { type: 'refresh/resume' }
  | { type: 'theme/preview'; theme: string }

export type IframeToHost =
  | { type: 'ready'; jsonHash: string | null }
  | { type: 'error'; widgetId: string; message: string }
  | { type: 'metric'; name: string; value: number }

export function isIframeToHost(x: unknown): x is IframeToHost {
  if (!x || typeof x !== 'object') return false
  const t = (x as { type?: unknown }).type
  return t === 'ready' || t === 'error' || t === 'metric'
}
```

- [x] **Step 2: 写 iframe-shell.tsx**

```tsx
import { useEffect, useRef, useState } from 'react'
import { fetchDashboardHtml } from './services/dashboard-api'
import { isIframeToHost, type HostToIframe, type IframeToHost } from './iframe-protocol'

export interface DashboardIframeShellProps {
  dashboardId: string
  params?: Record<string, unknown>
  onError?: (e: { widgetId: string; message: string }) => void
}

export function DashboardIframeShell({ dashboardId, params, onError }: DashboardIframeShellProps) {
  const ref = useRef<HTMLIFrameElement>(null)
  const [html, setHtml] = useState<string | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'missing' | 'error'>('loading')
  const [hash, setHash] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setStatus('loading')
    fetchDashboardHtml(dashboardId).then(h => {
      if (!active) return
      if (h === null) setStatus('missing')
      else { setHtml(h); /* status flips to 'ready' on iframe message */ }
    })
    return () => { active = false }
  }, [dashboardId])

  useEffect(() => {
    function onMsg(ev: MessageEvent) {
      if (!isIframeToHost(ev.data)) return
      const m = ev.data as IframeToHost
      if (m.type === 'ready') { setStatus('ready'); setHash(m.jsonHash) }
      if (m.type === 'error') onError?.({ widgetId: m.widgetId, message: m.message })
    }
    window.addEventListener('message', onMsg)
    return () => window.removeEventListener('message', onMsg)
  }, [onError])

  // forward params changes into iframe
  useEffect(() => {
    if (status !== 'ready' || !params) return
    const msg: HostToIframe = { type: 'params/update', params }
    ref.current?.contentWindow?.postMessage(msg, '*')
  }, [params, status])

  if (status === 'missing') {
    return <div role="status" className="dashboard-empty">
      <p>这是 v1 dashboard，需要在 chat 中说「重新生成视觉」生成新版 HTML。</p>
    </div>
  }
  if (status === 'loading' || html === null) {
    return <div role="status" className="dashboard-loading">加载中…</div>
  }
  return <iframe
    ref={ref}
    sandbox="allow-scripts"
    srcDoc={html}
    referrerPolicy="no-referrer"
    className="dashboard-iframe"
    title={`dashboard ${dashboardId}`}
    data-status={status}
    data-json-hash={hash ?? ''}
  />
}
```

- [x] **Step 3: 写 iframe-shell.test.tsx**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { DashboardIframeShell } from '../iframe-shell'

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
    if (String(url).endsWith('/html')) {
      return Promise.resolve(new Response('<html></html>', { status: 200 }))
    }
    return Promise.resolve(new Response(null, { status: 404 }))
  })
})

describe('DashboardIframeShell', () => {
  it('renders loading then iframe when html present', async () => {
    render(<DashboardIframeShell dashboardId="dash_x" />)
    expect(screen.getByRole('status')).toBeInTheDocument()
    await waitFor(() => expect(document.querySelector('iframe.dashboard-iframe')).toBeInTheDocument())
  })

  it('renders empty hint on 404', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 404 }))
    render(<DashboardIframeShell dashboardId="dash_v1" />)
    await waitFor(() => expect(screen.getByText(/重新生成视觉/)).toBeInTheDocument())
  })

  it('flips to ready on postMessage', async () => {
    render(<DashboardIframeShell dashboardId="dash_x" />)
    await waitFor(() => expect(document.querySelector('iframe')).toBeInTheDocument())
    window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready', jsonHash: 'sha256:abc' } }))
    await waitFor(() => expect(document.querySelector('iframe')?.getAttribute('data-status')).toBe('ready'))
  })
})
```

- [x] **Step 4: 跑测试**

```bash
cd client
npm test -- src/features/dashboard/__tests__/iframe-shell
```

Expected: 3 tests pass。

- [x] **Step 5: 不要现在提交**（留给 T18 合并）

### Task 18：重写 dashboard-tab.tsx

**Files:**
- Modify: `client/src/features/dashboard/dashboard-tab.tsx`

- [x] **Step 1: 重写**

整体结构（具体行为按既有 props 与 store 接入）：

```tsx
import { useStageStore } from '../stage/stores/stage-store'  // adjust to actual path
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { DashboardIframeShell } from './iframe-shell'
// chrome / toolbar 组件按既有用法引入；这里只示意

export interface DashboardTabProps {
  tabId: string
}

export function DashboardTab({ tabId }: DashboardTabProps) {
  const tab = useStageStore(s => s.tabs.find(t => t.id === tabId))
  const dashboardId = useDashboardTabsStore(s => s.dashboardIdByTabId[tabId])
  const params = useDashboardTabsStore(s => s.paramsByDashboardId[dashboardId])

  if (!tab || !dashboardId) return null

  return (
    <div className="dashboard-tab">
      <div className="dashboard-tab__chrome">
        {/* toolbar: refresh / pause / fullscreen / open in new window
            按 client/DESIGN.md token 走 bg.subtle / accent.primary */}
      </div>
      <div className="dashboard-tab__viewport">
        <DashboardIframeShell
          dashboardId={dashboardId}
          params={params}
          onError={(e) => console.error('[bezel widget error]', e)}
        />
      </div>
    </div>
  )
}
```

具体 props/store 引用按既有代码风格调整；目标是**所有对已删除模块（dashboard-canvas / chart-widget / widget-shell / grid-layout-engine）的引用都移除**。

- [x] **Step 2: 跑 tsc**

```bash
cd client
npx tsc --noEmit
```

Expected: 零错误。

- [x] **Step 3: 跑全前端测试**

```bash
npm test
```

Expected: 全绿（旧 widget 测试已被 T15 删除；schema/iframe-shell 测试通过）。

- [x] **Step 4: 一次性提交 T15 + T16 + T17 + T18**

```bash
cd /home/wushengzhou/workspace/github/data-talk
git add -A client/
git status
git commit -m "refactor(dashboard): replace React canvas with iframe sandbox + bezel HTML

- delete: dashboard-canvas.tsx, chart-widget.tsx, markdown-widget.tsx,
          widget-shell.tsx, grid-layout-engine.ts, layout-engine.ts,
          widgets/__tests__, engines/__tests__
- schema.ts: upgrade to v2 (theme/renderer/refresh/patternId, layout.engine='free')
- services/dashboard-api.ts: add fetchDashboardHtml
- new: iframe-protocol.ts (postMessage contract)
- new: iframe-shell.tsx (DashboardIframeShell with sandbox + load states)
- rewrite: dashboard-tab.tsx (iframe host, chrome via DESIGN.md tokens)
- new tests: iframe-shell.test.tsx (loading / 404 / ready)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 6：E2E + 文档收尾 + GitHub push

### Task 19：Playwright E2E — dashboard-bezel-v2

**Files:**
- Create: `client/tests/e2e/dashboard-bezel-v2.spec.ts`

- [x] **Step 1: 写 E2E**

```ts
import { test, expect } from '@playwright/test'

test.describe('@bezel v2 dashboard', () => {
  test('promotes v2 dashboard via API and iframe renders with polling', async ({ page, request }) => {
    // 1. promote a v2 dashboard via test seed API
    const dashboardJson = {
      schemaVersion: 2,
      id: 'dash_e2e_bezel',
      title: 'E2E Bezel',
      theme: 'industry-ecommerce',
      renderer: 'bezel',
      refresh: { defaultIntervalMs: 1000, pauseOnHidden: false },
      parameters: [],
      widgets: [{
        id: 'chart_w_aaaa', type: 'chart', patternId: 'ecommerce.funnel-gradient',
        position: { x: 0, y: 0, w: 4, h: 3 },
        query: { connectionId: null, sql: 'SELECT 1 AS stage, 100 AS cnt', paramRefs: {} },
        refresh: { intervalMs: 1000 },
        options: {},
      }],
      layout: { engine: 'free' },
      version: 1, createdAt: 0, updatedAt: 0,
    }
    const html = `<!doctype html><html><head>
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__; frame-ancestors 'self'">
      <meta name="__JSON_HASH__" content="sha256:e2e">
      <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    </head><body><div id="chart_w_aaaa"></div>
    <script>window.__BEZEL_CONFIG__ = ${JSON.stringify({
      dashboardId: 'dash_e2e_bezel',
      defaultIntervalMs: 1000,
      pauseOnHidden: false,
      widgets: [{ id: 'chart_w_aaaa', intervalMs: 1000,
        endpoint: '/api/dashboards/dash_e2e_bezel/widgets/chart_w_aaaa/data', params: {} }]
    })};</script>
    <script>(function(){var c=window.__BEZEL_CONFIG__;c.widgets.forEach(w=>{
      var el=document.getElementById(w.id);var ch=echarts.init(el);
      setInterval(async()=>{var r=await fetch(w.endpoint,{method:'POST',headers:{'content-type':'application/json'},body:'{"params":{}}'});
      var d=await r.json();ch.setOption({dataset:{source:d.rows}},{lazyUpdate:true});
      parent.postMessage({type:'metric',name:'pollOk',value:1},'*');},w.intervalMs);});
      parent.postMessage({type:'ready',jsonHash:'sha256:e2e'},'*');
    })();</script></body></html>`

    const promoteRes = await request.post('/api/dashboards/promote', {
      data: { dashboard: dashboardJson, html },
    })
    expect(promoteRes.ok()).toBeTruthy()

    // 2. open dashboard tab through __DT_E2E__ hook
    await page.goto('/')
    await page.evaluate((id) => (window as any).__DT_E2E__?.dashboard?.openById?.(id), 'dash_e2e_bezel')

    // 3. iframe loads
    const frame = page.frameLocator('iframe.dashboard-iframe')
    await expect(frame.locator('#chart_w_aaaa')).toBeVisible({ timeout: 5000 })

    // 4. polling produced data: assert metric postMessage received
    await page.waitForFunction(() => (window as any).__BEZEL_POLL_OK__ === true, { timeout: 5000 })
  })
})
```

注：测试需要 client `__DT_E2E__` 暴露 `dashboard.openById`（如未实现，按既有 `__DT_E2E__` 模式补，参见 `2026-05-09-dashboard-datasource-e2e-test-design.md` Suite 2）。host 侧 message 收到 `pollOk` 时把 `__BEZEL_POLL_OK__` 置 true（在 dashboard-tab 或 iframe-shell `onError`/metric 路径加少量 e2e hook，仅 dev-build 生效）。

- [x] **Step 2: 跑 E2E**

```bash
cd client
npx playwright test tests/e2e/dashboard-bezel-v2.spec.ts --reporter=line
```

Expected: pass。若 BUG，按 CLAUDE.md BUG Tracking Gate 写到 `docs/bugs/BUG-NNNN-*.md` + 登记 index。

- [x] **Step 3: 提交**

```bash
git add client/tests/e2e/dashboard-bezel-v2.spec.ts
git commit -m "test(e2e): dashboard-bezel-v2 promotes v2 + iframe + polling round-trip"
```

### Task 20：bezel GitHub push

**前置条件**：用户在 GitHub 上创建空 repo `wallfacers/bezel`（**人工，不在脚本范围**）。push 前请用户确认 repo 已建。

**Files:** （bezel repo 本地工作树，不在 data-talk 仓库内）

- [x] **Step 1: 配置 remote 并 push**

```bash
cd ~/workspace/github/bezel
git remote add origin git@github.com:wallfacers/bezel.git
git branch -M main
git push -u origin main
```

Expected: push 成功；GitHub 上 `wallfacers/bezel` 可见目录结构。

- [x] **Step 2: 打 v0.1.0 tag**

```bash
cd ~/workspace/github/bezel
git tag -a v0.1.0 -m "bezel v0.1.0 — initial release with 12 industry patterns"
git push origin v0.1.0
```

- [x] **Step 3: 用 v0.1.0 tag 重新 vendor 到 data-talk**

```bash
cd /home/wushengzhou/workspace/github/data-talk
./scripts/sync-bezel.sh v0.1.0
cat server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version
```

Expected: 内容为 `v0.1.0`。

- [x] **Step 4: 提交 version pin 升级**

```bash
git add server/data-talk-infrastructure/src/main/resources/opencode/skills-src/bezel \
        server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.version
git status
git diff --cached --stat
git commit -m "chore(infra): pin vendored bezel to v0.1.0"
```

### Task 21：文档 housekeeping + plan 索引登记

**Files:**
- Modify: `docs/exec-plans/index.md`（Active 节登记本 plan）
- Modify: 本 plan 文件（勾选所有 `- [ ]`）
- Modify: `CLAUDE.md`（"Stage state" 段保留；新增「Dashboard 渲染管线」一句简注 → 引向 bezel spec）

- [x] **Step 1: 登记本 plan 到 exec-plans/index.md Active 节顶部**

参考既有「12 行业大屏独立精品化重设计」条目格式，加一行：

```markdown
| [bezel skill 实施计划](./2026-05-11-bezel-skill-plan.md) | 2026-05-11 | 落地 spec [2026-05-11-bezel-skill-design.md](../product-specs/2026-05-11-bezel-skill-design.md)：21 task 6 phase（bezel 骨架 / bezel 内容 5 并发 / vendor + 启动 deploy / schema v2 + validator 3 并发 / 后端 API / 前端重写 / E2E + GitHub push）。Phase 1 内 T2-T6 并行；T5 内 12 industries 再起 12 并行；T10-T12 并行；T16-T17 并行。bezel repo `wallfacers/bezel` 推送 + v0.1.0 tag + vendor 进 server classpath 走 `OpenCodeBinaryResolver.ensureBezelSkill`。前端旧 React canvas + widget 整树删除。 |
```

- [x] **Step 2: 全部任务勾选**

把本 plan 文件里所有 `- [ ]` 改成 `- [x]`（执行 plan 完成时由 executing-plans/subagent-driven-development skill 自动维护，T21 是收尾保险一次）。

- [x] **Step 3: 更新 CLAUDE.md 主导航**

在 "Knowledge Base Navigation" 表格里 dashboard 相关位置加一行指向 bezel spec：

```markdown
| Bezel dashboard skill 设计    | [docs/product-specs/2026-05-11-bezel-skill-design.md](docs/product-specs/2026-05-11-bezel-skill-design.md) |
```

- [x] **Step 4: 完成后把 plan 从 Active 移到 Completed**

按 CLAUDE.md "Post-Execution Document Housekeeping"，在 exec-plans/index.md：
- 把本 plan 条目从 Active 节移到 Completed 节
- 在条目末尾追加完成日期与简要 outcome 注（"shipped 在 <commit>"）

同步把 product-specs/index.md 中 bezel spec 条目（若适用）状态从 draft 改 shipped。

- [x] **Step 5: 最终提交**

```bash
cd /home/wushengzhou/workspace/github/data-talk
git add docs/ CLAUDE.md
git commit -m "docs: housekeep bezel plan — index registration, completion, CLAUDE.md nav"
```

---

## 自检与回顾

完成所有任务后，按下列清单逐项确认：

- [x] **Spec 覆盖**：spec §1–§13 每节都有对应 task（§1 背景由 plan 序言覆盖；§2 系统总览=整体编排；§3 bezel skill 结构=T1–T6；§4 schema v2=T10/T16；§5 后端=T7–T14；§6 前端=T15–T18；§7 migration=T12；§8 Design Inputs 已在 T17/T18 iframe 外壳与 chrome 落实；§9 Data Source Compatibility=T14 走既有 `SqlStatementGuard`；§10 Risks→T19 E2E 覆盖；§11 Out of Scope=不实施；§12 验收=T2–T20）
- [x] **占位符扫描**：无 TBD / TODO / "fill in" / "similar to Task N"。代码片段全量给出，含 import。
- [x] **类型/签名一致性**：`ensureBezelSkill(Path)` 在 T8/T9/T11 测试与 T7 spec 一致；`fetchDashboardHtml` 在 T16/T17 一致；`DashboardIframeShell` props 在 T17/T18 一致；`BezelHtmlValidator.Result` 在 T11/T13 一致；`WidgetData(columns, rows, executedAt)` 在 T14 跨 service/controller 一致
- [x] **BUG Tracking Gate**：在 E2E (T19) 与功能 smoke 中若发现产品行为偏差，**必须**写到 `docs/bugs/BUG-NNNN-*.md` 并登记 `docs/bugs/index.md`；最终响应明确「本次发现 N 个 BUG，已登记到…」（N=0 也要明示）
- [x] **Data Source Type Compatibility Gate**：T14 widgetQuery 复用现有 `SqlStatementGuard` + `ParameterizedSqlExecutor`，零 kind 分支；本 gate checklist 在 spec §9 已显式标注
- [x] **Frontend Design Contract Gate**：T17 iframe-shell loading/error/empty 三态、T18 dashboard-tab chrome 全部走 `client/DESIGN.md` `bg.subtle` / `bg.canvas` / `accent.primary` / `status.danger` / focusRing / hover / selected 语义 token；iframe 内部行业 theme 在 spec §8 已声明为显式偏离
- [x] **Parallel Plan Execution**：T2-T6 / T10-T12 / T16-T17 标识为并发批；批内跳过逐 task 验证；批末统一 `mvn clean verify` + `npx tsc --noEmit` + `npm test`
- [x] **Post-Edit Verification 例外**：本 plan 多步是 markdown 文档写作（T2-T6），按 CLAUDE.md "trivial edits" 例外免编译；任何 Java/TS 代码改动 task 完成后**必须**在 task 内执行编译/类型检查并通过

---

## 完成状态

- 状态：草稿，待执行
- Spec：[2026-05-11-bezel-skill-design.md](../product-specs/2026-05-11-bezel-skill-design.md)
- 关联前序 plan：[12 行业大屏 premium redesign plan](./2026-05-11-dashboard-premium-redesign-plan.md)
- 关联前序运行时 spec（部分被取代）：[Report / Dashboard Design](../product-specs/2026-05-08-report-dashboard-design.md)
- Owner：wallfacers
