## Context

bezel 是 DataTalk 内置的"AI 大屏生成"skill,完整链路:

```
用户 chat 请求 → AI 生成 dashboard JSON → DashboardArtifactService 编译为 HTML
                                            ↓
              iframe 加载 HTML → polling scheduler 启动 → 每个 widget 周期拉数渲染
```

JSON 是 source-of-truth(`SKILL.md:119`),HTML 是编译产物。同一份 JSON 喂给 AI 看(few-shot)+ 喂给后端编译(`DashboardArtifactService`)+ 喂给运行时调度器(`__BEZEL_CONFIG__`)。

三份 reference 文档对"调度器如何初始化 widget"的描述互相打架,**而且 `BezelWidgetConfig` schema 缺字段使得 AI 即便想按 type 分流也做不到**。详见 proposal.md "Why" 部分的证据链。

## Goals

- 把"按 widget type 分流初始化"提升为 schema 强制约束,而非靠 AI 自觉
- 让 chart 的首屏 base option 在 schema 上是显式字段(`baseOption`),不再是"埋在 patterns 里的隐含约定"
- 三份 reference 文档 + 12 份 template 同步对齐,消除 AI few-shot 噪音

## Non-Goals

- **不重写 `DashboardArtifactService` 的编译模型**(方案 C)—— 不把 base option 整段烤进 HTML 字面量,继续保留"JSON 即 source-of-truth"不变量
- **不动 postMessage 协议**(`refresh/pause` / `params/update` / `ready` / `error` 等)—— iframe 与 host 通信契约不在本次范围
- **不动 widget 业务字段**(`patternId` / `query` / `parameters` / `refresh.strategy`)—— 只增 `type` 和 `baseOption` 两个运行时配置字段

## Decisions

### Decision 1: 在 `BezelWidgetConfig` 上新增 `type` 字段而不是只看 `id` 前缀

JSON schema 里 `Widget.type` 已经存在(枚举 8 种)。运行时 `BezelWidgetConfig` 是另一个 schema,只暴露调度器需要的字段。早期省略 `type` 的设计假设是"反正调度器对所有 widget 一视同仁",这正是本 bug 的根因。

**选项 A(采纳)**: `BezelWidgetConfig` 新增 `type` 字段,与 JSON 层的 `Widget.type` 一一对应;编译期由 `DashboardArtifactService` 拷贝
- 优点:运行时 zero-cost 判断,无歧义
- 缺点:`__BEZEL_CONFIG__` 体积略增(每 widget +~20 字节)

**选项 B(拒绝)**: 调度器靠 `id` 前缀(`chart_w_xxx` / `kpi_w_xxx`)推导 type
- 缺点:把语义编进 id 是反模式;现有 id 校验正则(`[a-z]+_w_[a-zA-Z0-9]{4,16}`)不强制前缀语义,有历史 id 不符合就翻车

### Decision 2: chart 的 base option 显式放在 `BezelWidgetConfig.baseOption`,而不是从 widget.options 隐式推导

**选项 A(采纳)**: 编译期把 `widget.options`(JSON 层)整体拷到 `BezelWidgetConfig.baseOption`(运行时层),调度器首屏直接 `ch.setOption(w.baseOption)`,轮询时 `ch.setOption({dataset: {...}})`
- 优点:运行时算法明确两步,validator 可以强制 chart 类型 `baseOption` 非空且至少含 `series` 或 axes
- 缺点:JSON 中 `options` 已经存在,运行时 config 多一份相同数据;评估下来 `__BEZEL_CONFIG__` 总体积仍可控(典型 6-widget 大屏 < 8KB)

**选项 B(拒绝)**: 让 `DashboardArtifactService` 在 HTML 里生成 `charts['xxx'].setOption({...inlined option...})` 字面量(方案 C)
- 缺点:破坏 SKILL.md:119 "JSON 是 source-of-truth" 不变量 —— 同一个 option 既在 JSON 里又在 HTML 字面量里,编辑 widget 必须重编整个 HTML,iframe 无法只对单 widget 做热更
- 缺点:HTML 字面量化的 ECharts option 容易引入 JS 语法风险(string escape / function 序列化)

**选项 C(拒绝)**: 调度器在首屏 `fetch` 完后,把 `data.rows` 和"AI 生成的 option 模板"合并 setOption
- 缺点:首屏延迟 = 一次 RTT,白屏期更长;BUG-0051 / BUG-0050 已经证实这条路体验差

### Decision 3: HTML-only widget 的 refresh 路径走 `applyHtmlData(el, rows)` 而非 ECharts API

调度器内新增一个轻量 helper:

```js
function applyHtmlData(el, rows) {
  // 根据 el.dataset.bezelRenderKind 决定渲染策略:
  //   - 'kpi'        → 重写 .value / .delta DOM
  //   - 'table'      → 重写 <tbody> 行
  //   - 'markdown'   → 静态,跳过
  //   - 'section'/'divider'/'image' → 静态,跳过
  //   - 'filter'     → 静态(事件驱动)
}
```

`el.dataset.bezelRenderKind` 由 `DashboardArtifactService` 编译期写在 widget 容器元素上。validator 校验"每个 type !== 'chart' 的 widget 容器必有 `data-bezel-render-kind`"。

### Decision 4: 历史 JSON 兼容 —— v1 → v2 migration 期间自动推导 `type`

`DashboardArtifactService.migrateV1ToV2` 已存在;在迁移路径上对老 widget:
- 已有 `type` 字段(v2 schema)→ 直接拷
- 仅有 `patternId` → 按 patterns-catalog 反查 type;`generic.kpi-tile` → `kpi`,`generic.echarts-card` → `chart`,以此类推
- 无法推导 → 默认 `chart`(向后兼容,旧 dashboard 全部是 echarts 容器),并 log.warn

## Risks

| 风险 | 缓解 |
|---|---|
| 历史 dashboard JSON 缺 `widget.options` 或 `options` 不是合法 ECharts base option,迁移后首屏直接报错 | `DashboardArtifactService` 迁移期 try-catch:解析失败 → `baseOption = { title: { text: 'widget options invalid', ... } }`,iframe 显示降级提示而非崩 |
| 12 份 template 改 `bindWidget` 时不一致,AI 学到混杂 few-shot | tasks.md 单独列 "template 一致性 sweep",改完跑 `scripts/validate.py` 对每个 template 走一遍 |
| `BezelWidgetConfig` 新字段被前端 host 的 `iframe-protocol.ts` 校验拒掉 | postMessage 协议无关 `BezelWidgetConfig`,host 不消费此结构;无需改 |
| BUG-0050 / BUG-0051 已 fixed 但本质未根治,新算法上线后是否引入回归 | 上线前在 e2e `dashboard-bezel-v2.spec.ts` 加 KPI + chart 混合 dashboard 的 fixture,断言 KPI 容器不含 canvas、chart 容器首屏即可见 series |
| validate.py 校验过严,生产已部署的 dashboard JSON 反向校验失败 | validator 仅在 promote 时跑,不阻塞运行时;失败 JSON 转 v2 时由 migration 兜底补字段 |

## Migration Plan

1. **Phase 1 — 契约文档**: 改 `data-contract.md` / `compile-rules.md` / `patterns-catalog.md` 三份 reference + `SKILL.md` 第 121 行的描述
2. **Phase 2 — 编译期**: 改 `DashboardArtifactService`,`__BEZEL_CONFIG__` 注入新增 `type` / `baseOption` 字段;`migrateV1ToV2` 加 type 推导
3. **Phase 3 — 调度器**: 重写 12 份 template 的 `bindWidget` + 新增 `applyHtmlData` helper
4. **Phase 4 — 校验**: `scripts/validate.py` 加新规则,跑一遍所有 template 确认通过
5. **Phase 5 — 测试**: e2e 加 KPI + chart 混合 dashboard fixture;单元测试覆盖 `DashboardArtifactService` 的 type 注入和 migration 推导逻辑
6. **Phase 6 — BUG 回归验证**: 跑一次完整大屏生成,确认 BUG-0050 / BUG-0051 / BUG-0055 三个 BUG 的复现步骤全部不再触发,update 三份 BUG 文档状态

## Open Questions

- `BezelWidgetConfig.baseOption` 是否需要支持函数序列化(ECharts option 里偶尔有 `formatter: function(p){...}`)?当前 JSON 限制下函数会丢。**倾向**: 不支持,formatter 全部改成字符串模板(ECharts 5+ 支持),AI 文档明确禁用函数
- HTML-only widget 真的需要轮询吗?当前 `kpi-tile` 的 `refresh.intervalMs: 5000`,意味着 KPI 数字会变。需要确认产品意图:是 KPI 也要拉数刷新,还是 KPI 只首屏拉一次?**倾向**: 保留轮询能力(KPI 数字确实可能变化),`applyHtmlData` 实现要支持
