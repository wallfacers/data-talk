---
title: bezel 编译器重构设计 — JSON → 服务端编译 HTML
date: 2026-05-20
status: draft
owners: [wallfacers]
---

# bezel 编译器重构设计

## 0. TL;DR

1. **核心变化**: AI 不再生成 HTML/CSS/JS，只输出纯 JSON（schemaVersion 3）；服务端 Java 编译器确定性编译 JSON → HTML（~50ms）
2. **单一真源**: `pattern-catalog.yaml` 同时驱动编译器（运行时）和 AI 文档（构建期自动生成），解决 AI 文档与编译器行为不一致（BUG-0055 根因）
3. **多轮迭代从 407s → <2s**: 每轮 AI 只需修改 JSON（<1s），服务端 diff 引擎判断增量/全量编译，iframe 热更新
4. **旧代码彻底删除**: 不兼容 v1/v2；旧 React canvas + bezel skill 旧引用文档 + dashboard-html fence 管线全部删除
5. **两阶段变更模式**: AI 始终输出完整 JSON（不做 patch），服务端 Differ 决定编译范围

## 1. 背景与问题

### 1.1 当前系统痛点

| 问题 | 根因 | 影响 |
|------|------|------|
| 生成慢 | AI 全量输出 HTML/CSS/JS/scheduler（~407s） | 用户等待 7 分钟才能看到大屏 |
| 迭代慢 | 每次修改全量重新生成 HTML | 多轮对话每轮 ~410s |
| 不稳定 | AI 驱动的 HTML 编译，受模型随机性影响 | BUG-0048/0051/0055 等持续出现 |
| 文档打架 | patterns-catalog.md 与 compile-rules.md 互相矛盾 | BUG-0055: KPI HTML-only vs 每 widget echarts.init |
| 契约脆弱 | 135 行 SKILL.md + 7 份 reference 靠 AI 遵守 | AI 跳过 HTML block、写错 CSS Grid、漏 CSP meta |

### 1.2 核心洞察

ChatGPT/DeepSeek 可以全量重新生成走天下，因为模型推理极快（<10s）且产物简单。DataTalk 有两个不同条件：

1. 模型速度慢（OpenCode → 远端模型 → 复杂 HTML 编译 407s）
2. 12 行业 premium 模板对视觉品质有高标准

**方案选择**：把 HTML 编译从 AI 职责中移除，交给服务端确定性编译器。AI 只负责 JSON（纯数据、结构化、可校验），编译器负责 HTML（确定性、可测试、毫秒级）。

## 2. 编译管线架构

### 2.1 六阶段编译管线

```
Dashboard JSON (AI 产出)
  → Phase 1: Schema Validate (Zod + Jackson)
  → Phase 2: Template Resolve (theme + layout.template → 骨架 HTML)
  → Phase 3: Widget Compile (逐 widget: patternId → HTML 容器 + __BEZEL_CONFIG__ 条目)
  → Phase 4: Option Merge (模板默认 ← 语义字段 ← options 透传 = 最终 ECharts option)
  → Phase 5: Assemble & Output (注入 CSP + scheduler + config → 最终 HTML 字符串)
  → Phase 6: HTML Validate (安全校验: CSP 存在性、无内联 on*、外链白名单)
  → 最终 HTML → GET /api/dashboards/{id}/html → iframe srcDoc
```

### 2.2 编译触发时机

| 场景 | 触发方式 | 是否落库 |
|------|---------|---------|
| 首次创建大屏 | `POST /api/dashboards/promote` → 编译器编译 → 落库 JSON + HTML | 是 |
| 多轮迭代修改 | `POST /api/dashboards/{id}/update` → 编译器 diff + 增量/全量编译 → 更新 JSON + HTML → 热通知前端 | 是 |
| 用户手动调参 | `POST /api/dashboards/{id}/preview` → 编译器编译 HTML（不落库，只返回） | 否 |
| 已存 dashboard 打开 | `GET /api/dashboards/{id}/html` → 返回已存储 HTML（不重新编译） | 否 |

### 2.3 编译策略

- `compile(json)` 是纯函数 — 输入相同输出相同，无副作用，无 DB 访问
- 首次编译：全量编译（~50ms）
- 增量编译：Differ 检测仅 widget 属性变化（chartType/colorScheme/title 等）且数量 ≤3 → 增量编译；layout/theme 变化或 widget 增减 → 全量

### 2.4 模块归属（四层架构）

编译器归属 `data-talk-application` 层，文件结构：

```
data-talk-application/src/main/java/.../dashboard/
  DashboardCompiler.java             # 编译器入口 compile(json) → html
  TemplateResolver.java              # 模板解析（theme + layout → 骨架 HTML）
  WidgetCompiler.java                # 逐 widget 编译（patternId → HTML + config）
  OptionMerger.java                  # ECharts option 三级合并引擎
  CspInjector.java                   # CSP meta 注入
  SchedulerBundler.java             # polling scheduler IIFE 注入
  HtmlValidator.java                # 安全/合规校验
  DashboardDiffer.java              # v3 JSON diff 引擎
```

## 3. 单一真源体系

### 3.1 核心原则

`pattern-catalog.yaml` 是人手维护的唯一契约，同时驱动：

- **Java 编译器**（运行时加载，强制执行）
- **AI 文档**（构建期 `scripts/generate-bezel-docs.sh` 自动生成 .md）

```
pattern-catalog.yaml
       ├──→ Java Compiler (运行时读取，验证 + 编译)
       └──→ Docs Generator (构建期生成 patterns-catalog.md + layout-templates.md)
                └──→ AI 看到的 skill 文档 (一致性由机器保证)
```

### 3.2 pattern-catalog.yaml 结构

```yaml
templates:
  - id: single-focus
    description: "大图表居中 + KPI 环绕"
  - id: two-column-left-heavy
    description: "左侧 38% + 右侧 62%"
  - id: two-column-right-heavy
    description: "左侧 62% + 右侧 38%"
  - id: three-column-kpi-center
    description: "左 KPI + 中主图 + 右明细"
  - id: top-kpi-bottom-charts
    description: "顶部 KPI 条 + 下方图表区"
  - id: grid-equal
    description: "等分网格 2×3 或 3×2"

chartTypes:
  bar:
    defaultOptionFile: echarts-options/bar.json
    semanticFields: [stacked, showLegend, showTooltip, labelPosition]
  line:
    defaultOptionFile: echarts-options/line.json
    semanticFields: [stacked, showLegend, showTooltip, showAreaFill, labelPosition]
  # ...

patterns:
  ecommerce.gmv-trend:
    description: "GMV 趋势图"
    industry: ecommerce
    renderKind: chart
    defaultChartType: area
    supportedChartTypes: [bar, line, area]
    defaultColorScheme: warm
    supportedColorSchemes: [warm, cool, brand]
  generic.kpi-tile:
    description: "KPI 数值卡片"
    renderKind: html        # ← 编译器不 init ECharts
    defaultChartType: null
    supportedChartTypes: []
```

## 4. 模板与渲染体系

### 4.1 资源文件结构

```
server/.../resources/dashboard/
  pattern-catalog.yaml               # 单一真源
  templates/                          # 6 个布局骨架模板
    single-focus.html / two-column-left-heavy.html / ...
  styles/                             # 行业 CSS
    base.css / ecommerce.css / finance.css / ... (12+1)
  renderers/                          # widget 类型渲染器 JS 片段
    chart-renderer.js / kpi-renderer.js / table-renderer.js / ...
  scheduler.js                        # polling 调度器 IIFE
  echarts-options/                    # 预置 ECharts option 片段
    bar.json / line.json / area.json / pie.json / ...
```

### 4.2 模板占位符系统

模板是 HTML + 精确占位符，用 Java `String.replace` 替换（不引入模板引擎）：

| 占位符 | 来源 | 替换内容 |
|--------|------|---------|
| `__CSP_POLICY__` | CspInjector | 根据模板能力自动生成 |
| `__JSON_HASH__` | JSON sha256 | `sha256:xxxx` |
| `__TITLE__` | JSON.title | 纯文本 |
| `__BASE_CSS__` | styles/base.css | 文件内容 |
| `__THEME_CSS__` | styles/{industry}.css | 文件内容 |
| `__THEME_SLUG__` | JSON.theme 映射 | `ecommerce`/`finance`/... |
| `__LAYOUT_TEMPLATE__` | JSON.layout.template | 模板 ID |
| `__WIDGETS__` | WidgetCompiler | 所有 widget HTML 片段拼接 |
| `__ECHARTS_LOADER__` | 配置 | CDN `<script>` 或内联 |
| `__BEZEL_CONFIG_JSON__` | WidgetCompiler | JSON.stringify(config) |
| `__SCHEDULER_IIFE__` | scheduler.js | 文件内容 |

### 4.3 ECharts Option 三级合并

```
最终 option = 深度合并( Layer1, Layer2, Layer3 )

Layer 1: echarts-options/{chartType}.json     (编译器内置默认)
Layer 2: chartSemantics 字段转换               (chartType/colorScheme/stacked/...)
Layer 3: widget.options.rawEchartsOption       (用户透传，最高优先级)
```

### 4.4 Widget 编译流程

```
WidgetCompiler.compile(widget, themeCssVars):
  1. 查 pattern-catalog（Java HashMap，不是 AI）
  2. 按 renderKind 分流:
     - chart → 生成容器 + baseOption + ECharts init
     - html  → 生成容器 + 初始 HTML 内容 + polling 数据更新逻辑
  3. 生成 __BEZEL_CONFIG__ 条目
```

## 5. AI Skill 精简

### 5.1 AI 职责对比

| | 现状 | 方案 B |
|---|---|---|
| 阅读文档 | 7+ 份（135行 SKILL.md + 所有 reference） | 2 份（SKILL.md ~30行 + patterns-catalog.md） |
| 产出 | JSON + 完整 HTML/CSS/JS/scheduler | 纯 JSON |
| 正确性 | 依赖 AI 遵守契约（弱） | Zod + 编译器双重校验（强） |
| 首次生成 | ~407s | ~15s |
| 迭代修改 | ~410s | <2s |

### 5.2 精简后的 SKILL.md（~30 行）

```markdown
---
name: bezel
description: Premium industrial dashboards. Use when the user asks to build a dashboard,
  monitoring screen, KPI board, big-screen display, or operations cockpit. Output a
  `dashboard` fenced code block with schemaVersion 3 JSON. The server-side compiler
  handles all HTML/CSS/JS generation.
---

# bezel — Dashboard Skill

## What you do

Emit a single ```` ```dashboard ```` fenced block containing schemaVersion 3 JSON.
The server compiler transforms JSON into the final HTML. You never write HTML/CSS/JS.

## JSON skeleton

(AI fills in based on patterns-catalog.md + session data context)

## Rules

1. Emit only the `dashboard` fenced block. No `dashboard-html` block.
2. Every patternId must exist in `references/patterns-catalog.md`.
3. chartType must be in the pattern's supportedChartTypes list.
4. widget.id suffix after `_w_` must be ≥ 4 characters.
5. Set defaultConnectionId / defaultDatabase / defaultSchema from session context.
6. Every widget SQL must be a single SELECT statement.

## When modifying

Output the **complete** updated JSON in a new `dashboard` fenced block (not a patch).
The server diff-engine detects what changed and recompiles accordingly.

## References

1. `references/patterns-catalog.md` — patterns with supported chartTypes/colorSchemes
2. `references/layout-templates.md` — available layout templates
```

## 6. 多轮对话协议

### 6.1 AI 始终输出完整 JSON

AI 每轮输出完整的 dashboard JSON（~2KB），不做 JSON Patch。理由：

- AI 理解当前状态容易出错（patch path 算错）
- 完整 JSON 对 15 widget 的大屏也只有 ~3KB，模型输出时间 <1s
- 服务端 Differ 确定性判断变更范围（比 AI 的 patch 可靠）

### 6.2 服务端 Diff + 增量编译

```
DashboardDiffer.diff(oldJson, newJson):
  if layoutChanged || themeChanged → 全量编译
  if changedWidgets.size() <= 3 → 增量编译（只重编译变化的 widget）
  else → 全量编译（变化太多不如全量）

增量响应: { version, changes: [{ widgetId, baseOption, ... }] }
全量响应: { version, html }
```

### 6.3 iframe 热更新

```
方式 A (srcDoc 全量): layout/theme/widget 增减 → 全量 HTML 替换 (~200ms)
方式 B (postMessage 增量): 仅 widget 属性变化 → postMessage 更新单个 widget (~50ms, 无闪烁)
```

### 6.4 API 设计

```
POST /api/dashboards/promote
  请求: { dashboard: <v3 JSON> }
  返回: { id, version, html }

POST /api/dashboards/{id}/update
  请求: { dashboard: <v3 JSON>, baseVersion: <int> }
  逻辑: 乐观锁检查 → Differ → 增量/全量编译 → 落库 → 返回
  返回: { version } + (html | changes)

POST /api/dashboards/{id}/preview
  请求: { dashboard: <v3 JSON> }  不落库，不递增 version
  返回: { html }

GET /api/dashboards/{id}/html
  返回: 已存 HTML (text/html)

POST /api/dashboards/{id}/widgets/{wid}/data
  逻辑: 不变，同现有
```

## 7. JSON Schema v3

### 7.1 关键变化（vs v2）

新增高层语义字段 `chartSemantics`，替代裸写 ECharts option：

```typescript
const chartSemantics = z.object({
  chartType: z.enum(['bar', 'line', 'area', 'pie', 'funnel', 'scatter', 'radar', 'map']).optional(),
  colorScheme: z.enum(['warm', 'cool', 'monochrome', 'brand']).optional(),
  stacked: z.boolean().optional(),
  showLegend: z.boolean().optional(),
  showTooltip: z.boolean().optional(),
  showAreaFill: z.boolean().optional(),
  labelPosition: z.enum(['inside', 'outside', 'none']).optional(),
  gridGap: z.enum(['compact', 'normal', 'spacious']).optional(),
  rawEchartsOption: z.record(z.string(), z.unknown()).optional(),  // escape hatch
})

const layoutV3 = z.object({
  engine: z.literal('free'),
  template: z.enum([...6 个布局模板]),
  viewport: z.object({ minWidth, aspect }).optional(),
})

const widgetV3 = z.object({
  // ... 同 v2
  title: z.string().min(1).max(64),           // 新增：widget 显示名
  chartSemantics: chartSemantics.optional(),    // 新增：图表语义
})

export const dashboardSchemaV3 = z.object({
  schemaVersion: z.literal(3),
  // ... 其余字段同 v2
  layout: layoutV3,
  widgets: z.array(widgetV3),
})
```

## 8. 文件变更清单

### 8.1 删除

```
# 旧 AI skill 文档
server/.../resources/skills-src/bezel/skills/bezel/references/compile-rules.md
server/.../resources/skills-src/bezel/skills/bezel/references/design-language.md
server/.../resources/skills-src/bezel/skills/bezel/references/data-contract.md  → 精简重写
server/.../resources/skills-src/bezel/skills/bezel/assets/templates/*.html
server/.../resources/skills-src/bezel/skills/bezel/scripts/validate.py
server/.../resources/skills-src/bezel/skills/bezel/scripts/preview.py

# 旧前端渲染管线（如前序 spec 未删除则同步清理）
client/src/features/dashboard/dashboard-canvas.tsx
client/src/features/dashboard/engines/
client/src/features/dashboard/widgets/

# 旧 dashboard-html fence 管线
client/src/features/chat/components/markdown/ 中 dashboard-html 相关代码

# 后端旧逻辑
// DashboardArtifactService: storeHtml / loadHtml / HTML artifact 落库逻辑
// OpenCodeBinaryResolver: ensureBezelSkill / BEZEL_MARKER / 启动期解压逻辑
// .gitignore: .opencode/skills/bezel/ / .opencode/.bezel-installed
```

### 8.2 新增

```
# 服务端编译器
server/data-talk-application/src/main/java/.../dashboard/
  DashboardCompiler.java / TemplateResolver.java / WidgetCompiler.java
  OptionMerger.java / CspInjector.java / SchedulerBundler.java
  HtmlValidator.java / DashboardDiffer.java

# 编译器资源
server/data-talk-application/src/main/resources/dashboard/
  pattern-catalog.yaml / templates/ (6 html) / styles/ (13 css)
  renderers/ (8 js) / scheduler.js / echarts-options/ (8 json)

# 构建工具
scripts/generate-bezel-docs.sh

# 前端
client/src/features/dashboard/DashboardFrame.tsx  (替代 iframe-shell.tsx)
```

### 8.3 修改

```
# 后端
DashboardController.java: promote/update/preview 接口改造
DashboardArtifactService.java: 调用编译器，不再存储 AI 的 HTML

# 前端
schema.ts → v3 / types.ts → v3 / dashboard-tab.tsx → 改用 DashboardFrame
dashboard-block.tsx → 只读 dashboard fence
dashboard-api.ts → promoteDashboard 去掉 HTML 参数

# AI skill
SKILL.md → 重写为 ~30 行 / data-contract.md → 精简为 v3 schema 字段参考
patterns-catalog.md → 改为 YAML 自动生成 / layout-templates.md → 新增 YAML 自动生成
```

## 9. 实施计划

### Phase 1: 编译器上线（AI 可立即使用新路径）

- 新增编译器 + pattern-catalog.yaml + 全部模板/CSS/JS
- DashboardController.promote 改为接收 JSON → 编译器编译 → 落库
- 前端 promoteDashboard 去掉 HTML 参数
- 新增 POST /api/dashboards/{id}/update（diff + 增量更新）
- 新增 POST /api/dashboards/preview
- 端到端验证: 一句话 → v3 JSON → 编译器 → iframe 渲染

### Phase 2: 前端 + AI skill 切换

- SKILL.md 重写为 ~30 行
- DashboardBlock 移除 dashboard-html fence 读取
- schema.ts 升 v3
- 构建期文档生成脚本
- 旧 bezel skill 引用文档删除
- 旧 React canvas 代码确认删除
- 端到端验证: 多轮对话 <2s 生效

### Phase 3: 清理

- 旧 bezel 仓库 vendored copy 清理
- 后端 storeHtml / loadHtml / HTML artifact 逻辑清理
- .opencode/skills/bezel/ 启动期解压逻辑删除
- 全量回归测试 + E2E 覆盖

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 编译器 HTML 视觉品质不如 AI 手写 | 以现有人工 12 份模板为基准逐张 A/B 对比验收；rawEchartsOption escape hatch 兜底 |
| 模板不灵活，定制需求覆盖不足 | chartSemantics 覆盖 90% 场景 + rawEchartsOption escape hatch |
| 模板 + 行业 CSS 组合爆炸（6×12=72） | CSS 变量体系解耦 layout 和 theme；按 CSS Grid 组织骨架，行业 CSS 仅提供颜色/字体变量 |
| 构建期文档生成增加 build 复杂度 | 仅 dev/CI 运行；git 提交生成的 .md，mvn package 不重新生成 |
| 服务端编译增加 CPU 开销 | 纯字符串替换 + JSON merge，单次 ~50ms；可加 LRU 缓存（按 JSON hash key） |
| ECharts 本地化（CDN → 本地） | 同 BUG-0051 修复方案，编译器注入时做 URL 替换 |

## 11. 验收标准

1. 用户一句话 "做一个电商运营大屏" → 编译器产出 HTML → iframe 渲染正确
2. 多轮对话 "GMV 趋势改折线图" → AI 只输出 JSON → 编译增量更新 → iframe 热更新 <2s 总耗时
3. 12 份行业模板编译器输出 vs 现有人工 HTML 视觉品质 A/B 对比通过
4. 前端 `npx tsc --noEmit` 零错误
5. 后端 `mvn clean verify` 全绿
6. E2E Playwright 覆盖端到端创建 + 多轮修改流程
