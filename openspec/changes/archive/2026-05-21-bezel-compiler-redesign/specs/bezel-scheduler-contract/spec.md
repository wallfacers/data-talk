## MODIFIED Requirements

### Requirement: `BezelWidgetConfig` SHALL 携带 widget 类型与首屏 option

`window.__BEZEL_CONFIG__.widgets[]` 的每个元素 SHALL 包含:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | Widget DOM id |
| `type` | `'chart' \| 'kpi' \| 'table' \| 'markdown' \| 'filter' \| 'section' \| 'divider' \| 'image'` | 是 | 由 `pattern-catalog.yaml` 的 `renderKind` 推导(`chart` → `chart`,其余按 pattern 类型) |
| `intervalMs` | `number` | 是 | 轮询间隔 ms |
| `endpoint` | `string` | 是 | 数据 API endpoint |
| `params` | `Record<string, unknown>` | 是 | 查询附加参数 |
| `baseOption` | `object \| null` | 是 | `type === 'chart'` 时 SHALL 非 null,值为 `OptionMerger` 三级合并产出的首屏 ECharts option;其他 type SHALL 为 null |

`baseOption` SHALL 由服务端 `OptionMerger` 确定性产出(不再由 AI 手写)。chart/非 chart 的 `baseOption` 约束 SHALL 由编译期 `HtmlValidator`(Java regex fingerprint)守卫,不再由 `scripts/validate.py` 校验(该脚本已移除)。

#### Scenario: chart widget config 携带 baseOption
- **GIVEN** dashboard JSON 中有一个 chart widget,`chartSemantics: { chartType: 'line' }`
- **WHEN** `DashboardCompiler` / `WidgetCompiler` 编译为 HTML
- **THEN** `__BEZEL_CONFIG__.widgets[0]` 包含 `type: 'chart'`
- **AND** `baseOption` 为 `OptionMerger` 产出的完整 ECharts option(含 line series)

#### Scenario: kpi widget config baseOption 为 null
- **GIVEN** dashboard JSON 中有一个 kpi widget,`patternId: 'generic.kpi-tile'`(catalog `renderKind: html`)
- **WHEN** 编译为 HTML
- **THEN** `__BEZEL_CONFIG__.widgets[0]` 包含 `type: 'kpi'`
- **AND** `baseOption === null`

#### Scenario: HtmlValidator 拒绝 chart 缺 baseOption
- **GIVEN** 编译产物 `__BEZEL_CONFIG__` 中某 chart widget 的 `baseOption` 为 null
- **WHEN** `HtmlValidator` 校验该 HTML
- **THEN** 校验失败,错误信息含 chart widget 需非 null baseOption 的标识
- **AND** 该 HTML 不落库

#### Scenario: HtmlValidator 拒绝非 chart 携带 baseOption
- **GIVEN** 某 kpi widget 配置中 `baseOption` 非 null
- **WHEN** `HtmlValidator` 校验
- **THEN** 校验失败,错误信息含非 chart widget 必须 baseOption===null 的标识

### Requirement: Polling scheduler SHALL 按 widget type 分流初始化

scheduler SHALL 由编译器 `SchedulerBundler` 注入(`scheduler.js` IIFE),不再由 AI emit。iframe 加载时,scheduler SHALL 按以下算法初始化每个 widget,**禁止**对非 chart 类型 widget 调用 `echarts.init()`:

```
对 cfg.widgets 中每个 widget w:
  el = document.getElementById(w.id)
  if (!el) return  // 容器不存在,跳过

  if (w.type === 'chart') {
    if (w.baseOption?.__needsMap) echarts.registerMap(...)  // map 类型按需注册
    ch = echarts.init(el)
    ch.setOption(w.baseOption)         // ← 首屏 base option,必须存在
    charts[w.id] = ch
  }
  // 非 chart 类型不 init,DOM 已由编译期 HTML 渲染好

  if (w.intervalMs > 0) {
    schedule(w)                         // 启动轮询(chart 和 html 都允许轮询)
  }
```

scheduler SHALL 额外监听 host `postMessage({ type: 'widget/update', widgetId, baseOption, html? })`:对 chart widget `charts[widgetId].setOption(baseOption)`,对 HTML-only widget 替换 `el.innerHTML = html`,以支持单 widget 增量热更新而不重载 iframe。

#### Scenario: KPI widget 不被 echarts.init
- **GIVEN** 一份混合 dashboard,包含 1 个 chart 和 1 个 kpi
- **WHEN** iframe 加载完成
- **THEN** kpi 容器内**不存在** `<canvas>` 元素
- **AND** chart 容器内存在 `<canvas>` 元素

#### Scenario: chart 首屏立即可见
- **GIVEN** 一份只含 1 个 chart 的 dashboard,`refresh.intervalMs = 30000`
- **WHEN** iframe 加载完成(在首次 poll 之前)
- **THEN** chart 容器内的 ECharts canvas 已渲染出坐标轴和(空 dataset 的)series 框架
- **AND** 不出现空白等待期

#### Scenario: chart base option 缺失时降级
- **GIVEN** 某 chart widget 的 `baseOption` 在编译期解析失败
- **WHEN** iframe 加载
- **THEN** 该 widget 容器显示降级提示
- **AND** 其他 widget 正常初始化

#### Scenario: 收到 widget/update 增量热更新
- **GIVEN** iframe 已加载一个 chart widget
- **WHEN** host `postMessage({ type: 'widget/update', widgetId, baseOption })`
- **THEN** 该 widget 的 ECharts 实例调用 `setOption(baseOption)`
- **AND** iframe 未被整体重载

### Requirement: HTML-only widget 容器 SHALL 携带 `data-bezel-render-kind` 属性

`WidgetCompiler` 编译 HTML 时,SHALL 在每个非 chart widget 的 root DOM 元素上写入 `data-bezel-render-kind="<type>"` 属性,以供运行时 `applyHtmlData` 选择渲染策略。

#### Scenario: KPI widget 容器有 data-bezel-render-kind
- **GIVEN** 一个 kpi widget
- **WHEN** 编译为 HTML
- **THEN** widget root `<div>` 上存在 `data-bezel-render-kind="kpi"` 属性

#### Scenario: chart widget 容器无 data-bezel-render-kind
- **GIVEN** 一个 chart widget
- **WHEN** 编译为 HTML
- **THEN** widget root `<div>` 上**不存在** `data-bezel-render-kind` 属性
- **AND** 仅依靠 `__BEZEL_CONFIG__.widgets[].type` 判定为 chart

## REMOVED Requirements

### Requirement: v1 → v2 migration SHALL 推导缺失的 widget type

**Reason**: 本变更彻底删除 v1/v2 schema 支持(经确认无需考虑老实现),`DashboardArtifactService.migrateV1ToV2` 一并移除;编译器仅接受 schemaVersion 3。widget `type` 由 `pattern-catalog.yaml` 的 `renderKind` 在编译期确定,不再需要从历史 JSON 推导。

**Migration**: 无数据迁移路径。存量 v1/v2 dashboard 文件不再可被编辑(仍可通过已落盘 HTML 经 `GET /{id}/html` 查看);如需继续编辑须重新由 AI 生成 v3 JSON。
