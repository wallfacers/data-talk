# AI 设置中心 · Part 1 · 后端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 AI 设置中心的后端：SQLite 偏好表、OpenCodeHttpClient 扩展、AiSettingsController 代理层、ConnectionController F1 补齐、ChannelService 消息携带模型。

**Architecture:** 三层职责分工不变——domain 不动；application 新增 `ai/` 包（仓储接口 + `AiSettingsService`）和 `connection/` 服务扩展；infrastructure 实现 JDBC 仓储 + 扩展 `OpenCodeHttpClient`；adapter 新增 `AiSettingsController` 和扩展 `ConnectionController`。凭证只透传不落盘；偏好落 SQLite。

**Tech Stack:** Spring Boot 3.5 / Java 21 / JdbcTemplate / Flyway / Jackson / WebClient / JUnit 5 / AssertJ / WireMock 3.x（FakeOpenCodeServer 模式）

**Spec 映射：** 实现 [2026-04-17-ai-settings-opencode-port.md](../../product-specs/2026-04-17-ai-settings-opencode-port.md) 的 §5（数据模型）、§6（后端接口）、§8（错误与降级）、§9.1（后端测试）。

---

## 文件结构地图

**新增**
- `server/data-talk-infrastructure/src/main/resources/db/migration/V2__ai_prefs.sql` — Flyway 迁移
- `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiUserPrefsRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiModelPrefsRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiSettingsService.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiUserPrefsRepositoryJdbc.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiModelPrefsRepositoryJdbc.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeExceptions.java` — `OpenCodeUnavailableException`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsController.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsExceptionHandler.java` — 503 归一化
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsMigrationIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsRepositoryIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/AiSettingsControllerIT.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`

**修改**
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java` — 新增 `listProviders / getProviderAuth / putAuth`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java` — 新增 PUT / DELETE / test
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java` — 新增 `update / deleteById / testConnection`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java` — 新增 `update / deleteById`
- `server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java` — 在 `sendMessage` 透传 `model` 字段

---

## Task 1：SQLite V2 迁移建表

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V2__ai_prefs.sql`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsMigrationIT.java`

- [ ] **Step 1：写失败的迁移集成测试**

```java
// AiPrefsMigrationIT.java
package com.datatalk.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiPrefsMigrationIT {

    @Autowired @Qualifier("datatalkJdbc")
    JdbcTemplate jdbc;

    @Test
    void ai_user_prefs_table_exists_with_default_row() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_user_prefs WHERE id = 'default'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ai_model_prefs_table_exists_empty() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_prefs", Integer.class);
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiPrefsMigrationIT
```
期望：`no such table: ai_user_prefs`

- [ ] **Step 3：写 V2 迁移**

```sql
-- V2__ai_prefs.sql
CREATE TABLE ai_user_prefs (
  id            TEXT PRIMARY KEY,
  current_model TEXT,
  updated_at    INTEGER NOT NULL
);

INSERT INTO ai_user_prefs(id, current_model, updated_at)
VALUES ('default', NULL, strftime('%s','now')*1000);

CREATE TABLE ai_model_prefs (
  provider_id TEXT    NOT NULL,
  model_id    TEXT    NOT NULL,
  enabled     INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (provider_id, model_id)
);
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiPrefsMigrationIT
```
期望：两个测试 PASS。

- [ ] **Step 5：提交**

```
cd server && mvn compile -q
git add server/data-talk-infrastructure/src/main/resources/db/migration/V2__ai_prefs.sql \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsMigrationIT.java
git commit -m "feat(db): add V2 migration for ai_user_prefs and ai_model_prefs"
```

---

## Task 2：AiUserPrefsRepository 接口 + JDBC 实现

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiUserPrefsRepository.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiUserPrefsRepositoryJdbc.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsRepositoryIT.java`

- [ ] **Step 1：写失败的仓储测试（先只覆盖 user prefs 部分）**

```java
// AiPrefsRepositoryIT.java
package com.datatalk.adapter.persistence;

import com.datatalk.application.ai.AiUserPrefsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiPrefsRepositoryIT {

    @Autowired AiUserPrefsRepository userPrefs;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE ai_user_prefs SET current_model = NULL WHERE id = 'default'");
    }

    @Test
    void get_returns_null_initially() {
        assertThat(userPrefs.getCurrentModel()).isNull();
    }

    @Test
    void set_then_get_round_trips() {
        userPrefs.setCurrentModel("anthropic/claude-3-5-sonnet");
        assertThat(userPrefs.getCurrentModel()).isEqualTo("anthropic/claude-3-5-sonnet");
    }

    @Test
    void set_null_clears_value() {
        userPrefs.setCurrentModel("openai/gpt-5");
        userPrefs.setCurrentModel(null);
        assertThat(userPrefs.getCurrentModel()).isNull();
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiPrefsRepositoryIT
```
期望：编译错误（类不存在）。

- [ ] **Step 3：写接口**

```java
// AiUserPrefsRepository.java
package com.datatalk.application.ai;

public interface AiUserPrefsRepository {
    String getCurrentModel();
    void setCurrentModel(String modelId);
}
```

- [ ] **Step 4：写 JDBC 实现**

```java
// AiUserPrefsRepositoryJdbc.java
package com.datatalk.infra.ai;

import com.datatalk.application.ai.AiUserPrefsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;

@Repository
public class AiUserPrefsRepositoryJdbc implements AiUserPrefsRepository {

    private static final String ID = "default";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiUserPrefsRepositoryJdbc(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String getCurrentModel() {
        var list = jdbc.query(
            "SELECT current_model FROM ai_user_prefs WHERE id = ?",
            (rs, i) -> rs.getString("current_model"), ID);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void setCurrentModel(String modelId) {
        jdbc.update("UPDATE ai_user_prefs SET current_model = ?, updated_at = ? WHERE id = ?",
            modelId, clock.millis(), ID);
    }
}
```

- [ ] **Step 5：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=AiPrefsRepositoryIT
```
期望：3 个测试 PASS。

- [ ] **Step 6：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/ai/AiUserPrefsRepository.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiUserPrefsRepositoryJdbc.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsRepositoryIT.java
git commit -m "feat(ai): add AiUserPrefsRepository for current-model persistence"
```

---

## Task 3：AiModelPrefsRepository 接口 + JDBC 实现

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiModelPrefsRepository.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiModelPrefsRepositoryJdbc.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsRepositoryIT.java`

- [ ] **Step 1：追加 model prefs 测试到 AiPrefsRepositoryIT**

在 `AiPrefsRepositoryIT` 内追加：

```java
    @Autowired com.datatalk.application.ai.AiModelPrefsRepository modelPrefs;

    @BeforeEach
    void resetModelPrefs() {
        jdbc.update("DELETE FROM ai_model_prefs");
    }

    @Test
    void disabled_prefs_retrieved_as_map() {
        modelPrefs.setEnabled("openai", "gpt-5", false);
        modelPrefs.setEnabled("openai", "gpt-5-nano", false);
        var disabled = modelPrefs.disabledSet();
        assertThat(disabled).containsExactlyInAnyOrder(
            "openai/gpt-5", "openai/gpt-5-nano");
    }

    @Test
    void enabling_removes_row_from_disabled_set() {
        modelPrefs.setEnabled("openai", "gpt-5", false);
        modelPrefs.setEnabled("openai", "gpt-5", true);
        assertThat(modelPrefs.disabledSet()).isEmpty();
    }

    @Test
    void enabling_already_enabled_is_idempotent() {
        modelPrefs.setEnabled("openai", "gpt-5", true);
        assertThat(modelPrefs.disabledSet()).isEmpty();
    }
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiPrefsRepositoryIT
```
期望：编译失败（`AiModelPrefsRepository` 不存在）。

- [ ] **Step 3：写接口**

```java
// AiModelPrefsRepository.java
package com.datatalk.application.ai;

import java.util.Set;

public interface AiModelPrefsRepository {
    /** 返回 "providerId/modelId" 格式的禁用集合。不在集合内即默认启用。 */
    Set<String> disabledSet();

    void setEnabled(String providerId, String modelId, boolean enabled);
}
```

- [ ] **Step 4：写 JDBC 实现**

```java
// AiModelPrefsRepositoryJdbc.java
package com.datatalk.infra.ai;

import com.datatalk.application.ai.AiModelPrefsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

@Repository
public class AiModelPrefsRepositoryJdbc implements AiModelPrefsRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiModelPrefsRepositoryJdbc(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Set<String> disabledSet() {
        Set<String> out = new HashSet<>();
        jdbc.query("SELECT provider_id, model_id FROM ai_model_prefs WHERE enabled = 0",
            rs -> {
                out.add(rs.getString("provider_id") + "/" + rs.getString("model_id"));
            });
        return out;
    }

    @Override
    public void setEnabled(String providerId, String modelId, boolean enabled) {
        if (enabled) {
            jdbc.update("DELETE FROM ai_model_prefs WHERE provider_id = ? AND model_id = ?",
                providerId, modelId);
        } else {
            long now = clock.millis();
            // SQLite upsert
            jdbc.update("""
                INSERT INTO ai_model_prefs(provider_id, model_id, enabled, updated_at)
                VALUES(?, ?, 0, ?)
                ON CONFLICT(provider_id, model_id) DO UPDATE SET enabled = 0, updated_at = ?
                """, providerId, modelId, now, now);
        }
    }
}
```

- [ ] **Step 5：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=AiPrefsRepositoryIT
```
期望：6 个测试全 PASS。

- [ ] **Step 6：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/ai/AiModelPrefsRepository.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ai/AiModelPrefsRepositoryJdbc.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/AiPrefsRepositoryIT.java
git commit -m "feat(ai): add AiModelPrefsRepository with disable-only persistence"
```

---

## Task 4：OpenCodeHttpClient 新增 listProviders()

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeExceptions.java`

查阅现有 `FakeOpenCodeServer` 模式（位于 `data-talk-adapter` 测试 resources），新增方法直接返 `JsonNode` 而非强类型 DTO——OpenCode 响应字段多、变动可能，前端也期望透传。

- [ ] **Step 1：写失败的集成测试（沿用 FakeOpenCodeServer 模式）**

```java
// OpenCodeHttpClientIT.java（若已有则追加 case；否则创建）
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java
package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

class OpenCodeHttpClientIT {

    static WireMockServer server;
    static OpenCodeHttpClient client;

    @BeforeAll
    static void up() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        client = new OpenCodeHttpClient("http://localhost:" + server.port(), new ObjectMapper());
    }

    @AfterAll
    static void down() { server.stop(); }

    @AfterEach
    void reset() { server.resetAll(); }

    @Test
    void listProviders_returns_connected_and_all() {
        server.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "default":{"openai":"gpt-5"},
             "connected":["openai"]}
            """)));
        var node = client.listProviders();
        assertThat(node.get("connected").get(0).asText()).isEqualTo("openai");
        assertThat(node.get("all").get(0).get("id").asText()).isEqualTo("openai");
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```
期望：方法 `listProviders` 不存在。

- [ ] **Step 3：在 OpenCodeHttpClient 新增方法**

```java
// 在 OpenCodeHttpClient.java 文件末尾（}前）追加：

    public com.fasterxml.jackson.databind.JsonNode listProviders() {
        String body = wc.get().uri("/provider")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /provider response", e);
        }
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```
期望：PASS。

- [ ] **Step 5：提交**

```
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java
git commit -m "feat(opencode): add listProviders() to OpenCodeHttpClient"
```

---

## Task 5：OpenCodeHttpClient 新增 getProviderAuth()

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java`

- [ ] **Step 1：追加测试**

```java
    @Test
    void getProviderAuth_returns_methods_per_provider() {
        server.stubFor(get("/provider/auth").willReturn(okJson("""
            {"openai":[{"type":"api","label":"API Key"}],
             "anthropic":[{"type":"oauth"},{"type":"api","label":"API Key"}]}
            """)));
        var node = client.getProviderAuth();
        assertThat(node.get("openai").get(0).get("type").asText()).isEqualTo("api");
    }
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```
期望：方法不存在。

- [ ] **Step 3：实现**

```java
    public com.fasterxml.jackson.databind.JsonNode getProviderAuth() {
        String body = wc.get().uri("/provider/auth")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /provider/auth response", e);
        }
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```

- [ ] **Step 5：提交**

```
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java
git commit -m "feat(opencode): add getProviderAuth() to OpenCodeHttpClient"
```

---

## Task 6：OpenCodeHttpClient 新增 putAuth()

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java`

- [ ] **Step 1：追加测试（包含 happy path + 4xx 错误透传）**

```java
    @Test
    void putAuth_posts_body_to_provider_endpoint() {
        server.stubFor(put(urlEqualTo("/auth/openai"))
            .willReturn(aResponse().withStatus(200)));
        client.putAuth("openai", java.util.Map.of("type", "api", "key", "sk-test"));
        server.verify(putRequestedFor(urlEqualTo("/auth/openai"))
            .withRequestBody(matchingJsonPath("$.type", equalTo("api")))
            .withRequestBody(matchingJsonPath("$.key", equalTo("sk-test"))));
    }

    @Test
    void putAuth_bubbles_upstream_4xx_as_runtime_with_status() {
        server.stubFor(put(urlEqualTo("/auth/openai"))
            .willReturn(aResponse().withStatus(400).withBody("invalid key format")));
        assertThatThrownBy(() -> client.putAuth("openai",
                java.util.Map.of("type", "api", "key", "x")))
            .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
    }
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```

- [ ] **Step 3：实现**

```java
    public void putAuth(String providerId, java.util.Map<String, Object> payload) {
        wc.put().uri("/auth/{id}", providerId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenCodeHttpClientIT
```

- [ ] **Step 5：提交**

```
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/OpenCodeHttpClientIT.java
git commit -m "feat(opencode): add putAuth() to OpenCodeHttpClient"
```

---

## Task 7：AiSettingsService 聚合 provider + 偏好

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ai/AiSettingsService.java`

- [ ] **Step 1：写失败的单元测试（用 Mockito 替身仓储 + client）**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/ai/AiSettingsServiceTest.java
package com.datatalk.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSettingsServiceTest {

    @Test
    void models_merges_opencode_list_with_disabled_set() throws Exception {
        var om = new ObjectMapper();
        var client = mock(OpenCodeHttpClient.class);
        when(client.listProviders()).thenReturn(om.readTree("""
            {"all":[{"id":"openai","name":"OpenAI","models":{
                "gpt-5":{"id":"gpt-5","name":"GPT-5"},
                "gpt-5-nano":{"id":"gpt-5-nano","name":"GPT-5 Nano"}}}],
             "connected":["openai"]}
            """));
        var modelPrefs = mock(AiModelPrefsRepository.class);
        when(modelPrefs.disabledSet()).thenReturn(Set.of("openai/gpt-5-nano"));
        var userPrefs = mock(AiUserPrefsRepository.class);
        var svc = new AiSettingsService(client, modelPrefs, userPrefs, om);

        var out = svc.listModels();
        assertThat(out.providers()).hasSize(1);
        var p = out.providers().get(0);
        assertThat(p.id()).isEqualTo("openai");
        assertThat(p.connected()).isTrue();
        assertThat(p.models()).extracting("id", "enabled")
            .containsExactlyInAnyOrder(
                tuple("gpt-5", true),
                tuple("gpt-5-nano", false));
    }

    @Test
    void models_skips_disconnected_providers() throws Exception {
        var om = new ObjectMapper();
        var client = mock(OpenCodeHttpClient.class);
        when(client.listProviders()).thenReturn(om.readTree("""
            {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":[]}
            """));
        var modelPrefs = mock(AiModelPrefsRepository.class);
        when(modelPrefs.disabledSet()).thenReturn(Set.of());
        var svc = new AiSettingsService(client, modelPrefs,
            mock(AiUserPrefsRepository.class), om);

        var out = svc.listModels();
        assertThat(out.providers().get(0).connected()).isFalse();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... vals) {
        return org.assertj.core.groups.Tuple.tuple(vals);
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-application test -Dtest=AiSettingsServiceTest
```
期望：类不存在。

- [ ] **Step 3：实现 AiSettingsService**

```java
// AiSettingsService.java
package com.datatalk.application.ai;

import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiSettingsService {

    private final OpenCodeHttpClient oc;
    private final AiModelPrefsRepository modelPrefs;
    private final AiUserPrefsRepository userPrefs;
    private final ObjectMapper om;

    public AiSettingsService(OpenCodeHttpClient oc,
                             AiModelPrefsRepository modelPrefs,
                             AiUserPrefsRepository userPrefs,
                             ObjectMapper om) {
        this.oc = oc;
        this.modelPrefs = modelPrefs;
        this.userPrefs = userPrefs;
        this.om = om;
    }

    public JsonNode listProviders() { return oc.listProviders(); }

    public JsonNode providerAuth() { return oc.getProviderAuth(); }

    public void putCredentials(String providerId, Map<String, Object> payload) {
        oc.putAuth(providerId, payload);
    }

    public ModelsDto listModels() {
        JsonNode root = oc.listProviders();
        Set<String> connected = new HashSet<>();
        if (root.has("connected")) root.get("connected").forEach(n -> connected.add(n.asText()));
        Set<String> disabled = modelPrefs.disabledSet();

        List<ProviderDto> providers = new ArrayList<>();
        if (root.has("all")) {
            for (JsonNode p : root.get("all")) {
                String pid = p.get("id").asText();
                String name = p.has("name") ? p.get("name").asText() : pid;
                List<ModelDto> models = new ArrayList<>();
                JsonNode m = p.get("models");
                if (m != null && m.isObject()) {
                    m.fields().forEachRemaining(e -> {
                        String mid = e.getKey();
                        String mname = e.getValue().has("name") ? e.getValue().get("name").asText() : mid;
                        boolean enabled = !disabled.contains(pid + "/" + mid);
                        models.add(new ModelDto(mid, mname, enabled));
                    });
                }
                providers.add(new ProviderDto(pid, name, connected.contains(pid), models));
            }
        }
        return new ModelsDto(providers);
    }

    public void setModelEnabled(String providerId, String modelId, boolean enabled) {
        modelPrefs.setEnabled(providerId, modelId, enabled);
    }

    public String getCurrentModel() { return userPrefs.getCurrentModel(); }
    public void setCurrentModel(String modelId) { userPrefs.setCurrentModel(modelId); }

    public record ModelsDto(List<ProviderDto> providers) {}
    public record ProviderDto(String id, String name, boolean connected, List<ModelDto> models) {}
    public record ModelDto(String id, String name, boolean enabled) {}
}
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-application test -Dtest=AiSettingsServiceTest
```

- [ ] **Step 5：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/ai/AiSettingsService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ai/AiSettingsServiceTest.java
git commit -m "feat(ai): add AiSettingsService for provider/model aggregation"
```

---

## Task 8：AiSettingsController provider 端点（list + auth + credentials）

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/AiSettingsControllerIT.java`

集成测试用 WireMock 模拟 OpenCode（沿用项目里 `@SpringBootTest + dynamic WireMock` 模式，参考 `EndToEndSmokeIT`）。

- [ ] **Step 1：写失败的 IT**

```java
// AiSettingsControllerIT.java
package com.datatalk.adapter.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiSettingsControllerIT {

    static WireMockServer oc;
    @LocalServerPort int port;
    WebTestClient web;

    @TestConfiguration
    static class Override {
        @Bean @Primary
        OpenCodeHttpClient openCodeHttpClient(ObjectMapper om) {
            return new OpenCodeHttpClient("http://localhost:" + oc.port(), om);
        }
    }

    @BeforeAll
    static void up() {
        oc = new WireMockServer(wireMockConfig().dynamicPort());
        oc.start();
    }
    @AfterAll
    static void down() { oc.stop(); }
    @BeforeEach
    void setup() {
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        oc.resetAll();
    }

    @Test
    void list_providers_transparently_proxies() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{}}],
             "connected":["openai"]}
            """)));
        web.get().uri("/api/ai/providers").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.connected[0]").isEqualTo("openai");
    }

    @Test
    void provider_auth_proxies() {
        oc.stubFor(get("/provider/auth").willReturn(okJson("""
            {"openai":[{"type":"api"}]}
            """)));
        web.get().uri("/api/ai/providers/auth").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.openai[0].type").isEqualTo("api");
    }

    @Test
    void put_credentials_forwards_body() {
        oc.stubFor(put("/auth/openai").willReturn(aResponse().withStatus(200)));
        web.put().uri("/api/ai/providers/openai/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"type":"api","key":"sk-test"}
                """)
            .exchange()
            .expectStatus().isNoContent();
        oc.verify(putRequestedFor(urlEqualTo("/auth/openai"))
            .withRequestBody(matchingJsonPath("$.key", equalTo("sk-test"))));
    }

    @Test
    void opencode_down_returns_503() {
        oc.stubFor(get("/provider").willReturn(aResponse().withStatus(500)));
        web.get().uri("/api/ai/providers").exchange()
            .expectStatus().isEqualTo(503)
            .expectBody().jsonPath("$.error").isEqualTo("OPENCODE_UNAVAILABLE");
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiSettingsControllerIT
```
期望：所有 endpoint 404。

- [ ] **Step 3：写 Controller**

```java
// AiSettingsController.java
package com.datatalk.adapter.api;

import com.datatalk.application.ai.AiSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {

    private final AiSettingsService svc;
    public AiSettingsController(AiSettingsService svc) { this.svc = svc; }

    @GetMapping("/providers")
    public JsonNode listProviders() { return svc.listProviders(); }

    @GetMapping("/providers/auth")
    public JsonNode providerAuth() { return svc.providerAuth(); }

    @PutMapping("/providers/{id}/credentials")
    public ResponseEntity<Void> putCredentials(@PathVariable String id,
                                               @RequestBody Map<String, Object> body) {
        svc.putCredentials(id, body);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4：写 `AiSettingsExceptionHandler` 归一化 503**

```java
// AiSettingsExceptionHandler.java
package com.datatalk.adapter.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = AiSettingsController.class)
public class AiSettingsExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> upstream(WebClientResponseException e) {
        // 4xx 原样透传；5xx 归一 503 OPENCODE_UNAVAILABLE
        if (e.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", "UPSTREAM_4XX",
                "message", e.getResponseBodyAsString()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "OPENCODE_UNAVAILABLE",
            "message", e.getMessage()));
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<Map<String, Object>> connectFailure(WebClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "OPENCODE_UNAVAILABLE",
            "message", e.getMessage()));
    }
}
```

- [ ] **Step 5：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=AiSettingsControllerIT
```
期望：4 个测试 PASS。

- [ ] **Step 6：提交**

```
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsController.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsExceptionHandler.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/AiSettingsControllerIT.java
git commit -m "feat(ai): add AiSettingsController provider/auth/credentials endpoints"
```

---

## Task 9：AiSettingsController models + current-model 端点

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsController.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/AiSettingsControllerIT.java`

- [ ] **Step 1：追加测试 case**

```java
    @Test
    void list_models_merges_prefs() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{
                "gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":["openai"]}
            """)));
        web.get().uri("/api/ai/models").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.providers[0].id").isEqualTo("openai")
            .jsonPath("$.providers[0].connected").isEqualTo(true)
            .jsonPath("$.providers[0].models[0].enabled").isEqualTo(true);
    }

    @Test
    void patch_model_disables() {
        oc.stubFor(get("/provider").willReturn(okJson("""
            {"all":[{"id":"openai","name":"OpenAI","models":{"gpt-5":{"id":"gpt-5","name":"GPT-5"}}}],
             "connected":["openai"]}
            """)));
        web.patch().uri("/api/ai/models/openai/gpt-5")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"enabled\":false}")
            .exchange()
            .expectStatus().isNoContent();
        web.get().uri("/api/ai/models").exchange()
            .expectBody().jsonPath("$.providers[0].models[0].enabled").isEqualTo(false);
    }

    @Test
    void current_model_round_trip() {
        web.patch().uri("/api/ai/current-model")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"modelId\":\"openai/gpt-5\"}")
            .exchange().expectStatus().isNoContent();
        web.get().uri("/api/ai/current-model").exchange()
            .expectBody().jsonPath("$.modelId").isEqualTo("openai/gpt-5");
    }
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=AiSettingsControllerIT
```

- [ ] **Step 3：在 AiSettingsController 追加端点**

```java
    // path 要支持形如 openai/gpt-5 的 modelId —— 用 {id:.+}
    @GetMapping("/models")
    public AiSettingsService.ModelsDto listModels() { return svc.listModels(); }

    public record ModelPatchBody(boolean enabled) {}

    @PatchMapping("/models/{providerId}/{modelId:.+}")
    public ResponseEntity<Void> patchModel(@PathVariable String providerId,
                                           @PathVariable String modelId,
                                           @RequestBody ModelPatchBody body) {
        svc.setModelEnabled(providerId, modelId, body.enabled());
        return ResponseEntity.noContent().build();
    }

    public record CurrentModelDto(String modelId) {}

    @GetMapping("/current-model")
    public CurrentModelDto getCurrentModel() {
        return new CurrentModelDto(svc.getCurrentModel());
    }

    @PatchMapping("/current-model")
    public ResponseEntity<Void> patchCurrentModel(@RequestBody CurrentModelDto body) {
        svc.setCurrentModel(body.modelId());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=AiSettingsControllerIT
```

- [ ] **Step 5：提交**

```
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/api/AiSettingsController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/AiSettingsControllerIT.java
git commit -m "feat(ai): add models list/patch and current-model endpoints"
```

---

## Task 10：ConnectionRepository 补 update + deleteById

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`

- [ ] **Step 1：查找现有 `ConnectionRepositoryIT`（若有）或创建**

```
find server -name "ConnectionRepositoryIT.java"
```

若不存在，创建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/ConnectionRepositoryIT.java`：

```java
package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ConnectionRepositoryIT {

    @Autowired ConnectionRepository repo;

    @BeforeEach
    void reset() { repo.deleteAll(); }

    @Test
    void update_overwrites_fields_and_keeps_id() {
        repo.insert(new ConnectionRecord("c1", "mysql", "h1", 3306, "db1", "u1",
            new byte[]{1}, null, 100));
        repo.update(new ConnectionRecord("c1", "postgres", "h2", 5432, "db2", "u2",
            new byte[]{2}, null, 100));
        var rec = repo.findById("c1").orElseThrow();
        assertThat(rec.host()).isEqualTo("h2");
        assertThat(rec.port()).isEqualTo(5432);
        assertThat(rec.kind()).isEqualTo("postgres");
    }

    @Test
    void deleteById_returns_true_when_deleted_false_when_absent() {
        repo.insert(new ConnectionRecord("c1", "mysql", "h", 1, "d", "u", new byte[]{1}, null, 1));
        assertThat(repo.deleteById("c1")).isTrue();
        assertThat(repo.deleteById("c1")).isFalse();
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=ConnectionRepositoryIT
```
期望：编译失败（`update` / `deleteById` 不存在）。

- [ ] **Step 3：实现 `update` + `deleteById`**

在 `ConnectionRepository.java` 追加：

```java
    public void update(ConnectionRecord c) {
        int n = jdbc.update("""
            UPDATE connections
               SET kind = ?, host = ?, port = ?, database_name = ?, username = ?,
                   password_enc = ?, schema_digest = ?
             WHERE id = ?
            """, c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
            c.passwordEnc(), c.schemaDigest(), c.id());
        if (n == 0) throw new java.util.NoSuchElementException("unknown connection: " + c.id());
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM connections WHERE id = ?", id) > 0;
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=ConnectionRepositoryIT
```

- [ ] **Step 5：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/ConnectionRepositoryIT.java
git commit -m "feat(connection): add update and deleteById to ConnectionRepository"
```

---

## Task 11：ConnectionService 补 update / deleteById / testConnection

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`

- [ ] **Step 1：写测试**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionServiceTest {

    @Test
    void testConnection_succeeds_against_h2_in_memory() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var clk = Clock.systemUTC();
        var svc = new ConnectionService(repo, vault, clk);

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0)));
        when(vault.open(any())).thenReturn("");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isTrue();
        assertThat(r.latencyMs()).isNotNegative();
    }

    @Test
    void testConnection_fails_fast_on_bad_port() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var svc = new ConnectionService(repo, vault, Clock.systemUTC());
        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0)));
        when(vault.open(any())).thenReturn("p");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isFalse();
        assertThat(r.reason()).isNotBlank();
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-application test -Dtest=ConnectionServiceTest
```

- [ ] **Step 3：实现三个方法**

在 `ConnectionService.java` 追加（末尾 `}` 前）：

```java
    public void update(String id, String kind, String host, int port, String database,
                       String username, String password) {
        var existing = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
        byte[] enc = password != null ? vault.seal(password) : existing.passwordEnc();
        repo.update(new ConnectionRecord(id, kind, host, port, database, username,
            enc, existing.schemaDigest(), existing.createdAt()));
    }

    public boolean deleteById(String id) {
        return repo.deleteById(id);
    }

    public TestResult testConnection(String id) {
        var c = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
        String password = vault.open(c.passwordEnc());
        String url = jdbcUrl(c);
        long started = System.nanoTime();
        try (var conn = java.sql.DriverManager.getConnection(url, c.username(), password)) {
            boolean ok = conn.isValid(3);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            return new TestResult(ok, ms, ok ? null : "connection reported invalid");
        } catch (Throwable t) {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            return new TestResult(false, ms, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String jdbcUrl(ConnectionRecord c) {
        return switch (c.kind()) {
            case "mysql" -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName()
                + "?connectTimeout=3000&socketTimeout=3000";
            case "postgres", "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName()
                + "?connectTimeout=3&socketTimeout=3";
            case "h2" -> "jdbc:h2:" + c.databaseName();
            default -> throw new IllegalArgumentException("unsupported kind: " + c.kind());
        };
    }

    public record TestResult(boolean ok, long latencyMs, String reason) {}
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-application test -Dtest=ConnectionServiceTest
```

- [ ] **Step 5：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java
git commit -m "feat(connection): add update/deleteById/testConnection to service"
```

---

## Task 12：ConnectionController 补 PUT / DELETE / test

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`

- [ ] **Step 1：写 IT**

```java
// ConnectionControllerIT.java
package com.datatalk.adapter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerIT {

    @LocalServerPort int port;
    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void put_updates_existing_connection() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"u1","kind":"mysql","host":"h","port":3306,
                 "database":"d","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();

        w.put().uri("/api/connections/u1").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"kind":"postgres","host":"h2","port":5432,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isNoContent();

        w.get().uri("/api/connections").exchange()
            .expectBody().jsonPath("$.connections[0].host").isEqualTo("h2");
    }

    @Test
    void delete_returns_204_on_success_404_on_missing() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"d1","kind":"mysql","host":"h","port":3306,
                 "database":"d","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();

        w.delete().uri("/api/connections/d1").exchange().expectStatus().isNoContent();
        w.delete().uri("/api/connections/d1").exchange().expectStatus().isNotFound();
    }

    @Test
    void test_endpoint_returns_ok_false_for_bad_target() {
        var w = web();
        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"id":"t1","kind":"mysql","host":"127.0.0.1","port":1,
                 "database":"x","username":"u","password":"p"}
                """)
            .exchange().expectStatus().isCreated();
        w.post().uri("/api/connections/t1/test").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.ok").isEqualTo(false);
    }
}
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-adapter test -Dtest=ConnectionControllerIT
```

- [ ] **Step 3：扩展 Controller**

```java
// ConnectionController.java 在现有 create/list 之后追加：

    public record UpdateBody(String kind, String host, int port, String database,
                             String username, String password) {}

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody UpdateBody body) {
        try {
            svc.update(id, body.kind(), body.host(), body.port(),
                body.database(), body.username(), body.password());
            return ResponseEntity.noContent().build();
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return svc.deleteById(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> test(@PathVariable String id) {
        try {
            var r = svc.testConnection(id);
            return ResponseEntity.ok(r);
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-adapter test -Dtest=ConnectionControllerIT
```

- [ ] **Step 5：提交**

```
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java
git commit -m "feat(connection): add PUT/DELETE/test endpoints"
```

---

## Task 13：ChannelService.sendMessage 携带 current_model

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java`

- [ ] **Step 1：查现有 ChannelService 测试（应有），追加 case**

```
find server -name "ChannelServiceTest*" -o -name "ChannelServiceIT*"
```

在现有测试类（或创建 `ChannelServiceModelParamTest`）中追加：

```java
    @Test
    void sendMessage_forwards_current_model_when_set() {
        // 假设 fixture 构造 ChannelService 时注入了 AiUserPrefsRepository mock
        when(userPrefs.getCurrentModel()).thenReturn("openai/gpt-5");
        sessions.save(new SessionRecord("s1", ...));  // 按项目风格创建 session

        svc.sendMessage("s1", List.of(new Part.Text("hi")));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(anyString(), cap.capture());
        assertThat(cap.getValue()).containsEntry("model", "openai/gpt-5");
    }

    @Test
    void sendMessage_omits_model_when_null() {
        when(userPrefs.getCurrentModel()).thenReturn(null);
        sessions.save(new SessionRecord("s1", ...));

        svc.sendMessage("s1", List.of(new Part.Text("hi")));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(anyString(), cap.capture());
        assertThat(cap.getValue()).doesNotContainKey("model");
    }
```

- [ ] **Step 2：运行测试确认失败**

```
cd server && mvn -pl data-talk-application test -Dtest=ChannelService*
```

- [ ] **Step 3：修改 ChannelService，注入 `AiUserPrefsRepository` 并在 forward 时合并 model**

在构造器和字段追加：

```java
    private final com.datatalk.application.ai.AiUserPrefsRepository userPrefs;

    public ChannelService(SessionRepository sessions, MessageRepository messages,
                          SessionBusRegistry buses, PendingCallRegistry pending,
                          IdGenerator ids, Clock clock,
                          OpenCodeGateway gateway, OpenCodeSessionMap sessionMap,
                          ObjectMapper om,
                          com.datatalk.application.ai.AiUserPrefsRepository userPrefs) {
        // ...现有赋值...
        this.userPrefs = userPrefs;
    }
```

把原本的 `gateway.forwardUserMessage(...)` 改成：

```java
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("parts", parts.stream().map(p -> om.convertValue(p, Map.class)).toList());
        String model = userPrefs.getCurrentModel();
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        gateway.forwardUserMessage(ocSid, body);
```

- [ ] **Step 4：运行测试确认通过**

```
cd server && mvn compile -q && mvn -pl data-talk-application test -Dtest=ChannelService*
```
若构造器签名变动导致其他测试编译失败，逐一补 `userPrefs` mock。

- [ ] **Step 5：提交**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/
git commit -m "feat(channel): forward current_model to OpenCode when set"
```

---

## Task 14：全量测试跑过一遍 + 索引登记

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1：后端全量测试**

```
cd server && mvn clean verify
```
期望：BUILD SUCCESS，所有测试 PASS。

- [ ] **Step 2：编译前端类型检查（无代码变更但确认无破坏）**

```
cd client && npx tsc --noEmit
```
期望：0 error。

- [ ] **Step 3：在 `docs/exec-plans/index.md` 的活跃计划表追加**

```markdown
## 活跃计划

| 计划 | 状态 | 摘要 |
|------|------|------|
| [AI Settings · Part 1 · Backend](../superpowers/plans/2026-04-17-ai-settings-part1-backend.md) | 🟡 进行中 | OpenCode 代理 + 偏好持久化 + 连接 CRUD |
| [AI Settings · Part 2 · Frontend](../superpowers/plans/2026-04-17-ai-settings-part2-frontend.md) | ⚪ 待启动 | 设置中心 UI + 对话框 + Chat 模型选择器 |
```

- [ ] **Step 4：提交**

```
git add docs/exec-plans/index.md
git commit -m "docs(plan): register AI settings part 1/2 plans in index"
```

---

## 执行顺序备注

1. Task 1 → Task 2 → Task 3（数据层）
2. Task 4 → Task 5 → Task 6（OpenCode client 扩展）
3. Task 7 → Task 8 → Task 9（服务 + Controller）
4. Task 10 → Task 11 → Task 12（连接 CRUD）
5. Task 13（channel 接线，依赖 Task 2 的 AiUserPrefsRepository）
6. Task 14（收尾）

Task 1/2/3 可与 Task 4/5/6 并行执行（数据层 vs 客户端扩展无交集）。Task 7 依赖 Task 2/3/4，Task 13 依赖 Task 2。

---

## 决策日志

- **why `JsonNode` not typed DTO for providers/auth**：OpenCode 上游字段多且可能扩展，透传更稳；前端也只消费其中几个字段
- **why disable-only storage for model prefs**：OpenCode 模型清单随版本变化，启用为默认态可免去孤儿记录清理
- **why `{id:.+}` path pattern**：modelId 形如 `gpt-5` 单段即可，但部分 provider 可能出 `gpt-5-turbo-preview/v2`；用 `.+` 放宽
- **why 503 归一化 5xx 而不原样透传**：前端只需知道"是否上游故障"，不关心 OpenCode 具体 5xx 语义
