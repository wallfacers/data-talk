---
title: bezel — Premium Industrial Dashboard Skill 设计
date: 2026-05-11
status: draft
owners: [wallfacers]
related-spec: 2026-05-11-dashboard-premium-redesign-design.md
related-plan: 2026-05-11-dashboard-premium-redesign-plan.md
supersedes-runtime: 2026-05-08-report-dashboard-design.md (React canvas rendering only)
---

# bezel — Premium Industrial Dashboard Skill 设计

> JSON → HTML → iframe 的 dashboard 渲染管线，外置为一个独立 GitHub repo（`wallfacers/bezel`）作为 AI 看的「设计语言 + 行业 pattern + 数据契约」参考。data-talk 通过 server classpath 资源在启动期自动解压到 `.opencode/skills/bezel/`。

## 0. TL;DR

1. 新建独立 repo `wallfacers/bezel`，沿用 [letterpress](https://github.com/wallfacers/letterpress) 形态（`skills/bezel/SKILL.md` 嵌套结构 + `.claude-plugin/marketplace.json`）。
2. 把 `tmp/Dashboard/` 下 12 张 premium HTML 沉淀为 bezel skill 的 12 份「行业 pattern」+ 设计语言文档 + JSON→HTML 编译契约。
3. data-talk 现有 React `dashboard-canvas` / `chart-widget` / `grid-layout-engine` **彻底下线**；新管线 = JSON（source-of-truth）→ AI 一次性产出 HTML artifact → 后端落盘到 session 资源盘 → 前端 iframe sandbox 渲染 → HTML 内置 polling 周期拉数据更新 ECharts。
4. dashboard schema 升 v2：新增 `theme` / `patternId` / `refresh` / `renderer` 字段；`layout.engine` 从 `'grid'` 改为 `'free'`。
5. bezel 通过 **server classpath 资源 + 启动期解压**模式集成（与 `OpenCodeBinaryResolver.ensureNodeModules()` 同形态），构建期一次性 vendor 进 `server/data-talk-infrastructure/src/main/resources/opencode/skills/bezel.tar.gz`，启动期 `ensureBezelSkill(projectRoot)` 解压到 `data-talk/.opencode/skills/bezel/`。
6. OpenCode 子进程 cwd = data-talk 仓库根，按 OpenCode 优先级规则自动读 `.opencode/skills/bezel/SKILL.md`。

## 1. 背景与目标

### 1.1 触发场景

`2026-05-11-dashboard-premium-redesign-plan` 已经在 `tmp/Dashboard/` 下产出 12 张行业级 premium HTML（电商 / 制造 / SaaS / 金融 / 物流 / 医疗 / HR / 能源 / 网安 / 农业 / 教育 / 多屏综合）。这批 HTML 是**设计范本**，不是运行时产物——直接 copy-paste 不可持续；想让 chat 里 AI 现场生成同等质感的 dashboard，必须把范本背后的「设计语言 + 行业气质 + ECharts 配方 + 数据契约」沉淀成 AI 可消费的指令集。

同期看，现有 dashboard 模块（基于 `2026-05-08-report-dashboard-design`）的 React canvas + recharts/echarts widget 树**无法忠实复现**这批 HTML 的视觉自由度（粒子背景、玻璃拟态、自由 CSS Grid、行业特征化布局），且 widget 与 layout 强耦合的 12 列网格无法承载行业级大屏的非对称构图。

### 1.2 目标

- **产物 1**：独立 GitHub repo `wallfacers/bezel`，结构对照 `wallfacers/letterpress`，可 `/plugin marketplace add wallfacers/bezel` 装到 Claude Code，也可被 OpenCode 项目级 skills 目录读到。
- **产物 2**：把 12 张 HTML 抽象成 12 份「行业 pattern 详解」+ 1 份「设计语言」+ 1 份「JSON→HTML 编译契约」+ 1 份「数据接口契约」+ 1 份顶层 `SKILL.md`。
- **产物 3**：data-talk dashboard schema 升 v2；后端新增 `GET /api/dashboards/{id}/html` 与 `POST /api/dashboards/{id}/widgets/{wid}/data` 两个 endpoint；前端 `dashboard-tab.tsx` 重写为 iframe sandbox 宿主；旧 React canvas + widget 代码删除。
- **产物 4**：server build 期 vendor bezel 内容到 classpath；server 启动期把 bezel 解压到 `data-talk/.opencode/skills/bezel/`；OpenCode 子进程启动后自动读到。

### 1.3 非目标

- 不替换 chat 内的 chart fence 渲染（chart fence 仍走现有 `chart-widget` 之外的轻量渲染器）。
- 不引入实时推送（SSE/WebSocket）。dashboard 内的「实时」由 widget 内置 polling 提供（最小间隔 1000 ms，默认 10000 ms）。
- 不实现多用户协同（CRDT）。
- 不支持手编 HTML 直接保存——HTML 是 derived artifact，所有变更必须经过 JSON。
- 不引入新的 widget kind 到 `FileArtifactKind` 枚举（仍复用现有 `'dashboard'` kind）。

## 2. 系统总览

### 2.1 角色边界

```
┌────────────────────────────────────────────────────────────────────┐
│ bezel repo (wallfacers/bezel)                                      │
│   定位：AI 看的「设计语言 + 行业 pattern + 数据契约」参考          │
│   非运行时依赖：data-talk 进程不 link bezel；只在 build 时 vendor，│
│                  启动时把内容解压到磁盘供 OpenCode 子进程读        │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ data-talk server                                                    │
│   - vendor bezel/skills/bezel/ → resources/opencode/skills/bezel.tar.gz │
│   - OpenCodeBinaryResolver.ensureBezelSkill() 启动期解压            │
│   - Spring DashboardController 新增 HTML serve / widget data endpoint│
│   - DashboardArtifactService 接 v2 schema + 双 artifact promote     │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ data-talk client                                                    │
│   - dashboard-tab.tsx 重写为 iframe sandbox 宿主                    │
│   - 删除 dashboard-canvas / chart-widget / grid-layout-engine       │
│   - 新加 iframe-shell.tsx + iframe-protocol.ts                      │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ OpenCode 子进程（cwd = data-talk repo root）                       │
│   - 按优先级规则读 .opencode/skills/bezel/SKILL.md                  │
│   - AI 在 chat 中被请求生成 dashboard 时触发 bezel                  │
│   - 产出 dashboard.json + dashboard.html 两份 artifact              │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 端到端数据流

```
[1] 用户在 chat：「做个电商运营实时监控大屏，关联 mysql-prod」

[2] AI（OpenCode agent）触发 bezel skill
       读 patterns-catalog.md → 选 industries/02-ecommerce.md
       读 data-contract.md → 知道 widget→endpoint 映射
       读 design-language.md → 知道用什么色/字/间距 token

[3] AI 产出两份 artifact（同一逻辑步骤）
       (a) dashboard.json：schemaVersion 2，含 patternId / refresh / theme
       (b) dashboard.html：自包含；ECharts CDN；window.__BEZEL_CONFIG__；
           polling 调度器；不写死数据，初始 dataset 为空

[4] AI 调 promote action（既有 DashboardAction.promote 扩展）
       后端 DashboardArtifactService.promote(v2 json, html)
         - JSON  → dashboards 表 + version+1（source-of-truth）
         - HTML  → file_artifact (kind='dashboard', external=1, atomic write)
         - 两者通过 dashboardId + version 强绑定

[5] 用户在 Files Library 或 chat suggestion 中点开 dashboard tab
       client DashboardTab → fetchDashboardHtml(id)
         GET /api/dashboards/{id}/html → text/html 字符串
       <DashboardIframeShell> 把 HTML 注入 <iframe sandbox="allow-scripts">

[6] iframe 内 boot 脚本
       解析 window.__BEZEL_CONFIG__：每 widget 的 endpoint / intervalMs / mapping
       启动 polling 调度器（visibilitychange 暂停 / Page Visibility API）

[7] 周期 polling
       widget 按 refresh.intervalMs：
         POST /api/dashboards/{id}/widgets/{wid}/data
           body: { params: { ... } }
         后端用 widgetQuery.sql + widgetQuery.connectionId 跑真 SQL
         返回 { columns, rows, executedAt }
       iframe 内：chart.setOption({ dataset: { source: rows } }, { lazyUpdate: true })
       整页 DOM 不重建。只更新数据点。

[8] 用户/AI 编辑
       JSON 改 → AI 在 chat 中输出 ui_patch JSON Patch ops
       AI 重新产出 HTML（不再走 ui_patch 增量；HTML 整体重生成）
       后端 DashboardArtifactService.patch(baseVersion, ops) +
            DashboardArtifactService.replaceHtml(id, newHtmlBytes)
       version+1；旧 HTML artifact 保留（审计）
```

### 2.3 不变量

1. **JSON 是唯一 source-of-truth**。HTML 是 derived artifact，从 JSON 编译而来。
2. HTML 不可手编。任何外观变更必须改 JSON → AI 重生成 HTML。
3. **HTML 自包含，无 bezel 运行时依赖**。生成后 bezel 删了 HTML 仍能正常渲染。
4. **polling 只更新数据点**，不重建 DOM；视觉骨架在 HTML 初始化时一次性建好。
5. **JSON 与 HTML 通过 dashboardId + version 强绑定**；HTML head 内嵌 `__JSON_HASH__` meta，前端加载时比对，hash 不匹配触发 re-render 警告。

## 3. bezel skill 仓库结构

```
bezel/
├── .claude-plugin/
│   └── marketplace.json              # Claude Code marketplace 元信息
├── skills/
│   └── bezel/
│       ├── SKILL.md                  # 顶层：何时触发、产物形态、关键不变量
│       ├── references/
│       │   ├── design-language.md    # ~300 行：色 token / 字 / 间距 / 动效 / CSS 变量
│       │   ├── data-contract.md      # ~250 行：JSON schema v2、polling 协议、
│       │   │                         #          window.__BEZEL_CONFIG__、错误处理
│       │   ├── compile-rules.md      # ~200 行：JSON → HTML 拼装算法、必含元素清单、
│       │   │                         #          安全约束（无内联 on*、无外站资源除 ECharts CDN）
│       │   ├── patterns-catalog.md   # ~100 行：12 行业索引 + 通用 widget pattern 列表
│       │   └── industries/
│       │       ├── 01-multi-screen.md     综合大屏：left/right 列、漂浮 KPI 胶囊
│       │       ├── 02-ecommerce.md        电商：红黄/粒子/漏斗/瀑布榜单/地图热力
│       │       ├── 03-manufacturing.md    制造：蓝灰/进度环/SCADA 看板风
│       │       ├── 04-saas.md             SaaS：紫粉/留存矩阵/MRR 趋势
│       │       ├── 05-finance.md          金融：墨绿黑金/K 线/资金流向 sankey
│       │       ├── 06-logistics.md        物流：青/路径地图/时效圈
│       │       ├── 07-healthcare.md       医疗：白蓝/科室热力/患者画像
│       │       ├── 08-hr.md               HR：暖灰/组织树/招聘漏斗
│       │       ├── 09-energy.md           能源：橙黑/电网拓扑/负荷曲线
│       │       ├── 10-cybersecurity.md    网安：深绿黑/告警瀑布/IP 弧线
│       │       ├── 11-agriculture.md      农业：青褐/田块地图/物候曲线
│       │       └── 12-education.md        教育：橙青/学情雷达/进度条阵列
│       ├── assets/
│       │   └── templates/
│       │       └── 12 份原始 HTML（视觉范本展示用，AI 不直接 copy-paste；
│       │                         作为 industries/*.md 的「成品参考」附在仓库里
│       │                         便于人类设计师审美对齐）
│       └── scripts/
│           ├── validate.py           # 产出 HTML 合规性自检：
│           │                         #  - 含 window.__BEZEL_CONFIG__？
│           │                         #  - polling 调度器 boot 段？
│           │                         #  - 无内联 on* 属性？
│           │                         #  - 无外站资源（除白名单 CDN）？
│           │                         #  - jsonHash meta 存在？
│           └── preview.py            # 本地 JSON + mock data → HTML 渲染，开发调试用
├── README.md                         # letterpress 同形态：定位 / 安装 / 使用
└── LICENSE                           # Apache 2.0
```

### 3.1 每份 `industries/NN-xxx.md` 内部结构

```
# 02 Ecommerce — 电商运营实时监控

## 业务上下文
（这个行业用户最关心的 5-8 个 KPI 与典型分析维度）

## 视觉签名
- 主色 #ff4444 / 辅色 #ffc107 / 背景 #0d0a07
- 装饰元素：粒子背景、玻璃拟态、流光卡片
- 推荐 ECharts theme name

## 推荐布局骨架
- 顶栏 72px（logo + KPI 胶囊 + 时钟）
- 主体 38% / 62% 双列
- 底部 56px 滚动 ticker

## 典型 widget 配方（4-8 个）
### funnel-gradient
- 何时用：转化漏斗 (PV → 加购 → 下单 → 支付)
- ECharts option 模板片段
- SQL 输出列约定：stage VARCHAR, cnt BIGINT
- 推荐 refresh: 10000 ms

### gmv-marquee
（瀑布榜单滚动）
...

## 触发线索（AI 用）
- 用户提到「电商 / GMV / 转化 / 加购 / 客单价」→ 选此 industry
- 用户提到 mysql/pg 表名含 orders / sku / gmv / customer → 强信号
```

#### Pattern 选择优先级（patterns-catalog.md 内显式规则）

当用户用语同时命中多个 industry 时，AI 按以下顺序裁决，写到 `patterns-catalog.md` 顶部：

1. **业务名词权重 > 行业名词权重**：「下单转化」（业务名词，电商专属）> 「电商工厂」（行业名词，可能是制造业的电商客户）
2. **表/列名信号 > 自然语言信号**：connection 已选定且表名含 `gmv` / `orders` → 强信号优先
3. **多 industry 兼容时优先 multi-screen（01）**：用户明示「综合监控」「全景大屏」「指挥中心」→ 01-multi-screen；否则按业务名词优先级单选一个 industry，不混搭（混搭视觉会失控）
4. **不明确时反问**：若无明确信号，AI 必须在 chat 中先问「你想做哪个行业的大屏？」给出 3 个候选选项，不要默认猜测

### 3.2 顶层 SKILL.md（YAML frontmatter 草稿）

```yaml
---
name: bezel
description: |
  Premium industrial dashboards rendered from JSON. Use this skill whenever
  the user asks to build a dashboard, monitoring screen, KPI board, big-screen
  display, operations cockpit, or industry-specific data visualization — even
  when the word "dashboard" is not explicitly used. Produces a JSON
  description plus a self-contained HTML artifact with embedded polling, ready
  to be embedded as an iframe inside DataTalk.
---
```

description 字段要"略 pushy"，避免 AI undertrigger。后续走 skill-creator 的描述优化循环再调。

## 4. JSON Schema v2 扩展

### 4.1 新增字段

| 字段 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `schemaVersion` | 根 | `const 2` | 是 | 从 v1 升 v2 |
| `theme` | 根 | `string`（`industry-ecommerce` / `industry-finance` / ... / `industry-neutral`） | 是 | 驱动 HTML 渲染风格 |
| `renderer` | 根 | `const "bezel"` | 是 | 留作未来扩展点（未来若有其他 renderer 通过此字段路由） |
| `refresh` | 根 | `{ defaultIntervalMs, pauseOnHidden }` | **否**（整对象省略 = 走 `{ 10000, true }` 默认） | dashboard 级刷新策略容器 |
| `refresh.defaultIntervalMs` | 根 | `integer ≥ 1000` | 否（默认 10000） | dashboard 级默认刷新间隔 |
| `refresh.pauseOnHidden` | 根 | `boolean` | 否（默认 true） | 标签页隐藏时暂停 polling |
| `layout.engine` | 根 | `const "free"` | 是 | 从 v1 的 `'grid'` 改为 `'free'`；允许 CSS Grid 自由布局 |
| `layout.viewport` | 根 | `{ minWidth: int, aspect: string }` | 否 | 推荐最小宽度与宽高比 |
| `patternId` | widget | `string`（如 `ecommerce.funnel-gradient`） | 是 | bezel pattern 标识；AI 写 HTML 时按此 id 拼装对应骨架 |
| `refresh.intervalMs` | widget | `integer ≥ 1000` | 否（继承 dashboard 默认） | widget 级覆盖 |
| `refresh.strategy` | widget | `'data-only' \| 'full-rerender'` | 否（默认 `data-only`） | data-only = 只 setOption；full-rerender = 重建 widget DOM（少用） |

### 4.2 保留字段（v1 兼容）

- `id`、`title`、`description`、`defaultConnectionId`、`parameters`、`createdAt`、`updatedAt`、`version` 保留语义不变。
- `widgets[].id` 正则不变。
- `widgets[].query: { connectionId, sql, paramRefs }` 保留不变——这是 widget polling endpoint 的核心数据源。
- `widgets[].position: { x, y, w, h, z }` 保留，但语义放宽：在 `layout.engine='free'` 下 `(x,y,w,h)` 仅作为 AI 编排参考（实际几何由 HTML 内置 CSS Grid 直接决定），不再强制 12 列 grid 校验。

### 4.3 字段移除

- `widgets[].options.echartsOption` 不再要求嵌入完整 ECharts option（HTML 里自带）。保留为可选透传字段，便于 ui_patch 场景对单 widget 局部调整。
- `widgets[].options.dataMapping` 保留 `rowsAsDataset` 单字段，不扩展。
- `widget.type='chart' | 'kpi' | ...` 保留为弱类型 hint；真正的视觉由 `patternId` 决定。

### 4.4 zod schema 改动（client/src/features/dashboard/schema.ts）

新增：
```ts
const refreshPolicy = z.object({
  intervalMs: z.number().int().min(1000).optional(),
  strategy: z.enum(['data-only', 'full-rerender']).optional(),
})

const dashboardRefresh = z.object({
  defaultIntervalMs: z.number().int().min(1000).default(10000),
  pauseOnHidden: z.boolean().default(true),
})

const freeLayout = z.object({
  engine: z.literal('free'),
  viewport: z.object({
    minWidth: z.number().int().min(640),
    aspect: z.string().regex(/^\d+:\d+$/),
  }).optional(),
})

// widgetBase 扩展
const widgetBase = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9]{4,16}$/),
  patternId: z.string().regex(/^[a-z0-9-]+\.[a-z0-9-]+$/),  // 新增必填
  position: gridPosition,
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
  refresh: refreshPolicy.optional(),                          // 新增
})

// dashboardSchema 扩展
export const dashboardSchema = z.object({
  schemaVersion: z.literal(2),                                // 升 v2
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  theme: z.string().regex(/^industry-[a-z-]+$/),              // 新增必填
  renderer: z.literal('bezel'),                               // 新增必填
  refresh: dashboardRefresh.optional(),                        // 新增 optional：整对象省略 = 全默认
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: freeLayout,                                         // engine 从 'grid' 改为 'free'
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0),
  updatedAt: z.number().int().min(0),
})
```

后端 `dashboard-schema.json` 同步升 v2，CHECK constraint 改 `schemaVersion = 2`。

## 5. 后端改造（server/）

### 5.1 模块边界沿用现有架构

domain ← application ← infrastructure ← adapter 方向不变。

| 模块 | 改动 |
|---|---|
| `data-talk-domain` | `Dashboard` record 加 `theme: String`、`renderer: String`、`refresh: DashboardRefresh`；`Widget` record 加 `patternId: String`、`refresh: Optional<WidgetRefresh>`；schemaVersion sealed 从 v1 升 v2 |
| `data-talk-application` | `DashboardArtifactService` 接受 v2；`promote(v2 dashboard, html bytes)` 双 artifact 落盘；`replaceHtml(id, version, bytes)` 在 patch 后被 AI 调用同步新 HTML |
| `data-talk-application` | 新加 `WidgetDataService`：`fetchWidgetData(dashboardId, widgetId, paramValues) → ResultSet`，内部走现有 `ParameterizedSqlExecutor`，受 `SqlStatementGuard` L1 限制（与既有 dashboard 同口子） |
| `data-talk-adapter` | `DashboardController` 新增 `GET /api/dashboards/{id}/html` → `text/html;charset=UTF-8`；新增 `POST /api/dashboards/{id}/widgets/{wid}/data` → JSON `{ columns, rows, executedAt }` |
| `data-talk-infrastructure` | `OpenCodeBinaryResolver` 新增 `ensureBezelSkill(Path projectRoot)`；`OpenCodeProcessManager.start()` 在 binary deploy 后、子进程 spawn 前调一次 |
| 弃用 | 旧 `chart`/`markdown`/`image` widget 的服务端契约保留（v1 dashboard 仍能 fetch，但前端不再渲染；v2 dashboard 不经过 widget shape 检查） |

### 5.2 启动期 deploy 集成（关键章节）

新增常量与方法（参照 `ensureNodeModules` 同构形态）：

```java
// OpenCodeBinaryResolver.java
static final String BEZEL_RESOURCE = "opencode/skills/bezel.tar.gz";
static final String BEZEL_MARKER   = ".bezel-installed";

public void ensureBezelSkill(Path projectRoot) {
    Path opencodeDir = projectRoot.resolve(".opencode");
    Path skillDir    = opencodeDir.resolve("skills").resolve("bezel");
    Path marker      = opencodeDir.resolve(BEZEL_MARKER);

    String embeddedVersion = readEmbeddedBezelVersion();   // 读 classpath:opencode/skills/bezel.version
    if (Files.exists(marker) && Files.isDirectory(skillDir)) {
        try {
            if (embeddedVersion.equals(Files.readString(marker).trim())) {
                return;  // 已是最新版，幂等返回
            }
        } catch (IOException ignored) { /* fall through to reinstall */ }
        deleteRecursively(skillDir);
    }

    try (InputStream in = getClass().getClassLoader().getResourceAsStream(BEZEL_RESOURCE)) {
        if (in == null) {
            log.info("No bundled bezel skill on classpath, skipping extraction");
            return;
        }
        Files.createDirectories(skillDir);
        extractDepsTarGz(in, skillDir);
        Files.createDirectories(opencodeDir);
        Files.writeString(marker, embeddedVersion);
        log.info("Extracted bundled bezel skill to {}", skillDir);
    } catch (Exception e) {
        log.warn("Failed to extract bezel skill: {}", e.getMessage());
    }
}
```

`OpenCodeProcessManager.start()` 接入点：

```java
// 现有顺序：
//   binaryResolver.extractFromClasspath(homeDir)  → 部署 opencode 二进制
//   binaryResolver.ensureNodeModules()             → 部署 npm deps
// 新增：
binaryResolver.ensureBezelSkill(Paths.get(""));   // cwd = data-talk repo root
// 然后才 spawn opencode 子进程
```

`Paths.get("")` 在 Spring Boot 进程的 cwd 解析为绝对路径——与现有 OpenCodeProcessManager `pb.directory(...)` 设定保持一致。

### 5.3 构建期 vendor

`server/data-talk-infrastructure/pom.xml` 新增 maven-assembly-plugin execution：

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

`src/assembly/bezel.xml` 描述：

```xml
<assembly>
  <id>bezel</id>
  <formats><format>tar.gz</format></formats>
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
      </excludes>
    </fileSet>
  </fileSets>
</assembly>
```

`skills-src/bezel/` = bezel 仓库的本地 vendored copy（通过 `scripts/sync-bezel.sh` 维护，不通过 git submodule，避免 build agent 拉远程依赖）。同时写一份纯文本 `src/main/resources/opencode/skills/bezel.version` 表明嵌入版本（git tag 名），供 `ensureBezelSkill` 比对幂等。

### 5.4 同步脚本（`scripts/sync-bezel.sh`）

```bash
#!/usr/bin/env bash
set -euo pipefail
BEZEL_TAG="${1:?usage: sync-bezel.sh <tag>}"
SERVER_RES="server/data-talk-infrastructure/src/main/resources/opencode"
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT
git clone --depth 1 --branch "$BEZEL_TAG" https://github.com/wallfacers/bezel "$TMP/bezel"
rm -rf "$SERVER_RES/skills-src/bezel"
mkdir -p "$SERVER_RES/skills-src/bezel"
cp -r "$TMP/bezel/." "$SERVER_RES/skills-src/bezel/"
echo "$BEZEL_TAG" > "$SERVER_RES/skills/bezel.version"
git add "$SERVER_RES/skills-src/bezel" "$SERVER_RES/skills/bezel.version"
echo "synced bezel@$BEZEL_TAG into $SERVER_RES"
```

### 5.5 .gitignore 增列

```
# bezel runtime install
/.opencode/skills/bezel/
/.opencode/.bezel-installed
```

`.opencode/skills/bezel/` 是启动期产物，不入 git。`skills-src/bezel/` 是 vendored 源，入 git。

### 5.6 测试矩阵

| 测试 | 文件 | 验证 |
|---|---|---|
| `OpenCodeBinaryResolverBezelTest` | infrastructure/test | 临时目录 + classpath mock 资源 → 验证解压、marker、幂等、版本升级覆盖、缺失资源时 noop |
| `DashboardHtmlServeControllerIT` | adapter/test | promote v2 dashboard + html bytes → GET /html 返回正确 content-type + content-length |
| `WidgetDataControllerIT` | adapter/test | mock connection + widgetQuery → POST /widgets/{wid}/data 返回 JSON `{ columns, rows, executedAt }`；权限校验；param 注入安全 |
| `DashboardArtifactServiceV2Test` | application/test | v2 schema 验证、双 artifact 同事务落盘、version drift 防御 |
| `DashboardSchemaV2MigrationTest` | application/test | v1 dashboard 走 migration → v2 + theme=industry-neutral + patternId=generic.* |

## 6. 前端改造（client/）

### 6.1 删除清单

```
client/src/features/dashboard/
  dashboard-canvas.tsx           ← 删
  engines/grid-layout-engine.ts  ← 删
  engines/layout-engine.ts       ← 删
  engines/__tests__/             ← 删
  widgets/chart-widget.tsx       ← 删
  widgets/markdown-widget.tsx    ← 删
  widgets/widget-shell.tsx       ← 删
  widgets/__tests__/             ← 删
```

`stores/dashboard-tabs-store.ts` 保留（仍管理 dashboardId / activeTabId）。
`adapters/DashboardAdapter.ts` 保留（与 OpenCode 协议契合层，不依赖渲染树）。

### 6.2 新增清单

```
client/src/features/dashboard/
  iframe-shell.tsx               ← 新加：iframe sandbox 宿主组件
  iframe-protocol.ts             ← 新加：iframe ↔ host postMessage 协议契约
  services/dashboard-api.ts      ← 改：新加 fetchDashboardHtml(id)
  schema.ts                      ← 改：升 v2（见 §4.4）
  dashboard-tab.tsx              ← 重写：以 <DashboardIframeShell dashboardId> 为主体
```

### 6.3 iframe sandbox 配置

```tsx
<iframe
  sandbox="allow-scripts"
  srcDoc={html}
  referrerPolicy="no-referrer"
  className="dashboard-iframe"
  onLoad={handleLoad}
  onError={handleError}
/>
```

`sandbox="allow-scripts"`（不含 `allow-same-origin`）= iframe 内 JS 可执行，`document.cookie` / localStorage 不可读 host 域，iframe.contentWindow.origin = `"null"`。

#### CSP 落地——必须走 `<meta>` 而非响应头

**关键约束**：srcDoc iframe 的 CSP **不**继承 `GET /api/dashboards/{id}/html` 的响应头。srcDoc 内容继承的是父页面（Tauri webview）的 CSP，叠加 iframe 自己 HTML 内的 `<meta http-equiv="Content-Security-Policy">` 标签。所以 CSP 必须**内嵌**到 HTML 里。

AI 产出的 dashboard.html 顶部**必须**含：

```html
<!doctype html>
<html>
<head>
  <meta http-equiv="Content-Security-Policy" content="
    default-src 'none';
    script-src 'unsafe-inline' https://cdn.jsdelivr.net;
    style-src 'unsafe-inline';
    img-src data: https:;
    connect-src __BEZEL_SERVER_ORIGIN__;
    frame-ancestors 'self';
  ">
  <meta name="__JSON_HASH__" content="sha256:...">
  ...
</head>
```

**`__BEZEL_SERVER_ORIGIN__` 占位符**：因为 sandbox iframe 的 origin 是 `"null"`，`connect-src 'self'` 解析为不能调任何 URL，所以 `connect-src` 必须用**显式 server origin**。bezel 不知道部署环境的 origin，所以 HTML 内写占位符，**后端在 `GET /api/dashboards/{id}/html` 返回前做一次字符串替换**：

```java
// DashboardController#serveHtml
String origin = "http://" + req.getServerName() + ":" + req.getServerPort();
String rendered = htmlBytes.replace("__BEZEL_SERVER_ORIGIN__", origin);
return ResponseEntity.ok().contentType(TEXT_HTML).body(rendered);
```

iframe 内 fetch 从 origin=null 出发，调用绝对 URL（`http://127.0.0.1:8080/api/...`）时浏览器按 `connect-src` 白名单检查——白名单写死成具体 origin 即匹配成功。

后端同时为这条 endpoint **设置 CORS** 允许 `Origin: null` 的请求：

```java
@CrossOrigin(origins = "null", allowCredentials = "false")
public ResponseEntity<...> fetchWidgetData(...) { ... }
```

不允许 credentials（cookie），与 sandbox 无 same-origin 协同。

`'unsafe-inline'` 在 script-src 必需——polling 调度器与 ECharts 初始化都是 inline script。这是当前妥协；后续若想强化，需把 polling 调度器抽成 hash-allowed inline 或外联到 data-talk 服务静态资源（增加复杂度，本 spec 不涉及）。

#### Validator 强制项

`scripts/validate.py`（bezel 内）与 server-side Java validator（`DashboardArtifactService.promote` 前置）**都必须检查**：

1. HTML 含 `<meta http-equiv="Content-Security-Policy">`
2. CSP content 含 `__BEZEL_SERVER_ORIGIN__` 占位符（防止 AI 误写死 origin）
3. CSP content 含 `frame-ancestors 'self'`
4. CSP content 不含 `unsafe-eval`、不含 `*` 通配（除允许的 `data:` / `https:` for img）
5. HTML 含 `<meta name="__JSON_HASH__">`
6. HTML 不含任何内联 `on*` 属性（`onclick` / `onload` 等）
7. HTML 中 `<script src=...>` 的 src 仅允许白名单 CDN（`https://cdn.jsdelivr.net/`）
8. HTML 中所有 `fetch(...)` / `XMLHttpRequest` 调用的 URL 形如 `/api/dashboards/${BEZEL.dashboardId}/widgets/${id}/data`（启发式 grep；不严格 AST 解析）

### 6.4 iframe ↔ host postMessage 协议

```ts
// iframe-protocol.ts
export type HostToIframe =
  | { type: 'params/update'; params: Record<string, unknown> }
  | { type: 'refresh/pause' }
  | { type: 'refresh/resume' }
  | { type: 'theme/preview'; theme: string }

export type IframeToHost =
  | { type: 'ready'; jsonHash: string }
  | { type: 'error'; widgetId: string; message: string }
  | { type: 'metric'; name: string; value: number }   // for telemetry
```

host 监听 `message` 事件做白名单源校验（`event.origin === 'null'` 因为 srcDoc iframe 没 origin）。

### 6.5 与 `client/DESIGN.md` 的关系

iframe **内部**视觉不沿用 data-talk 的 cobalt/neutral semantic tokens——bezel 行业 theme 自包含。

iframe **外壳**（dashboard-tab 的 toolbar、参数面板、错误态 banner、loading）仍走 client/DESIGN.md 的语义 token：
- `bg.subtle` / `bg.canvas` 分别用于 chrome / iframe 容器
- `accent.primary` 用于刷新按钮 active 态
- `status.danger` 用于 polling error banner
- focusRing / hover / selected 按 DESIGN.md 全套五态映射

## 7. v1 → v2 Migration

### 7.1 范围

现存生产 dashboard 数据库行均为 v1（`schemaVersion=1`，`layout.engine='grid'`，无 `theme/patternId/refresh`）。

### 7.2 策略

不写 Flyway DB migration，dashboard 行 JSON 已存在 `dashboards.json_blob`。在 application 层 `DashboardArtifactService.load(id)` 内做**lazy migration**：

```java
public Dashboard load(String id) {
    String raw = repository.findRawJson(id);
    JsonNode tree = mapper.readTree(raw);
    int version = tree.path("schemaVersion").asInt();
    if (version == 1) {
        tree = migrateV1ToV2(tree);
        // 不立即写回；下次 promote/patch 时随新 version 一起入库
    }
    return mapper.treeToValue(tree, Dashboard.class);
}

private JsonNode migrateV1ToV2(JsonNode v1) {
    ObjectNode v2 = v1.deepCopy();
    v2.put("schemaVersion", 2);
    v2.put("renderer", "bezel");
    v2.put("theme", "industry-neutral");
    v2.set("refresh", mapper.createObjectNode()
        .put("defaultIntervalMs", 10000)
        .put("pauseOnHidden", true));
    ((ObjectNode) v2.get("layout")).put("engine", "free");
    for (JsonNode w : v2.withArray("widgets")) {
        ObjectNode wo = (ObjectNode) w;
        if (!wo.has("patternId")) {
            wo.put("patternId", inferPatternFromType(wo.path("type").asText()));
        }
    }
    return v2;
}

private String inferPatternFromType(String type) {
    return switch (type) {
        case "chart"     -> "generic.echarts-card";
        case "kpi"       -> "generic.kpi-tile";
        case "table"     -> "generic.table";
        case "markdown"  -> "generic.markdown";
        case "filter"    -> "generic.filter-bar";
        case "section"   -> "generic.section-header";
        case "divider"   -> "generic.divider";
        case "image"     -> "generic.image";
        default          -> "generic.echarts-card";
    };
}
```

### 7.3 HTML artifact backfill

v1 dashboard 没有 HTML artifact。首次打开 v2-migrated dashboard 时，前端 `fetchDashboardHtml(id)` 收到 404，触发 fallback：弹 modal「这是 v1 dashboard，需要重新生成视觉」，引导用户走 chat 中「重生成 dashboard」flow（让 AI 根据 v2 JSON 输出新 HTML）。

不做后端自动重生成——保留人工确认环节，避免 AI 误改视觉。

### 7.4 回滚

不支持 v2 → v1 回滚。如需回滚 bezel 上线，前端 dashboard-tab 走 feature flag 切回旧 React canvas（feature flag 在前端配置，后端 schema 一旦 v2 化无法回退）。

## 8. Design Inputs（Frontend Design Contract Gate）

本设计已读 [client/DESIGN.md](../../client/DESIGN.md)。本设计中**遵循**的约束：

- **Dual-Core, One System**：dashboard tab 外壳与 chat/sidebar 共享同一 token 系统；iframe 内部是独立视觉子系统，但 toolbar/chrome 仍贯穿 client/DESIGN.md 语义。
- **Stage 状态全局**：dashboard tab 沿用现有 `StageTab` 模型，instance 上无 `scope`，类型级 scope 在 `tab-type-registry.ts`。本设计不破坏该不变量。
- **Neutral Backbone, Focused Signal**：iframe 外壳所有 chrome 元素只用 `bg.subtle` / `bg.canvas` / `accent.primary`，不直接引用 primitive color。
- **Motion as Confirmation**：iframe 外壳 loading / error 切换走 client/DESIGN.md `motion.normal` 与 `easing.standard`；iframe 内部动效是 bezel 行业风格自带（粒子、玻璃拟态等），不受 client/DESIGN.md 约束。
- **Accessibility**：iframe 外壳所有交互满足 4.5:1 文字对比 / 3:1 大文字对比 / 可见 focusRing；iframe 内部 a11y 由 bezel skill 在 compile-rules.md 中自行规范（独立 a11y 子节）。

本设计中**显式偏离** client/DESIGN.md 的部分：

- iframe **内部**视觉**不**使用 cobalt/neutral 语义 token。理由：12 行业大屏的视觉差异是 bezel 的核心价值，强行套 cobalt/neutral 等于丢弃 bezel 全部价值。该偏离限制在 iframe srcDoc 内部，DOM 边界清晰，不污染 host。

## 9. Data Source Type Compatibility Gate

本设计涉及 dashboard 数据源访问，按 [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 走 checklist：

| Checklist 项 | 适用 | 说明 |
|---|---|---|
| 前端连接 UI | N/A | 不新增 connection 类型 |
| 后端 JDBC 连接处理 | N/A | 不新增 JDBC provider |
| Schema discovery | N/A | 不变 |
| SQL 执行 | **是** | `WidgetDataService` 走现有 `ParameterizedSqlExecutor` + `SqlStatementGuard` L1，所有 connection kind（MySQL/PG/SQLite/H2/ClickHouse/Oracle/SQLServer/DuckDB/MariaDB/TiDB/OceanBase/openGauss/Kingbase/Dameng/etc.）一视同仁，不引入 kind-specific 分支 |
| SQL splitting / risk 分析 | **是** | widget SQL 是单语句 SELECT，复用现有 splitter 与 risk pipeline，受 SqlStatementGuard L1 限制（不允许 DDL/DML/管理命令进入 widgetQuery.sql）|
| 诊断 | N/A | dashboard 不消费 diagnostics |
| MCP action schema | **是** | DashboardAction.promote 接受 v2 schema；不新增 action |
| 运行时 agent prompt | **是** | bezel SKILL.md 中 `data-contract.md` 显式说明：widgetQuery.sql 必须是单 SELECT，不可写 DDL/DML，否则 SqlStatementGuard 拒绝 |

## 10. Open Risks

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | **Tauri webview CSP**：iframe srcDoc + 外部 ECharts CDN 加载可能被 Tauri 默认 CSP 阻断 | 高 | spec 实施前先做 spike：在 Tauri 测试环境跑一份 dashboard.html 验证 ECharts CDN 加载成功；若失败，备选方案=把 ECharts UMD（~800KB minified+gzipped ~250KB）内联到每份 dashboard.html，HTML 体积上升但完全自包含 |
| R2 | **HTML XSS**：AI 产出 HTML 可能含恶意 `<script>` / inline `on*` | 中 | `scripts/validate.py` 在 bezel 内强制运行；server `DashboardArtifactService.promote` 在落盘前再跑一遍 Java 版 validator（不信任 client） |
| R3 | **BUG-0009 回归**：Files Library Tab 渲染 dashboard kind 文件曾崩溃（已 fixed 2026-05-09）。本设计把 HTML 也作为 dashboard kind artifact 落盘 | 中 | E2E suite 覆盖 Files Library 双击 v2 dashboard artifact 打开正常；HTML artifact 与 JSON artifact 在 Files Library 显示为同一行（聚合视图）而非两行，避免用户困惑 |
| R4 | **polling 打爆后端**：高频 polling（5 widget × 1s）= 5 QPS/dashboard × N concurrent dashboards | 中 | widget refresh.intervalMs 最小 1000；后端 widget data endpoint 加 per-dashboardId rate limit（每 dashboard 最高 20 QPS）；visibilitychange 暂停 |
| R5 | **JSON ↔ HTML drift**：用户/AI 改了 JSON 但忘了让 AI 重生成 HTML，前端加载的 HTML 与 JSON 不一致 | 中 | HTML head 内嵌 `<meta name="__JSON_HASH__" content="sha256:...">`；前端加载时 fetch JSON sha256 与 HTML 内嵌 hash 比对；不匹配时显示「视觉与数据描述不同步，请在 chat 中说『重新生成视觉』」banner |
| R6 | **bezel skill 安装期失败**：classpath 资源缺失 / 权限不足 / 磁盘满 | 低 | `ensureBezelSkill` 失败不抛出（log warn），OpenCode 启动不阻塞；AI 在没有 bezel 时 fallback 到 `generic.*` pattern + 朴素 ECharts |
| R7 | **v1 dashboard migration**：现存生产 dashboard 无视觉重生成会持续报「需要重生成」 | 低 | spec §7.3 已明示手工触发，不做自动；预期生产环境 dashboard 数量小，逐个手工 OK |
| R8 | **OpenCode skills 加载差异**：OpenCode 对 SKILL.md frontmatter 字段（如 `version`、`compatibility`）的解析与 Claude Code 可能有差异 | 低 | bezel SKILL.md 仅使用 OpenCode + Claude Code 共同支持的字段（`name`、`description`）；其他字段写到 SKILL.md 正文 markdown |

## 11. Out of Scope

明确不在本 spec 实施：

1. SSE / WebSocket 实时推送（widget refresh 走 HTTP polling）
2. dashboard URL 公开分享（kiosk mode）
3. dashboard 多人协同编辑（CRDT）
4. 手编 HTML 直接保存（HTML 是 derived artifact）
5. chart fence 渲染器替换（chart fence 在聊天气泡内，与 dashboard 是不同消费场景）
6. PDF / 图片 export（HTML iframe 截图）
7. 第 13+ 行业 pattern 扩展（按需后续追加）
8. AI 在 promote 时自动从 v1 dashboard 重生成 HTML（spec §7.3 已明示手工触发）
9. bezel skill 内嵌 ECharts UMD 离线化（除非 R1 spike 失败才启用）
10. .opencode 目录管理 UI（用户不直接操作 .opencode/skills/bezel/）

## 12. 验收标准

1. **bezel repo 完整可装**：
   - `wallfacers/bezel` repo 推送成功
   - `/plugin marketplace add wallfacers/bezel` 在 Claude Code 中可直装
   - `scripts/sync-bezel.sh <tag>` 在本地能 vendor 进 data-talk

2. **data-talk 启动期 deploy 成功**：
   - `mvn install -pl server/data-talk-infrastructure -am` 后，`target/classes/opencode/skills/bezel.tar.gz` 存在
   - `mvn spring-boot:run -pl server/data-talk-adapter` 启动后，`.opencode/skills/bezel/SKILL.md` 在 data-talk repo 根存在
   - 重启 server 不重复解压（marker 幂等）
   - 升级 bezel 版本（重跑 sync-bezel.sh）后 marker 自动失效 + 重新解压

3. **chat 内生成可用 v2 dashboard**：
   - 用户在 chat 中说「做个电商运营大屏，关联 mysql」
   - AI 触发 bezel，产出 v2 dashboard.json + dashboard.html
   - 前端打开 dashboard tab，iframe 渲染正确，KPI 数据从 mysql 通过 widget data endpoint 拉取
   - widget refresh.intervalMs 起效，10s 后 ECharts 自动 setOption 新数据

4. **旧 React canvas 完全删除**：
   - `dashboard-canvas.tsx` / `chart-widget.tsx` / `grid-layout-engine.ts` 等文件不存在
   - `cd client && npx tsc --noEmit` 零错误
   - `cd client && npm test` 全绿
   - `cd server && mvn clean verify` 全绿

5. **v1 dashboard 不破坏**：
   - 现存 v1 dashboard 打开时走 lazy migration → v2 JSON
   - HTML 缺失时弹 modal 提示用户在 chat 中重生成视觉
   - 不影响 v1 dashboard JSON 在 Files Library 的显示

6. **E2E 覆盖**：
   - Playwright 新增 `dashboard-bezel-v2.spec.ts`，覆盖 §2.2 端到端流程（chat 发起 → AI 产出 → iframe 渲染 → polling 起效）

## 13. 相关文档

- 前序设计：[12 行业场景大屏独立精品化重设计](./2026-05-11-dashboard-premium-redesign-design.md)
- 前序计划：[12 行业大屏 premium redesign plan](../exec-plans/2026-05-11-dashboard-premium-redesign-plan.md)
- 被替换的运行时设计：[Report / Dashboard Design](./2026-05-08-report-dashboard-design.md)（仅 React 渲染部分被取代；schema/promote/patch/file_artifact 集成保留）
- 间接相关：[Dashboard ↔ File Artifact Integration Design](./2026-05-09-dashboard-file-artifact-integration-design.md)（external artifact 机制照旧）
- 间接相关：[Dashboard / FileArtifact + Data Source E2E Test Design](./2026-05-09-dashboard-datasource-e2e-test-design.md)（v2 dashboard E2E 在本 spec 实施时一并扩充）
- 引用约定：[client/DESIGN.md](../../client/DESIGN.md)、[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
- 上游参考：[letterpress repo](https://github.com/wallfacers/letterpress)（bezel 仓库形态与 marketplace.json 对齐）
