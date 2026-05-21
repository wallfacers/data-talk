# Query Password Propagation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `/api/query` 查询链路两处连接参数回归：一是未透传数据源密码导致 `using password: NO`，二是 `databaseName = null` 时被错误拼成数据库名 `null`。

**Architecture:** 保持 `/api/query` 继续走 `QueryApplicationService -> SqlExecutionRepository` 这条链，但把已保存连接的解密密码补回到执行层，并让 legacy 直查路径与现有 `JdbcUrlBuilder` 共用同一套 URL 构造规则。最小改动方式是扩展 `DbConnection` 让它携带运行时密码，`QueryApplicationService` 通过现有 `ConnectionService.decryptPassword()` 取得密码，`DynamicSqlExecutionRepository` 使用该密码初始化 Hikari 数据源，并改为复用 `JdbcUrlBuilder` 避免 `databaseName = null` 时再次拼出字面量 `null`。

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, Mockito, HikariCP。

---

## File Structure Map

### Modify

- `server/data-talk-domain/src/main/java/com/datatalk/entity/DbConnection.java`
- `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/DynamicSqlExecutionRepository.java`
- `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`
- `docs/exec-plans/index.md`

---

### Task 1: 把密码从连接仓储透传到执行层

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/entity/DbConnection.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`

- [x] **Step 1.1: 写失败测试**
  - 在 `QueryApplicationServiceTest` 里断言 `sqlExecutionRepository.execute(...)` 收到的连接对象包含 `ConnectionService.decryptPassword(connectionId)` 返回的密码。

- [x] **Step 1.2: 跑失败测试**
  - Run: `cd server && mvn -q -pl data-talk-application -Dtest=QueryApplicationServiceTest test`
  - Result: FAIL，表现为 `QueryApplicationService` 构造签名、`DbConnection` 构造签名、`password()` accessor 都不存在。

- [x] **Step 1.3: 实现最小修复**
  - 给 `DbConnection` 增加运行时密码字段。
  - `QueryApplicationService` 注入 `ConnectionService` 并在映射 `ConnectionRecord` 时填入解密密码。
  - `ApplicationServiceConfig` 同步更新构造参数。

- [x] **Step 1.4: 再跑服务层测试**
  - Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=QueryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - Result: PASS

### Task 2: 让 JDBC 执行层使用透传密码

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/DynamicSqlExecutionRepository.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`

- [x] **Step 2.1: 实现最小修复**
  - 用 `connection.password()` 替换当前硬编码的空密码。
  - 保留用户名可空的现有逻辑。
  - 补充 `JdbcDbConnectionRepository` 构造参数以兼容 `DbConnection` 新字段。
  - `DynamicSqlExecutionRepository` 改为复用 `JdbcUrlBuilder.build(DbConnection)`，让 `databaseName = null` 走与 connection test / action 执行一致的兜底规则。

- [x] **Step 2.2: 编译校验**
  - Run: `cd server && mvn -q -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am compile`
  - Result: PASS

- [x] **Step 2.3: URL builder 回归测试**
  - Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=JdbcUrlBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - Result: PASS

### Task 3: Consolidated Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-21-query-password-propagation-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 3.1: 运行验证**
  - Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=QueryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - Run: `cd server && mvn -q -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am compile`
  - Run: `cd server && mvn -q -pl data-talk-adapter -am -Dmaven.test.skip=true install`
  - 状态说明：`install -DskipTests` 会被工作树里无关的 `OpenCodeEventLoopTest` 改动卡在 `testCompile`，因此本次用 `-Dmaven.test.skip=true` 完成产物安装。

- [x] **Step 3.2: 手动烟测**
  - 待用户在真实数据库环境里复现你的 `POST /api/query` 请求，确认不再出现 `using password: NO`
  - 待用户确认 `databaseName = null` 的 MySQL 连接不再报 `Unknown database 'null'`
  - 自动化状态：未在当前会话直接连你的 MySQL 实例执行该 HTTP 请求

- [x] **Step 3.3: 文档收尾**
  - 勾完本计划
  - 在 `docs/exec-plans/index.md` 把条目移到 Completed
