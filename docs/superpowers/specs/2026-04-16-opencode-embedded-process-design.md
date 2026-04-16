# OpenCode 嵌入式进程管理 — 设计文档

> **日期**: 2026-04-16
> **状态**: 待实现
> **范围**: 后端 Spring Boot 启动时自动嵌入管理 OpenCode 进程

## 1. 概述

将 OpenCode 作为子进程嵌入 Spring Boot 应用生命周期，消除用户手动启动 `opencode serve` 的需求。支持开发模式（GitHub 下载）和 JAR 内嵌模式（从 Classpath 资源提取），跨平台（Linux/macOS/Windows），并在父进程退出时确保子进程被清理。

## 2. 配置

### 2.1 新增配置项 (`application.yml`)

```yaml
datatalk:
  opencode:
    serve:
      enabled: true                    # 是否由 Spring Boot 嵌入启动 OpenCode
      auto-upgrade: false              # 是否自动检测 GitHub 最新版本
      version: 1.4.6                   # 默认版本号
      base-port: 4096                  # 起始端口（冲突时自动递增）
      port-retries: 100                # 端口冲突最大重试次数
      hostname: 127.0.0.1              # 绑定地址
      cors: http://localhost:8080      # CORS origin
    required: false                    # OpenCode 是否必需，true 则启动失败导致应用退出
    base-url: http://localhost:4096    # 已有配置，将被动态端口覆盖
    plugin-callback-base: http://localhost:8080
    shared-secret: ""
```

### 2.2 运行时目录

```
~/.data-talk/opencode/
├── .current                  # 当前活跃版本号（如 "1.4.6"）
├── v1.4.6/                   # 当前版本二进制目录
│   └── opencode              # Linux/macOS 可执行文件或 Windows .exe
├── v1.4.7/                   # 新版本（升级时并行存在，作为回退）
│   └── opencode
└── temp-{uuid}/              # 提取/下载过程中的临时目录
```

## 3. 架构与模块划分

### 3.1 新增组件

所有新组件位于 `data-talk-infrastructure` 模块：

| 类 | 职责 |
|---|---|
| `OpenCodeProcessManager` | `SmartLifecycle` 实现，核心生命周期管理 |
| `OpenCodeBinaryResolver` | 二进制查找、下载、Classpath 提取、版本管理 |
| `OpenCodePortAllocator` | 动态端口探测 + 冲突递增 |
| `OpenCodePlatform` | OS/arch 枚举 + 二进制命名映射 |
| `OpenCodeServeProperties` | `@ConfigurationProperties` 配置绑定 |

### 3.2 与现有组件的关系

```
OpenCodeProcessManager (新增, SmartLifecycle phase=-100)
  ↓ 启动 opencode serve，获取实际端口
  ↓ 创建/更新
OpenCodeHttpClient (已有)
  ↓ 使用动态 base-url (含实际端口)
OpenCodeGateway (已有)
  ↓ 注册 Tools、转发消息
```

`OpenCodeProcessManager` 在 `SmartLifecycle.start()` 中完成全部准备和启动工作，在 `ApplicationReadyEvent`（现有 wiring 触发点）之前就绪。

## 4. 启动序列

```
SmartLifecycle.start() [phase=-100]
  │
  ├─ 1. 检查 serve.enabled → false 则跳过
  │
  ├─ 2. 二进制检查与获取
  │     ├─ 2a. 检查 ~/.data-talk/opencode/.current + 对应版本可执行文件
  │     │       存在且可执行 → 直接使用，跳到 5
  │     ├─ 2b. 从 Classpath resources/opencode/{platform}/ 提取（JAR 模式）
  │     │       提取到临时目录 → 更新 .current → 跳到 5
  │     └─ 2c. 从 GitHub Release 下载（开发模式）
  │             查询 API（auto-upgrade=true 时）或固定版本
  │             下载 → 解压 → 更新 .current
  │
  ├─ 3. 动态端口分配
  │     └─ ServerSocket.bind(base-port) → 失败则 port++ → 最多重试 port-retries 次
  │
  ├─ 4. 启动 opencode serve
  │     ├─ 命令: opencode serve --port {actualPort} --hostname {hostname} --cors {cors}
  │     ├─ ProcessBuilder:
  │     │   - workingDir: ~/.data-talk/opencode/
  │     │   - redirectErrorStream: true
  │     │   - stdout → BufferedReader → logger.info (每行)
  │     ├─ 等待就绪: 轮询 stdout 匹配就绪信号，超时 30s
  │     └─ 进程异常退出 → 抛异常
  │
  ├─ 5. 注册 Shutdown Hook
  │     └─ Runtime.addShutdownHook(new Thread(() -> process.destroyForcibly()))
  │
  └─ 6. 将实际 base-url 注入 OpenCodeHttpClient
        └─ OpenCodeHttpClient 通过 setter `setBaseUrl(String url)` 或构造函数参数接收动态地址
           （具体注入机制：ProcessManager 持有 HttpClient 引用，在 start() 末尾调用 setBaseUrl）
```

## 5. 关闭生命周期

### 5.1 优雅关闭

```
Spring 上下文关闭
  │
  ├─ SmartLifecycle.stop() [phase=-100]
  │     ├─ 1. 停止 OpenCodeEventLoop (SSE 消费)
  │     ├─ 2. process.destroy() (SIGTERM)
  │     ├─ 3. process.waitFor(10, SECONDS)
  │     ├─ 4. 若仍在运行 → process.destroyForcibly() (SIGKILL)
  │     └─ 5. 清理 OpenCodeSessionMap, OpenCodeEventTranslator.forget()
  │
  └─ JVM ShutdownHook (兜底)
        └─ process.destroyForcibly()
```

### 5.2 非优雅关闭

极端场景（`kill -9` / OOM）下 JVM 被立即杀死，子进程可能成为孤儿。由于项目是 Tauri 桌面应用，正常情况下 Tauri 关闭 Spring Boot 会走 SIGTERM 路径，非优雅关闭作为已知限制记录，不引入 JNA 增加跨平台复杂度。

## 6. 错误处理与降级

### 6.1 启动阶段

| 错误 | `required=true` | `required=false` |
|---|---|---|
| 下载/解压/提取失败 | 抛异常，启动失败 | WARN 日志，降级模式 |
| 端口全部被占用 | 抛异常，启动失败 | WARN 日志，降级模式 |
| opencode serve 启动超时 | 抛异常，启动失败 | WARN 日志，降级模式 |
| `serve.enabled=false` | 跳过，依赖外部实例 | 跳过，依赖外部实例 |

### 6.2 运行阶段

| 错误 | 行为 |
|---|---|
| OpenCode 进程意外崩溃 | ERROR 日志，自动重启（最多 3 次，指数退避），失败则降级 |
| JVM Shutdown | ShutdownHook 兜底 destroyForcibly() |

## 7. 跨平台二进制命名

| 平台 | 架构 | resources 路径 | 文件名 |
|---|---|---|---|
| Linux | x86_64 | `opencode/linux-x64/` | `opencode` |
| Linux | arm64 | `opencode/linux-arm64/` | `opencode` |
| macOS | x86_64 | `opencode/darwin-x64/` | `opencode` |
| macOS | arm64 (Apple Silicon) | `opencode/darwin-arm64/` | `opencode` |
| Windows | x86_64 | `opencode/windows-x64/` | `opencode.exe` |

## 8. OpenCode 自动升级

当 `auto-upgrade=true` 时：

1. 启动时查询 GitHub API `GET /repos/anomalyco/opencode/releases/latest`
2. 获取 latest release tag（如 `v1.4.7`）
3. 与 `.current` 中版本比较
4. 如果有新版本：
   - 下载到 `~/.data-talk/opencode/v1.4.7/`（保留旧版本 `v1.4.6/`）
   - 验证新二进制可执行性
   - 验证通过 → 更新 `.current`
   - 验证失败 → 保留 `.current` 不变，使用旧版本
5. 如果查询 GitHub API 失败（网络问题）→ 使用 `.current` 版本

当 `auto-upgrade=false` 时：使用配置中 `version` 指定的版本。

## 9. 测试策略

### 9.1 单元测试

- `OpenCodePortAllocator`: 端口探测、冲突递增、重试上限
- `OpenCodePlatform`: OS/arch 检测准确性
- `OpenCodeBinaryResolver`: checksum 验证、classpath 路径解析

### 9.2 集成测试

- 使用 WireMock 模拟 GitHub Release API
- 使用真实 OpenCode 二进制（CI 环境）或 mock 脚本验证启动/关闭流程
- 验证 ShutdownHook 在进程退出时清理子进程

### 9.3 降级模式测试

- 启动不可达的 OpenCode → 验证 `required=false` 时应用继续运行
- 端口被占用 → 验证 port++ 策略
