# dashboard-emit-feedback Specification

## Purpose
TBD - created by archiving change bezel-widget-id-feedback. Update Purpose after archive.
## Requirements
### Requirement: bezel SKILL.md widget id 规则与 schema 同源

`server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md` 中描述 widget id 命名规则的段落 SHALL 与前端 `client/src/features/dashboard/schema.ts` 的 `widget.id` Zod 正则、后端 `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json` 的 `widget.id` JSON Schema pattern 完全一致：`^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`。SKILL.md MUST 直接附正则原文（用 inline code），并 MUST 给出至少一个 ✓ 例子（如 `kpi_w_orders01`）和至少一个 ✗ 例子（如 `kpi_w_gmv`，并标注"后缀仅 3 字符不满足 ≥4"）。

#### Scenario: 三处 widget id 规则一致

- **WHEN** 取出 SKILL.md 中 widget id 的字符约束描述、`schema.ts` 中 `widget.id` 的正则字面量、`dashboard-schema.json` 中 `widget.id` 的 pattern 字段
- **THEN** 三者所表示的字符集与长度区间 MUST 等价（字符集 `[a-z]+_w_[a-zA-Z0-9_]`、长度 4–32）

#### Scenario: SKILL.md 含正反例

- **WHEN** 解析 SKILL.md 中 widget id 段落
- **THEN** MUST 至少出现一个标记为有效（如 ✓ 或 "valid"）的 widget id 字面量例
- **AND** MUST 至少出现一个标记为无效（如 ✗ 或 "invalid"）的 widget id 字面量例
- **AND** 无效例 MUST 触发同 SKILL.md 段落中描述的失败原因（如"后缀长度不足"）

### Requirement: bezel SKILL.md pre-emit checklist 强制 widget id 校验

重写后的精简 `SKILL.md`(~30 行)的 "Rules" 段(或等价的 pre-emit 约束段)SHALL 保留一项明确要求 AI 在 emit 前对每个 `widget.id` 跑一次正则 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`,并 MUST 显式禁用短英文缩写后缀(如 `gmv`、`cpu`、`qps` 等 ≤3 字符的纯字母后缀),给出推荐替代(如 `gmv01` / `total_gmv`)。即便 SKILL.md 精简、reference 改为 YAML 自动生成,该 widget id 校验项 SHALL NOT 丢失。

#### Scenario: 精简后的 SKILL.md 仍含 widget id 校验项

- **WHEN** 解析重写后 SKILL.md 的 Rules / pre-emit 约束段
- **THEN** MUST 出现一项含正则 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 字面量或等价描述的约束项
- **AND** 该项 MUST 含至少一个不被推荐的后缀字面量(如 `gmv` / `cpu` / `qps` 之一)
- **AND** 该项 MUST 含至少一个推荐的替代后缀字面量(如 `gmv01` / `total_gmv` 之一)

### Requirement: DashboardBlock Zod 错误卡按字段定位 + 人话翻译

`client/src/features/chat/components/markdown/dashboard-block.tsx` 在 `dashboardSchema.safeParse` 失败时 SHALL 把每个 Zod issue 转换为结构化条目 `{ path: string, friendly: string }`：`path` 为人类可读字段路径（如 `widgets[0].id`），`friendly` 为 i18n 解析后的人话解释。错误卡折叠区 MUST 按条目逐行展示。已知正则失败 4 类（widget id / dashboard id / patternId / theme）MUST 命中显式映射；未知 issue MUST 回退展示 `issue.message` 原文（不得吞掉）。条目数 MUST 等于 Zod issues 数。

#### Scenario: widget id 后缀过短显示中文人话

- **GIVEN** chat 中含一段 dashboard JSON，其 `widgets[0].id = "kpi_w_gmv"`（其余字段均合规）
- **WHEN** `DashboardBlock` 渲染并展开错误明细
- **THEN** 条目列表 MUST 至少含一条 `path` 为 `widgets[0].id` 的条目
- **AND** 该条目的 `friendly` 文本 MUST 含字符串 `kpi_w_gmv`
- **AND** `friendly` 文本 MUST 提示后缀长度不足（含数字 3 或字符串 "≥4" 之一）

#### Scenario: dashboard.id 格式错显示中文人话

- **GIVEN** chat 中含一段 dashboard JSON，其 `id = "my-dashboard"`（违反 `^dash_[a-zA-Z0-9_]{4,}$`）
- **WHEN** `DashboardBlock` 渲染并展开错误明细
- **THEN** 条目列表 MUST 至少含一条 `path` 为 `id` 或 `dashboard.id` 的条目
- **AND** 该条目的 `friendly` 文本 MUST 含 `dash_` 前缀提示

#### Scenario: 多 issue 同时存在全部展示

- **GIVEN** 一段 dashboard JSON 同时违反 widget id 正则与 theme 正则
- **WHEN** `DashboardBlock` 渲染并展开错误明细
- **THEN** 条目列表 MUST 至少含 2 条
- **AND** 每条 `path` MUST 与 Zod issue 的真实 `path` 数组对齐（不丢、不重）

#### Scenario: 未知 Zod issue 回退原文

- **GIVEN** 一段 dashboard JSON 触发了 humanizer 未覆盖的 Zod issue（如 `version` 字段为字符串而非数字，触发 `invalid_type`）
- **WHEN** `DashboardBlock` 渲染并展开错误明细
- **THEN** 条目列表 MUST 含一条对应该 issue 的条目
- **AND** 条目的 `friendly` 文本 MUST 等于（或包含）该 Zod issue 的 `message` 原文，**不得**为空字符串

### Requirement: DashboardBlock 错误卡保留原始 JSON 复制路径

错误卡 SHALL 在结构化条目列表之外，提供一个独立的可展开区域，展示触发解析失败的原始 JSON 全文。该区域 MUST 可被键盘聚焦、文字 MUST 可被选中复制，字体 MUST 是 monospace（与原 `<pre>` 形态一致）。

#### Scenario: 原始 JSON 仍可复制

- **GIVEN** 一段触发解析失败的 dashboard JSON
- **WHEN** 用户在错误卡里展开 "原始 JSON" 区域
- **THEN** 该区域 MUST 含与原始 JSON 文本一致的内容
- **AND** 该内容 MUST 可通过浏览器原生选中 + 复制操作复制到剪贴板（即不被 `user-select: none` 限制）

### Requirement: 错误卡 UI 严格映射 client/DESIGN.md token

`DashboardBlock` 错误卡的颜色、字体、动画 MUST 仅使用 `client/DESIGN.md` 中已定义的 semantic token，不得出现 raw primitive color。具体：

- 错误卡边框/背景：`status.danger` / `status.dangerSurface`
- 结构化条目 path 标签：`typography.mono-sm`
- 结构化条目 friendly 文字：`typography.ui-sm` + `text.base`
- 折叠 chevron 动画：`motion.fast` + `motion.easing.standard`
- chevron 颜色：`text.muted`

不依赖颜色单独传达"哪条错了"——条目文字本身已含 path 与解释。

#### Scenario: 错误卡用 token 不用 raw color

- **WHEN** grep `dashboard-block.tsx` 中错误卡相关 className 或 inline style
- **THEN** MUST 不出现 `#`-开头的十六进制颜色字面量与 `rgb(`/`rgba(` 函数调用（用于错误卡的颜色相关属性）
- **AND** 颜色 / 字体 / 动画属性 MUST 全部走 CSS 变量（`--dt-*`）或 Tailwind class 映射到 `client/DESIGN.md` 已声明 token

### Requirement: DashboardBlock SHALL 只解析 dashboard fence,不再读取 dashboard-html

chat markdown 渲染管线 SHALL 只识别并处理 `dashboard` fenced code block(v3 JSON)。`dashboard-html` fence SHALL NOT 再被解析或附加到 dashboard mount;若历史/缓存消息仍含 `dashboard-html`,管线 SHALL 静默忽略它(不渲染原始 HTML、不报错)。`DashboardBlock` 组件 SHALL NOT 再接收 `html` prop。

#### Scenario: 只处理 dashboard fence
- **GIVEN** 一条聊天消息含一个 `dashboard` fence 与(陈旧的)一个 `dashboard-html` fence
- **WHEN** markdown 管线渲染
- **THEN** 只有 `dashboard` fence 被解析为 DashboardBlock
- **AND** `dashboard-html` 的内容 MUST NOT 出现在渲染结果中(既不作为原始 HTML 文本,也不作为 iframe)

#### Scenario: 无 dashboard-html 时正常渲染
- **GIVEN** 一条只含 `dashboard` fence(v3 JSON)的消息
- **WHEN** 渲染
- **THEN** DashboardBlock 正常渲染预览(经 preview API 取编译 HTML),无 "Missing HTML" 警告
