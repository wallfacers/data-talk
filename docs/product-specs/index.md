# 产品规格目录

DataTalk 产品功能全景图。核心理念：**AI 操作一切**——传统数据库工具能做的，DataTalk 都能做，但全部通过自然语言对话驱动，按操作风险分级执行。

## 1. 产品定位与核心价值

**定位**：面向数据工程师、分析师和开发者的**智能数据库协作平台**。不是 Navicat / DataGrip 的简单替代品，而是将 AI 作为一等公民，让数据库管理、查询、可视化和设计工作全部通过自然语言对话驱动和串联。

**核心价值**：

- **交互革新**：以聊天为主界面，数据库操作为上下文，实现"边说边查，边问边画"
- **智能增强**：AI 理解数据意图、生成 SQL、解释执行计划、推荐图表类型
- **可视协同**：内置 ER 图和报表能力，数据结构与数据洞察一目了然
- **架构开放**：通过 OpenCode 标准协议接入 AI，模型可替换、可私有化
- **全操作覆盖**：从 SELECT 到 DROP、从建表到迁移，所有传统 DB 工具的能力 AI 都能驱动

## 2. AI 操作分级策略

DataTalk 的差异化在于 **AI 直接操作数据库**，但不能让 AI 蛮干。所有数据库操作按影响划分三级，后端基于 SQL AST **强制判级**，不信任 AI 自报。

| 等级 | 标识 | 范围 | 交互行为 |
|------|------|------|---------|
| **L1 直接执行** | 🟢 | 只读类：SELECT、EXPLAIN、SHOW、DESC、元数据查询 | AI 直接执行，结果流式返回 |
| **L2 轻确认** | 🟡 | 数据变更类：INSERT、UPDATE（非批量）、索引创建、视图创建 | AI 生成 SQL 并预览，用户点击"执行"确认 |
| **L3 强确认** | 🔴 | 不可逆/高影响类：DELETE、DROP、ALTER、TRUNCATE、权限授予、批量 UPDATE | AI 展示 SQL + 预估影响行数 + 二次确认（输入表名或勾选框） |

**配套机制**：

- L3 操作自动包装事务，失败自动回滚
- 所有 AI 执行过的 SQL 写入审计日志，可追溯
- 批量 UPDATE / DELETE 执行前先 `SELECT COUNT(*)` 预估影响行数

## 3. 功能全景图

按功能域组织，每条功能标注 AI 操作等级和开发阶段。

### 3.1 连接管理

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 创建/编辑连接 | 🟢 | MVP | 支持 MySQL / PostgreSQL / SQLite，密码加密存储 |
| 连接测试 | 🟢 | MVP | 对话中询问即测，返回延迟 / 版本 |
| 切换活跃连接 | 🟢 | MVP | "切到生产库"自然语言切换 |
| 扩展支持 SQL Server / Oracle | 🟢 | 二期 | 动态 JDBC 驱动加载 |
| 连接池监控 | 🟢 | 二期 | 查看当前连接数、空闲连接 |
| 连接凭据托管 | 🟢 | 三期 | 系统凭据管理器集成、SSH 隧道 |

### 3.2 DDL 管理（结构定义）

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 查看表结构 | 🟢 | MVP | "看看 users 表结构" |
| 创建表 | 🟡 | 二期 | AI 根据描述生成 `CREATE TABLE`，预览后确认 |
| 修改表（加字段 / 改类型 / 改约束） | 🔴 | 二期 | `ALTER TABLE` 预览影响，二次确认 |
| 删除表 | 🔴 | 二期 | `DROP TABLE` 需输入表名确认 |
| 索引管理（建 / 删 / 重建） | 🟡 / 🔴 | 二期 | 建索引 🟡，删索引 🔴 |
| 视图管理 | 🟡 | 三期 | `CREATE / ALTER / DROP VIEW` |
| 存储过程 / 函数 | 🟡 | 三期 | AI 辅助编写、调试 |
| 触发器管理 | 🔴 | 三期 | 高风险，强确认 |
| 约束管理（FK / UNIQUE / CHECK） | 🔴 | 三期 | 加 / 删约束预览影响 |

### 3.3 DML 操作（数据变更）

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 自然语言查询（SELECT） | 🟢 | MVP | 核心能力 |
| 插入单行数据 | 🟡 | 二期 | AI 生成 `INSERT`，预览字段值 |
| 批量插入 | 🟡 | 二期 | 预览行数 + 示例行 |
| 单行更新 | 🟡 | 二期 | 预览前后对比 |
| 批量更新（带 WHERE） | 🔴 | 二期 | 预估影响行数，强确认 |
| 单行删除 | 🟡 | 二期 | 预览将删除的行 |
| 批量删除 | 🔴 | 二期 | 预估行数 + 输入"确认"才执行 |
| `TRUNCATE TABLE` | 🔴 | 三期 | 最高级确认 |
| 事务控制（BEGIN / COMMIT / ROLLBACK） | 🟡 | 三期 | AI 自动包装事务，失败回滚 |

### 3.4 查询与分析

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 自然语言转 SQL | 🟢 | MVP | 注入表结构上下文 |
| SQL 直接编辑运行 | 🟢 / 🟡 / 🔴 | 二期 | 手写 SQL 按语句类型适配等级 |
| 查询结果分页 / 虚拟滚动 | 🟢 | 二期 | 大结果集性能优化 |
| 查询历史 | 🟢 | 二期 | 会话内 + 跨会话 |
| 结果对比 | 🟢 | 三期 | "和昨天的查询对比" |
| 保存查询为模板 | 🟢 | 三期 | 命名查询、再次调用 |
| 多数据库联合查询 | 🟢 | 三期 | 跨 DB 虚拟视图 |

### 3.5 可视化

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 表格结果展示 | 🟢 | MVP | 基础表格 + 复制 / 导出 |
| AI 推荐图表类型 | 🟢 | 二期 | 根据字段类型推荐 |
| ECharts 图表渲染 | 🟢 | 二期 | 折线 / 柱 / 饼 / 散点 |
| 图表追问替换 | 🟢 | 二期 | "换成绿色"原地替换（Plan C） |
| ER 图自动生成 | 🟢 | 二期 | React Flow 从元数据生成 |
| 导出图表（PNG / SVG） | 🟢 | 二期 | 结果可分享 |
| ER 图交互式编辑 | 🟡 | 三期 | 拖拽改结构 → 生成 DDL |
| 报表组合（多图表 Dashboard） | 🟢 | 三期 | 一个 Tab 组合多个图表 |

### 3.6 性能优化

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 执行计划分析 | 🟢 | 二期 | `EXPLAIN` 可视化 + AI 解读 |
| 慢查询识别 | 🟢 | 三期 | 读 slow_log，AI 诊断 |
| 索引推荐 | 🟢 | 三期 | AI 分析 WHERE / JOIN 推荐索引 |
| 表统计信息 | 🟢 | 三期 | 行数、大小、碎片率 |
| SQL 重写建议 | 🟢 | 三期 | AI 优化 JOIN 顺序、子查询 |

### 3.7 数据导入导出

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 导出查询结果（CSV / Excel / JSON） | 🟢 | 二期 | 流式导出大结果 |
| 导出表结构（DDL 脚本） | 🟢 | 二期 | 单表 / 全库 |
| 导出表数据（SQL dump） | 🟢 | 三期 | 数据 + 结构 |
| 导入 CSV / Excel 到表 | 🟡 | 三期 | AI 推断字段映射，预览首行 |
| 导入 SQL 脚本 | 🔴 | 三期 | 批量执行前解析 + 风险标注 |

### 3.8 安全与权限

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 用户列表 / 查看权限 | 🟢 | 三期 | `SHOW GRANTS` |
| 创建用户 | 🟡 | 三期 | AI 生成 `CREATE USER` |
| 授予权限 | 🔴 | 三期 | `GRANT` 强确认 |
| 撤销权限 | 🔴 | 三期 | `REVOKE` 强确认 |
| 删除用户 | 🔴 | 三期 | 最高级确认 |
| 操作审计日志 | 🟢 | 三期 | DataTalk 侧记录所有 AI 执行过的 SQL |

### 3.9 备份与恢复

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 单表备份（导出 SQL） | 🟢 | 三期 | 本地文件 |
| 全库备份 | 🟢 | 三期 | `mysqldump` / `pg_dump` 调用 |
| 定时备份任务 | 🟢 | 三期 | Cron 调度 |
| 从备份恢复 | 🔴 | 三期 | 覆盖前强确认 |
| 增量备份 | 🟢 | 三期 | Binlog / WAL 解析 |

### 3.10 数据库迁移

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 跨 DB 结构迁移（MySQL → PG） | 🟡 | 三期 | AI 翻译类型差异 + 预览 DDL |
| 跨 DB 数据迁移 | 🔴 | 三期 | 分批迁移 + 进度 + 回滚 |
| 结构对比（两库 diff） | 🟢 | 三期 | 可视化结构差异 |
| 同步脚本生成 | 🟡 | 三期 | 生成 `ALTER` 脚本使 A → B |

## 4. 开发路线图

### MVP（第一阶段）— 跑通核心链路

**定位**：在聊天中完成"连接 → 查询 → 展示"闭环。

**关键能力**：连接管理、自然语言查询、SQL 执行、基础表格展示、OpenCode 集成。

### 二期 — 全操作能力 + 可视化

**定位**：覆盖传统 DB 工具 80% 的日常操作，引入 AI 分级执行。

**关键能力**：DDL / DML 全套、图表 / ER 图、查询编辑器、分页 / 导出、执行计划分析。

### 三期 — 智能运维 + 高级能力

**定位**：成为 AI 驱动的 DBA 工作台。

**关键能力**：性能诊断、索引推荐、权限管理、备份 / 迁移、操作审计、Agent 能力。

## 5. 用户场景

| 场景 | 描述 | 等级 | 阶段 |
|------|------|------|------|
| 自然语言查询 | "查询近一周注册趋势"，AI 生成 SQL 并执行 | 🟢 | MVP |
| 数据可视化 | 查询结果一键生成折线图 | 🟢 | 二期 |
| ER 图浏览 | 从元数据生成交互式 ER 图 | 🟢 | 二期 |
| 工件追问替换 | "换成绿色"原地替换图表 | 🟢 | 二期 |
| 结构变更 | "给 users 加个 phone 字段"，AI 生成 `ALTER` 预览 | 🔴 | 二期 |
| 批量清理 | "删除过期订单"，显示影响 1,247 行，输入"确认"执行 | 🔴 | 二期 |
| 性能诊断 | "这个查询为什么慢"，AI 读 `EXPLAIN` 解读 + 推荐索引 | 🟢 | 三期 |
| 数据导入 | 拖拽 CSV，AI 推断字段映射并预览前 10 行 | 🟡 | 三期 |
| 跨库迁移 | "把测试库的 orders 同步到生产库"，生成迁移脚本 | 🔴 | 三期 |
| 操作回溯 | "我今天改了什么"，AI 读审计日志 | 🟢 | 三期 |

## 6. 关键问题与对策

| 问题 | 对策 |
|------|------|
| 数据库密码安全 | 系统凭据管理器 + 后端动态连接池，不落地明文 |
| SQL 注入风险 | AI 侧强化 Prompt 用参数化查询；后端侧对非查询 SQL 二次校验 |
| 大结果集传输性能 | 后端分页 / 流式；前端虚拟滚动 |
| OpenCode 与 DB 驱动隔离 | OpenCode 纯推理，所有 DB 操作由 Spring Boot 代理执行 |
| AI 误判操作等级 | 后端基于 SQL AST 强制判级，不信任 AI 自报 |
| 高危操作回滚 | L3 操作自动包装事务；审计日志可追溯 |
| 批量操作预估偏差 | 执行前 `SELECT COUNT(*)` 预估，前端展示真实行数 |

## 7. 相关文档

- 架构设计：[ARCHITECTURE.md](../../ARCHITECTURE.md)
- 设计文档索引：[docs/design-docs/index.md](../design-docs/index.md)
- 执行计划索引：[docs/exec-plans/index.md](../exec-plans/index.md)
- 后端开发指南：[docs/BACKEND.md](../BACKEND.md)
- 前端开发指南：[docs/FRONTEND.md](../FRONTEND.md)

## 8. 个别设计文档

按时间倒序列出本目录下的单功能设计 spec。Superpowers brainstorming 产出的新 spec 应写入本目录并在此登记。

| 设计文档 | 日期 | 主题 |
|----------|------|------|
| [AI Message Table Actions and Structured Format Design](./2026-04-20-ai-message-table-actions-and-structured-format-design.md) | 2026-04-20 | 为所有统一 Markdown 渲染链路中的表格增加表格级动作栏与复制/导出能力：首期支持复制表格、CSV、TSV、Markdown、JSON、下载 CSV；实现上采用 DOM-first 增强 + 轻量 TableModel/serializer，并同步定义后续结构化格式扩展优先级 |
| [Composer Data Source Picker](./2026-04-20-composer-data-source-picker-design.md) | 2026-04-20 | Composer 底部新增与模型并列的数据源选择器：支持搜索、最近使用排序、高频切换；无当前数据源时直接拉起选择弹框并在选中后自动恢复原动作，而非先报错阻断；同时补齐 `ui_exec choose_connection` 前端适配器能力，并要求 Stage 卡片固化来源数据源、仅提供显式回切 |
| [AI Message Code Window and Table Design](./2026-04-20-ai-message-code-window-and-table-design.md) | 2026-04-20 | 统一 AI 消息中的代码块窗体视觉与 Markdown 表格渲染：所有代码展示区域收口为带顶部 chrome 的浅色 code window，深色主题下仍保持亮面窗体；同时为 pipe table 增加窄范围规范化、统一滚动容器和 token 驱动的增强样式 |
| [Blank Session List Actions](./2026-04-20-blank-session-list-actions-design.md) | 2026-04-20 | 会话列表中的空白会话继续显示并可进入，但不再暴露“更多”按钮，也不支持重命名和删除，避免删除后立刻出现一个可再次操作的新会话 |
| [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md) | 2026-04-20 | StageWindow 升级为 AI 可操作的多 Tab 工作屏：移植 open-db-studio UI Object 协议（`ui_read/patch/exec/list` 四件套 + Adapter 注册表），建立"展示路径（结果不进 AI 上下文）vs 分析路径（结果进 Artifact）"双轨，新增用户 `!<sql>` 直查通道，引入 Global/Session/Tab 三层连接绑定 |
| [SQL Risk Classification & IT CI Gate](./2026-04-20-sql-risk-classification-and-it-ci-gate-design.md) | 2026-04-20 | 用 Apache Calcite 建立后端通用 SQL AST 风险判级能力，挂到 `ActionDispatcher` 统一预处理层；同时将 `*IT.java` 通过 Maven `verify` 纳入真实门禁，并同步更新技术债与质量文档 |
| [Assistant Model Metadata Propagation](./2026-04-20-assistant-model-metadata-propagation-design.md) | 2026-04-20 | 修复流式 assistant 消息不显示模型名：`Message` record 新增 `providerID / modelID`，`OpenCodeEventLoop.parseMessage` 兼容 user 嵌套 / assistant 扁平两种 OpenCode 1.4.7 形态；清理 `DtEvent.MessageCompleted` 死代码及前端对应分支 |
| [SSE Heartbeat & Async Timeout 治理](./2026-04-20-sse-heartbeat-design.md) | 2026-04-20 | SSE GET 订阅改无限 timeout + 30s 心跳注释帧（`":\n\n"`）主动探活；POST turn 保留 10 分钟上限；`AsyncRequestTimeoutException` 降级 DEBUG；消除 `response committed already` 误导性 WARN |
| [Per-Session Streaming Indicator](./2026-04-19-per-session-streaming-indicator-design.md) | 2026-04-19 | `useChannel().isStreaming` 从 hook-local useState 提升到 `chat-parts-store.streamingBySession: Set<string>`；切 session 后回到 A 正确显示"还在跑"指示；不改 SSE 订阅结构 |
| [Single Empty Session](./2026-04-19-single-empty-session-design.md) | 2026-04-19 | 全局最多 1 个空白会话（`hasEverSent=false`）：前端本地查重 + `isPending` 短路；后端 `SessionService.create` `synchronized` 幂等 + `reusedEmpty` 响应字段；零 migration |
| [AI Message Rendering Migration](./2026-04-19-ai-message-rendering-migration-design.md) | 2026-04-19 | 将 OpenCode 桌面端消息渲染（Markdown 增量 / PacedMarkdown / TextShimmer / BasicTool / ContextToolGroup / ToolRegistry）React 化迁移至 DataTalk，叠加风险分级、SQL 代码块增强、Artifact 跳转特化；同步承接 history-passthrough 的 OpenCode 原生 shape + 乐观 UI |
| [Datasource Name Field](./2026-04-19-datasource-name-design.md) | 2026-04-19 | 数据源新增 name 字段（唯一）+ 表格样式修复（列标题间距统一、操作列显示标题） |
| [Request Logging & Tracing](./2026-04-18-request-logging-design.md) | 2026-04-18 | HTTP 请求耗时统计、traceId 全链路日志跟踪、慢请求告警 |
| [Connection Test Status Persistence](./2026-04-18-connection-test-status-design.md) | 2026-04-18 | 持久化数据源连接测试结果，重启后可见上次测试状态 |
| [OpenCode Session Title Sync](./2026-04-18-opencode-session-title-sync-design.md) | 2026-04-18 | 订阅 OpenCode `session.*` 事件家族（8 个），同步自动生成的 session title，保留手动 rename 锁定 |
| [Composer Model Picker Dialog](./2026-04-18-composer-model-picker-dialog-design.md) | 2026-04-18 | Composer 模型选择器由 Popover 改为 960×540 双栏对话框，触发按钮视觉不变 |
| [Stage Reveal Animation](./2026-04-17-stage-reveal-animation-design.md) | 2026-04-17 | Stage 气泡式开/关动画（clip-path circle）+ 圆角内 bg-muted 色差 |
| [AI Settings · OpenCode Port](./2026-04-17-ai-settings-opencode-port.md) | 2026-04-17 | AI 设置中心：数据源 / 提供商 / 模型三页，对齐 OpenCode Desktop |
| [Stage As Computer](./2026-04-17-stage-as-computer-design.md) | 2026-04-17 | 右栏外壳化（macOS titlebar）+ 可关可开 + 智能自弹 + 删 /preview |
| [Client Rebuild (Tauri + Vite)](./2026-04-16-client-rebuild-tauri-vite-design.md) | 2026-04-16 | Tauri v2 + React 19 + Vite 客户端骨架重建方案 |
| [Manus Split View](./2026-04-16-manus-split-view-design.md) | 2026-04-16 | Manus 风格分屏交互 + Action Registry + Ontology 层 |
| [Model Config Page](./2026-04-16-model-config-page-design.md) | 2026-04-16 | 模型配置页面：提供商管理 / 模型可见性 / 自定义提供商 |
| [OpenCode Embedded Process](./2026-04-16-opencode-embedded-process-design.md) | 2026-04-16 | Spring Boot 嵌入管理 OpenCode 进程：自动下载 / 动态端口 / 生命周期 |
