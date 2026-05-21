# AI Text-to-Chart Fence Design

- **Date**: 2026-04-23
- **Status**: Shipped — 2026-04-24
- **Owner**: wallfacers
- **Related**:
  - [client/DESIGN.md](../../client/DESIGN.md) — 设计合同（必读约束）
  - [docs/product-specs/2026-04-21-stage-ui-object-protocol-design.md](./2026-04-21-stage-ui-object-protocol-design.md) — Stage UI Object 协议
  - [docs/product-specs/2026-04-19-ai-message-rendering-migration-design.md](./2026-04-19-ai-message-rendering-migration-design.md) — 聊天渲染链路基础
  - `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
  - `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  - 参考：`~/project/open-db-studio/src/components/shared/ChartBlock.tsx`、`~/project/open-db-studio/prompts/chat_assistant.txt`

---

## 1. 背景与目标

DataTalk 当前的图表路径依赖 `datatalk.render_chart` action：AI 把 ECharts option 通过结构化 tool call 传给后端，后端产出 chart artifact，前端 Stage 里用 recharts（通过 `echarts-to-recharts.ts` 翻译）展示。实践中两个问题：

1. **流式无即时反馈**：AI 决定作图到 artifact 落地之间要走一整轮 action 往返，聊天里看不到"图在生成中"；用户感知是"模型卡住"。
2. **图表类型受限**：前端只识别 line / bar / pie，其他 ECharts 图（scatter、radar、candlestick…）一律落到 `unsupported`。

参考项目 `open-db-studio` 的做法是：AI 直接在聊天 markdown 里写 ` ```chart\n<ECharts JSON>\n``` ` 围栏，前端 `ChartBlock` 解析 JSON 后用 echarts-for-react 渲染，流式 JSON 未完整时展示骨架动画。方案体验好、协议简单。

**本 spec 的目标**：把这个模式移植到 DataTalk，同时保留 Stage artifact 通路作为"打开到工作台"的显式保存路径。核心需求：

- AI 默认路径：` ```chart` 围栏 → 聊天内联渲染 + 流式骨架占位
- 聊天图表块提供"打开到工作台"按钮，把当前 option 提升为 Stage artifact
- `datatalk.render_chart` 从"AI 默认路径"降级为"显式保存/固定/supersede"路径
- Stage 端的 `ChartArtifact` 与聊天共用同一个渲染器（`echarts-for-react`），废弃 recharts 翻译

**图表类型口径**：协议层面接受任意合法 ECharts option；发布层面首版只打包 6 种系列模块（`Bar/Line/Pie/Scatter/Radar/Candlestick`）+ 必要 component。遇到未打包的 series type，`ChartRenderer` 在 ECharts 内部会报错，被 `ChartErrorBoundary` 捕获降级为"渲染失败 / 复制 JSON"。扩类型只需再导一个 ECharts 模块常量，协议与 AI 提示词不变。

**非目标**：见 §9。

---

## 2. Design Inputs（来自 `client/DESIGN.md`，强制约束）

- `components.chart.focus = accent.primary` → light: `cobalt.700`（`#1D4ED8`） / dark: `cobalt.400`（`#60A5FA`）。主系列配色。
- `components.chart.compare = accent.warn` → amber.500（`#F59E0B`）/ amber.400（`#FBBF24`）。对比 / 次要系列。
- `components.chart.grid = border.subtle` → light: `#E2E8F0` / dark: `rgba(255,255,255,0.08)`。网格、轴线、分割线。
- `data-visualization` 条款：一张图只有**一个**主强调目标；green/red 仅用于 outcome/health 状态；中性色承载历史 / 背景系列。
- Dual-theme 一等公民：必须同时提供 `datatalk-light` 与 `datatalk-dark` 两套 ECharts 主题，颜色从 semantic token / CSS 变量解析，**禁止在 feature 代码写原色字面量**。
- 字体：`typography.ui-sm`（Source Sans 3）承接图例 / 标题；`typography.mono-sm`（JetBrains Mono）承接数值 / 坐标刻度。
- Motion：`motion.fast = 120ms`。骨架呼吸动画遵循 `prefers-reduced-motion`（减动模式完全静态）。

---

## 3. 架构总览

**触点四层**：

| 层 | 文件 | 变化 |
|---|---|---|
| Agent 提示词 | `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | 新增 "Charts" 节；改 `render_chart` 用途；`sourceArtifactId` 说明为可选 |
| Chat 渲染 | `client/src/features/chat/components/markdown/` 新增 `chart-block.tsx` / `chart-renderer.tsx` / `chart-theme.ts` / `chart-expand-modal.tsx`；`markdown.tsx` 加 `decorateChartBlocks` | 主要新增点 |
| Stage artifact | `client/src/features/ontology/components/chart-artifact.tsx`（改造）；删 `echarts-to-recharts.ts` 与未使用的 `client/src/components/ui/chart.tsx` | 切换到共享 `ChartRenderer` |
| Backend | `RenderChartAction.java`（放宽 `sourceArtifactId` 为可选 + 抽取 `ChartArtifactService`）；新增 REST `ChartArtifactController` | Stage 提升通路 |

**依赖**：

- 新增：`echarts ^5`、`echarts-for-react ^3`（按需 import：`BarChart / LineChart / PieChart / ScatterChart / RadarChart / CandlestickChart` + `GridComponent / TooltipComponent / LegendComponent / TitleComponent / DataZoomComponent / VisualMapComponent / ToolboxComponent` + `CanvasRenderer`，控 bundle ≤ ~180KB gz）
- 移除：`recharts`、`client/src/components/ui/chart.tsx`、`client/src/features/ontology/echarts-to-recharts.ts`

**组件边界**：

- `ChartBlock`（消息块外壳）：工具栏（打开到工作台 / 放大 / 复制）、三态切换（骨架 / 预览 / 错误 / 稳定）
- `ChartRenderer`（共享渲染器）：echarts-for-react + ResizeObserver + `injectOptionFix`；chat 与 Stage 共用
- `ChartTheme`：`registerTheme('datatalk-light' / 'datatalk-dark')`；启动时注册一次，主题切换时重新注册并 re-key 所有 renderer
- `ChartExpandModal`：portal 全屏预览，内部复用 `ChartRenderer`
- Stage `ChartArtifact`：去 recharts 翻译，直接把 `echartsOption` 喂给 `ChartRenderer`

**数据流（流式场景）**：

```
AI message.part.delta → text-part → markdown.tsx
  │
  │ marked 解析 → <pre><code class="language-chart">{partial JSON}</code></pre>
  │
  ▼
decorateChartBlocks 替换
  <div data-component="markdown-chart"
       data-chart-json-b64="…"
       data-chart-streaming="true|false"
       data-chart-source-artifact-id="art_xxx|null"
       data-chart-key="<messageId>:<blockIndex>"></div>
  │
  │ markdown.tsx useEffect 扫描 [data-component="markdown-chart"]
  │
  ▼
Map<HTMLElement, Root>：
  - 命中 → root.render(<ChartBlock {…新 props} />)
  - 未命中 → createRoot() + render；morphdom onBeforeElUpdated 保留 React 接管子树
  - key 消失 → root.unmount()
  │
  ▼
ChartBlock 按 (streaming × JSON ok) 切换：骨架 / 预览 / 稳定 / 错误
```

---

## 4. 聊天围栏协议

**基础语法**（与参考项目一致，做 DataTalk 专有扩展）：

~~~
```chart
{ <ECharts option JSON> }
```
~~~

**info-string 扩展**（可选，带数据血缘）：

~~~
```chart:art_exec_12ab
{ <ECharts option JSON> }
```
~~~

- 冒号后是 `sourceArtifactId`，由 AI 在基于某 `datatalk.execute_sql` 结果作图时写入
- decorator 用 `className.match(/\blanguage-chart(?::([A-Za-z0-9_-]+))?/)` 解析
- 未提供 → "打开到工作台"时创建独立 chart artifact（`sourceArtifactId = null`）
- 提供 → "打开到工作台"时绑定父 artifact；如父 artifact 已删，后端静默接受（不校验父存在性，只记录 id）

**JSON 约束**（写入 AGENTS.md）：

- 必须是严格 JSON（`JSON.parse` 可解），不含注释、不含 JS 函数、不用 `undefined`
- 不写 `formatter` 回调（如需格式化，用字符串模板占位或省略）
- 不写 `backgroundColor`（`ChartRenderer` 会强制覆盖为 semantic token）

---

## 5. ChartBlock 状态机（"占位"核心）

| 输入 | streaming | JSON 完整 | 展示 |
|---|---|---|---|
| 首次 delta | true | ❌ | **骨架占位**（"占位"）：中性色条形脉冲动画 + `生成图表中…`，容器 320px 高 |
| 中段 delta（JSON 已可解） | true | ✅ | **预览态**：实图即时渲染；工具栏"打开到工作台"`disabled`（tooltip: `等待生成完成`） |
| JSON 一度可解又变不可解 | true | ✅ → ❌ | 保留最近一次可解的 option（不闪回骨架），静默等下一轮 delta |
| 流结束 | false | ✅ | **稳定态**：实图 + 完整工具栏（打开到工作台 / 放大 / 复制） |
| 流结束 | false | ❌ | **错误态**：红色错误条 + 原始 JSON 折叠 mono pre，不影响其他消息 |

**骨架（"占位"）视觉**：

- 容器：`bg.panel` 背景 + `border.subtle` 1px 外框 + `radius.md` 圆角；固定 `320px` 高
- 内部：5 根柱体（高度 `[20, 32, 44, 32, 20]`），配色 `accent.primary` at 40% opacity
- 动画：`@keyframes chart-bar-pulse` 单根柱 `transform: scaleY(0.6 → 1)`，1.1s `cubic-bezier(0.2,0,0,1)` infinite，相邻柱 `animation-delay: -0.22s` 错位
- Chrome：顶部一行 `<span>chart · 生成中</span>`，`ui-xs` 字号，左侧加一个呼吸圆点 `ai-dot`
- 减动：`@media (prefers-reduced-motion: reduce)` 下柱体静态、圆点不呼吸

**预览态 → 稳定态切换**：

- ChartBlock 监听外部 `streaming` prop 跌落 → 把"打开到工作台"按钮从 `disabled` 切到 `enabled`
- ECharts option 在两态之间不重置（`notMerge` 每次 render 都传 true，但 key 不变），避免重绘闪烁

**错误态**：

- `JSON.parse` 抛错 → 显示 `图表数据错误` 红色条 + 原始 JSON（`<pre class="mono-sm">`）
- ECharts `init` 抛错（`ChartErrorBoundary`）→ 降级文字 `渲染失败`，保留复制按钮

---

## 6. 工具栏与 "打开到工作台" 提升

### 6.1 UX

- 稳定态工具栏（右上角）按钮顺序：`打开到工作台` · `放大` · `复制`，全部图标按钮，ARIA label + tooltip
- 预览态：`打开到工作台` / `放大` 存在但 `disabled`；`复制` 允许（复制当前可解 JSON）
- 错误态：只有 `复制`
- 已提升过该图（同 `messageId:blockIndex`）：按钮文案 `已在工作台`，点击切换 Stage 焦点而不是二次创建

### 6.2 "已在工作台" 状态来源

不引入任何新的前端持久化层。状态从**既已持久化的 artifact 本身**推导：

- `Artifact` 类型（`client/src/services/channel/event-reducer.ts`）扩两个可选字段：`originMessageId?: string`、`originPartId?: string`
- 对应后端 `ArtifactRecord` 加同名列（migration：`origin_message_id TEXT NULL` + `origin_part_id TEXT NULL`）
- `RenderChartAction` 与新增的 REST 端点写 artifact 时把 `originMessageId` / `originPartId` 填入
- ChartBlock 按钮态推导：对当前 session 的 `artifactsBySession` 过滤 `a.kind === 'chart' && a.originMessageId === msgId && a.originPartId === partId`，命中即 `已在工作台`；命中的 `a.id` 作为 "focus" 目标
- 重载后走 `useSessionHistory.replaceSession`，origin 字段随 artifact 一起回来，状态自动复原；无须前端 persist 中间件

### 6.3 Backend REST

- **端点**：`POST /api/sessions/{sessionId}/artifacts/chart`
- **鉴权**：跟着现有 session-scoped API 惯例，不额外限流
- **Request**：
  ```json
  {
    "echartsOption": { "...": "..." },
    "sourceArtifactId": "art_xxx|null",
    "originMessageId": "m_xxx",
    "originPartId":    "p_xxx"
  }
  ```
- **Response 200**：
  ```json
  { "artifactId": "art_yyy", "version": 1 }
  ```
- **错误码**：
  - `404` sessionId 不存在
  - `413` `echartsOption` 序列化后 > 256KB
  - `400` `echartsOption` 非 object
  - `500` 内部异常（落审计日志）

### 6.4 Backend 实现

- 抽取 `application/ChartArtifactService`：封装"从 echartsOption 产生 chart artifact"的纯逻辑
  - 输入：`sessionId`、`echartsOption`、`sourceArtifactId?`、`originMessageId?`、`originPartId?`、`callId?`
  - 输出：`ArtifactRecord`（insert 由 service 内部完成，带 origin 字段）
  - 不校验 `sourceArtifactId` / origin 的存在性（与提升语义一致），仅在非空时记录
  - **成功后必须向 session bus 发布 `DtEvent.OntologyUpdated`**，载荷（与现有 `ActionDispatcher.applyEffects` 产出的 `datatalk.artifact` 事件形状兼容）：
    ```
    objectType = "datatalk.artifact"
    id         = <artifactId>
    op         = "upsert"
    patch      = { version, kind: "chart", supersedesId?, originMessageId?, originPartId?, producedBy }
    ```
    `op="upsert"` 与既有约定一致；`kind/supersedesId/origin*` 是 patch 的新键，`use-channel.ts:163` 分支原本就在读 `d.patch?.kind / supersedesId`，向后兼容
- `RenderChartAction` 委托到 `ChartArtifactService`；`inputSchema()` 把 `sourceArtifactId` 从 required 移出；`sideEffects()` 改回空列表（由 `ChartArtifactService` 亲自发 event，避免 `ActionDispatcher.applyEffects` 发生重复+低信息量 upsert）
- 新增 `adapter/ChartArtifactController`：`POST /api/sessions/{sessionId}/artifacts/chart` 调 `ChartArtifactService`，不做额外广播（`ChartArtifactService` 统一负责）

### 6.5 客户端调用路径

- 新增 `client/src/services/artifacts/promote-chart.ts`：`promoteChartToStage({ sessionId, option, sourceArtifactId?, originMessageId, originPartId })`
- 图表**不**创建新的 Stage tab 类型（`workspace.open` 枚举保持 `query_editor / er_canvas / markdown_note / report / dashboard`，不动）。图表经 Timeline + ArtifactCanvas 渲染，这是当前就有的展现通路
- ChartBlock 点按钮流程（无本地假 artifact、不污染 OntologyStore）：
  1. ChartBlock 组件内切 `buttonState: 'idle' → 'loading'`（按钮显示 spinner，禁用二次点击）
  2. `promoteChartToStage` 发起 `POST /api/sessions/{id}/artifacts/chart`
  3. 后端成功 → 发布 `ontology.updated`（见 §6.4）；前端既有 `use-channel.ts:163` 分支：
     - `useOntologyStore.upsertArtifact(sessionId, { id, version, kind: 'chart', supersedesId, payload })`
     - `useTimelineStore.addArtifact(sessionId, id, supersedesId)`
     - Stage 的 `ArtifactCanvas` 响应 Timeline 活跃项切换，自动渲染新图
  4. 同一 `useOntologyStore` 订阅也会推动 ChartBlock 的派生状态（§6.2）切到 `已在工作台`；`buttonState` 落回 `idle`
  5. 异常路径（REST 4xx/5xx 或 20s 内未等到 `ontology.updated`）：`buttonState → 'idle'` + toast 报错；无状态残留需要回滚
- 二次点击同一 ChartBlock：按钮已是 `已在工作台`，点击走 `TimelineStore.setActive(sessionId, <matching artifactId>)` 切焦点，不触发 REST

---

## 7. 双主题 ECharts 绑定

### 7.1 CSS 变量扩展

`client/src/styles/globals.css` 的 light / dark 两块各新增 3 行：

```css
/* light */
--dt-chart-focus:   var(--dt-accent-primary);          /* cobalt.700 */
--dt-chart-compare: #F59E0B;                            /* amber.500 */
--dt-chart-grid:    var(--dt-border-subtle);

/* dark */
--dt-chart-focus:   var(--dt-accent-primary);          /* cobalt.400 */
--dt-chart-compare: #FBBF24;                            /* amber.400 */
--dt-chart-grid:    var(--dt-border-subtle);
```

（`amber` 在当前 `--dt-*` 体系里暂无专属变量，沿用字面量并在本 spec 的 Follow-ups 登记；未来补入 design tokens 时统一切回 `var(--dt-…)`。）

### 7.2 主题构造

- `chart-theme.ts` 导出 `buildChartTheme(mode: 'light' | 'dark'): EChartsTheme`
- 启动时 `applyChartThemes(document.documentElement)` 读 `getComputedStyle` 取以下 var：
  - `--dt-chart-focus` → `color[0]`
  - `--dt-chart-compare` → `color[1]`
  - `--dt-chart-grid` → `axisLine / splitLine / axisTick / splitArea` 配色
  - `--dt-text-strong` / `--dt-text-muted` → 文本层级
  - `--dt-bg-panel` / `--dt-bg-elevated` → 背景 / tooltip 背景
- 之后色板依次：`neutral.500`、`sky.500`、`green.500`、`red.500`（共 6 位），覆盖常见 6 系列需求，超出重复
- `textStyle.fontFamily` = `"Source Sans 3", "Noto Sans SC", "PingFang SC", sans-serif`
- 轴刻度 `axisLabel.fontFamily` = `"JetBrains Mono", ...`
- 调 `echarts.registerTheme('datatalk-light', …)` 与 `datatalk-dark`
- 主题切换（由 `useTheme` 暴露的 `mode` 驱动）时重新调 `registerTheme` + 把所有挂载的 `ChartRenderer` key 递增触发 re-create

### 7.3 option 修复（借鉴参考项目 `injectOptionFix`）

`ChartRenderer` 渲染前对 option 做下列规范化：

1. `grid.containLabel = true`（若已数组则每一格都加），保证轴刻度不越界
2. 任一 `yAxis.name` 存在时，`grid.top ≥ 80`，避免顶端轴名与 title / legend 重叠；给该轴注入 `nameTextStyle.align = 'right'`
3. `title.backgroundColor = 'transparent'`（防 AI 乱涂色块）
4. `backgroundColor = var(--dt-bg-panel)` 强制覆盖（放在 spread 末尾防 AI option 盖掉）

---

## 8. AGENTS.md 改动细节

### 8.1 新增 "Charts" 节（放在 Actions 表之后、Recommended Workflows 之前）

```md
## Charts

- **Default chart path**: write an ECharts option inside a fenced block.
  ```chart
  { ... ECharts option as strict JSON ... }
  ```
  It renders inline in chat with a skeleton placeholder while streaming.
- Use `\`\`\`chart:<sourceArtifactId>` when the chart is built from a prior
  `datatalk.execute_sql` result — the UI uses this to bind the promoted
  Stage artifact to the source table.
- Only call `datatalk.render_chart` when the user explicitly asks to save /
  pin / open-in-workbench the chart, or when you need to `supersede` an
  earlier Stage chart.
- JSON constraints: strict JSON, no comments, no JS functions, no
  `formatter` callbacks, no `backgroundColor`.
```

### 8.2 修改 `datatalk.render_chart` 小节

- `Input` 示例：`sourceArtifactId` 改为可选（在 `"required"` 里拿掉，文字标注 `optional`）
- `Use when` 段改为：
  > Use when the user explicitly wants to save or pin the chart as a Stage artifact, or to `supersede` an earlier chart. For ephemeral charts, prefer the inline `\`\`\`chart` fence.

### 8.3 修改 `Recommended Workflows` 的 "Create a chart"

旧：

> 1. `datatalk.execute_sql` → get `artifactId`
> 2. `datatalk.render_chart` → get chart `artifactId`
> 3. …

新：

> - **Inline (default)**: `datatalk.execute_sql` → write a ```chart:<artifactId>` fence with the ECharts option.
> - **Persist to workbench**: additionally call `datatalk.render_chart`; pass `supersedes` if replacing an earlier Stage chart, and call `datatalk.supersede_artifact` afterwards.

---

## 9. 错误与边界

| 场景 | 处理 |
|---|---|
| 流式 JSON 可解 → 不可解抖动 | 保留最近一次可解 option，不闪回骨架 |
| `data-chart-json-b64` > 256KB | 错误条 `图表数据过大，请缩小系列`，不建 echarts 实例 |
| ECharts `init` 抛错 | `ChartErrorBoundary` 降级 `渲染失败`，保留复制按钮 |
| 提升 REST 返回 4xx/5xx | `buttonState → 'idle'` + toast；无 pending artifact 需回滚 |
| REST 200 但 20s 内未等到 `ontology.updated` | `buttonState → 'idle'` + toast `提升已提交，但刷新后才能看到`；artifact 实际已在库，下次 `useSessionHistory` 拉到即可 |
| 未打包的 series type（如 `heatmap`） | ECharts 抛错被 `ChartErrorBoundary` 捕获；降级为"渲染失败 + 复制 JSON"，不影响其他消息 |
| `sourceArtifactId` 指向 artifact 已删 | 后端接受，artifact 入库无父；Stage lineage UI 显示"独立图表" |
| 主题切换 | `registerTheme` 刷新 + `ChartRenderer` key 递增；流式预览态不重置 option |
| `prefers-reduced-motion` | 骨架、呼吸点、提升按钮脉冲全部禁用 |

---

## 10. 测试计划

| 层 | 工具 | 覆盖 |
|---|---|---|
| 前端单元 | vitest + @testing-library/react | `ChartBlock` 三态；info-string `chart:art_xxx` 解析；JSON 抖动保留最近 option；按钮状态机（idle / loading / already-in-workbench）由 origin 匹配派生 |
| 前端集成 | vitest（jsdom） | `markdown.tsx` + `decorateChartBlocks` + React root 生命周期：新增 / 更新 / 消失时 mount / unmount 正确；`ontology.updated` 事件到达后 `useOntologyStore` 出现匹配 origin 的 chart，ChartBlock 文案同步切到 `已在工作台` |
| 前端主题 | vitest | `buildChartTheme('light'\|'dark')` 读 CSS var 正确；`injectOptionFix` 不覆盖用户显式字段 |
| 后端单元 | JUnit 5 + AssertJ | `RenderChartAction` `sourceArtifactId` optional 三分支；`ChartArtifactService` 抽取后两条入口（action / REST）都发出 `OntologyUpdated`；origin 字段正确回写 artifact |
| 后端 REST | Spring MockMvc + session bus spy | `POST /api/sessions/{id}/artifacts/chart` 成功路径、`sourceArtifactId=null`、未知 session 404、超大 payload 413；成功后 bus 收到 `ontology.updated(objectType=datatalk.artifact, op=created)` 一次 |
| 端到端（手动冒烟） | 清单 | ① AI 流式发图：骨架 → 预览 → 稳定 ② 点 `打开到工作台`：按钮 loading → Stage 的 `ArtifactCanvas` 展示该图 + Timeline 活跃项切过去，**不新增 Stage tab 类型** ③ 刷新页面后该 ChartBlock 仍显示 `已在工作台`（由 origin 字段派生） ④ 主题切换 echarts 跟随 ⑤ 错误 JSON 不崩页 ⑥ 同一图表二次点击 `已在工作台` → Timeline 焦点切换 |

---

## 11. Non-goals（YAGNI）

- 聊天不提供图表编辑 UI（改图重新让 AI 生成）
- 不支持 `chart` 围栏内嵌 JS function / `formatter` 回调
- 不做客户端 JSON → artifactId 去重（每次 `打开到工作台` 都产生新 artifact）
- 不改 `datatalk.supersede_artifact` 语义；聊天提升不走 supersede
- 不改 `workspace.open` 支持的 tab type 枚举；图表走 Timeline + `ArtifactCanvas`，不作为新 Stage tab 类型
- 不引入新的前端 persist / storage 中间件；"已在工作台"纯由后端 artifact 的 origin 字段派生
- **发布范围**：首版只打包 6 种 ECharts 系列模块（bar/line/pie/scatter/radar/candlestick）。协议上任意合法 option 都接受，但未打包类型会走 `ChartErrorBoundary` 降级。扩类型 = 多导一个 ECharts 模块常量，无协议改动
- 不做图表导出（PNG / SVG）——延后到可视化二期

---

## 12. 迁移与回滚

- 迁移：删除 `echarts-to-recharts.ts` / `ui/chart.tsx` / `recharts` 依赖；`ChartArtifact` 全量切换到 `ChartRenderer`。现存会话的老 chart artifact 的 `echartsOption` 直接喂给 echarts 就能渲染（本就是 ECharts schema），不需要数据迁移
- 回滚：
  - 前端：revert `decorateChartBlocks` 相关 commit + 恢复 recharts 实现即可
  - 后端：`sourceArtifactId` optional 是向后兼容，不回滚；若要紧急禁用聊天围栏，可在 `markdown.tsx` 把 `language === 'chart'` 走到普通 `CodeBlock`

---

## 13. Follow-ups

- 把 amber chart 颜色（`#F59E0B` / `#FBBF24`）并入 `--dt-*` 语义变量家族
- 图表导出（PNG / SVG）
- ECharts 按需模块扩展：heatmap / sunburst / boxplot
- `打开到工作台` 支持链接分享（把 Stage chart tab URL 分享给其他会话）
- 聊天 `chart` 围栏 → 数据表联动：悬停 Stage 表格某列高亮对应图表系列
