项目计划书。

### 📋 项目愿景与核心定位

**项目定位**：一个面向数据工程师、分析师和开发者的**智能数据库协作平台**。它并非 Navicat 或 DataGrip 的简单替代品，而是将 AI 作为一等公民，让数据库管理、查询、可视化和设计工作，都通过自然语言对话来驱动和串联。

**核心价值**：
*   **交互革新**：以聊天为主界面，数据库操作为上下文，实现“边说边查，边问边画”。
*   **智能增强**：利用 AI 理解数据意图、生成 SQL、解释执行计划、推荐图表类型。
*   **可视协同**：内置 ER 图和报表能力，让数据结构与数据洞察一目了然。
*   **架构开放**：通过 OpenCode 标准协议接入 AI 能力，模型可替换、可私有化。

### 🏛️ 系统架构设计

整个系统采用 **桌面端 + 后端服务 + AI 服务** 的三层分离架构。

| 层级 | 技术选型 | 职责说明 |
| :--- | :--- | :--- |
| **桌面客户端** | Tauri v2 + React + Vite + shadcn/ui + React Flow + ECharts | 负责 UI 渲染、状态管理、与后端 API 通信。利用 Tauri 的 IPC 调用本地系统能力。 |
| **后端服务** | Spring Boot 3.5.x + GraalVM (Native Image) | 核心业务逻辑层。提供 REST API，管理数据库连接池、执行 SQL、处理用户项目和会话、与 AI 服务交互。 |
| **AI 服务** | OpenCode Server (HTTP Mode) | 作为独立的 AI 能力提供方。后端通过 HTTP 调用 OpenCode 的 `/session`、`/message` 等 API 来驱动 AI 对话、代码生成和工具调用。 |

**关键交互流程（以查询为例）**：
1.  用户在客户端聊天框输入：“帮我查询用户表 `users` 中近一周的注册人数趋势”。
2.  前端将**当前激活的数据库连接信息**作为上下文，连同消息一起发送给后端。
3.  后端构建一个详细的 Prompt（包含表结构、数据库类型等），调用 OpenCode 的 `/session/:id/message` 接口。
4.  OpenCode 在沙箱环境中推理，可能调用内置的 `database-schema` 工具获取表结构，生成 SQL 语句。
5.  后端接收 OpenCode 返回的 SQL 语句，在目标数据库执行，并将结果集（或错误信息）返回。
6.  前端在对话区展示 AI 的思考过程和 SQL，并在右侧 Tab 页展示结果表格，同时可调用 ECharts skill 一键生成图表。

### 📁 项目结构规划

项目建议采用 **Monorepo** 结构，便于统一管理。

```
chat-database-tool/
├── client/                          # Tauri 前端项目
│   ├── src/
│   │   ├── components/
│   │   │   ├── layout/              # 布局组件 (侧边栏、主内容区等)
│   │   │   ├── connection/          # 数据库连接管理组件
│   │   │   ├── session/             # 会话列表组件
│   │   │   ├── chat/                # 对话界面组件
│   │   │   ├── data-grid/           # 数据表格展示组件
│   │   │   ├── diagram/             # ER图 (React Flow) 组件
│   │   │   └── chart/               # 图表 (ECharts) 组件
│   │   ├── pages/                   # 主页面
│   │   ├── services/                # 与后端 API 通信的服务层
│   │   ├── stores/                  # 状态管理
│   │   └── ...
│   ├── src-tauri/                   # Tauri 后端 (Rust)
│   └── ...
├── server/                          # Spring Boot 后端项目
│   ├── src/main/java/com/yourcompany/chatdb/
│   │   ├── controller/              # REST API 控制器
│   │   ├── service/                 # 业务逻辑 (数据库连接、查询、AI调用)
│   │   ├── entity/                  # 数据实体 (项目、连接、会话)
│   │   ├── repository/              # 数据仓库 (初期使用 SQLite)
│   │   ├── config/                  # 配置类 (数据库连接池、OpenCode Client)
│   │   └── client/                  # OpenCode HTTP 客户端封装
│   └── ...
└── README.md
```

### 🗺️ 功能模块与开发路线图

#### **第一阶段：基础能力搭建 (MVP)**
*   **目标**：跑通核心流程，能在聊天中查询数据库。
*   **后端**：
    1.  项目初始化，集成 SQLite 作为元数据库。
    2.  实现数据库连接管理 API (支持 MySQL, PG)。
    3.  实现基础的会话管理 API。
    4.  封装 OpenCode HTTP Client，实现消息发送与接收。
    5.  实现 SQL 执行器，动态加载 JDBC 驱动。
*   **前端**：
    1.  搭建 Tauri + React 基础框架。
    2.  实现主布局：左侧边栏（连接与会话列表）、右侧内容区（暂不分栏）。
    3.  实现数据库连接的创建、测试、保存 UI。
    4.  实现聊天界面组件，能发送消息并流式展示 AI 回复。

#### **第二阶段：核心功能深化**
*   **目标**：实现分栏交互、数据展示和可视化。
*   **后端**：
    1.  优化 AI Prompt 上下文，自动注入选中数据库的表结构信息。
    2.  开发结果集查询 API，支持分页和格式转换。
    3.  支持 SQL Server, Oracle 数据库。
*   **前端**：
    1.  实现主窗体分栏：左侧对话区 + 右侧 Tab 页管理器。
    2.  开发数据表格组件，展示查询结果，并支持复制、导出。
    3.  集成 ECharts，实现 `echarts-ai-skill`：接收 AI 生成的图表配置并渲染。
    4.  集成 React Flow，实现基础的 ER 图展示（从数据库元数据生成）。

#### **第三阶段：智能化与体验优化**
*   **目标**：提升 AI 协作深度和用户体验。
*   **后端**：
    1.  实现会话上下文持久化，AI 能记忆之前的对话和查询结果。
    2.  开发 Agent 能力：如“数据洞察”Agent，能自动对查询结果进行描述性统计分析。
    3.  实现用户认证与项目隔离。
*   **前端**：
    1.  支持在 Tab 页中编辑和运行 SQL。
    2.  实现 React Flow 的交互式编辑（通过 AI 修改表结构等）。
    3.  优化流式响应下的 Markdown 渲染和代码高亮。
    4.  支持暗色/亮色主题切换。

### 🎨 界面布局与交互设计（参照 chat.deepseek.com）

界面采用经典的三栏式结构，清晰且高效：

```
+----------------+-----------------------------------+-----------------------------------+
|    左侧边栏     |           对话主区域 (Chat)         |          工作区 (Workspace)        |
| (Sidebar)      |                                   |   (Tabbed Pane)                   |
+----------------+-----------------------------------+-----------------------------------+
| [新建连接] ▼   |                                   | [查询结果] [ER图] [报表] [SQL编辑器]|
| [连接列表]     |  User:                            |-----------------------------------|
|  - MySQL-Local |  查询用户表近一周增长趋势          |                                   |
|  - PG-Prod     |                                   |  | id | name  | created_at |     |
|----------------|  AI:                              |  |----|-------|------------|     |
| [新建会话] ▼   |  好的，已为您生成SQL并执行。       |  | 1  | John  | 2026-04-10|     |
| [会话历史]     |                                   |  | 2  | Jane  | 2026-04-11|     |
|  - 今日        |  ```sql                          |  | 3  | Bob   | 2026-04-12|     |
|    * 用户分析  |  SELECT DATE(created_at) as date  |  | ... | ...   | ...        |     |
|    * 订单排查  |  , COUNT(*) as count             |  |                                   |
|  - 本周        |  FROM users                      |  | [ 📊 一键生成图表 ] [ 📋 复制 ]    |
|    * 库存报表  |  WHERE created_at > DATE('now',  |  |                                   |
|                |        '-7 days')                |  +-----------------------------------+
|                |  GROUP BY date                   |  |                                   |
|                |  ORDER BY date;                  |  |  (当选中ER图时，此处展示React Flow) |
|                |  ```                             |  |                                   |
|                |                                   |  |                                   |
|                |  查询耗时: 120ms，共 5 行。        |  |                                   |
+----------------+-----------------------------------+-----------------------------------+
```

### 🔌 核心接口设计示例

**1. 后端调用 OpenCode 发送消息**

```java
// 后端 OpenCodeClient.java 伪代码
public Mono<String> sendMessage(String sessionId, String userPrompt, DatabaseContext context) {
    // 1. 构建完整的 Prompt，注入表结构等上下文
    String fullPrompt = buildPromptWithSchema(userPrompt, context);
    
    // 2. 构造 OpenCode API 请求体
    MessageRequest request = MessageRequest.builder()
            .parts(List.of(new Part("text", fullPrompt)))
            .model("claude-4") // 指定模型
            .build();

    // 3. 调用 OpenCode HTTP API，获取流式响应
    return webClient.post()
            .uri("/session/{id}/message", sessionId)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class); // 处理 SSE 流
}
```

**2. 前端接收并展示**

前端通过 `EventSource` 或 `fetch` API 连接到后端的流式端点，逐块接收 AI 的响应内容，并使用 `react-markdown` 等库实时渲染。

### 🧠 关键问题与对策

| 潜在问题 | 对策 |
| :--- | :--- |
| **数据库密码安全** | 密码在客户端加密后存储在后端（或使用系统凭据管理器），后端连接池动态创建，不落地明文密码。 |
| **SQL 注入风险** | 1. **AI 侧**：强化 Prompt，要求其优先使用参数化查询。<br>2. **后端侧**：对所有非查询类 SQL 进行二次校验和拦截，提供“预演”模式。 |
| **大结果集传输性能** | 后端结果集查询 API 必须支持分页和流式传输。前端表格组件使用虚拟滚动（如 `react-virtual`）。 |
| **OpenCode 与数据库驱动的隔离** | 保持 OpenCode 作为纯推理服务，不直接连接数据库。所有数据库操作均由后端 Spring Boot 服务代理执行。这遵循了 OpenCode 作为“智能体”的定位。 |

### 📄 总结

这份计划书为你勾勒出了一个架构清晰、交互现代且技术先进的项目蓝图。它以 **OpenCode** 作为 AI 大脑，以 **Spring Boot** 作为强健的数据处理躯干，以 **Tauri** 构建轻快美观的交互界面。

建议第一步是搭建好 Tauri + Spring Boot 的脚手架，并跑通一次简单的、硬编码的 SQL 查询流程。随后再将 OpenCode 集成进来，你会很快看到一个智能助手的雏形。

针对技术选型，需要我再详细对比一下前端用 React 还是 Vue 在 Tauri 生态中的优劣吗？
