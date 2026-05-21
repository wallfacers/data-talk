## Requirements

### Requirement: 编译器 SHALL 是无副作用的纯函数

`DashboardCompiler.compile(json)` SHALL 是纯函数:输入相同的 v3 JSON 必产出字节相同的 HTML,且 SHALL NOT 访问数据库、session、文件系统或任何外部可变状态。

#### Scenario: 相同输入产出相同 HTML
- **GIVEN** 一份合法 v3 dashboard JSON
- **WHEN** 对同一 JSON 连续调用 `compile(json)` 两次
- **THEN** 两次返回的 HTML 字符串 MUST 完全相等(可作为 LRU 缓存 key=sha256 的依据)

#### Scenario: 编译不触达 DB 或 session
- **WHEN** 在无数据库连接、无 session 上下文的环境调用 `compile(json)`
- **THEN** 编译 MUST 成功返回 HTML,不抛出与 DB/session 相关的异常

### Requirement: 编译管线 SHALL 按六阶段顺序执行

`compile(json)` SHALL 依次执行:Schema Validate → Template Resolve → Widget Compile → Option Merge → Assemble → HTML Validate。任一阶段失败 SHALL 中止并返回结构化错误(阶段名 + 字段路径 + 原因),不产出半成品 HTML。

#### Scenario: schema 校验失败中止
- **GIVEN** 一份 `schemaVersion` 不为 3 的 JSON
- **WHEN** 调用 `compile`
- **THEN** 在 Schema Validate 阶段失败,返回错误含阶段标识与字段路径
- **AND** MUST NOT 返回任何 HTML

#### Scenario: HTML 校验失败中止
- **GIVEN** 编译中途产出的 HTML 缺少 CSP meta(被某 bug 注入路径破坏)
- **WHEN** 执行到 HTML Validate 阶段
- **THEN** 编译失败并报告缺失项,不返回该 HTML

### Requirement: schemaVersion 3 SHALL 用 chartSemantics 替代裸 ECharts option

v3 widget SHALL 通过 `chartSemantics` 高层语义字段声明图表,而非要求 AI 手写完整 ECharts option。`chartSemantics.chartType` SHALL 支持 `bar | line | area | pie | funnel | scatter | radar | map` 八种;并 SHALL 提供 `rawEchartsOption` escape hatch 作为最高优先级透传。v3 SHALL 不兼容 v1/v2(`schemaVersion` 仅接受字面量 `3`)。

#### Scenario: chartSemantics 编译为 ECharts option
- **GIVEN** 一个 widget `chartSemantics: { chartType: 'line', colorScheme: 'cool', stacked: true }`
- **WHEN** 编译
- **THEN** 产出的 `baseOption` 的 series type 为 `line`、采用 cool 配色、堆叠生效

#### Scenario: 拒绝 v2 JSON
- **GIVEN** 一份 `schemaVersion: 2` 的 JSON
- **WHEN** 调用 `compile`
- **THEN** Schema Validate 失败,错误指明仅支持 schemaVersion 3

#### Scenario: 八种图表类型均可编译
- **WHEN** 分别用 `chartType` 取 bar/line/area/pie/funnel/scatter/radar/map 各编译一次
- **THEN** 每种均 MUST 成功产出非空 `baseOption`

### Requirement: ECharts Option SHALL 三级深度合并

`OptionMerger` SHALL 按 `deepMerge(Layer1, Layer2, Layer3)` 产出最终 option:Layer1=`echarts-options/{chartType}.json` 内置默认,Layer2=`chartSemantics` 字段转换,Layer3=`widget.options.rawEchartsOption` 用户透传。深合并语义 SHALL 为:object 递归合并,array 整体替换;后层 SHALL 覆盖前层同 key。

#### Scenario: rawEchartsOption 覆盖默认
- **GIVEN** Layer1 默认 `tooltip: { trigger: 'axis' }`,widget `rawEchartsOption: { tooltip: { trigger: 'item' } }`
- **WHEN** 合并
- **THEN** 最终 option `tooltip.trigger === 'item'`

#### Scenario: array 整体替换不逐元素合并
- **GIVEN** Layer1 `color: ['#a','#b','#c']`,Layer3 `color: ['#x']`
- **WHEN** 合并
- **THEN** 最终 `color === ['#x']`(不是 `['#x','#b','#c']`)

### Requirement: Widget SHALL 按具名 slot 落位,溢出时校验失败

每个 layout 模板 SHALL 在 `pattern-catalog.yaml` 的 `templates[].slots` 声明具名槽(`id` / `kind` / `capacity`)。v3 `widget.slot` SHALL 为必填,且 SHALL ∈ 当前 `layout.template` 的 slots 集合。同一 slot 内 widget 数 SHALL NOT 超过其 `capacity`,超过时 Schema Validate SHALL 失败并明确报错;未填的 slot SHALL 由 CSS 折叠隐藏(不报错)。

#### Scenario: widget 落入声明的 slot
- **GIVEN** `layout.template: 'three-column-kpi-center'`,某 widget `slot: 'kpi-rail'`,且该模板声明了 `kpi-rail` 槽
- **WHEN** 编译
- **THEN** 该 widget 的 HTML 片段 MUST 出现在模板 `data-slot="kpi-rail"` 容器内

#### Scenario: 未知 slot 校验失败
- **GIVEN** 某 widget `slot: 'nonexistent'`,当前模板无此槽
- **WHEN** 编译
- **THEN** Schema Validate 失败,错误指明该 slot 不属于所选模板

#### Scenario: slot 超容量校验失败
- **GIVEN** 某 slot `capacity: 2`,JSON 给该 slot 放了 3 个 widget
- **WHEN** 编译
- **THEN** Schema Validate 失败,错误指明该 slot 超容量

#### Scenario: 空 slot 不报错
- **GIVEN** 模板有 4 个槽,JSON 只填了 3 个
- **WHEN** 编译
- **THEN** 编译成功,未填槽在产出 HTML 中存在但为空(由 CSS `:empty` 折叠)

### Requirement: 编译产物 SHALL 保留 origin 占位符,serve 时替换

`compile` 产出的 HTML SHALL 以占位符形式保留 `__BEZEL_SERVER_ORIGIN__` 与 `/bezel/echarts.min.js` 引用,SHALL NOT 在编译期烤死具体 origin。`GET /api/dashboards/{id}/html` SHALL NOT 重新编译,但 SHALL 做 serve-time 字符串替换(注入实际请求 origin、dashboardId)。

#### Scenario: 存储 HTML 含占位符
- **WHEN** `compile` 产出 HTML 并落盘
- **THEN** 落盘 HTML MUST 含字面量 `__BEZEL_SERVER_ORIGIN__`

#### Scenario: GET 返回时替换为实际 origin
- **GIVEN** 已落盘含占位符的 HTML,请求来自 `http://localhost:8080`
- **WHEN** `GET /{id}/html`
- **THEN** 返回 HTML 中 `__BEZEL_SERVER_ORIGIN__` 已替换为 `http://localhost:8080`
- **AND** echarts 引用为绝对 URL `http://localhost:8080/bezel/echarts.min.js`
- **AND** 服务端 MUST NOT 因此重新执行 `compile`

### Requirement: 编译产物 SHALL 通过 HtmlValidator 安全校验

`HtmlValidator`(合并自原 `BezelHtmlValidator`)SHALL 在 Assemble 后校验:存在 CSP meta、存在 `__BEZEL_SERVER_ORIGIN__` 占位符、存在 `window.__BEZEL_CONFIG__`、无内联 `on*` 事件处理器、无 `unsafe-eval`、外链仅限白名单。校验失败 SHALL 阻止落库。

#### Scenario: 缺 CSP 被拒
- **GIVEN** 一份缺少 CSP meta 的 HTML
- **WHEN** `HtmlValidator` 校验
- **THEN** 返回失败,错误含 CSP 缺失标识

#### Scenario: 内联事件处理器被拒
- **GIVEN** HTML 含 `<div onclick="...">`
- **WHEN** 校验
- **THEN** 返回失败,错误指明禁止内联事件处理器

### Requirement: map 图表 SHALL 内置 geoJSON 并安全注册

`chartType: 'map'` 的 widget SHALL 由编译器内置 geoJSON(中国/世界)到 `resources/static/bezel/geo/`,`scheduler.js` SHALL 在 init 前按需 `echarts.registerMap`;CSP `connect-src` SHALL 含该同 origin 静态路径(占位符替换)。geoJSON 加载失败 SHALL 降级为提示文案,不留空白。

#### Scenario: map widget 注册地图后渲染
- **GIVEN** 一个 `chartType: 'map'` 的 widget
- **WHEN** iframe 加载
- **THEN** `echarts.registerMap` 在 `echarts.init` 之前被调用
- **AND** CSP `connect-src` 含 `__BEZEL_SERVER_ORIGIN__/bezel/geo/`

#### Scenario: geoJSON 加载失败降级
- **GIVEN** geoJSON 静态资源 404
- **WHEN** map widget 初始化
- **THEN** 该 widget 容器显示降级提示文案,不是空白
- **AND** 其他 widget 不受影响
