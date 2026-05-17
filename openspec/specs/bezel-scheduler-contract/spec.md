# bezel-scheduler-contract Specification

## Purpose

定义 bezel skill 编译产物(HTML + `__BEZEL_CONFIG__`)的运行时调度器契约,确保:
1. KPI / table / markdown 等 HTML-only widget 不被错误地 `echarts.init()`(BUG-0055 根因)
2. chart widget 首屏即可见 base option(坐标轴 / series 框架),不是空白等首次 poll
3. v1 → v2 dashboard JSON migration 自动推导历史 widget 缺失的 `type` 字段

契约同时约束:
- AI 端 emit 时遵守(由 `compile-rules.md` / `data-contract.md` / `patterns-catalog.md` / `SKILL.md` + 12 份 industry template 共同强制)
- pre-promote 阶段 `scripts/validate.py`(Python) 静态校验
- promote 阶段 `BezelHtmlValidator`(Java) regex fingerprint 守卫

## Requirements

### Requirement: `BezelWidgetConfig` SHALL 携带 widget 类型与首屏 option

`window.__BEZEL_CONFIG__.widgets[]` 的每个元素 SHALL 包含:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | Widget DOM id |
| `type` | `'chart' \| 'kpi' \| 'table' \| 'markdown' \| 'filter' \| 'section' \| 'divider' \| 'image'` | 是 | 由后端 `DashboardArtifactService` 从 JSON `widget.type` 拷入 |
| `intervalMs` | `number` | 是 | 轮询间隔 ms |
| `endpoint` | `string` | 是 | 数据 API endpoint |
| `params` | `Record<string, unknown>` | 是 | 查询附加参数 |
| `baseOption` | `object \| null` | 是 | `type === 'chart'` 时 SHALL 非 null,值为 ECharts 首屏 option;其他 type SHALL 为 null |

#### Scenario: chart widget config 携带 baseOption
- **GIVEN** dashboard JSON 中有一个 `type: 'chart'` 的 widget,`options: { xAxis: {...}, yAxis: {...}, series: [{type: 'line'}] }`
- **WHEN** `DashboardArtifactService` 编译为 HTML
- **THEN** `__BEZEL_CONFIG__.widgets[0]` 包含 `type: 'chart'`
- **AND** `baseOption` 字段等于该 widget 的完整 ECharts option

#### Scenario: kpi widget config baseOption 为 null
- **GIVEN** dashboard JSON 中有一个 `type: 'kpi'` 的 widget,`patternId: 'generic.kpi-tile'`
- **WHEN** `DashboardArtifactService` 编译为 HTML
- **THEN** `__BEZEL_CONFIG__.widgets[0]` 包含 `type: 'kpi'`
- **AND** `baseOption === null`

#### Scenario: validator 拒绝 chart 缺 baseOption
- **GIVEN** 一份手工修改过的 `__BEZEL_CONFIG__` 中,某 `type: 'chart'` widget 的 `baseOption` 为 null
- **WHEN** `scripts/validate.py` 校验该配置
- **THEN** 校验失败,错误信息含 `chart widget xxx requires non-null baseOption`

#### Scenario: validator 拒绝非 chart 携带 baseOption
- **GIVEN** 某 `type: 'kpi'` widget 配置中 `baseOption: { series: [...] }`
- **WHEN** `scripts/validate.py` 校验
- **THEN** 校验失败,错误信息含 `non-chart widget xxx must have baseOption === null`

### Requirement: Polling scheduler SHALL 按 widget type 分流初始化

iframe 加载时,polling scheduler SHALL 按以下算法初始化每个 widget,**禁止**对非 chart 类型 widget 调用 `echarts.init()`。

```
对 cfg.widgets 中每个 widget w:
  el = document.getElementById(w.id)
  if (!el) return  // 容器不存在,跳过

  if (w.type === 'chart') {
    ch = echarts.init(el)
    ch.setOption(w.baseOption)         // ← 首屏 base option,必须存在
    charts[w.id] = ch
  }
  // 非 chart 类型不 init,DOM 已由编译期 HTML 渲染好

  if (w.intervalMs > 0) {
    schedule(w)                         // 启动轮询(chart 和 html 都允许轮询)
  }
```

#### Scenario: KPI widget 不被 echarts.init
- **GIVEN** 一份混合 dashboard,包含 1 个 chart 和 1 个 kpi
- **WHEN** iframe 加载完成
- **THEN** kpi 容器内**不存在** `<canvas>` 元素
- **AND** chart 容器内存在 `<canvas>` 元素

#### Scenario: chart 首屏立即可见
- **GIVEN** 一份只含 1 个 chart 的 dashboard,`refresh.intervalMs = 30000`(30 秒后才会首次 poll)
- **WHEN** iframe 加载完成(在首次 poll 之前)
- **THEN** chart 容器内的 ECharts canvas 已渲染出坐标轴和(空 dataset 的)series 框架
- **AND** 不出现"等 30 秒才看到任何东西"的空白期

#### Scenario: chart base option 缺失时降级
- **GIVEN** 某 chart widget 的 `baseOption` 在编译期解析失败(JSON 损坏 / 不是合法 ECharts option)
- **WHEN** iframe 加载
- **THEN** 该 widget 容器显示降级提示("widget options invalid")
- **AND** 其他 widget 正常初始化,不被一个 widget 的坏 option 拖垮

### Requirement: Polling scheduler SHALL 按 widget type 分流数据应用

每轮 `fetch(widget.endpoint)` 成功后,scheduler SHALL 按以下算法应用数据,**禁止**对非 chart 类型 widget 调用 `setOption`。

```
applyWidgetData(w, data):
  if (w.type === 'chart') {
    charts[w.id].setOption({ dataset: { source: data.rows } }, { lazyUpdate: true })
  } else {
    applyHtmlData(w.type, document.getElementById(w.id), data.rows)
  }
```

其中 `applyHtmlData(kind, el, rows)` SHALL 根据 kind 选择 DOM 重写策略:
- `'kpi'` → 重写 `.value` / `.delta` / `.trend` 元素文本
- `'table'` → 重写 `<tbody>` 行
- `'markdown'` / `'section'` / `'divider'` / `'image'` / `'filter'` → 静态,SHALL 跳过(`intervalMs` 即使 > 0 也只 fetch 不渲染,但这种 widget 编译期应已设 `intervalMs: 0`)

#### Scenario: KPI 轮询更新数字
- **GIVEN** 一个 `type: 'kpi'` widget,`intervalMs: 5000`,首屏显示 `value: 100`
- **WHEN** 5 秒后服务端返回 `rows: [{ value: 150 }]`
- **THEN** widget 容器内 `.value` 元素 `textContent` 更新为 `150`
- **AND** 不调用 `echarts.setOption`

#### Scenario: chart 轮询只更新 dataset
- **GIVEN** 一个 `type: 'chart'` widget,首屏已 setOption(baseOption)
- **WHEN** 轮询返回新数据
- **THEN** 调用 `ch.setOption({ dataset: { source: rows } }, { lazyUpdate: true })`
- **AND** 不传 `series` / `xAxis` / `yAxis` 等结构字段(继承 baseOption)

### Requirement: HTML-only widget 容器 SHALL 携带 `data-bezel-render-kind` 属性

`DashboardArtifactService` 编译 HTML 时,SHALL 在每个非 chart widget 的 root DOM 元素上写入 `data-bezel-render-kind="<type>"` 属性,以供运行时 `applyHtmlData` 选择渲染策略。

#### Scenario: KPI widget 容器有 data-bezel-render-kind
- **GIVEN** 一个 `type: 'kpi'` widget
- **WHEN** 编译为 HTML
- **THEN** widget root `<div>` 上存在 `data-bezel-render-kind="kpi"` 属性

#### Scenario: chart widget 容器无 data-bezel-render-kind
- **GIVEN** 一个 `type: 'chart'` widget
- **WHEN** 编译为 HTML
- **THEN** widget root `<div>` 上**不存在** `data-bezel-render-kind` 属性
- **AND** 仅依靠 `__BEZEL_CONFIG__.widgets[].type` 判定为 chart

### Requirement: v1 → v2 migration SHALL 推导缺失的 widget type

`DashboardArtifactService.migrateV1ToV2` 处理历史 dashboard JSON 时,SHALL 按以下规则推导 `widget.type`:

- 若 `widget.type` 已存在(v2 schema)→ 直接保留
- 若仅有 `patternId` → 按 `patterns-catalog.md` 的 `renderKind` 字段反查:
  - `generic.kpi-tile` → `kpi`
  - `generic.echarts-card` / 任意 `<industry>.<chart-name>` → `chart`
  - `generic.table` → `table`
  - `generic.markdown` → `markdown`
  - `generic.section-header` → `section`
  - `generic.divider` → `divider`
  - `generic.image` → `image`
  - `generic.filter-bar` → `filter`
- 无法推导 → fallback `'chart'` + `log.warn("widget %s type not inferable, defaulting to chart")`

#### Scenario: v1 dashboard 缺 type 字段成功迁移
- **GIVEN** 一份 v1 JSON,widget 只有 `patternId: 'generic.kpi-tile'`,无 `type`
- **WHEN** `migrateV1ToV2` 处理
- **THEN** 输出 v2 JSON 该 widget `type: 'kpi'`
- **AND** `options` 字段被搬到 widget `options`(运行时再拷为 `baseOption: null`,因为非 chart)

#### Scenario: 未知 patternId 默认 chart 并记录 warn
- **GIVEN** 一份 v1 JSON,widget `patternId: 'custom.xxx'`,无 `type`
- **WHEN** `migrateV1ToV2` 处理
- **THEN** 输出 widget `type: 'chart'`
- **AND** log 中存在 `widget xxx type not inferable, defaulting to chart`
