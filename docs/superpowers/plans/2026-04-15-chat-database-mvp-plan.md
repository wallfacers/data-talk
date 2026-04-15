# Chat Database Tool MVP 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Tauri + React 前端 + Spring Boot 后端的 Monorepo 项目，跑通前后端通信和数据库查询核心链路。

**Architecture:** Monorepo 结构，Tauri v2 桌面客户端通过 HTTP REST 与 Spring Boot 后端通信，后端使用 H2 内存数据库预置演示数据执行查询，SQLite 存储元数据。

**Tech Stack:** Tauri v2, React 19, TypeScript, Vite, shadcn/ui, Spring Boot 3.5.x, JDK 21, Gradle, H2, SQLite

---

## 环境前置检查

当前环境状态：
- ✅ Node.js v24.14.1, npm 11.11.0
- ✅ Rust 1.94.1, Cargo 1.94.1
- ✅ Tauri 系统依赖: libwebkit2gtk-4.1-dev, libgtk-3-dev
- ⚠️ Java 1.8 在 PATH（需要 JDK 21）
- ❌ Gradle 未安装
- ❌ Tauri CLI 未安装

---

## 文件地图

### 后端文件 (server/)
| 文件 | 操作 | 职责 |
| :--- | :--- | :--- |
| `server/build.gradle` | 创建 | Gradle 构建配置，依赖声明 |
| `server/settings.gradle` | 创建 | 项目名称配置 |
| `server/gradlew` | 创建 | Gradle wrapper 脚本 |
| `server/gradle/wrapper/gradle-wrapper.jar` | 创建 | Wrapper JAR |
| `server/gradle/wrapper/gradle-wrapper.properties` | 创建 | Wrapper 配置 |
| `server/src/main/resources/application.yml` | 创建 | Spring Boot 配置 |
| `server/src/main/resources/schema-demo.sql` | 创建 | H2 演示库建表脚本 |
| `server/src/main/resources/data-demo.sql` | 创建 | H2 演示库初始化数据 |
| `server/src/main/resources/schema-sqlite.sql` | 创建 | SQLite 建表脚本 |
| `server/src/main/java/com/datatalk/chatdb/ChatDbApplication.java` | 创建 | 启动类 |
| `server/src/main/java/com/datatalk/chatdb/config/DataSourcesConfig.java` | 创建 | H2 + SQLite 双数据源配置 |
| `server/src/main/java/com/datatalk/chatdb/config/CorsConfig.java` | 创建 | CORS 跨域配置 |
| `server/src/main/java/com/datatalk/chatdb/model/QueryRequest.java` | 创建 | 查询请求 DTO |
| `server/src/main/java/com/datatalk/chatdb/model/QueryResponse.java` | 创建 | 查询响应 DTO |
| `server/src/main/java/com/datatalk/chatdb/service/QueryService.java` | 创建 | 查询执行服务 |
| `server/src/main/java/com/datatalk/chatdb/controller/QueryController.java` | 创建 | REST 控制器 |
| `server/src/test/java/com/datatalk/chatdb/controller/QueryControllerTest.java` | 创建 | 控制器单元测试 |

### 前端文件 (client/)
| 文件 | 操作 | 职责 |
| :--- | :--- | :--- |
| `client/package.json` | 创建 | npm 依赖配置 |
| `client/vite.config.ts` | 创建 | Vite 构建配置 + Tauri 插件 |
| `client/tsconfig.json` | 创建 | TypeScript 配置 |
| `client/tsconfig.node.json` | 创建 | Node 环境 TS 配置 |
| `client/tailwind.config.ts` | 创建 | Tailwind CSS 配置 |
| `client/postcss.config.js` | 创建 | PostCSS 配置 |
| `client/index.html` | 创建 | HTML 入口 |
| `client/src/main.tsx` | 创建 | React 入口 |
| `client/src/App.tsx` | 创建 | 根组件 |
| `client/src/index.css` | 创建 | 全局样式 + Tailwind |
| `client/src/vite-env.d.ts` | 创建 | Vite 类型声明 |
| `client/src-tauri/Cargo.toml` | 创建 | Rust 依赖配置 |
| `client/src-tauri/tauri.conf.json` | 创建 | Tauri 配置 |
| `client/src-tauri/build.rs` | 创建 | Rust 构建脚本 |
| `client/src-tauri/capabilities/default.json` | 创建 | Tauri 权限配置 |
| `client/src-tauri/src/lib.rs` | 创建 | Rust 应用入口 |
| `client/src-tauri/src/main.rs` | 创建 | Rust 主入口 |
| `client/src-tauri/.gitignore` | 创建 | Rust 忽略文件 |
| `client/src/lib/utils.ts` | 创建 | shadcn 工具函数 |
| `client/src/components/ui/sidebar.tsx` | 创建 | Sidebar 布局组件 |
| `client/src/components/ChatArea.tsx` | 创建 | 聊天区域组件 |
| `client/src/components/MessageBubble.tsx` | 创建 | 消息气泡组件 |
| `client/src/components/QueryResult.tsx` | 创建 | 查询结果表格组件 |
| `client/src/services/api.ts` | 创建 | 后端 API 通信服务 |

---

### Task 1: 环境准备 — 确保 JDK 21 和 Gradle 可用

**Files:** 无（环境配置）

- [ ] **Step 1: 定位或安装 JDK 21**

用户确认有 JDK 21 但不在 PATH 中。先尝试查找：

```bash
# 搜索系统上的 JDK 21
find /home/wushengzhou -name "java" -path "*/jdk-21*" 2>/dev/null
find /opt -name "java" -path "*/21*" 2>/dev/null
ls /usr/lib/jvm/ 2>/dev/null
```

如果找到了 JDK 21 路径，将其加入 PATH（在后续命令中用绝对路径）。如果没找到，安装 SDKMAN 并安装 JDK 21：

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem
java -version  # 确认输出 "openjdk version 21.x"
```

- [ ] **Step 2: 安装 Gradle**

使用 SDKMAN 安装 Gradle 8.5：

```bash
sdk install gradle 8.5
gradle --version  # 确认输出 Gradle 8.5 且 JVM 为 21
```

- [ ] **Step 3: 安装 Tauri CLI**

```bash
cargo install tauri-cli --version "^2"
cargo tauri --version  # 确认输出版本号 2.x
```

---

### Task 2: 搭建 Spring Boot 后端脚手架 + H2 演示数据库

**Files:**
- Create: `server/build.gradle`
- Create: `server/settings.gradle`
- Create: `server/gradle/wrapper/gradle-wrapper.properties`
- Create: `server/gradlew`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/schema-demo.sql`
- Create: `server/src/main/resources/data-demo.sql`
- Create: `server/src/main/resources/schema-sqlite.sql`
- Create: `server/src/main/java/com/datatalk/chatdb/ChatDbApplication.java`
- Create: `server/src/main/java/com/datatalk/chatdb/config/DataSourcesConfig.java`
- Create: `server/src/main/java/com/datatalk/chatdb/config/CorsConfig.java`

- [ ] **Step 1: 创建项目目录结构**

```bash
mkdir -p server/src/main/java/com/datatalk/chatdb/config
mkdir -p server/src/main/java/com/datatalk/chatdb/model
mkdir -p server/src/main/java/com/datatalk/chatdb/service
mkdir -p server/src/main/java/com/datatalk/chatdb/controller
mkdir -p server/src/main/resources
mkdir -p server/src/test/java/com/datatalk/chatdb/controller
mkdir -p server/gradle/wrapper
```

- [ ] **Step 2: 创建 `server/settings.gradle`**

```gradle
rootProject.name = 'chat-database-tool-server'
```

- [ ] **Step 3: 创建 `server/build.gradle`**

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.datatalk'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.xerial:sqlite-jdbc:3.49.1.0'
    runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: 创建 `server/gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: 创建 `server/gradlew`**

由于 Gradle wrapper JAR 较大（~45KB），使用已安装的 Gradle 生成：

```bash
cd server
gradle wrapper
cd ..
```

> 注意：如果 Step 1 安装了 Gradle，这里直接运行 `gradle wrapper` 即可生成 `gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar`。

- [ ] **Step 6: 创建 `server/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: chat-database-tool-server

  datasource:
    url: jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console

  sql:
    init:
      mode: always
      schema-locations: classpath:schema-demo.sql
      data-locations: classpath:data-demo.sql

  sqlite-datasource:
    url: jdbc:sqlite:./data/metadata.db
    driver-class-name: org.sqlite.JDBC

  jdbc:
    template:
      query-timeout: 30

server:
  port: 8080

logging:
  level:
    com.datatalk.chatdb: DEBUG
```

- [ ] **Step 7: 创建 `server/src/main/resources/schema-demo.sql`**

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 8: 创建 `server/src/main/resources/data-demo.sql`**

```sql
INSERT INTO users (id, name, email, created_at) VALUES
    (1, '张三', 'zhangsan@example.com', '2026-04-10 10:00:00'),
    (2, '李四', 'lisi@example.com', '2026-04-11 14:30:00'),
    (3, '王五', 'wangwu@example.com', '2026-04-12 09:15:00'),
    (4, '赵六', 'zhaoliu@example.com', '2026-04-13 16:45:00'),
    (5, '陈七', 'chenqi@example.com', '2026-04-14 11:20:00');
```

- [ ] **Step 9: 创建 `server/src/main/resources/schema-sqlite.sql`**

```sql
CREATE TABLE IF NOT EXISTS db_connections (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    db_type TEXT NOT NULL,
    host TEXT,
    port INTEGER,
    database_name TEXT,
    username TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    title TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now'))
);
```

- [ ] **Step 10: 创建 `server/src/main/java/com/datatalk/chatdb/ChatDbApplication.java`**

```java
package com.datatalk.chatdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatDbApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatDbApplication.class, args);
    }
}
```

- [ ] **Step 11: 创建 `server/src/main/java/com/datatalk/chatdb/config/DataSourcesConfig.java`**

```java
package com.datatalk.chatdb.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourcesConfig {

    /**
     * H2 演示数据库数据源（主数据源，用于 Demo 查询）
     */
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties h2DataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource h2DataSource(
            @Qualifier("h2DataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("h2DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * SQLite 元数据库数据源（用于存储连接配置、会话等）
     */
    @Bean
    @ConfigurationProperties("spring.sqlite-datasource")
    public DataSourceProperties sqliteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource sqliteDataSource(
            @Qualifier("sqliteDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public JdbcTemplate sqliteJdbcTemplate(
            @Qualifier("sqliteDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

- [ ] **Step 13: 创建 `server/src/main/java/com/datatalk/chatdb/config/CorsConfig.java`**

```java
package com.datatalk.chatdb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // Tauri dev server runs on localhost:1420
        config.setAllowedOrigins(List.of("http://localhost:1420", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **Step 14: 创建 data 目录**

```bash
mkdir -p server/data
```

- [ ] **Step 15: 验证后端能编译启动**

```bash
cd server
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

```bash
./gradlew bootRun &
sleep 10
curl -s http://localhost:8080/actuator/health || curl -s http://localhost:8080/h2-console
```

Expected: 后端启动成功，无报错。

```bash
# 停掉后端
pkill -f "bootRun" || true
```

- [ ] **Step 16: Commit**

```bash
git add server/
git commit -m "feat: scaffold Spring Boot backend with H2 demo database and SQLite metadata store"
```

---

### Task 3: 实现查询 API — QueryRequest/QueryResponse/QueryService/QueryController

**Files:**
- Create: `server/src/main/java/com/datatalk/chatdb/model/QueryRequest.java`
- Create: `server/src/main/java/com/datatalk/chatdb/model/QueryResponse.java`
- Create: `server/src/main/java/com/datatalk/chatdb/service/QueryService.java`
- Create: `server/src/main/java/com/datatalk/chatdb/controller/QueryController.java`
- Create: `server/src/test/java/com/datatalk/chatdb/controller/QueryControllerTest.java`

- [ ] **Step 1: 创建 `server/src/main/java/com/datatalk/chatdb/model/QueryRequest.java`**

```java
package com.datatalk.chatdb.model;

import jakarta.validation.constraints.NotBlank;

public class QueryRequest {

    @NotBlank(message = "connectionId is required")
    private String connectionId;

    private String sql;

    public QueryRequest() {}

    public QueryRequest(String connectionId, String sql) {
        this.connectionId = connectionId;
        this.sql = sql;
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
}
```

- [ ] **Step 2: 创建 `server/src/main/java/com/datatalk/chatdb/model/QueryResponse.java`**

```java
package com.datatalk.chatdb.model;

import java.util.List;
import java.util.Map;

public class QueryResponse {

    private List<String> columns;
    private List<Map<String, Object>> rows;
    private long durationMs;
    private int rowCount;

    public QueryResponse() {}

    public QueryResponse(List<String> columns, List<Map<String, Object>> rows, long durationMs) {
        this.columns = columns;
        this.rows = rows;
        this.durationMs = durationMs;
        this.rowCount = rows.size();
    }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
}
```

- [ ] **Step 3: 创建 `server/src/main/java/com/datatalk/chatdb/service/QueryService.java`**

```java
package com.datatalk.chatdb.service;

import com.datatalk.chatdb.model.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final JdbcTemplate jdbcTemplate;

    public QueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行查询。MVP 阶段固定查 H2 演示库的 users 表。
     * connectionId 固定为 "demo"，sql 参数被忽略，使用硬编码 SQL。
     */
    public QueryResponse executeQuery(String connectionId, String sql) {
        long start = System.currentTimeMillis();

        if (!"demo".equals(connectionId)) {
            throw new IllegalArgumentException(
                "MVP only supports 'demo' connection, got: " + connectionId);
        }

        String demoSql = "SELECT id, name, email, created_at FROM users ORDER BY id";
        log.debug("Executing demo SQL: {}", demoSql);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(demoSql);
        long duration = System.currentTimeMillis() - start;

        List<String> columns = rows.isEmpty()
                ? List.of("id", "name", "email", "created_at")
                : new ArrayList<>(rows.get(0).keySet());

        return new QueryResponse(columns, rows, duration);
    }
}
```

- [ ] **Step 4: 创建 `server/src/main/java/com/datatalk/chatdb/controller/QueryController.java`**

```java
package com.datatalk.chatdb.controller;

import com.datatalk.chatdb.model.QueryRequest;
import com.datatalk.chatdb.model.QueryResponse;
import com.datatalk.chatdb.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 查询接口。MVP 阶段执行硬编码 SQL 查询 H2 演示库。
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@Valid @RequestBody QueryRequest request) {
        try {
            QueryResponse response = queryService.executeQuery(
                    request.getConnectionId(), request.getSql());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "INVALID_CONNECTION"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "code", "QUERY_FAILED"));
        }
    }
}
```

- [ ] **Step 5: 创建 `server/src/test/java/com/datatalk/chatdb/controller/QueryControllerTest.java`**

```java
package com.datatalk.chatdb.controller;

import com.datatalk.chatdb.model.QueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthCheckReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void queryDemoConnectionReturnsUsers() throws Exception {
        QueryRequest request = new QueryRequest("demo", "SELECT * FROM users");

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(5))
                .andExpect(jsonPath("$.rows", hasSize(5)))
                .andExpect(jsonPath("$.rows[0].name").value("张三"))
                .andExpect(jsonPath("$.durationMs").isNumber());
    }

    @Test
    void queryNonDemoConnectionReturnsBadRequest() throws Exception {
        QueryRequest request = new QueryRequest("production", "SELECT 1");

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("demo")));
    }

    @Test
    void queryMissingConnectionIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 6: 运行测试**

```bash
cd server
./gradlew test
```

Expected: 4 tests passed, BUILD SUCCESSFUL

- [ ] **Step 7: 手动验证 API**

```bash
cd server
./gradlew bootRun &
sleep 10

# 健康检查
curl -s http://localhost:8080/api/health | python3 -m json.tool
# Expected: {"status": "ok"}

# 查询接口
curl -s -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"connectionId":"demo","sql":"SELECT * FROM users"}' | python3 -m json.tool
# Expected: {"columns":["id","name","email","created_at"],"rows":[...5 rows...],"durationMs":X,"rowCount":5}

# 停掉后端
pkill -f "bootRun" || true
```

- [ ] **Step 8: Commit**

```bash
git add server/
git commit -m "feat: implement query API with QueryService, QueryController, and tests"
```

---

### Task 4: 搭建 Tauri + React + TypeScript 前端脚手架

**Files:**
- Create: `client/package.json`
- Create: `client/vite.config.ts`
- Create: `client/tsconfig.json`
- Create: `client/tsconfig.node.json`
- Create: `client/tailwind.config.ts`
- Create: `client/postcss.config.js`
- Create: `client/index.html`
- Create: `client/src/main.tsx`
- Create: `client/src/App.tsx`
- Create: `client/src/index.css`
- Create: `client/src/vite-env.d.ts`
- Create: `client/src-tauri/Cargo.toml`
- Create: `client/src-tauri/tauri.conf.json`
- Create: `client/src-tauri/build.rs`
- Create: `client/src-tauri/capabilities/default.json`
- Create: `client/src-tauri/src/lib.rs`
- Create: `client/src-tauri/src/main.rs`
- Create: `client/src-tauri/.gitignore`

- [ ] **Step 1: 创建 client 目录**

```bash
mkdir -p client
```

- [ ] **Step 2: 使用 Tauri CLI 创建前端项目**

```bash
cd client
cargo tauri init .
```

> 按提示选择：
> - Project name: `chat-database-tool-client`
> - Window title: `Chat Database Tool`
> - Web asset dir: `../dist` (relative)
> - Dev url: `http://localhost:1420`
> - Default template: `vanilla`（我们后续手动替换为 React）

> 如果交互式提示有问题，直接创建下面的文件。

- [ ] **Step 3: 创建 `client/package.json`**

```json
{
  "name": "chat-database-tool-client",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "tauri": "tauri"
  },
  "dependencies": {
    "@tauri-apps/api": "^2",
    "@tauri-apps/plugin-shell": "^2",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "class-variance-authority": "^0.7.1",
    "clsx": "^2.1.1",
    "lucide-react": "^0.475.0",
    "tailwind-merge": "^3.0.0",
    "tailwindcss-animate": "^1.0.7"
  },
  "devDependencies": {
    "@tauri-apps/cli": "^2",
    "@types/node": "^22.0.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.3.0",
    "autoprefixer": "^10.4.20",
    "postcss": "^8.5.0",
    "tailwindcss": "^3.4.17",
    "typescript": "~5.7.0",
    "vite": "^6.0.0"
  }
}
```

- [ ] **Step 4: 创建 `client/vite.config.ts`**

```typescript
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;

export default defineConfig(async () => ({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host
      ? { protocol: "ws", host, port: 1421 }
      : undefined,
    watch: { ignored: ["**/client/src-tauri/**"] },
  },
}));
```

- [ ] **Step 5: 创建 `client/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"]
}
```

- [ ] **Step 6: 创建 `client/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 7: 创建 `client/tailwind.config.ts`**

```typescript
import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

export default {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{ts,tsx,js,jsx}",
  ],
  theme: {
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
    },
  },
  plugins: [tailwindcssAnimate],
} satisfies Config;
```

- [ ] **Step 8: 创建 `client/postcss.config.js`**

```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 9: 创建 `client/index.html`**

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Chat Database Tool</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 10: 创建 `client/src/main.tsx`**

```typescript
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

- [ ] **Step 11: 创建 `client/src/vite-env.d.ts`**

```typescript
/// <reference types="vite/client" />
```

- [ ] **Step 12: 创建 `client/src/index.css`**

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 222.2 84% 4.9%;
    --primary: 222.2 47.4% 11.2%;
    --primary-foreground: 210 40% 98%;
    --secondary: 210 40% 96.1%;
    --secondary-foreground: 222.2 47.4% 11.2%;
    --muted: 210 40% 96.1%;
    --muted-foreground: 215.4 16.3% 46.9%;
    --accent: 210 40% 96.1%;
    --accent-foreground: 222.2 47.4% 11.2%;
    --border: 214.3 31.8% 91.4%;
    --input: 214.3 31.8% 91.4%;
    --ring: 222.2 84% 4.9%;
    --radius: 0.5rem;
  }
}

@layer base {
  * {
    @apply border-border;
  }
  body {
    @apply bg-background text-foreground;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
}
```

- [ ] **Step 13: 创建 `client/src/App.tsx`**（临时占位，后续会被替换）

```typescript
function App() {
  return (
    <div className="flex h-screen w-screen items-center justify-center">
      <h1 className="text-2xl font-bold">Chat Database Tool</h1>
    </div>
  );
}

export default App;
```

- [ ] **Step 14: 创建 `client/src-tauri/Cargo.toml`**

```toml
[package]
name = "chat-database-tool-client"
version = "0.0.1"
description = "智能数据库协作平台"
authors = ["you"]
edition = "2021"

[lib]
name = "chat_database_tool_client_lib"
crate-type = ["staticlib", "cdylib", "rlib"]

[build-dependencies]
tauri-build = { version = "2", features = [] }

[dependencies]
tauri = { version = "2", features = [] }
tauri-plugin-shell = "2"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
```

- [ ] **Step 15: 创建 `client/src-tauri/tauri.conf.json`**

```json
{
  "$schema": "https://raw.githubusercontent.com/tauri-apps/tauri/main/crates/tauri-config-schema/schema.json",
  "productName": "chat-database-tool-client",
  "version": "0.0.1",
  "identifier": "com.datatalk.chatdb",
  "build": {
    "beforeDevCommand": "npm run build",
    "devUrl": "http://localhost:1420",
    "beforeBuildCommand": "npm run build",
    "frontendDist": "../dist"
  },
  "app": {
    "windows": [
      {
        "title": "Chat Database Tool",
        "width": 1280,
        "height": 800,
        "resizable": true
      }
    ],
    "security": {
      "csp": "default-src 'self' ipc: http://ipc.localhost; connect-src 'self' http://localhost:*"
    }
  },
  "bundle": {
    "active": true,
    "targets": "all",
    "icon": []
  }
}
```

- [ ] **Step 16: 创建 `client/src-tauri/build.rs`**

```rust
fn main() {
    tauri_build::build()
}
```

- [ ] **Step 17: 创建 `client/src-tauri/src/main.rs`**

```rust
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    chat_database_tool_client_lib::run()
}
```

- [ ] **Step 18: 创建 `client/src-tauri/src/lib.rs`**

```rust
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

- [ ] **Step 19: 创建 `client/src-tauri/capabilities/default.json`**

```json
{
  "$schema": "../gen/schemas/desktop-schema.json",
  "identifier": "default",
  "description": "Capability for the main window",
  "windows": ["main"],
  "permissions": [
    "core:default",
    "shell:allow-open"
  ]
}
```

- [ ] **Step 20: 创建 `client/src-tauri/.gitignore`**

```gitignore
/target
/gen
```

- [ ] **Step 21: 安装前端依赖**

```bash
cd client
npm install
```

- [ ] **Step 22: 验证 Vite 开发服务器**

```bash
cd client
npm run dev &
sleep 5
curl -s http://localhost:1420 | head -5
# Expected: HTML with <div id="root">
pkill -f "vite" || true
```

- [ ] **Step 23: Commit**

```bash
git add client/
git commit -m "feat: scaffold Tauri v2 + React + TypeScript frontend with Tailwind CSS"
```

---

### Task 5: 安装 shadcn/ui 组件 + 实现 Sidebar 布局

**Files:**
- Create: `client/src/lib/utils.ts`
- Create: `client/src/components/ui/sidebar.tsx`
- Modify: `client/src/App.tsx` (完全重写)
- Modify: `client/src/index.css` (追加 shadcn sidebar CSS 变量)
- Create: `client/src/components/ChatArea.tsx`
- Create: `client/src/components/MessageBubble.tsx`
- Create: `client/src/components/QueryResult.tsx`

- [ ] **Step 1: 创建 shadcn 工具函数 `client/src/lib/utils.ts`**

```typescript
import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

- [ ] **Step 2: 安装 shadcn/ui 组件**

运行 shadcn init 初始化配置：

```bash
cd client
npx shadcn@latest init -d
```

> `-d` 使用默认配置。这将创建 `client/components.json` 配置文件。

- [ ] **Step 3: 安装 sidebar 组件**

```bash
cd client
npx shadcn@latest add sidebar-07
```

> 注意：如果 `sidebar-07` 不在注册表中，使用 `npx shadcn@latest add sidebar` 安装基础 sidebar，然后手动调整为类似 sidebar-07 的样式。安装后文件会输出到 `client/src/components/ui/sidebar.tsx`。

- [ ] **Step 4: 安装其他必需的 shadcn 组件**

```bash
cd client
npx shadcn@latest add button input textarea tabs table
```

- [ ] **Step 5: 更新 `client/src/index.css`**，追加 sidebar 必需的 CSS 变量

在现有内容末尾追加：

```css
@layer base {
  :root {
    --sidebar-background: 0 0% 98%;
    --sidebar-foreground: 240 5.3% 26.1%;
    --sidebar-primary: 240 5.9% 10%;
    --sidebar-primary-foreground: 0 0% 98%;
    --sidebar-accent: 240 4.8% 95.9%;
    --sidebar-accent-foreground: 240 5.9% 10%;
    --sidebar-border: 220 13% 91%;
    --sidebar-ring: 217.2 91.2% 59.8%;
  }

  .dark {
    --sidebar-background: 240 5.9% 10%;
    --sidebar-foreground: 240 4.8% 95.9%;
    --sidebar-primary: 224.3 76.3% 48%;
    --sidebar-primary-foreground: 0 0% 100%;
    --sidebar-accent: 240 3.7% 15.9%;
    --sidebar-accent-foreground: 240 4.8% 95.9%;
    --sidebar-border: 240 3.7% 15.9%;
    --sidebar-ring: 217.2 91.2% 59.8%;
  }
}
```

- [ ] **Step 6: 创建 `client/src/components/MessageBubble.tsx`**

```typescript
import { cn } from "@/lib/utils";

interface MessageBubbleProps {
  role: "user" | "ai";
  content: string;
  className?: string;
}

export function MessageBubble({ role, content, className }: MessageBubbleProps) {
  return (
    <div
      className={cn(
        "flex w-full",
        role === "user" ? "justify-end" : "justify-start",
        className,
      )}
    >
      <div
        className={cn(
          "max-w-[80%] rounded-lg px-4 py-2 text-sm",
          role === "user"
            ? "bg-primary text-primary-foreground"
            : "bg-muted text-muted-foreground",
        )}
      >
        {content}
      </div>
    </div>
  );
}
```

- [ ] **Step 7: 创建 `client/src/components/QueryResult.tsx`**

```typescript
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface QueryResultProps {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  durationMs: number;
}

export function QueryResult({ columns, rows, rowCount, durationMs }: QueryResultProps) {
  if (rows.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        No results
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col p-4">
      <div className="mb-2 flex items-center gap-2 text-xs text-muted-foreground">
        <span>{rowCount} rows</span>
        <span>•</span>
        <span>{durationMs}ms</span>
      </div>
      <div className="flex-1 overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              {columns.map((col) => (
                <TableHead key={col}>{col}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, i) => (
              <TableRow key={i}>
                {columns.map((col) => (
                  <TableCell key={col}>
                    {row[col] !== null && row[col] !== undefined
                      ? String(row[col])
                      : "null"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
```

- [ ] **Step 8: 创建 `client/src/components/ChatArea.tsx`**

```typescript
import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { MessageBubble } from "./MessageBubble";

interface Message {
  id: number;
  role: "user" | "ai";
  content: string;
}

export function ChatArea() {
  const [messages, setMessages] = useState<Message[]>([
    { id: 0, role: "ai", content: "你好！我是数据库助手，请输入你的查询。" },
  ]);
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (!input.trim()) return;

    const userMsg: Message = {
      id: Date.now(),
      role: "user",
      content: input,
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput("");

    // MVP: 简单模拟 AI 回复，实际调用由父组件处理
    setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: "ai",
          content: "已收到查询，正在处理...",
        },
      ]);
    }, 500);
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg) => (
          <MessageBubble
            key={msg.id}
            role={msg.role}
            content={msg.content}
          />
        ))}
      </div>
      <div className="border-t p-4">
        <div className="flex gap-2">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="输入查询，例如：查询 users 表的所有数据"
            className="min-h-[44px] resize-none"
          />
          <Button onClick={handleSend}>发送</Button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 9: 重写 `client/src/App.tsx`**，使用 Sidebar 布局

```typescript
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarInset,
} from "@/components/ui/sidebar";
import { ChatArea } from "@/components/ChatArea";
import { Database, MessageSquare, Plus } from "lucide-react";

function App() {
  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Database className="size-4" />
                <span>Chat Database Tool</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>数据库连接</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <Database className="size-4" />
                    <span>Demo (H2)</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>

          <SidebarGroup>
            <SidebarGroupLabel>会话历史</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <MessageSquare className="size-4" />
                    <span>今日会话</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Plus className="size-4" />
                <span>新建连接</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>

      <SidebarInset>
        <header className="flex h-12 items-center gap-2 border-b px-4">
          <SidebarTrigger />
          <h1 className="text-lg font-semibold">查询助手</h1>
        </header>
        <main className="flex h-[calc(100vh-3rem)]">
          <div className="w-1/2 border-r">
            <ChatArea />
          </div>
          <div className="w-1/2 flex items-center justify-center text-muted-foreground">
            Workspace（查询结果将在这里展示）
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}

export default App;
```

- [ ] **Step 10: 验证前端编译**

```bash
cd client
npm run build
```

Expected: Build succeeds, no TypeScript errors.

- [ ] **Step 11: Commit**

```bash
git add client/
git commit -m "feat: add shadcn/ui components, sidebar layout, chat area, and query result"
```

---

### Task 6: 前端连接后端 API

**Files:**
- Create: `client/src/services/api.ts`
- Modify: `client/src/components/ChatArea.tsx` (接入 API 调用)

- [ ] **Step 1: 创建 `client/src/services/api.ts`**

```typescript
const API_BASE = "http://localhost:8080/api";

export interface QueryRequest {
  connectionId: string;
  sql: string;
}

export interface QueryResponse {
  columns: string[];
  rows: Record<string, unknown>[];
  durationMs: number;
  rowCount: number;
}

export interface ApiError {
  error: string;
  code: string;
}

export async function executeQuery(
  request: QueryRequest,
): Promise<QueryResponse> {
  const response = await fetch(`${API_BASE}/query`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.error || "Query failed");
  }

  return response.json();
}

export async function healthCheck(): Promise<{ status: string }> {
  const response = await fetch(`${API_BASE}/health`);
  return response.json();
}
```

- [ ] **Step 2: 更新 `client/src/components/ChatArea.tsx`**，接入后端 API 调用

替换整个文件内容：

```typescript
import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { MessageBubble } from "./MessageBubble";
import { executeQuery } from "@/services/api";
import type { QueryResponse } from "@/services/api";

interface Message {
  id: number;
  role: "user" | "ai";
  content: string;
}

interface ChatAreaProps {
  onQueryResult?: (result: QueryResponse) => void;
}

export function ChatArea({ onQueryResult }: ChatAreaProps) {
  const [messages, setMessages] = useState<Message[]>([
    { id: 0, role: "ai", content: "你好！我是数据库助手，请输入你的查询。" },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMsg: Message = {
      id: Date.now(),
      role: "user",
      content: input,
    };
    setMessages((prev) => [...prev, userMsg]);
    const userInput = input;
    setInput("");
    setLoading(true);

    try {
      const result = await executeQuery({
        connectionId: "demo",
        sql: userInput,
      });

      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: "ai",
          content: `查询完成，返回 ${result.rowCount} 行数据，耗时 ${result.durationMs}ms。`,
        },
      ]);

      onQueryResult?.(result);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: "ai",
          content: `查询失败: ${err instanceof Error ? err.message : "未知错误"}`,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg) => (
          <MessageBubble
            key={msg.id}
            role={msg.role}
            content={msg.content}
          />
        ))}
      </div>
      <div className="border-t p-4">
        <div className="flex gap-2">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="输入查询，例如：查询 users 表的所有数据"
            className="min-h-[44px] resize-none"
            disabled={loading}
          />
          <Button onClick={handleSend} disabled={loading}>
            {loading ? "查询中..." : "发送"}
          </Button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 更新 `client/src/App.tsx`**，集成 QueryResult 组件

修改 App 组件，将查询结果传递到右侧 Workspace：

```typescript
import { useState } from "react";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarInset,
} from "@/components/ui/sidebar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChatArea } from "@/components/ChatArea";
import { QueryResult } from "@/components/QueryResult";
import type { QueryResponse } from "@/services/api";
import { Database, MessageSquare, Plus, Table } from "lucide-react";

function App() {
  const [queryResult, setQueryResult] = useState<QueryResponse | null>(null);

  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Database className="size-4" />
                <span>Chat Database Tool</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>数据库连接</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <Database className="size-4" />
                    <span>Demo (H2)</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>

          <SidebarGroup>
            <SidebarGroupLabel>会话历史</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                <SidebarMenuItem>
                  <SidebarMenuButton>
                    <MessageSquare className="size-4" />
                    <span>今日会话</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton>
                <Plus className="size-4" />
                <span>新建连接</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>

      <SidebarInset>
        <header className="flex h-12 items-center gap-2 border-b px-4">
          <SidebarTrigger />
          <h1 className="text-lg font-semibold">查询助手</h1>
        </header>
        <main className="flex h-[calc(100vh-3rem)]">
          <div className="w-1/2 border-r">
            <ChatArea onQueryResult={setQueryResult} />
          </div>
          <div className="w-1/2">
            <Tabs defaultValue="result" className="h-full">
              <div className="border-b px-4">
                <TabsList>
                  <TabsTrigger value="result">
                    <Table className="mr-1 size-3" />
                    查询结果
                  </TabsTrigger>
                </TabsList>
              </div>
              <TabsContent value="result" className="h-[calc(100%-3rem)] m-0">
                {queryResult ? (
                  <QueryResult
                    columns={queryResult.columns}
                    rows={queryResult.rows}
                    rowCount={queryResult.rowCount}
                    durationMs={queryResult.durationMs}
                  />
                ) : (
                  <div className="flex h-full items-center justify-center text-muted-foreground">
                    执行查询后结果将显示在这里
                  </div>
                )}
              </TabsContent>
            </Tabs>
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}

export default App;
```

- [ ] **Step 4: 验证前端编译**

```bash
cd client
npm run build
```

Expected: BUILD SUCCESS, no TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat: connect frontend to backend API, integrate query result display"
```

---

### Task 7: 集成测试 — 跑通完整链路

**Files:** 无（集成测试）

- [ ] **Step 1: 启动后端**

```bash
cd server
./gradlew bootRun &
BACKEND_PID=$!
sleep 15

# 验证后端就绪
curl -s http://localhost:8080/api/health
# Expected: {"status":"ok"}
```

- [ ] **Step 2: 启动前端 Tauri 开发模式**

```bash
cd client
cargo tauri dev &
sleep 30
```

> Tauri 首次编译 Rust 依赖需要较长时间（5-10 分钟）。等待桌面应用窗口打开。

- [ ] **Step 3: 验证完整链路**

在 Tauri 应用窗口中：
1. 确认左侧 Sidebar 显示连接和会话列表
2. 确认中间 Chat 区域显示欢迎消息
3. 在输入框输入任意内容，点击「发送」
4. 确认收到后端返回的查询结果（5 行用户数据）
5. 确认右侧 Workspace 显示查询结果表格

同时通过终端验证：

```bash
# 验证后端收到查询
curl -s -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"connectionId":"demo","sql":"test"}' | python3 -m json.tool
# Expected: 5 rows of user data
```

- [ ] **Step 4: 停掉服务**

```bash
pkill -f "bootRun" || true
pkill -f "tauri" || true
```

- [ ] **Step 5: Commit（如果有额外变更）**

```bash
git status
# 如果有未提交的变更
git add -A
git commit -m "fix: integration test cleanup"
```

---

## 成功标准检查清单

- [ ] `cd server && ./gradlew build` 编译成功
- [ ] `cd server && ./gradlew test` 全部测试通过
- [ ] `cd client && npm run build` 编译成功
- [ ] 后端 `./gradlew bootRun` 启动成功，`/api/health` 返回 ok
- [ ] 后端 `/api/query` 接口返回 5 行演示数据
- [ ] 前端 Tauri 应用启动，显示三栏布局（Sidebar + Chat + Workspace）
- [ ] 前端发送查询请求，后端返回结果，前端表格展示数据
