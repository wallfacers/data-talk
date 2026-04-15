# MVP 设计文档：智能数据库协作平台 - 最小可运行版本

**日期**: 2026-04-15
**状态**: 待审批

## 1. 项目概述

### 1.1 目标
搭建一个最小可运行的智能数据库协作平台 MVP，跑通 Tauri 前端 → Spring Boot 后端 → 数据库查询 的核心链路。

### 1.2 核心价值
- 验证 Monorepo 项目结构
- 验证前后端通信
- 验证数据库查询流程
- 为后续 AI 集成奠定基础

## 2. 技术选型

| 层级 | 技术 | 版本/说明 |
| :--- | :--- | :--- |
| 前端框架 | Tauri + React + TypeScript | Tauri v2 |
| 构建工具 | Vite | - |
| UI 组件 | shadcn/ui | 组件通过 `npx shadcn@latest add` 安装，不支持的才手写 |
| 布局 | sidebar-07 | `npx shadcn@latest add sidebar-07` |
| 后端框架 | Spring Boot | 3.5.x |
| JDK | Java | 21 |
| 元数据库 | SQLite | 存储连接配置、会话记录等 |
| 演示数据库 | H2 | 内存数据库，用于演示硬编码查询 |
| 构建工具 | Gradle | - |

## 3. 项目结构

```
chat-database-tool/
├── client/                          # Tauri 前端项目
│   ├── src/
│   │   ├── components/              # UI 组件（shadcn/ui + 手写）
│   │   ├── pages/                   # 主页面
│   │   ├── services/                # API 通信服务层
│   │   ├── App.tsx                  # 根组件
│   │   └── main.tsx                 # 入口
│   ├── src-tauri/                   # Tauri Rust 后端
│   ├── package.json
│   ├── vite.config.ts
│   └── tailwind.config.ts
├── server/                          # Spring Boot 后端项目
│   ├── build.gradle
│   └── src/main/java/com/datatalk/chatdb/
│       ├── controller/              # REST API 控制器
│       ├── service/                 # 业务逻辑
│       ├── entity/                  # 数据实体
│       ├── repository/              # 数据仓库
│       └── ChatDbApplication.java   # 启动类
└── README.md
```

## 4. 架构设计

### 4.1 前端架构

```
┌─────────────────────────────────────────────┐
│                  Tauri Window               │
├────────────┬────────────────────────────────┤
│  Sidebar   │        Main Content            │
│  (shadcn)  │                                │
│            │  ┌──────────┬───────────────┐  │
│  连接列表   │  │  Chat    │   Workspace   │  │
│  会话列表   │  │  区域     │   (Tabbed)    │  │
│            │  │          │               │  │
│  [新建]    │  │  消息列表 │   查询结果     │  │
│            │  │  输入框   │   表格展示     │  │
│            │  └──────────┴───────────────┘  │
└────────────┴────────────────────────────────┘
```

- 使用 shadcn/ui 的 `sidebar-07` 布局作为主框架
- 聊天区域手写组件，集成 markdown 渲染基础能力
- 工作区使用 Tab 结构，MVP 阶段展示查询结果表格

### 4.2 后端架构

```
Client (Tauri)
    │
    │ HTTP REST
    ▼
┌──────────────────────────────────┐
│         Spring Boot API          │
│                                  │
│  Controller → Service → DAO     │
│                                  │
│  ┌────────────┐  ┌────────────┐ │
│  │  SQLite DB │  │   H2 DB    │ │
│  │  (元数据)   │  │  (演示数据) │ │
│  └────────────┘  └────────────┘ │
└──────────────────────────────────┘
```

- REST API 提供查询接口
- SQLite 存储项目元数据（MVP 阶段只需建表，不强制实现完整 CRUD）
- H2 内存数据库预置演示数据，硬编码查询在此执行

### 4.3 数据流

```
用户输入查询 → 前端发送 POST /api/query → 后端接收
→ 执行硬编码 SQL（查 H2 演示库）→ 返回结果集 → 前端表格展示
```

## 5. API 设计

### 5.1 查询接口

```
POST /api/query
Request:  { "connectionId": "demo", "sql": "SELECT * FROM users" }
Response: { "columns": ["id", "name", "email"], "rows": [...], "duration": 12 }
```

MVP 阶段 `connectionId` 固定为 `"demo"`，`sql` 可以忽略或使用硬编码 SQL。

### 5.2 健康检查

```
GET /api/health
Response: { "status": "ok", "timestamp": "..." }
```

## 6. 前端组件设计

### 6.1 shadcn/ui 组件清单

需要安装的 shadcn/ui 组件：
- `sidebar-07`（布局框架）
- `button`
- `input`
- `textarea`
- `tabs`（工作区 Tab 切换）
- `table`（查询结果展示）

### 6.2 手写组件

- `ChatArea`：聊天消息列表 + 输入框
- `MessageBubble`：单条消息渲染（支持基础 markdown）
- `QueryResult`：查询结果表格封装

## 7. 错误处理

- 后端：统一异常处理，返回标准错误格式 `{ "error": "message", "code": "xxx" }`
- 前端：网络错误展示 Toast 提示，SQL 错误在聊天区展示

## 8. 测试

- 后端：至少一个 Controller 单元测试验证查询接口
- 前端：确保 Tauri 开发模式能正常启动和热更新

## 9. 成功标准

MVP 完成的标准：
1. `cd client && npm run tauri dev` 能启动桌面应用
2. `cd server && ./gradlew bootRun` 能启动后端服务
3. 前端能向后端发起查询请求
4. 后端返回 H2 演示库的查询结果
5. 前端在界面上展示查询结果
