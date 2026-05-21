# DataTalk

AI 驱动的智能数据库协作平台。通过自然语言对话即可查询数据库、生成 SQL、可视化数据，让数据分析像聊天一样简单。

![DataTalk 效果图](效果图.png)

## 功能亮点

- **自然语言查库** — 聊天式交互，AI 理解你的业务问题，自动生成并执行 SQL，结果以表格、图表、仪表盘呈现
- **多数据库支持** — 覆盖 MySQL、PostgreSQL、SQLite、DuckDB、ClickHouse、TiDB、MariaDB、OceanBase、StarRocks、Apache Doris、Trino、Presto、Oracle、SQL Server、Dameng、GaussDB、Apache Hive 等 17+ 数据源
- **可视化工作台** — 内置 SQL 编辑器（Monaco）、ER 图设计器/查看器、图表仪表盘、数据采集脚本编辑器等多 Tab 工作区
- **智能诊断** — SQL 报错自动分析、索引建议、锁等待检测、连接池状态、表空间监控
- **语义模型** — 将业务术语（"GMV"、"客单价"）映射为数据库查询，支持已验证查询记录与复用
- **桌面应用** — 基于 Tauri v2 的桌面客户端，连接本地或远程数据库，享受原生体验

## 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 21+ |
| Node.js | 22+ |
| Maven | 3.9+ |

### 启动后端

```bash
export JAVA_HOME=/path/to/jdk-21
cd server
mvn spring-boot:run -pl data-talk-adapter
```

后端默认运行在 `http://localhost:8080`。

### 启动前端（Web 开发模式）

```bash
cd client
npm install
npm run dev
```

### 启动 Tauri 桌面应用

```bash
cd client
npm install
npm run tauri dev
```

### 运行测试

```bash
# 后端测试
cd server && mvn verify

# 前端测试
cd client && npx vitest run
```

## 内置技能（Skills）

DataTalk 服务端内置了 18 个 AI 技能，定义了 AI 如何操作用户数据库、工作台和产出物。每个技能对应一个 SKILL.md 文件，OpenCode 在运行时加载。

### 数据查询与分析

| 技能 | 说明 | 文件 |
|------|------|------|
| **sql-execution** | 全 SQL 执行契约入口（SELECT/INSERT/UPDATE/CREATE/ALTER…ADD 直接执行）：DELETE 触发对话式 `confirmationId` 确认，破坏性 DDL（DROP/TRUNCATE/ALTER…DROP/GRANT/REVOKE 等）拦截并 `redirect_to_editor` 由用户手动执行 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/sql-execution/SKILL.md) |
| **sql-error-diagnostics** | SQL 报错自动诊断：语法错误、对象不存在、歧义候选、锁等待、慢查询、连接池/表空间 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/sql-error-diagnostics/SKILL.md) |
| **exploring-data** | 写表前探索协议（Pre-Action Exploration）：get_data_context → schema_search → read_schema → execute_sql 的探查顺序与命中预算 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/exploring-data/SKILL.md) |

### 连接与方言

| 技能 | 说明 | 文件 |
|------|------|------|
| **connection-management** | 数据源连接生命周期管理（创建/测试/编辑/选择），会话数据上下文切换，两阶段确认协议 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/connection-management/SKILL.md) |
| **database-dialects** | 17+ 数据库方言差异：连接类型、默认端口、JDBC 驱动、schema 可见性、SQL 分割器、风险等级、ER/诊断支持矩阵 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/database-dialects/SKILL.md) |

### 可视化与产出

| 技能 | 说明 | 文件 |
|------|------|------|
| **bezel** | 工业级仪表盘，从 JSON 描述生成自包含 HTML（含内嵌轮询），可作为 iframe 嵌入 DataTalk | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md) |
| **charts-and-dashboards** | 图表/趋势图/KPI/多组件仪表盘，支持内联 chart 代码块和持久化 dashboard schema | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/charts-and-dashboards/SKILL.md) |
| **artifacts-output** | 产出物生命周期管理：临时文件 vs 归档候选，超大响应落盘续传协议 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/artifacts-output/SKILL.md) |
| **ledger** | 企业级汇报文档生成（业务月报、问题复盘、季度总结、事件 postmortem），含模板渲染 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/ledger/SKILL.md) |

### 工作台交互

| 技能 | 说明 | 文件 |
|------|------|------|
| **query-editor-workflow** | SQL 编辑器全生命周期：新建 → 设置上下文 → 编辑内容 → 执行 → 聚焦，及编辑器复用规则 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/query-editor-workflow/SKILL.md) |
| **tab-management** | Tab 生命周期与复用策略：library vs workset、跨重启持久化、搜索定位 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/tab-management/SKILL.md) |
| **ui-contract** | 四个 UI 工具（ui_find/ui_read/ui_patch/ui_exec）的调用契约，用于发现、检查、编辑工作台 Tab | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/ui-contract/SKILL.md) |
| **concurrency-contract** | 乐观锁并发编辑协议：版本冲突检测、409 恢复流程、多编辑批处理 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/concurrency-contract/SKILL.md) |

### 数据建模

| 技能 | 说明 | 文件 |
|------|------|------|
| **er-tabs** | ER 图查看器（只读 + 标注）和 ER 设计器（独立 schema 草稿，产出 DDL） | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/er-tabs/SKILL.md) |
| **semantic-model-usage** | 语义模型使用：业务术语查找、已验证查询记录/复用、语义变更提案 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/semantic-model-usage/SKILL.md) |
| **skill-creator** | 为业务域创建新的语义模型 Skill（YAML），定义业务指标和度量 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/skill-creator/SKILL.md) |

### 数据工程

| 技能 | 说明 | 文件 |
|------|------|------|
| **data-collection** | 通过 Python/Node.js 脚本采集外部数据并写入数据库 | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md) |
| **file-upload-routing** | 文件上传路由：根据预分析摘要将上传文件分发到对应处理 Action | [SKILL.md](server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md) |

## 技术栈

| 层级 | 技术 |
|------|------|
| 桌面客户端 | Tauri v2, React 19, Vite, TanStack Router/Query, Zustand, shadcn/ui, Tailwind CSS v4, Monaco Editor, Recharts |
| 后端服务 | Spring Boot 3.5, Java 21 (Virtual Threads), JdbcTemplate, Flyway, SQLite (元数据) |
| AI 通信 | OpenCode 协议 (Streamable HTTP + SSE, JSON-RPC) |
| 用户数据库 | 动态 JDBC 连接池，支持 17+ 数据源 |

## 文档索引

| 文档 | 说明 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 项目开发指南与工作规则 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 系统架构总览 |
| [docs/FRONTEND.md](docs/FRONTEND.md) | 前端开发指南 |
| [docs/BACKEND.md](docs/BACKEND.md) | 后端开发指南 |
| [docs/DESIGN.md](docs/DESIGN.md) | 设计模式与约定 |
| [docs/design-docs/index.md](docs/design-docs/index.md) | 设计文档索引 |
| [docs/bugs/index.md](docs/bugs/index.md) | BUG 跟踪与缺陷注册 |
| [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) | 数据源类型兼容性矩阵 |
| [docs/QUALITY.md](docs/QUALITY.md) | 质量标准与评分 |
| [docs/RELIABILITY.md](docs/RELIABILITY.md) | 可靠性实践 |
| [docs/SECURITY.md](docs/SECURITY.md) | 安全指南 |
| [docs/I18N.md](docs/I18N.md) | 国际化指南 |
| [client/DESIGN.md](client/DESIGN.md) | 前端设计契约 |
