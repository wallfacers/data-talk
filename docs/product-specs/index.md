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
| 扩展支持主流 SQL/JDBC 数据源矩阵 | 🟢 | 二期 | 分批支持 Oracle / SQL Server / MariaDB / Apache Doris / StarRocks / ClickHouse / Hive / Trino / Presto / DuckDB / GaussDB / openGauss / 达梦 / KingbaseES / OceanBase / TiDB / Snowflake / BigQuery / Redshift / Databricks SQL / IBM Db2 / SAP HANA / Teradata 等；每种数据库必须按 [数据源兼容规范](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 完成连接、元数据、执行、风险、诊断、前端和 AI prompt 门禁 |
| 非 SQL / 半结构化数据源接入 | 🟢 | 三期 | MongoDB / Elasticsearch / OpenSearch 等不强行伪装成 SQL 数据库；需先定义 read/query contract、schema 映射、mutation 策略和 AI 工具语义 |
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

### 3.11 工作台与跨 Session Tab 协作

**动机**：当前 `StageStore` 的 Tab 集合按 session 切片，离开会话即失忆。报表设计器、ER 设计器等"长生命周期工作对象"需要跨多个 session 持续演进，必须把 Tab 提升为工作台级、可持久化、可被 AI 全量检索的一等对象。

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 工作台跨 session 共享 | 🟢 | 二期 | StageWindow 升级为全局共享工作台，所有 session 共用同一 Tab 集合，AI 与用户在不同会话间可接力编辑同一对象 |
| Tab 列表持久化与打开记录 | 🟢 | 二期 | 用户手动 / AI 自动打开的 Tab 默认全部持久化（SQL 编辑器、ER 设计器、报表设计器、文件预览等），关闭客户端后重启仍可恢复；提供与会话列表同构的"Tab 打开记录"入口 |
| Tab 标题与内容索引 | 🟢 | 二期 | 在全量 Tab 集合上建立标题 + 正文索引（SQL 文本、ER 字段、报表组件 schema、文件预览正文等），既支持用户模糊搜索 Tab 列表，也支持在 Tab 内容里全文检索关键词、字段名、片段 |
| AI 跨会话定位 Tab 与内容检索 | 🟢 | 二期 | 类比 Claude Code 用 bash 做 `find` / `grep` / `cat` 的组合：AI 通过 `ui_list` 列出候选 Tab，通过新增 `ui_find` 在 Tab 元数据 + 内容上做关键词 / 正则 / 语义检索，并按需把命中片段或整个 Tab 正文读取为对话上下文，再决定下一步 `ui_patch`；用于"用户在多轮对话中改 ER 字段、报表组件、SQL 文件中的某一段"等场景 |
| 报表 / Dashboard 跨 session 协作 | 🟢 | 三期 | 报表设计器作为持久化工作对象，可由不同 session 接力修改，AI 与用户共享同一 canvas，保留版本与变更日志 |

**关键设计要点**：

- Tab 作用域从「工作台级 / 会话级」演化为「**工作台级跨 session 持久化**」为默认；纯一次性产物（chart artifact 等）保持会话级，由 Tab type 注册表显式区分
- Tab 元数据（id / type / title / objectId / connectionId / lastTouchedAt）+ 内容快照（SQL 正文、ER schema、报表 schema）写入 SQLite 元数据库，与会话列表对等的查询与排序能力
- AI 通过 `ui_list` / `ui_find` 操作面对全量 Tab 集合查询，不再受限于当前 `session.tabs`；`ui_patch` 沿用 [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md) 的 Adapter 协议，Adapter 不感知"是否跨 session"
- `ui_find` 显式对标 Claude Code 在 bash 里用 `find + grep + cat` 的工作方式：单一调用既可按 Tab 标题 / type / connectionId 过滤（≈ `find`），又可在 Tab 正文里按关键词 / 正则 / 语义检索命中行 / 字段（≈ `grep`），并支持按 `tabId + range` 把命中片段或整段正文回读为对话上下文（≈ `cat` / `read`）；AI 可以先粗筛 Tab、再深入正文、再决定 `ui_patch`，整个链路保持只读、不副作用，避免"为定位一个字段先把整个工作台拉进上下文"
- AI 提示工程需要在系统消息中注入"当前打开 Tab 摘要 + 最近编辑 Tab 列表"，使其具备"用户正在改哪个对象"的默认认知，并把 `ui_find` 列为优先工具
- 客户端设计契约对齐 [client/DESIGN.md](../../client/DESIGN.md)：Tab 打开记录复用 sidebar 的 `bg.subtle / interaction.selected / text.strong`，落在 navigation skeleton 层；搜索命中态用 `accent.primary` 高亮；切换 / 定位 Tab 的过渡走 `motion.normal + easing.standard`，仅用于 confirm state；搜索面板键盘可达，焦点环遵循 `interaction.focusRing`
- 该方向是 [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md) 的自然延伸——把当时埋下的"工具 Tab 工作台级常驻"约束彻底落实为持久化 + 可检索

### 3.12 外部数据接入与自动采集（Skill 驱动）

**动机**：DataTalk 的可视化分析能力对"用户连接里已有的数据"很完整，但实际业务（电商运营、市场分析、舆情监控）经常需要把外部互联网数据先拉进来再分析。把这条链路通过 skill 系统插件化暴露，让 AI 在自然语言里完成"采集 → 落库 → 分析"全流程，而 DataTalk 主程序保持纯净、不绑死任何第三方平台。

| 功能 | 等级 | 阶段 | 描述 |
|------|------|------|------|
| 国内电商平台数据采集 | 🟡 | 三期 | 通过 skill 安装方式对接淘宝 / 京东 / 拼多多 / 抖音电商等常见平台开放接口，在用户授权前提下获取商品 / 订单 / 流量 / 退款等业务数据 |
| 通用互联网数据抓取 | 🟡 | 三期 | 通用网页 / REST / GraphQL 数据采集 skill 矩阵，AI 自然语言驱动从指定 URL / 接口拉取结构化数据，支持分页与限流 |
| 自动落库 | 🟡 | 三期 | 抓取数据自动建表并导入到用户当前连接，AI 推断 schema / 字段类型 / 索引建议，走 §3.2 / §3.3 的 L2 二期风险流程二次确认 |
| 端到端可视化分析 | 🟢 | 三期 | 与 §3.5 可视化打通：抓取 → 落库 → 查询 → 图表 → Dashboard 一条龙；分析结果作为持久化 Tab，复用 §3.11 的跨 session 协作能力 |

**关键设计要点**：

- 该能力**完全通过 skill 系统**扩展，DataTalk 核心不内置任何具体平台 SDK，规避合规 / 资质 / 版权 / 平台 ToS 风险渗透到主程序
- skill 负责凭据托管（OAuth / API key）、采集脚本、字段映射建议；DataTalk 负责任务调度、结果回写到用户连接、Tab 可视化呈现、采集进度展示
- 实施路径：① 先打通通用 HTTP / scraping skill 接入框架，与 OpenCode 现有 MCP / skill 协议互通 → ② 再针对个别高频平台沉淀官方 skill 包
- 数据来源 / 采集时间 / 原始 payload 必须写入审计日志（沿用 §3.8 操作审计），保证可回溯
- 与 §3.11 协同：采集任务运行视图、字段映射预览、目标表 DDL 预览作为持久化 Tab，跨 session 可继续编辑并由 AI 接力补全

## 4. 开发路线图

### MVP（第一阶段）— 跑通核心链路

**定位**：在聊天中完成"连接 → 查询 → 展示"闭环。

**关键能力**：连接管理、自然语言查询、SQL 执行、基础表格展示、OpenCode 集成。

### 二期 — 全操作能力 + 可视化

**定位**：覆盖传统 DB 工具 80% 的日常操作，引入 AI 分级执行。

**关键能力**：DDL / DML 全套、图表 / ER 图、查询编辑器、分页 / 导出、执行计划分析、主流 SQL/JDBC 数据源覆盖。

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
| 主流数据源接入 | "连接公司的 Doris / Oracle / Hive / GaussDB / 达梦库继续分析"，DataTalk 按数据源兼容规范完成连接、元数据读取、SQL 执行与风险门控 | 🟢 | 二期 |
| 结构变更 | "给 users 加个 phone 字段"，AI 生成 `ALTER` 预览 | 🔴 | 二期 |
| 批量清理 | "删除过期订单"，显示影响 1,247 行，输入"确认"执行 | 🔴 | 二期 |
| 性能诊断 | "这个查询为什么慢"，AI 读 `EXPLAIN` 解读 + 推荐索引 | 🟢 | 三期 |
| 数据导入 | 拖拽 CSV，AI 推断字段映射并预览前 10 行 | 🟡 | 三期 |
| 跨库迁移 | "把测试库的 orders 同步到生产库"，生成迁移脚本 | 🔴 | 三期 |
| 操作回溯 | "我今天改了什么"，AI 读审计日志 | 🟢 | 三期 |
| 跨 session 改报表 | "把昨天那个销售看板的 GMV 字段改成万元单位"，AI 通过 Tab 索引定位到对应 Dashboard Tab 并就地 patch | 🟢 | 二期 |
| 多轮对话改 ER | 在 ER 设计器 Tab 中对话改字段："把 users.email 改为 VARCHAR(255) NOT NULL"，AI 自然语言改字段并生成 DDL 预览 | 🟡 | 二期 |
| 电商运营数据落库 | "把抖音电商最近 7 天订单同步进来分析转化率"，skill 拉取数据 → AI 自动建表 → 落库 → 出图 | 🟡 | 三期 |

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
| [ER Canvas Redesign Design](./2026-04-30-er-canvas-redesign-design.md) | 2026-04-30 | 已落地：为 ER Designer / Inspector 两个 Stage Tab 做纯前端视觉重设，贴合 `client/DESIGN.md`：双模式共享画布，靠"模式徽章 + 强调色 + 节点 pencil/lock + Studio↔Instrument 几何参数"轻量身份化区分；列行采用左侧色条（PK 实色 `border.strong` / FK 虚线 `border.default` / hover/selected 升 `accent.primary`）+ KeyRound/Link 图标 + 字重三通道传角色，cobalt 严格保留给 focus/selection/primary action；新增 NN 胶囊、target 端 chevron 箭头、边 hover 加粗、空态双行排版 + reason 图标、上下文菜单方向键导航；建立贯穿 ER 全表面的图标色契约；纯前端 0 改动后端 / 协议 / 注册器 / 持久化。自动化验证通过；截图 / axe-core / PR 创建 deferred。 |
| [Data Source Coverage: MariaDB Design](./2026-04-30-data-source-coverage-mariadb-design.md) | 2026-04-30 | Wave A MariaDB child design：明确 `mariadb` 是独立 canonical kind，不静默当作 MySQL alias；要求先决定 MariaDB Connector/J vs MySQL Connector/J 兼容、URL/SSL/timeout、MySQL metadata/SQL splitter/risk/diagnostics/ER 复用边界，并用 MariaDB-specific tests 证明，支持状态在实现完成前保持 unsupported |
| [Data Source Coverage: SQL Server Design](./2026-04-30-data-source-coverage-sqlserver-design.md) | 2026-04-30 | Wave A SQL Server child design：以 `sqlserver` 为 canonical kind、`mssql` 仅作为显式 alias；补齐 Microsoft JDBC driver、encryption/trust certificate/instance fields、database/catalog + schema target resolution、`GO` batch splitter、SQL Server risk guard、SHOWPLAN/DMV diagnostics 与 structured unsupported 边界，支持状态在实现完成前保持 stub/legacy |
| [Data Source Coverage: Oracle Design](./2026-04-30-data-source-coverage-oracle-design.md) | 2026-04-30 | Wave A Oracle child design：把当前 `DbType` / diagnostics stub 推进为可实现的一等支持前置设计；明确 canonical kind `oracle`、service name/SID/role/JDBC properties 不得混塞字段、owner/schema target resolution、PL/SQL splitter、Oracle risk guard、EXPLAIN PLAN/DBMS_XPLAN 与 structured unsupported 的边界，支持状态在实现完成前保持 stub-only |
| [Data Source Coverage: SQLite Design](./2026-04-30-data-source-coverage-sqlite-design.md) | 2026-04-30 | Wave A SQLite child design，已于 2026-05-01 按 child plan 落地：`sqlite` 作为 canonical kind 完成文件路径 / `:memory:` 语义、前端连接表单与 picker、schema-less Query Editor context、`read_schema` / `datatalk_execute_sql` / prompt contract / structured unsupported diagnostics 验证，支持状态已升级为 first-class file-scoped support |
| [Data Source Coverage Governance Design](./2026-04-30-data-source-coverage-governance-design.md) | 2026-04-30 | 为主流数据源覆盖扩展建立总控治理：强制应用 `DATA_SOURCE_TYPE_COMPATIBILITY.md` gate，禁止 UI-only / prompt-only / JDBC-only 支持；固化候选矩阵与 Wave A-E 推进顺序；要求每个 kind 独立 child spec + child plan，并统一覆盖 canonical kind、alias normalization、连接、metadata、SQL execution、splitter、risk guard、diagnostics、frontend、MCP/runtime prompt、测试与文档 housekeeping |
| [SQL Editor Toolbar Context Design](./2026-04-30-sql-editor-toolbar-context-design.md) | 2026-04-30 | 将 SQL 执行上下文从 popover 重做为 toolbar 内联控件：`固定 session 上下文` 开关 + 连接 / 数据库 / Schema 联动下拉 + 最末尾分页限制；默认跟随 session，关闭后 tab 手动 override 即选即生效；补齐 AI `query_editor.set_context` 的 useSessionContext / limit / 联动参数契约和刷新失败全局提示 |
| [ER Graph Browsing & Designing](./2026-04-29-er-graph-browsing-design.md) | 2026-04-29 | 把 ER 能力从 LayoutErdAction placeholder + ErdArtifact 字段对不上的废弃组件升级为两个一等 Stage Tab type：`er_inspector`（只读浏览真库 + 视图层标注：选表 / 拖拽布局 / 折叠 / 虚拟关系 / 注释）和 `er_designer`（独立 schema 草稿 + DDL 生成）；引入 `@xyflow/react` v12 + `dagre`（前端 web worker 跑布局），借形 open-db-studio 的 line-jump 桥接 / 关系标签 anti-overlap / self-ref loopback / AI 两阶段高亮 / viewport 持久化（颜色 / 协议 / store 全本地化为 cobalt-only DESIGN.md token + Stage UI Object Protocol + useErTabsStore）；Apply 落库唯一通道 = `generate_ddl` 灌入新建 query_editor Tab + Task 5 L2/L3 confirm，零新建 mutation 出口；day-1 DDL 范围 CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX，DROP / ALTER COLUMN type 拒绝并 SkippedOp + aiHint 引导手写；dialect mysql/postgresql/h2 完整、sqlite Designer 仅 CREATE、oracle 显式 unsupported；12 条 AI Ergonomics 强制原则（零坐标计算 / 零 baseVersion 心智 / 批量 JSON Patch ops / 打开即可用 / `assignedIds` 回带 / aiHint 必带 / AGENTS.md recipe 表 / STAGE_TAB_DIGEST ER 行 / 两阶段 AI 高亮 / 全英文 prompt P12）；废弃 `datatalk_layout_erd` server action + `'erd'` artifact kind 全 codebase 退场；Plan A `er_inspector` 已于 2026-04-29 完成自动化验证，Plan B `er_designer` 继续作为独立后续计划 |
| [OpenCode Workdir & Artifact System](./2026-04-29-opencode-workdir-and-artifact-system-design.md) | 2026-04-29 | 把 OpenCode 在 `~/.data-talk/` 下的运行时产物规范化为双轨双维度的 file artifact 系统：OpenCode 单进程 cwd 固定，按 `opencode/sessions/<sid>/` 子目录软隔离；AI 中间产物默认 Temporary，显式 MCP `datatalk_archive_artifact` / frontmatter 提升 Candidate，用户在 UI 决策升档为 connection 维度 Archived 资产；新增 Stage Files / Files Library、Chat 内联卡片、session 删除两阶段终局确认；`io.methvin:directory-watcher` 跨平台监听 + debounce + reconcile；HousekeepingScheduler 滚动治理 `_trash` 等；LegacyMigrationRunner 一次性收拾历史孤儿到 `_legacy/`；Settings Maintenance 存储概览页。Part 1 (Migration & Domain) 与 Part 2 (Watcher & Reconcile) 已完成。 |
| [SQL DML Batch Execution](./2026-04-29-sql-dml-batch-execution-design.md) | 2026-04-29 | `/api/sql/execute` 执行层增加连续 DML JDBC batch 与同表 `INSERT ... VALUES` rewrite 优化 |
| [Diagnostics & Mutation Actions Design](./2026-04-29-diagnostics-mutation-design.md) | 2026-04-29 | 将三个 stub 诊断工具（lock_info / pool_status / table_space）升级为真实实现，新增两个 confirmable mutation action（terminate_session / optimize_table）形成诊断→建议→执行闭环；MySQL / PostgreSQL 完整实现，H2 受技术限制部分实现 / unsupported，Oracle 受连接栈未闭环本期保持 unsupported（架构债，待独立 follow-up）。Completed 2026-04-29 |
| [Large Schema Context Guards Design](./2026-04-29-large-schema-context-guards-design.md) | 2026-04-29 | 在不引入 schema 缓存/索引延迟的前提下，为 `datatalk_read_schema` 增加实时分页、搜索、显式 describe 限制和截断元数据；为 MCP 输出和 chat-path SQL 结果增加预算保护，避免大 schema / 大结果集压爆 OpenCode 上下文或后端内存（Shipped 2026-04-29） |
| [Shared Stage Workbench Design](./2026-04-28-shared-stage-workbench-design.md) | 2026-04-28 | 把 Stage（工作台）从「按 session 切片的 UI 状态」升级为「全局共享 + IDEA 风格库/工作集分离 + 多 session 并发写安全」：sidebar 删 NavTabs，StageWindow 内置三段式（左 rail 库 + 顶 tab 栏工作集 + 内容区），Tab `scope` 取消、`originSessionId` 降级为软标签且 FK SET NULL（V13 migration），`useStageStore` 9 个 `*BySession` 全部改单值；协议层强制 `expectedText` 指纹 + `replace /content` 强制 `baseVersion`，新增统一 `error.markdown` 友好反馈与 `EditConflictMarkdownFormatter`；`workspace.detach` / `archive` / `trash` 三新动词，`close` alias 已于 TD-033 删除；AGENTS.md 重写 UI 协议章节，新增 `Concurrency Contract` / `Library vs Workset` 两段，`STAGE_TAB_DIGEST` 渲染加 `inWorkset` / `originSession` / `version` 字段；分阶 β：Backend Protocol（P1）→ Frontend Layout（P2）→ State Globalization（P3） |
| [Intelligent Operations Design](./2026-04-27-intelligent-operations-design.md) | 2026-04-27 | 以 EXPLAIN + 索引推荐为第一 slice，建立可扩展数据库诊断平台底座；`DiagnosticsProvider`（Strategy）+ Action 双层架构；MySQL / PG / H2 实现，Oracle stub；预留 Lock / Pool / Space 接口；双入口（工具栏 + AI 工具调用）；`DiagnosticTab`（WORKBENCH scope，依赖 Task 6）；stub Action 占位（datatalk_lock_info / pool_status / table_space） |
| [Cross-Session Workbench Tabs Design](./2026-04-27-cross-session-workbench-tabs-design.md) | 2026-04-27 | (Shipped 2026-04-27) 把 StageWindow 的 Tab 集合从「按 session 切片的内存态」升级为「跨 session 持久化、可全文检索、AI 可精准定位的工作台对象」：新增 `stage_tabs` / `stage_tab_payload` / FTS5 trigram 索引（V12 migration）+ `StagePersistenceCoordinator`（debounce 1s 内容 + 立即元数据 + force-flush 关键 action）+ 单一 mutation 口子（ESLint custom rule + vitest 静态扫描双门禁）；hard-cut 替换 `ui_list` 为 `Executor.SERVER` 的 `ui_find`，三段独立可组合（filter / query / read）+ 四种 output mode（metadata / matches / tabs_only / count），后端 Java 21 虚拟线程 fan-out；sidebar 新增 `<NavTabs />` group 与 inline 搜索；AGENTS.md 注入 `{{STAGE_TAB_DIGEST}}` 摘要，AGENTS.md 8 处 `datatalk_ui_list` 替换为 `datatalk_ui_find`；90 天 lazy auto-archive |
| [Schema Read Bounds Design](./2026-04-27-schema-read-bounds-design.md) | 2026-04-27 | 将 `datatalk_read_schema` 收敛为默认只返回表摘要，显式传 `tables` 时才返回指定表列详情，并补充运行时 Agent 对大 schema / 截断输出的处理规范（Shipped 2026-04-27） |
| [Chat Auto-Follow Bottom Recovery Design](./2026-04-27-chat-auto-follow-bottom-recovery-design.md) | 2026-04-27 | 纠正聊天区 auto-follow 恢复条件的实现漂移：用户主动上滚后暂停跟随，但只要再次严格触底，无论是拖动滚动条、滚轮/触摸到底还是点击“回到底部”，后续流式内容都应恢复自动跟随（Shipped 2026-04-27） |
| [Guarded DDL/DML Execution Design](./2026-04-25-guarded-ddl-dml-execution-design.md) | 2026-04-25 | 把后端 L1/L2/L3 风险分级转化为用户可见的两步确认：`POST /api/sql/execute` 与 `ExecuteSqlAction` 引入 `confirmed + riskAck` 状态机，Workbench 用 `AlertDialog`，chat 走 `blocked_in_chat`（直接拒绝 L2/L3 + 引导回 Workbench）；`DELETE WITH WHERE` 由 L3 调整为 L2 与 `UPDATE WITH WHERE` 对称 |
| [SQL Result Export Design](./2026-04-25-sql-result-export-design.md) | 2026-04-25 | Stage SQL result set 首版导出：复制 CSV、复制 JSON、下载 CSV；支持当前页与当前已返回 bounded result，明确不做后端 streaming、Excel 和虚拟滚动 |
| [SQL Editor Selection Run And Result Scroll Design](./2026-04-25-sql-editor-selection-run-result-scroll-design.md) | 2026-04-25 | SQL 编辑器在存在非空 Monaco 选区时精确执行选中文本；无选区时继续执行全文；多结果集切换时按 `resultId` 独立保存并恢复上下、左右滚动条位置（Shipped 2026-04-25） |
| [Chat Tool System Arg Folding Design](./2026-04-24-chat-tool-system-arg-folding-design.md) | 2026-04-24 | 聊天区工具调用卡片的 trigger 默认只显示工具名称，所有输入参数统一下沉到展开内容，避免执行细节污染主阅读路径（Shipped 2026-04-24） |
| [OpenCode MCP Tool Migration Design](./2026-04-24-opencode-mcp-tool-migration-design.md) | 2026-04-24 | 将 DataTalk 从 legacy plugin-tool 注册/回调链路切到 MCP 单路径：embedded 模式采用 config-first 启动顺序，external 模式采用 config patch + runtime reconcile；通过 OpenCode plugin hook 注入隐藏会话上下文（含进程级 nonce 防伪），CLIENT action 用 `DeferredResult` 保留同步等待语义；定义 `/mcp` 端点的访问控制（loopback + Origin + nonce 三层）、`opencode.json` 合并算法（DataTalk 仅持有 `mcp.datatalk` / `agents` / `plugins` 三键、整块覆盖 + 去重追加 + 原子写）、`tools/list` 过滤位 `ActionDescriptor.exposeToMcp`、SessionMap 写入时机、前端 renderer 注册键 hard rename 清单，并钉死 OpenCode MCP transport 等设计输入；统一把对外 tool naming 切到 `datatalk_*` 风格 |
| [Reasoning Auto-Expand Setting Design](./2026-04-24-reasoning-auto-expand-setting-design.md) | 2026-04-24 | 在“设置 > 通用”新增“思考中自动展开”开关：默认关闭；关闭时 reasoning 面板在思考期间不自动展开；打开时思考开始自动展开；无论配置如何，思考完成后统一自动收起（Shipped 2026-04-24） |
| [AI Text-to-Chart Fence Design](./2026-04-23-ai-text-to-chart-fence-design.md) | 2026-04-23 | AI 以 ```chart 围栏 + ECharts JSON 在聊天流内联渲染图表，流式 JSON 未完整时展示骨架占位；图表块工具栏支持"打开到工作台"提升为 Stage artifact；`datatalk.render_chart` 从默认路径降级为显式保存路径，`sourceArtifactId` 放宽为可选；Stage `ChartArtifact` 统一改用 echarts-for-react，废弃 recharts（Shipped 2026-04-24） |
| [Query Editor Object Actions Design](./2026-04-23-query-editor-object-actions-design.md) | 2026-04-23 | 将 `query_editor` 收敛为由 `StageStore` 统一打开、命名和维护的对象：所有最终打开 SQL 编辑器的入口共享同一语义；对象对外暴露 `state + actions + capabilities`；SQL 正文按“虚拟文件内容”建模，支持 range-based text edits |
| [DataTalk Client Design System](./2026-04-23-datatalk-client-design-system-design.md) | 2026-04-23 | 为 `client/` 建立一份可执行的设计系统契约：定义 dual-theme 研究工作台视觉语言、primitive/semantic/component token 体系、Chat/Workbench 双核心页面模式、组件语义、图表/可访问性/治理规则，并作为后续 `client/DESIGN.md` 与前端实现的唯一真源 |
| [Chat Auto-Scroll Reentry Design](./2026-04-23-chat-auto-scroll-reentry-design.md) | 2026-04-23 | 修复聊天区 auto-follow 接管条件：用户只要主动向上滚离开底部，流式更新就不得再强制滚底；仅当用户再次回到底部后，自动滚动才恢复 |
| [SQL Tab Internal Activity Rail Design](./2026-04-23-sql-tab-internal-rail-design.md) | 2026-04-23 | 把 Stage 窗口顶层的 Activity Rail（Schema / 历史 / 大纲）下移到 SQL 编辑器 Tab 内部：rail 严格落在 Tab 内容矩形内，仅 `query_editor` 与未来 `er_designer` 挂载；文件预览、Dashboard、报表等 Tab 不再出现这些面板；状态作用域保持按 session 记忆，组件签名不变 |
| [Read File Preview In Session Stage Design](./2026-04-22-read-file-preview-design.md) | 2026-04-22 | 为固定 `<path><type>file</type><content>` 形态的 `read` 工具文件输出增加专属前端渲染与“同步到工作台”入口：点击后在当前会话 Stage 中创建或聚焦 `file_preview` Tab，以 Tag 区显示语言/类型/截断状态、以只读 Monaco 代码区高亮 `<content>` 正文，同时保持路径与文件属性为普通文本 |
| [Stage SQL Editor Format Design](./2026-04-22-stage-sql-editor-format-design.md) | 2026-04-22 | 为 Stage Query Editor 增加显式 SQL 格式化能力：工具栏 `Format` 按钮与 `Cmd/Ctrl + Shift + F` 统一走前端 `sql-formatter` helper，按上下文方言映射生成层次分明的 SQL 输出 |
| [Workspace And Backend I18n Design](./2026-04-22-workspace-i18n-design.md) | 2026-04-22 | 在现有双端 i18n 基础设施上，补齐工作台相关前端页面与组件的全部用户可见静态文案，并将后端静态元数据、对象显示名、SQL 结果标题及错误出口统一接入 `Translator`，同时保持协议字段、枚举值、日志与动态业务数据不变 |
| [Stage SQL Workbench Polish Design](./2026-04-22-stage-sql-workbench-polish-design.md) | 2026-04-22 | Stage SQL Workbench 在 Rebuild 之上的 IDE 级打磨：去除左侧资源栏整套渲染，工作台铺满主区；最右侧新增 28px Activity Rail 承载 Schema / History / Outline / AI Assist 四个 280px 面板；工具栏升级（Run · Cancel · Format · Limit · Context chip · Save · overflow）；Monaco 接入补全 / breadcrumb / 折叠 / 多光标 / 当前语句高亮；底部新增 Status Bar；Tabs 统一为 underline-only 扁平风；并引入 tab override + session 继承 + 程序化注入的分层上下文模型 |
| [PostgreSQL SQL Splitter Design](./2026-04-22-postgres-sql-splitter-design.md) | 2026-04-22 | 为 `/api/sql/execute` 的 PostgreSQL 多语句执行引入方言化 splitter：第一阶段以 PgJDBC 内部 `Parser` 作为 PostgreSQL 专用切分器，抽离 `SqlStatementSplitter` 边界，并为后续替换到 `libpg_query` 预留稳定接口 |
| [Stage SQL Workbench Rebuild Design](./2026-04-21-stage-sql-workbench-rebuild-design.md) | 2026-04-21 | 已落地：Stage SQL 主线彻底重做，以 `open-db-studio` 的 SQL 工作台结构为参考，前端迁移 Monaco + 编辑器顶部工作区 + 结果集 Tab，后端把 `/api/sql/execute` 重构为多语句 / 多结果契约，并删除旧的非 SQL Stage 页面与旧单结果链路 |
| [Chat Scroll Jitter Reduction Design](./2026-04-21-chat-scroll-jitter-reduction-design.md) | 2026-04-21 | 以“消息视觉与对齐零变化”为硬约束，优先通过单滚动容器、sticky composer 与节流 auto-follow 降低 AI 流式输出在靠近底部输入框时的抖动 |
| [Stage Window SQL Workbench Design](./2026-04-21-stage-window-sql-workbench-design.md) | 2026-04-21 | 以 `shadcn/ui` 为硬约束，将 Stage 升级为多面板 SQL 工作台：布局参考原型但不复刻原型控件体系，`query_editor` 收敛为唯一 SQL 工作页，`bang_query` 退场，并同步更新资源目录 `AGENTS.md` 与 UI Object 协议文档 |
| [Stage Window Layout Refactor Design](./2026-04-21-stage-window-layout-refactor-design.md) | 2026-04-21 | Stage 重构为左侧导航侧栏 + 右侧 Tabs 工作区的浏览器式工作台：顶部轻量工具行、下方连接资源浏览器、资源上下文驱动的工具打开规则，以及 Chrome-inspired 顶部页签，并移除底部 Dock |
| [Session Data Context & AI Data Source Management Design](./2026-04-21-session-data-context-and-ai-datasource-management-design.md) | 2026-04-21 | 建立 session 级 `connectionId + database + schema` 统一上下文，收敛 `use xxx` 的自动匹配 / 建议 / 歧义处理，打通 `!sql`、AI 对话、Stage Query Editor 与 schema 读取 / SQL 执行的同一解析链路，并补齐 AI 数据源管理能力边界（新增 / 测试 / 选择 / 修改，禁止删除） |
| [Bang Query Badge Minimization Design](./2026-04-21-bang-query-badge-minimization-design.md) | 2026-04-21 | 将 bang-query 用户气泡从显式 `SQL 直查` 文字 badge 收敛为右上角低存在感小图标，保留语义识别但减少视觉打扰 |
| [Stage Query Editor Design](./2026-04-21-stage-query-editor-design.md) | 2026-04-21 | Stage 特性全量接通 store + 新增 Query Editor tab（CodeMirror SQL 编辑器 + 结果面板 + 双路径执行：AI 预填直接执行、用户手写经风险判级；高风险拦截并提供"发给 AI 审查"安全阀）+ 后端新增 `POST /api/sql/execute` 端点 |
| [Bang Query Chat Visibility Design](./2026-04-21-bang-query-chat-visibility-design.md) | 2026-04-21 | 为 `!select` / `!with` 直查补齐聊天区可见性与持久化：将直查输入持久化为 DataTalk synthetic user message，与 OpenCode 历史统一合并排序；消息仍显示为普通用户气泡，但带 `SQL 直查` 标记；Composer 在命中直查模式时进入整框变色 + 状态标签的直查态 |
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
