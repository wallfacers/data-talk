## Requirements

### Requirement: promote SHALL 接收纯 JSON 并由服务端编译

`POST /api/dashboards/promote` SHALL 接收 `{ dashboard: <v3 JSON> }`(不再接收 AI 生成的 HTML),服务端调用编译器产出 HTML,落库 JSON + 编译 HTML,返回 `{ id, version, html }`。

#### Scenario: promote 只传 JSON
- **GIVEN** 一份合法 v3 dashboard JSON,请求体不含 html 字段
- **WHEN** `POST /promote`
- **THEN** 返回 `{ id, version: 1, html }`,html 为编译器产出
- **AND** 落盘的 `.html` 与编译结果一致

#### Scenario: promote 拒绝非法 JSON
- **GIVEN** 一份 schema 校验失败的 JSON
- **WHEN** `POST /promote`
- **THEN** 返回 4xx,body 含字段级错误,不落库

### Requirement: update SHALL 接收完整 JSON,经 Differ 决定增量/全量

`POST /api/dashboards/{id}/update` SHALL 接收 `{ dashboard: <完整 v3 JSON>, baseVersion }`,执行乐观锁检查后由 `DashboardDiffer.diff(old, new)` 决定编译范围:`layout.template` 或 `theme` 变化、或 widget 增减 → 全量(返回 `{ version, html }`);仅 ≤3 个 widget 的属性变化 → 增量(返回 `{ version, changes: [{ widgetId, baseOption, html? }] }`)。AI SHALL NOT 提交 JSON Patch。

#### Scenario: 仅改一个 widget 走增量
- **GIVEN** 已存 dashboard,新 JSON 只把一个 chart widget 的 `chartSemantics.chartType` 从 bar 改为 line
- **WHEN** `POST /{id}/update`
- **THEN** 响应为增量格式 `{ version, changes: [{ widgetId, baseOption }] }`
- **AND** changes 长度为 1

#### Scenario: 改 template 走全量
- **GIVEN** 新 JSON 的 `layout.template` 与旧不同
- **WHEN** `POST /{id}/update`
- **THEN** 响应为全量格式 `{ version, html }`

#### Scenario: baseVersion 过期触发乐观锁冲突
- **GIVEN** 已存 dashboard 当前 version=5,请求 `baseVersion=3`
- **WHEN** `POST /{id}/update`
- **THEN** 返回冲突错误(409 或等价),不覆盖落库

#### Scenario: 超过 3 个 widget 变化走全量
- **GIVEN** 新 JSON 改了 4 个 widget 的属性
- **WHEN** `POST /{id}/update`
- **THEN** 响应为全量格式 `{ version, html }`

### Requirement: preview SHALL 编译但不落库

`POST /api/dashboards/{id}/preview`(或无 id 形式)SHALL 接收 v3 JSON,编译并返回 `{ html }`,SHALL NOT 落库、SHALL NOT 递增 version。

#### Scenario: preview 不改变存储
- **GIVEN** 已存 dashboard version=2
- **WHEN** `POST /{id}/preview` 提交一份改动后的 JSON
- **THEN** 返回 `{ html }`
- **AND** 存储的 dashboard version 仍为 2,JSON 未变

### Requirement: 旧 PATCH(JSON-Patch)接口 SHALL 移除

`PATCH /api/dashboards/{id}`(JSON-Patch ops + `JsonPatchApplier`)SHALL 被移除;所有迭代修改 SHALL 统一经 `POST /api/dashboards/{id}/update` 提交完整 v3 JSON。

#### Scenario: 旧 PATCH 不再可用
- **WHEN** 向 `PATCH /api/dashboards/{id}` 提交 JSON-Patch ops
- **THEN** 该接口 MUST 已移除(404 / 405),迭代统一走 `POST /{id}/update`

### Requirement: iframe SHALL 支持全量 srcDoc 与单 widget postMessage 双通道热更新

`iframe-protocol.ts` SHALL 新增 `widget/update` 消息类型:`{ type: 'widget/update', widgetId, baseOption, html? }`,并以 Zod 校验。`scheduler.js` IIFE SHALL 内置该消息 handler:对 chart widget `setOption(baseOption)`,对 HTML-only widget 替换 innerHTML。全量更新 SHALL 用新 `srcDoc` 替换 iframe。host SHALL 比对响应 `version`,version 跳跃时 SHALL 强制全量重载兜底。

#### Scenario: 增量更新单个 chart 不重载 iframe
- **GIVEN** iframe 已加载,host 收到增量响应 `changes: [{ widgetId: 'bar_w_sales1', baseOption }]`
- **WHEN** host `postMessage({ type: 'widget/update', widgetId: 'bar_w_sales1', baseOption })`
- **THEN** 该 widget 的 ECharts 实例调用 `setOption(baseOption)`
- **AND** iframe 的 srcDoc MUST NOT 被整体替换(无整页闪烁)

#### Scenario: 全量更新替换 srcDoc
- **GIVEN** host 收到全量响应 `{ version, html }`
- **WHEN** 应用更新
- **THEN** iframe srcDoc 被新 html 替换

#### Scenario: version 跳跃强制全量兜底
- **GIVEN** iframe 当前渲染 version=4,host 收到增量响应 version=7(中间漏了若干增量)
- **WHEN** host 处理该响应
- **THEN** host MUST 改走全量重载(拉取 `GET /{id}/html`)而非应用增量

### Requirement: 聊天内 DashboardBlock SHALL 经 preview API 渲染缩略,Open 时编译落库

`dashboard-block.tsx` 在 JSON 解析成功后 SHALL 调用 `preview` API 取编译 HTML 渲染缩略 iframe(不落库);"Open to Workbench" SHALL 调用 `promoteDashboard(json)`(不带 html 参数)由服务端编译落库。`promoteDashboard` 签名 SHALL 去除 html 参数。

#### Scenario: 预览不落库
- **GIVEN** 聊天消息含一段合法 v3 dashboard JSON
- **WHEN** DashboardBlock 渲染预览
- **THEN** 调用 `preview` API 取 html 渲染缩略
- **AND** 此时未产生任何落库的 dashboard 记录

#### Scenario: Open 时落库
- **WHEN** 用户点击 "Open to Workbench"
- **THEN** 调用 `promoteDashboard(json)`(无 html 参数)
- **AND** 服务端编译并落库,返回 `{ id, version }`
