---
name: logging-standardization
description: Standardize backend logging with logback-spring.xml, fix System.err usage, add file appenders
type: design
---

# 日志规范化设计

## 问题

1. 没有 `logback-spring.xml`，仅依赖 Spring Boot 默认配置
2. `OpenCodeGatewayBeans.java` 使用 `System.err.println` 而非 logger
3. `SessionBus.java` 异常被静默吞掉
4. 没有文件日志输出，所有日志仅输出到控制台
5. 日志格式未统一

## 方案

### 1. logback-spring.xml 配置

**文件位置**：`server/data-talk-adapter/src/main/resources/logback-spring.xml`

**环境区分**：

- **dev**（默认 profile）：仅 CONSOLE appender，彩色输出
- **prod**：CONSOLE + FILE appender，纯文本

**文件 appender**：

- 路径：`logs/data-talk.log`
- 滚动策略：按天切割（`yyyy-MM-dd`）
- 保留 30 天
- 单文件最大 50MB
- 总大小上限 500MB

### 2. 日志格式

**开发环境（彩色）**：

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n
```

**生产环境（纯文本）**：

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n
```

### 3. 日志级别

| Logger | Level |
|---|---|
| `com.datatalk` | DEBUG |
| `org.springframework` | INFO |
| `org.hibernate` | WARN |
| root | INFO |

### 4. 代码修复

| 文件 | 问题 | 修复 |
|---|---|---|
| `OpenCodeGatewayBeans.java` | 3 处 `System.err.println` | 改为 `log.error` |
| `SessionBus.java` | catch 块静默吞异常 | 改为 `log.error("Event flusher failed", t)` |

## 影响

- 所有日志输出统一格式，可通过文件持久化
- 生产环境可保留 30 天日志，便于排查问题
- 消除 `System.err`  bypass，所有异常有日志
