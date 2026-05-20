## MODIFIED Requirements

### Requirement: bezel SKILL.md pre-emit checklist 强制 widget id 校验

重写后的精简 `SKILL.md`(~30 行)的 "Rules" 段(或等价的 pre-emit 约束段)SHALL 保留一项明确要求 AI 在 emit 前对每个 `widget.id` 跑一次正则 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`,并 MUST 显式禁用短英文缩写后缀(如 `gmv`、`cpu`、`qps` 等 ≤3 字符的纯字母后缀),给出推荐替代(如 `gmv01` / `total_gmv`)。即便 SKILL.md 精简、reference 改为 YAML 自动生成,该 widget id 校验项 SHALL NOT 丢失。

#### Scenario: 精简后的 SKILL.md 仍含 widget id 校验项

- **WHEN** 解析重写后 SKILL.md 的 Rules / pre-emit 约束段
- **THEN** MUST 出现一项含正则 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 字面量或等价描述的约束项
- **AND** 该项 MUST 含至少一个不被推荐的后缀字面量(如 `gmv` / `cpu` / `qps` 之一)
- **AND** 该项 MUST 含至少一个推荐的替代后缀字面量(如 `gmv01` / `total_gmv` 之一)

## ADDED Requirements

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
