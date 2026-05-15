# BUG 索引

DataTalk 运行时缺陷的集中记录。所有 BUG 详情请进单文件查看。

## 写作协议

新建 / 修改 BUG 文档前，**MUST** 先读 [README.md](README.md)（模板、字段语义、状态流转、index.md 同步清单）。

## 当前编号

下一个分配 ID：**BUG-0054**（永不复用，单调递增）

## Open BUGs（按 priority 倒序，P0 → P2）

| ID | Title | Priority | Source | Modules | Discovered |
|----|-------|----------|--------|---------|------------|
| —  | —     | —        | —      | —       | —          |


## In Progress（status = investigating | fixed 等待 verify）

| ID | Title | Status | Priority | Owner |
|----|-------|--------|----------|-------|
| [BUG-0053](BUG-0053-bezel-ai-widget-id-too-short-and-zod-error-unhelpful.md) | bezel AI 生成 widget id 后缀过短被前端 Zod 拒，且错误提示无法定位字段 | investigating | P1 | — |
| [BUG-0052](BUG-0052-long-session-empty-canvas-streaming-flag-race.md) | 重新打开 streaming=busy 的会话时历史消息全空，根因是 streaming flag 与 history fetch 的并发竞争 | fixed | P1 | — (ad7c6a2b) |
| [BUG-0051](BUG-0051-dashboard-iframe-long-blank-screen.md) | Dashboard tab iframe 加载长时间白屏（loader 早卸 + 外网 CDN） | fixed | P1 | — (pending) |
| [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md) | 大屏 JSON 模式 widget 仅渲染骨架，未调接口取数 + 文本乱码 | fixed | P1 | — (pending) |
| [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) | bezel 大屏 HTML iframe 内中文字符显示乱码 | fixed | P2 | — (pending) |
| [BUG-0035](BUG-0035-dialog-bg-canvas-typo-causes-transparent-background.md) | 数据源/凭据管理设置页删除弹框背景半透明，bg-canvas 类拼写错误 | fixed | P2 | — |
| [BUG-0013](BUG-0013-http-request-null-output-fields.md) | http_request action returns null for required output fields causing schema validation failure | fixed | P1 | — |
| [BUG-0014](BUG-0014-ssrf-deny-list-not-blocking-169-254.md) | SSRF deny list not blocking 169.254.169.254 with e2e profile | fixed | P1 | — |
| [BUG-0015](BUG-0015-oversized-payload-not-marked-failed.md) | Oversized payload not marked as failed with INGESTION_PAYLOAD_TOO_LARGE | fixed | P1 | — |
| [BUG-0017](BUG-0017-http-request-missing-payload-format.md) | http_request action output missing `payloadFormat` field | fixed | P1 | — |
| [BUG-0018](BUG-0018-basic-auth-not-base64.md) | Basic auth header sent cleartext instead of Base64-encoded | fixed | P0 | — |
| [BUG-0019](BUG-0019-page-pagination-ignores-hasmore.md) | PAGE pagination ignores `hasMore` termination signal | fixed | P1 | — |
| [BUG-0020](BUG-0020-offset-pagination-ignores-nextoffset.md) | OFFSET pagination ignores `nextOffset=null` termination | fixed | P1 | — |
| [BUG-0021](BUG-0021-cursor-pagination-missing-next-key.md) | CURSOR pagination misses top-level `next` key | fixed | P1 | — |
| [BUG-0022](BUG-0022-csv-html-parsers-no-coercion.md) | CSV / HTML parsers emit raw strings — no numeric / boolean coercion | fixed | P1 | — |
| [BUG-0023](BUG-0023-integer-64-promotion-gap.md) | `INTEGER_64` promotion gap for values > 2^31 | fixed | P2 | — |
| [BUG-0024](BUG-0024-upstream-401-not-mapped-to-auth-failed.md) | Upstream 401 → `INGESTION_FETCH_FAILED` instead of `INGESTION_AUTH_FAILED` | fixed | P1 | — |
| [BUG-0025](BUG-0025-infer-type-lowercase-mismatch.md) | `infer_ingestion_schema` emits lowercase `type` instead of canonical enum name | fixed | P1 | — |
| [BUG-0026](BUG-0026-html-fetch-throws-unsupported.md) | `http_request` with `payloadFormat=html` throws `UnsupportedOperationException` | fixed | P1 | — |
| [BUG-0027](BUG-0027-pagination-top-level-aliases-ignored.md) | `http_request` ignores top-level pagination shortcuts (`param`/`initial`/`pageSize`) | fixed | P1 | — |
| [BUG-0028](BUG-0028-tabular-source-path-missing-dollar.md) | CSV / HTML parsers emit `sourcePath = <header>` instead of `$.<header>` | fixed | P2 | — |
| [BUG-0029](BUG-0029-confirm-status-violates-check-constraint.md) | `POST /jobs/{id}/confirm` HTTP 500 — `status='confirmed'` violates CHECK constraint | fixed | P0 | — |
| [BUG-0030](BUG-0030-confirm-missing-mapping-gate.md) | `confirm` 缺 mapping / terminal-state 校验，可对未 infer 或已 cancelled job 发 token | fixed | P1 | — |
| [BUG-0031](BUG-0031-action-output-schema-rejects-null-and-missing-errorcode.md) | `create_ingestion_table` / `ingest_payload` 输出 schema 拒 null + 缺顶层 `errorCode`，吞掉根因 | fixed | P1 | — |
| [BUG-0032](BUG-0032-h2-fixture-uses-database-not-databasename.md) | `seedH2Connection` fixture 字段名笔误 → H2 fallback `mem:test` 全测试共享 | fixed | P1 | — |
| [BUG-0033](BUG-0033-json-jsonl-nullable-only-on-all-null.md) | JSON/JSONL parser 只在全 null 时标 nullable，单元素 null 触发 DDL NOT NULL → INSERT 失败 | fixed | P1 | — |
| [BUG-0034](BUG-0034-executesql-fixture-missing-source.md) | `executeSql` fixture 缺 `source` → 后端 `validateSource` 抛 400 | fixed | P2 | — |
| [BUG-0036](BUG-0036-skills-extracted-to-wrong-cwd-not-found-by-opencode.md) | bezel / data-ingestion skill 解压到 JVM cwd 而非 OpenCode 进程 cwd，OpenCode 找不到 skill | fixed | P1 | — |
| [BUG-0037](BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) | AI streaming 期间 CTRL+R 让转圈停止按钮误回"待发送"态 | fixed | P1 | — |
| [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) | CTRL+R 后 GET /subscribe 重放历史 session.idle 立刻清空 streamingBySession（BUG-0037 残留路径） | fixed | P1 | — |
| [BUG-0039](BUG-0039-composer-draft-sync-write-wrong-schema.md) | setComposerDraft 同步写入 schema 错位，CTRL+R 后已发送内容回填到输入框 | fixed | P1 | — |
| [BUG-0040](BUG-0040-agents-md-skill-path-triggers-llm-hallucination.md) | AGENTS.md 引用 `skills/data-ingestion/SKILL.md` 触发 LLM 幻觉绝对路径 Read 卡住 | fixed | P1 | — |
| [BUG-0041](BUG-0041-sql-code-block-theme-color-mismatch.md) | 聊天 SQL 代码块 Shiki 高亮在 dark 主题下串成 light 色板，identifier 几乎不可见 | fixed | P2 | — |
| [BUG-0046](BUG-0046-composer-button-refresh-stream-state-mismatch.md) | CTRL+R 刷新 streaming 中 → composer 按钮回退到"待发送"（首次刷新场景，regression） | fixed | P1 | — (2bc199f1) |
| [BUG-0047](BUG-0047-bezel-dashboard-unreachable-from-ai-and-block-render-fails.md) | bezel 大屏 skill 从 AI 端不可触达，且 chat 中 DashboardBlock 渲染抛 i18n 错误 | fixed | P1 | — |
| [BUG-0048](BUG-0048-dashboard-promote-v1-misses-html.md) | v1 dashboard promote 不透传 HTML，stage iframe 永远显示 missing 占位 | fixed | P1 | — |

## Recently Closed（最近 30 天，status = verified | closed）

| ID | Title | Status | Closed Date | FixCommit |
|----|-------|--------|-------------|-----------|
| [BUG-0044](BUG-0044-user-bubble-markdown-invisible-on-primary-bg.md) | 用户气泡 Markdown 代码块/表格白字白底（双主题均不可读） | verified | 2026-05-14 | c28058c5 |
| [BUG-0043](BUG-0043-sql-editor-tab-switch-loses-default-connection.md) | Chat Run SQL 打开多个 SQL 编辑器，切换 tab 导致默认 connection 丢失 | verified | 2026-05-14 | bbbdf015 |
| [BUG-0042](BUG-0042-sql-editor-session-follow-mode-ignores-connection-default-database.md) | SQL editor session-follow 模式下不应用 connection 默认 database | verified | 2026-05-14 | bbbdf015 |
| [BUG-0045](BUG-0045-ingestion-sweeper-it-missing-schema.md) | IngestionHeartbeatSweeperIT / IngestionStartupSweeperIT 测试库无 ingestion_job 表 | verified | 2026-05-14 | 6403fa9c |
| [BUG-0012](BUG-0012-widget-data-endpoint-ignores-default-connection-id.md) | Widget data endpoint 缺少 dashboard 级 database / schema 解析回路 | verified | 2026-05-14 | b8060d9 |
| [BUG-0011](BUG-0011-sql-result-display-test-dialogclose-mock-missing.md) | sql-result-display 测试缺少 DialogClose mock 导致 10 个用例失败 | verified | 2026-05-14 | 49f2e85 |
| [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md) | 聊天气泡内 ECharts X 轴标题（xAxis.name）右侧被裁 | verified | 2026-05-14 | 6bb1c93 |
| [BUG-0008](BUG-0008-stage-trash-last-tab-blank-pane.md) | Stage 永久删除最后一个 tab 后右侧工作区空白 | verified | 2026-05-14 | — |
| [BUG-0016](BUG-0016-credentials-section-missing-from-settings-dropdown.md) | Settings 下拉菜单缺少 Credentials 入口，无法通过 UI 导航到凭据页面 | verified | 2026-05-12 | 434d1ae9 |
| BUG-0001 | ER Inspector "Add virtual relation" 按钮无效 | verified | 2026-05-06 | — |
| BUG-0002 | ER Designer bind_target 成功但 diff_against_db / generate_ddl 仍拒绝 | verified | 2026-05-06 | — |
| BUG-0004 | Fork to Designer 不创建 er_designer tab | verified | 2026-05-07 | 9d67946 |
| BUG-0005 | 页面刷新后 ER Inspector Tab 不恢复 | verified | 2026-05-07 | 9d67946 |
| BUG-0006 | 页面刷新后 ER Designer Tab targetConnectionId 丢失 | verified | 2026-05-07 | 9d67946 |
| BUG-0007 | 后端重启后 MCP bridge nonce 漂移导致 datatalk_* 工具全部 -32001 | verified | 2026-05-08 | 4168e3f9 |
| BUG-0009 | Files Library Tab 渲染 dashboard kind 文件时崩溃 | fixed | 2026-05-09 | — |

## By Module（聚合视图，仅列 open + in-progress）

- **ingestion**: [BUG-0013](BUG-0013-http-request-null-output-fields.md) *(fixed)*, [BUG-0014](BUG-0014-ssrf-deny-list-not-blocking-169-254.md) *(fixed)*, [BUG-0015](BUG-0015-oversized-payload-not-marked-failed.md) *(fixed)*, [BUG-0017](BUG-0017-http-request-missing-payload-format.md) *(fixed)*, [BUG-0018](BUG-0018-basic-auth-not-base64.md) *(fixed)*, [BUG-0019](BUG-0019-page-pagination-ignores-hasmore.md) *(fixed)*, [BUG-0020](BUG-0020-offset-pagination-ignores-nextoffset.md) *(fixed)*, [BUG-0021](BUG-0021-cursor-pagination-missing-next-key.md) *(fixed)*, [BUG-0022](BUG-0022-csv-html-parsers-no-coercion.md) *(fixed)*, [BUG-0023](BUG-0023-integer-64-promotion-gap.md) *(fixed)*, [BUG-0024](BUG-0024-upstream-401-not-mapped-to-auth-failed.md) *(fixed)*, [BUG-0025](BUG-0025-infer-type-lowercase-mismatch.md) *(fixed)*, [BUG-0026](BUG-0026-html-fetch-throws-unsupported.md) *(fixed)*, [BUG-0027](BUG-0027-pagination-top-level-aliases-ignored.md) *(fixed)*, [BUG-0028](BUG-0028-tabular-source-path-missing-dollar.md) *(fixed)*, [BUG-0029](BUG-0029-confirm-status-violates-check-constraint.md) *(fixed)*, [BUG-0030](BUG-0030-confirm-missing-mapping-gate.md) *(fixed)*, [BUG-0031](BUG-0031-action-output-schema-rejects-null-and-missing-errorcode.md) *(fixed)*, [BUG-0032](BUG-0032-h2-fixture-uses-database-not-databasename.md) *(fixed)*, [BUG-0033](BUG-0033-json-jsonl-nullable-only-on-all-null.md) *(fixed)*, [BUG-0034](BUG-0034-executesql-fixture-missing-source.md) *(fixed)*
- **testing**: [BUG-0011](BUG-0011-sql-result-display-test-dialogclose-mock-missing.md), [BUG-0032](BUG-0032-h2-fixture-uses-database-not-databasename.md) *(fixed)*, [BUG-0034](BUG-0034-executesql-fixture-missing-source.md) *(fixed)*
- **security**: [BUG-0018](BUG-0018-basic-auth-not-base64.md) *(fixed)*
- **stage**: [BUG-0008](BUG-0008-stage-trash-last-tab-blank-pane.md), [BUG-0011](BUG-0011-sql-result-display-test-dialogclose-mock-missing.md), [BUG-0042](BUG-0042-sql-editor-session-follow-mode-ignores-connection-default-database.md) *(fixed)*, [BUG-0043](BUG-0043-sql-editor-tab-switch-loses-default-connection.md) *(fixed)*, [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md)
- **query-editor**: [BUG-0042](BUG-0042-sql-editor-session-follow-mode-ignores-connection-default-database.md) *(fixed)*, [BUG-0043](BUG-0043-sql-editor-tab-switch-loses-default-connection.md) *(fixed)*
- **connection**: [BUG-0042](BUG-0042-sql-editor-session-follow-mode-ignores-connection-default-database.md) *(fixed)*
- **chat**: [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md)
- **testing**: [BUG-0011](BUG-0011-sql-result-display-test-dialogclose-mock-missing.md)
- **markdown**: [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md), [BUG-0041](BUG-0041-sql-code-block-theme-color-mismatch.md) *(fixed)*, [BUG-0044](BUG-0044-user-bubble-markdown-invisible-on-primary-bg.md) *(fixed)*
- **chart**: [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md)
- **dashboard**: [BUG-0012](BUG-0012-widget-data-endpoint-ignores-default-connection-id.md) *(fixed)*, [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md), [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md), [BUG-0051](BUG-0051-dashboard-iframe-long-blank-screen.md) *(fixed)*, [BUG-0053](BUG-0053-bezel-ai-widget-id-too-short-and-zod-error-unhelpful.md) *(investigating)*
- **opencode**: [BUG-0036](BUG-0036-skills-extracted-to-wrong-cwd-not-found-by-opencode.md) *(fixed)*, [BUG-0040](BUG-0040-agents-md-skill-path-triggers-llm-hallucination.md) *(fixed)*, [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md)
- **session**: [BUG-0037](BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) *(fixed)*, [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) *(fixed)*, [BUG-0039](BUG-0039-composer-draft-sync-write-wrong-schema.md) *(fixed)*, [BUG-0046](BUG-0046-composer-button-refresh-stream-state-mismatch.md) *(fixed)*, [BUG-0052](BUG-0052-long-session-empty-canvas-streaming-flag-race.md) *(fixed)*
- **channel**: [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) *(fixed)*, [BUG-0046](BUG-0046-composer-button-refresh-stream-state-mismatch.md) *(fixed)*, [BUG-0052](BUG-0052-long-session-empty-canvas-streaming-flag-race.md) *(fixed)*
- **chat**: [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md), [BUG-0037](BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) *(fixed)*, [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) *(fixed)*, [BUG-0039](BUG-0039-composer-draft-sync-write-wrong-schema.md) *(fixed)*, [BUG-0041](BUG-0041-sql-code-block-theme-color-mismatch.md) *(fixed)*, [BUG-0044](BUG-0044-user-bubble-markdown-invisible-on-primary-bg.md) *(fixed)*, [BUG-0046](BUG-0046-composer-button-refresh-stream-state-mismatch.md) *(fixed)*, [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md), [BUG-0052](BUG-0052-long-session-empty-canvas-streaming-flag-race.md) *(fixed)*

## By Source（聚合视图，仅列 open + in-progress）

- **e2e-playwright**: [BUG-0008](BUG-0008-stage-trash-last-tab-blank-pane.md), [BUG-0012](BUG-0012-widget-data-endpoint-ignores-default-connection-id.md) *(fixed)*, [BUG-0013](BUG-0013-http-request-null-output-fields.md) *(fixed)*, [BUG-0014](BUG-0014-ssrf-deny-list-not-blocking-169-254.md) *(fixed)*, [BUG-0015](BUG-0015-oversized-payload-not-marked-failed.md) *(fixed)*, [BUG-0017](BUG-0017-http-request-missing-payload-format.md) *(fixed)*, [BUG-0018](BUG-0018-basic-auth-not-base64.md) *(fixed)*, [BUG-0019](BUG-0019-page-pagination-ignores-hasmore.md) *(fixed)*, [BUG-0020](BUG-0020-offset-pagination-ignores-nextoffset.md) *(fixed)*, [BUG-0021](BUG-0021-cursor-pagination-missing-next-key.md) *(fixed)*, [BUG-0022](BUG-0022-csv-html-parsers-no-coercion.md) *(fixed)*, [BUG-0023](BUG-0023-integer-64-promotion-gap.md) *(fixed)*, [BUG-0024](BUG-0024-upstream-401-not-mapped-to-auth-failed.md) *(fixed)*
- **e2e-mcp**: [BUG-0011](BUG-0011-sql-result-display-test-dialogclose-mock-missing.md)
- **manual-report**: [BUG-0010](BUG-0010-chart-axis-name-clipped-in-chat-bubble.md), [BUG-0036](BUG-0036-skills-extracted-to-wrong-cwd-not-found-by-opencode.md) *(fixed)*, [BUG-0037](BUG-0037-ctrl-r-during-streaming-flips-stop-button-to-send.md) *(fixed)*, [BUG-0038](BUG-0038-replay-idle-on-resubscribe-clears-streaming-flag.md) *(fixed)*, [BUG-0039](BUG-0039-composer-draft-sync-write-wrong-schema.md) *(fixed)*, [BUG-0040](BUG-0040-agents-md-skill-path-triggers-llm-hallucination.md) *(fixed)*, [BUG-0041](BUG-0041-sql-code-block-theme-color-mismatch.md) *(fixed)*, [BUG-0042](BUG-0042-sql-editor-session-follow-mode-ignores-connection-default-database.md) *(fixed)*, [BUG-0043](BUG-0043-sql-editor-tab-switch-loses-default-connection.md) *(fixed)*, [BUG-0044](BUG-0044-user-bubble-markdown-invisible-on-primary-bg.md) *(fixed)*, [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md), [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md), [BUG-0051](BUG-0051-dashboard-iframe-long-blank-screen.md) *(fixed)*, [BUG-0052](BUG-0052-long-session-empty-canvas-streaming-flag-race.md) *(fixed)*, [BUG-0053](BUG-0053-bezel-ai-widget-id-too-short-and-zod-error-unhelpful.md) *(investigating)*

## Wontfix / Duplicate（终态归档，无时间限制）

| ID | Resolution | Reason / DuplicateOf |
|----|------------|----------------------|
| —  | — | — |

## Closure History

30 天前的 closed/verified 折叠归档。详见 `git log -- docs/bugs/`，本节不维护。

## 相关文档

- 写作协议：[README.md](README.md)
- 设计 spec：[../product-specs/2026-05-05-bug-tracking-system-design.md](../product-specs/2026-05-05-bug-tracking-system-design.md)
- 技术债跟踪（互补）：[../exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)
- 手测脚本（互补）：[../testing/](../testing/)
