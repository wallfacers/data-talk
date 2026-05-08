# Report / Dashboard P1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 Report/Dashboard 第一阶段端到端 happy path：chat ` ```dashboard ` 围栏首生成 → 工具栏"打开到工作台" → Stage Tab 内 12 栏栅格渲染 chart + markdown widget → 简化 ui_patch（widget add/remove/replace + layout）跑通增量改。

**Architecture:**
- 后端：Java 21 records 域模型 + JSON Schema 校验 + 自定义 JsonPatchApplier（matchKey 路径 → 数组下标，原子应用） + 复用 file_artifact Part 1-5 系统 + 新 `DashboardController` REST + 把 `dashboard` 加入 `UiPatchAction` / `UiReadAction` / `UiExecAction` 的 `object` enum。
- 前端：`LayoutEngine` 接口 + `GridLayoutEngine`（react-grid-layout v1）+ Zustand `dashboard-tabs-store` + chart / markdown widget 复用 chart fence renderer + `DashboardCanvas` 组合 + `DashboardTab` Stage 容器 + `dashboard-block` chat 围栏 + `DashboardAdapter` 接 UIRouter。

**Tech Stack:** Spring Boot 3.5 / Java 21 / Jackson；React 19 / TypeScript / Zustand / TanStack Query / vitest / Playwright；react-grid-layout 1.5+（新增）；echarts 5.6 / zod 4.3（已有）。

---

## Status

- **Created:** 2026-05-08
- **Phase:** P1（foundation slice；P2-P6 各为独立 child plan）
- **Spec:** [docs/product-specs/2026-05-08-report-dashboard-design.md](../product-specs/2026-05-08-report-dashboard-design.md)
- **Roadmap:** [docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md](./2026-04-25-next-implementation-roadmap-plan.md) Task 8 visualization 子切片

## Design Inputs（来自 client/DESIGN.md，本 P1 强制约束）

- Stage chrome 用 `bg.subtle`；canvas 用 `bg.canvas`；padding 全 dashboard 统一 24px (`spacing.6`)
- Chart widget 用 `accent.primary` (cobalt) 作 focus，`accent.warn` (amber) 作 compare；green/red 仅 outcome 语义
- 控件五态映射 token：idle / hover / active / focus / disabled — 工具栏按钮、widget 选中边框、tab 切换全覆盖
- Stage Tab 全局非 per-session：dashboard Tab 是 workspace scope
- Motion 仅确认状态变化（拖拽对齐反馈、widget 增删过渡），`motion.normal + easing.standard`，遵守 `prefers-reduced-motion`
- 焦点环 `interaction.focusRing`；键盘可达：Tab → widget，方向键调位置（grid mode），Enter 编辑，Esc 退编辑
- 无障碍：chart accessible name，KPI ARIA 描述，色对比 ≥ 4.5:1（P1 widget 范围内：chart / markdown）
- 流式骨架动画用 `motion.fast (120ms)`，遵守 `prefers-reduced-motion`

## Data Source Compatibility Gate (CLAUDE.md)

P1 不新增 connection kind，仅消费现有 connection。Gate 各维度状态：

| 维度 | 状态 |
|---|---|
| Connection kind / URL builder | **N/A** |
| SQL Splitter / Risk | widget query 走 `SqlStatementGuard` 限 L1（SELECT/WITH）；不绕过现有风控 |
| Metadata 发现 | **N/A** |
| ER | **N/A** |
| Diagnostics | **N/A** |
| 前端连接表单 | **N/A** |
| MCP schema | 复用 `datatalk_execute_sql` 路径；新增 `dashboard` 仅是 `UiPatchAction` / `UiReadAction` / `UiExecAction` 的 `object` enum 扩展 |
| AGENTS.md | 新增 "Dashboards" 节（任务 B6） |

## File Structure Map

### 后端新增

| 文件 | 责任 |
|---|---|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Dashboard.java` | 域 record：schemaVersion / id / title / parameters / widgets / layout / version / 时间戳 |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Widget.java` | record：id / type / position / parameters / query / options |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/WidgetType.java` | enum：CHART / KPI / TABLE / MARKDOWN / FILTER / SECTION / DIVIDER / IMAGE |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/GridLayout.java` | record：engine / cols / rowHeight / gap |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/GridPosition.java` | record：x / y / w / h / z |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/WidgetQuery.java` | record：connectionId / sql / paramRefs (Map<String,String>) |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/ParameterDef.java` | record：id / scope / ownerWidgetId / name / type / default |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java` | promote / load / patch / writeback；调用 FileArtifactService |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardSchemaValidator.java` | JSON 校验：schema 必填字段 + 跨 widget 引用 + 布局不变量 + paramRefs map 完整性 |
| `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/JsonPatchApplier.java` | 自定义 RFC 6902 子集（add/remove/replace），`/widgets[id=...]` matchKey 路径，原子性，baseVersion 校验 |
| `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json` | JSON Schema 文件（前后端共用，validator 加载） |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java` | REST：POST /api/dashboards/promote · GET /api/dashboards/{id} · PATCH /api/dashboards/{id} |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DashboardPromoteRequest.java` | DTO：dashboard JSON |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DashboardPatchRequest.java` | DTO：baseVersion + patches |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (修改) | 新增 "Dashboards" 节 |

### 后端修改

| 文件 | 改动 |
|---|---|
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java` | `object` enum 加 `dashboard` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java` | `object` enum 加 `dashboard` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` | `object` enum 加 `dashboard`；`action` 加 `dashboard.create` 等动词 |

### 前端新增

| 文件 | 责任 |
|---|---|
| `client/src/features/dashboard/types.ts` | TS 类型：Dashboard / Widget / GridLayout / GridPosition / WidgetQuery / ParameterDef |
| `client/src/features/dashboard/schema.ts` | zod schema（JSON Schema 镜像，构建时与后端 dashboard-schema.json 跑一致性 diff） |
| `client/src/features/dashboard/engines/layout-engine.ts` | LayoutEngine 接口 + ContainerSize / RenderedPosition 类型 |
| `client/src/features/dashboard/engines/grid-layout-engine.ts` | GridLayoutEngine 实现，封装 react-grid-layout |
| `client/src/features/dashboard/engines/grid-layout-engine.test.ts` | engine 单元测试 |
| `client/src/features/dashboard/widgets/chart-widget.tsx` | chart widget renderer，复用 chat fence 的 ChartRenderer |
| `client/src/features/dashboard/widgets/markdown-widget.tsx` | markdown widget renderer，复用 chat markdown |
| `client/src/features/dashboard/widgets/widget-shell.tsx` | widget 共享外壳：标题 + loading + error + 工具按钮 |
| `client/src/features/dashboard/dashboard-canvas.tsx` | 主容器：组合 LayoutEngine + widgets + 工具栏 |
| `client/src/features/dashboard/dashboard-tab.tsx` | Stage Tab 内容外壳，从 file_artifact 加载并提供 viewer/editor 模式切换 |
| `client/src/features/dashboard/stores/dashboard-tabs-store.ts` | Zustand：dashboard 内存态 + hydrateTab + applyPatch |
| `client/src/features/dashboard/services/dashboard-api.ts` | fetch 封装：promote / load / patch |
| `client/src/features/dashboard/adapters/DashboardAdapter.ts` | UIRouter Adapter，PATCH_CAPABILITIES / ACTIONS / read / patch / exec |
| `client/src/features/dashboard/__tests__/dashboard-adapter.test.ts` | adapter 单元测试 |
| `client/src/features/chat/components/markdown/dashboard-block.tsx` | chat fence 渲染：streaming 骨架 / preview / error 三态 |
| `client/src/features/chat/components/markdown/dashboard-block-toolbar.tsx` | "打开到工作台" / "复制 JSON" 按钮 |

### 前端修改

| 文件 | 改动 |
|---|---|
| `client/src/features/stage/registry/tab-type-registry.ts` | 注册 `dashboard` 类型，`payloadSource: 'dashboard'`，`extractContent` 从 dashboard 文档提取 |
| `client/src/features/chat/components/markdown/markdown.tsx` | 新增 `decorateDashboardBlocks`，识别 ` ```dashboard ` 围栏 |
| `client/package.json` | 新增依赖 `react-grid-layout` 与 `@types/react-grid-layout` |
| `client/src/i18n/messages.ts` | 新增 dashboard 相关 i18n keys（tab type label / 工具栏按钮 / 错误提示） |

---

## Tasks

### Task B1: 后端域模型 records

**Goal:** 定义 Dashboard / Widget / 布局 / 查询 / 参数的 Java records，作为后端唯一真相源；Jackson 序列化无歧义。

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Dashboard.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/Widget.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/WidgetType.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/GridLayout.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/GridPosition.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/WidgetQuery.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/ParameterDef.java`
- Test: `server/data-talk-domain/src/test/java/com/datatalk/domain/dashboard/DashboardJacksonTest.java`

- [ ] **Step 1: Write failing test for Jackson round-trip**

`DashboardJacksonTest.java`:

```java
package com.datatalk.domain.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardJacksonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsMinimalDashboard() throws Exception {
        Dashboard original = new Dashboard(
            1, "dash_xxx", "销售看板", null, "conn_1",
            List.of(),
            List.of(new Widget(
                "chart_w1", WidgetType.CHART,
                new GridPosition(0, 0, 6, 8, null),
                List.of(),
                new WidgetQuery(null, "SELECT 1", Map.of()),
                Map.of("title", "test", "echartsOption", Map.of(), "dataMapping", Map.of("rowsAsDataset", true))
            )),
            new GridLayout("grid", 12, 32, 8),
            1L, 1700000000000L, 1700000000000L
        );

        String json = mapper.writeValueAsString(original);
        Dashboard reread = mapper.readValue(json, Dashboard.class);

        assertThat(reread.id()).isEqualTo("dash_xxx");
        assertThat(reread.widgets()).hasSize(1);
        assertThat(reread.widgets().get(0).type()).isEqualTo(WidgetType.CHART);
        assertThat(reread.widgets().get(0).position().w()).isEqualTo(6);
        assertThat(reread.layout().cols()).isEqualTo(12);
    }
}
```

- [ ] **Step 2: Run test to confirm fail**

Run: `cd server && mvn test -pl data-talk-domain -Dtest=DashboardJacksonTest -q`
Expected: COMPILE FAIL ("class Dashboard not found").

- [ ] **Step 3: Implement records**

`WidgetType.java`:
```java
package com.datatalk.domain.dashboard;
public enum WidgetType { CHART, KPI, TABLE, MARKDOWN, FILTER, SECTION, DIVIDER, IMAGE }
```

`GridPosition.java`:
```java
package com.datatalk.domain.dashboard;
public record GridPosition(int x, int y, int w, int h, Integer z) {}
```

`GridLayout.java`:
```java
package com.datatalk.domain.dashboard;
public record GridLayout(String engine, int cols, int rowHeight, int gap) {}
```

`WidgetQuery.java`:
```java
package com.datatalk.domain.dashboard;
import java.util.Map;
public record WidgetQuery(String connectionId, String sql, Map<String, String> paramRefs) {
    public WidgetQuery {
        paramRefs = paramRefs == null ? Map.of() : Map.copyOf(paramRefs);
    }
}
```

`ParameterDef.java`:
```java
package com.datatalk.domain.dashboard;
public record ParameterDef(
    String id,
    String scope,
    String ownerWidgetId,
    String name,
    String type,
    Object defaultValue
) {}
```

`Widget.java`:
```java
package com.datatalk.domain.dashboard;
import java.util.List;
import java.util.Map;
public record Widget(
    String id,
    WidgetType type,
    GridPosition position,
    List<ParameterDef> parameters,
    WidgetQuery query,
    Map<String, Object> options
) {
    public Widget {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
```

`Dashboard.java`:
```java
package com.datatalk.domain.dashboard;
import java.util.List;
public record Dashboard(
    int schemaVersion,
    String id,
    String title,
    String description,
    String defaultConnectionId,
    List<ParameterDef> parameters,
    List<Widget> widgets,
    GridLayout layout,
    long version,
    long createdAt,
    long updatedAt
) {
    public Dashboard {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `cd server && mvn test -pl data-talk-domain -Dtest=DashboardJacksonTest -q`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/dashboard/ \
        server/data-talk-domain/src/test/java/com/datatalk/domain/dashboard/
git commit -m "feat(dashboard): add P1 domain records (Dashboard / Widget / GridLayout / ParameterDef)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B2: JSON Schema 资源 + DashboardSchemaValidator

**Goal:** 用一份 JSON Schema 作为 dashboard 文档结构的强制契约；Validator 在 schema 之上补跨 widget 引用、布局不重叠、paramRefs key-coverage、版本号约束。Validator 是 promote / patch 落盘前唯一守门员。

**Files:**
- Create: `server/data-talk-application/src/main/resources/dashboard/dashboard-schema.json`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardSchemaValidator.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/ValidationResult.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/dashboard/DashboardSchemaValidatorTest.java`

- [ ] **Step 1: Write JSON Schema resource file**

`dashboard-schema.json` (摘录关键约束)：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["schemaVersion", "id", "title", "parameters", "widgets", "layout", "version"],
  "properties": {
    "schemaVersion": { "const": 1 },
    "id": { "type": "string", "pattern": "^dash_[a-zA-Z0-9_]{4,}$" },
    "title": { "type": "string", "minLength": 1, "maxLength": 256 },
    "description": { "type": "string", "maxLength": 32768 },
    "defaultConnectionId": { "type": ["string", "null"] },
    "version": { "type": "integer", "minimum": 1 },
    "parameters": { "type": "array", "items": { "$ref": "#/$defs/parameterDef" } },
    "widgets": { "type": "array", "items": { "$ref": "#/$defs/widget" } },
    "layout": { "$ref": "#/$defs/gridLayout" }
  },
  "$defs": {
    "parameterDef": {
      "type": "object",
      "required": ["id", "scope", "name", "type", "default"],
      "properties": {
        "id": { "type": "string", "pattern": "^(global|local):[a-zA-Z0-9_:]+$" },
        "scope": { "enum": ["global", "local"] },
        "ownerWidgetId": { "type": ["string", "null"] },
        "name": { "type": "string" },
        "type": { "enum": ["date", "date_range", "string", "number", "string_list"] },
        "default": {}
      }
    },
    "widget": {
      "type": "object",
      "required": ["id", "type", "position", "options"],
      "properties": {
        "id": { "type": "string", "pattern": "^[a-z]+_w_[a-zA-Z0-9]{4,16}$" },
        "type": { "enum": ["chart", "kpi", "table", "markdown", "filter", "section", "divider", "image"] },
        "position": { "$ref": "#/$defs/gridPosition" },
        "parameters": { "type": "array", "items": { "$ref": "#/$defs/parameterDef" } },
        "query": { "$ref": "#/$defs/widgetQuery" },
        "options": { "type": "object" }
      }
    },
    "widgetQuery": {
      "type": "object",
      "required": ["sql", "paramRefs"],
      "properties": {
        "connectionId": { "type": ["string", "null"] },
        "sql": { "type": "string" },
        "paramRefs": { "type": "object", "additionalProperties": { "type": "string" } }
      }
    },
    "gridPosition": {
      "type": "object",
      "required": ["x", "y", "w", "h"],
      "properties": {
        "x": { "type": "integer", "minimum": 0, "maximum": 11 },
        "y": { "type": "integer", "minimum": 0 },
        "w": { "type": "integer", "minimum": 1, "maximum": 12 },
        "h": { "type": "integer", "minimum": 1 },
        "z": { "type": ["integer", "null"] }
      }
    },
    "gridLayout": {
      "type": "object",
      "required": ["engine", "cols"],
      "properties": {
        "engine": { "const": "grid" },
        "cols": { "const": 12 },
        "rowHeight": { "type": "integer", "minimum": 8, "maximum": 128 },
        "gap": { "type": "integer", "minimum": 0, "maximum": 32 }
      }
    }
  }
}
```

- [ ] **Step 2: Write failing validator tests**

`DashboardSchemaValidatorTest.java`:

```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DashboardSchemaValidator validator = new DashboardSchemaValidator();

    private JsonNode parse(String json) throws Exception { return mapper.readTree(json); }

    @Test
    void rejectsMissingSchemaVersion() throws Exception {
        JsonNode doc = parse("""
            { "id": "dash_aaaa", "title": "x", "parameters": [], "widgets": [],
              "layout": { "engine": "grid", "cols": 12 }, "version": 1 }""");
        ValidationResult r = validator.validate(doc);
        assertThat(r.errors()).anyMatch(e -> e.path().equals("/schemaVersion"));
    }

    @Test
    void rejectsOverlappingWidgets() throws Exception {
        JsonNode doc = parse("""
          { "schemaVersion": 1, "id": "dash_aaaa", "title": "x", "parameters": [],
            "widgets": [
              { "id": "chart_w_aaaa", "type": "chart", "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
                "options": {}, "query": { "sql": "select 1", "paramRefs": {} } },
              { "id": "chart_w_bbbb", "type": "chart", "position": { "x": 3, "y": 1, "w": 6, "h": 4 },
                "options": {}, "query": { "sql": "select 1", "paramRefs": {} } }
            ],
            "layout": { "engine": "grid", "cols": 12 }, "version": 1 }""");
        ValidationResult r = validator.validate(doc);
        assertThat(r.errors()).anyMatch(e -> e.path().contains("widgets") && e.message().contains("overlap"));
    }

    @Test
    void rejectsParamRefsKeyMissingFromSql() throws Exception {
        JsonNode doc = parse("""
          { "schemaVersion": 1, "id": "dash_aaaa", "title": "x", "parameters": [
              { "id": "global:dateRange", "scope": "global", "name": "dateRange",
                "type": "date_range", "default": null }
            ],
            "widgets": [
              { "id": "chart_w_aaaa", "type": "chart", "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
                "options": {},
                "query": { "sql": "SELECT * FROM t WHERE d >= :startDate", "paramRefs": {} } }
            ],
            "layout": { "engine": "grid", "cols": 12 }, "version": 1 }""");
        ValidationResult r = validator.validate(doc);
        assertThat(r.errors()).anyMatch(e -> e.message().contains("paramRefs missing key 'startDate'"));
    }

    @Test
    void rejectsZNonZeroInGridMode() throws Exception {
        JsonNode doc = parse("""
          { "schemaVersion": 1, "id": "dash_aaaa", "title": "x", "parameters": [],
            "widgets": [{ "id": "chart_w_aaaa", "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 4, "z": 5 },
              "options": {}, "query": { "sql": "select 1", "paramRefs": {} } }],
            "layout": { "engine": "grid", "cols": 12 }, "version": 1 }""");
        ValidationResult r = validator.validate(doc);
        assertThat(r.errors()).anyMatch(e -> e.message().contains("z must be 0 in grid mode"));
    }

    @Test
    void acceptsCleanDashboard() throws Exception {
        JsonNode doc = parse("""
          { "schemaVersion": 1, "id": "dash_aaaa", "title": "x", "parameters": [],
            "widgets": [{ "id": "chart_w_aaaa", "type": "chart",
              "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
              "options": {}, "query": { "sql": "select 1", "paramRefs": {} } }],
            "layout": { "engine": "grid", "cols": 12 }, "version": 1 }""");
        ValidationResult r = validator.validate(doc);
        assertThat(r.errors()).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests to confirm fail**

Run: `cd server && mvn test -pl data-talk-application -Dtest=DashboardSchemaValidatorTest -q`
Expected: COMPILE FAIL。

- [ ] **Step 4: Implement validator**

`ValidationResult.java`:
```java
package com.datatalk.application.dashboard;
import java.util.List;
public record ValidationResult(List<Error> errors) {
    public boolean ok() { return errors.isEmpty(); }
    public record Error(String path, String code, String message) {}
}
```

`DashboardSchemaValidator.java`（关键逻辑骨架）：
```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DashboardSchemaValidator {

    private static final Pattern SQL_PLACEHOLDER = Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");
    private final JsonSchema schema;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardSchemaValidator() {
        try (InputStream stream = getClass().getResourceAsStream("/dashboard/dashboard-schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            this.schema = factory.getSchema(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load dashboard-schema.json", e);
        }
    }

    public ValidationResult validate(JsonNode doc) {
        List<ValidationResult.Error> errors = new ArrayList<>();
        schema.validate(doc).forEach(msg ->
            errors.add(new ValidationResult.Error(msg.getPath(), "schema", msg.getMessage())));
        if (!errors.isEmpty()) return new ValidationResult(errors);

        validateWidgetIdsUnique(doc, errors);
        validateWidgetsNotOverlap(doc, errors);
        validateZIsZeroInGridMode(doc, errors);
        validateParamRefsCoverage(doc, errors);
        validateParamIdReferences(doc, errors);
        validateFilterParamBinding(doc, errors);
        return new ValidationResult(errors);
    }

    private void validateWidgetsNotOverlap(JsonNode doc, List<ValidationResult.Error> errors) {
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                var a = widgets.get(i).path("position");
                var b = widgets.get(j).path("position");
                if (rectsOverlap(a, b)) {
                    errors.add(new ValidationResult.Error(
                        "/widgets[" + j + "]/position",
                        "overlap",
                        "widget '" + widgets.get(j).path("id").asText() + "' overlaps with '" +
                        widgets.get(i).path("id").asText() + "'"));
                }
            }
        }
    }

    private boolean rectsOverlap(JsonNode a, JsonNode b) {
        int ax1 = a.path("x").asInt(), ay1 = a.path("y").asInt();
        int ax2 = ax1 + a.path("w").asInt(), ay2 = ay1 + a.path("h").asInt();
        int bx1 = b.path("x").asInt(), by1 = b.path("y").asInt();
        int bx2 = bx1 + b.path("w").asInt(), by2 = by1 + b.path("h").asInt();
        return ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2;
    }

    private void validateZIsZeroInGridMode(JsonNode doc, List<ValidationResult.Error> errors) {
        if (!"grid".equals(doc.path("layout").path("engine").asText())) return;
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            JsonNode z = widgets.get(i).path("position").path("z");
            if (!z.isMissingNode() && !z.isNull() && z.asInt() != 0) {
                errors.add(new ValidationResult.Error(
                    "/widgets[" + i + "]/position/z",
                    "z_nonzero",
                    "z must be 0 in grid mode (reserved for canvas v2)"));
            }
        }
    }

    private void validateParamRefsCoverage(JsonNode doc, List<ValidationResult.Error> errors) {
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            var query = widgets.get(i).path("query");
            if (query.isMissingNode()) continue;
            String sql = query.path("sql").asText();
            var paramRefs = query.path("paramRefs");
            Matcher m = SQL_PLACEHOLDER.matcher(sql);
            Set<String> placeholders = new HashSet<>();
            while (m.find()) placeholders.add(m.group(1));
            for (String p : placeholders) {
                if (!paramRefs.has(p)) {
                    errors.add(new ValidationResult.Error(
                        "/widgets[" + i + "]/query/paramRefs",
                        "param_ref_missing",
                        "paramRefs missing key '" + p + "' for :placeholder in sql"));
                }
            }
        }
    }

    private void validateParamIdReferences(JsonNode doc, List<ValidationResult.Error> errors) {
        Set<String> definedParamIds = collectDefinedParamIds(doc);
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            var paramRefs = widgets.get(i).path("query").path("paramRefs");
            paramRefs.fields().forEachRemaining(entry -> {
                String paramId = stripSubAccessor(entry.getValue().asText());
                if (!definedParamIds.contains(paramId)) {
                    errors.add(new ValidationResult.Error(
                        "/widgets[" + entry.getKey() + "]/query/paramRefs",
                        "param_id_undefined",
                        "paramRefs references unknown paramId '" + entry.getValue().asText() + "'"));
                }
            });
        }
    }

    private void validateFilterParamBinding(JsonNode doc, List<ValidationResult.Error> errors) {
        Set<String> definedParamIds = collectDefinedParamIds(doc);
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            JsonNode w = widgets.get(i);
            if (!"filter".equals(w.path("type").asText())) continue;
            String paramId = stripSubAccessor(w.path("options").path("paramId").asText(""));
            if (paramId.isEmpty()) {
                errors.add(new ValidationResult.Error(
                    "/widgets[" + i + "]/options/paramId",
                    "filter_paramid_missing",
                    "filter widget must bind to a paramId"));
            } else if (!definedParamIds.contains(paramId)) {
                errors.add(new ValidationResult.Error(
                    "/widgets[" + i + "]/options/paramId",
                    "filter_paramid_undefined",
                    "filter binds to unknown paramId '" + paramId + "'"));
            }
        }
    }

    private void validateWidgetIdsUnique(JsonNode doc, List<ValidationResult.Error> errors) {
        Set<String> seen = new HashSet<>();
        var widgets = doc.path("widgets");
        for (int i = 0; i < widgets.size(); i++) {
            String id = widgets.get(i).path("id").asText();
            if (!seen.add(id)) {
                errors.add(new ValidationResult.Error(
                    "/widgets[" + i + "]/id",
                    "duplicate_widget_id",
                    "duplicate widget id '" + id + "'"));
            }
        }
    }

    private Set<String> collectDefinedParamIds(JsonNode doc) {
        Set<String> ids = new HashSet<>();
        doc.path("parameters").forEach(p -> ids.add(p.path("id").asText()));
        doc.path("widgets").forEach(w ->
            w.path("parameters").forEach(p -> ids.add(p.path("id").asText())));
        return ids;
    }

    static String stripSubAccessor(String paramId) {
        int dot = paramId.lastIndexOf('.');
        if (dot > 0 && (paramId.endsWith(".start") || paramId.endsWith(".end"))) {
            return paramId.substring(0, dot);
        }
        return paramId;
    }
}
```

注：使用 `com.networknt:json-schema-validator` 库。在 `data-talk-application/pom.xml` 加依赖：
```xml
<dependency>
  <groupId>com.networknt</groupId>
  <artifactId>json-schema-validator</artifactId>
  <version>1.5.5</version>
</dependency>
```

- [ ] **Step 5: Run tests to verify pass + commit**

Run: `cd server && mvn test -pl data-talk-application -Dtest=DashboardSchemaValidatorTest -q`
Expected: 5 tests PASS。

```bash
git add server/data-talk-application/src/main/resources/dashboard/ \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/ \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/ \
        server/data-talk-application/pom.xml
git commit -m "feat(dashboard): JSON schema + cross-widget validator (P1)

- Schema enforces id pattern, layout grid invariants, paramRefs map shape
- Validator adds widget overlap detection, z=0 in grid mode, paramRefs coverage,
  paramId reference resolution, filter widget binding check
- Uses com.networknt:json-schema-validator 1.5.5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B3: JsonPatchApplier（matchKey 路径解析 + 原子应用）

**Goal:** 自定义 RFC 6902 子集（add / remove / replace）应用器，支持 `/widgets[id=<id>]/...` matchKey 寻址，patch 集合原子应用，应用前必跑 validator。

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/JsonPatchApplier.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/PatchPath.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/dashboard/JsonPatchApplierTest.java`

- [ ] **Step 1: Write failing tests**

`JsonPatchApplierTest.java`（覆盖 5 场景：matchKey replace、append /widgets/-、remove by matchKey、原子失败回滚、baseVersion 冲突）：

```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonPatchApplierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DashboardSchemaValidator validator = new DashboardSchemaValidator();
    private final JsonPatchApplier applier = new JsonPatchApplier(validator, mapper);

    private JsonNode baseDoc() throws Exception {
        return mapper.readTree("""
          { "schemaVersion": 1, "id": "dash_aaaa", "title": "x", "parameters": [],
            "widgets": [
              { "id": "chart_w_aaaa", "type": "chart",
                "position": { "x": 0, "y": 0, "w": 6, "h": 4 },
                "options": { "echartsOption": {}, "dataMapping": { "rowsAsDataset": true } },
                "query": { "sql": "select 1", "paramRefs": {} } }
            ],
            "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 },
            "version": 1, "createdAt": 0, "updatedAt": 0 }""");
    }

    @Test
    void replaceByMatchKeyAppliesAndBumpsVersion() throws Exception {
        JsonNode doc = baseDoc();
        var ops = List.of(Map.<String, Object>of(
            "op", "replace",
            "path", "/widgets[id=chart_w_aaaa]/query/sql",
            "value", "SELECT 2"
        ));
        JsonNode patched = applier.apply(doc, 1L, ops);
        assertThat(patched.path("version").asLong()).isEqualTo(2L);
        assertThat(patched.path("widgets").get(0).path("query").path("sql").asText())
            .isEqualTo("SELECT 2");
    }

    @Test
    void addToWidgetsAppendsNewWidget() throws Exception {
        JsonNode doc = baseDoc();
        var newWidget = Map.<String, Object>of(
            "id", "kpi_w_bbbb", "type", "kpi",
            "position", Map.of("x", 6, "y", 0, "w", 3, "h", 3),
            "query", Map.of("sql", "SELECT 1 AS v", "paramRefs", Map.of()),
            "options", Map.of("label", "test", "valueColumn", "v", "format", "number")
        );
        var ops = List.of(Map.<String, Object>of(
            "op", "add", "path", "/widgets/-", "value", newWidget
        ));
        JsonNode patched = applier.apply(doc, 1L, ops);
        assertThat(patched.path("widgets")).hasSize(2);
        assertThat(patched.path("widgets").get(1).path("id").asText()).isEqualTo("kpi_w_bbbb");
    }

    @Test
    void removeByMatchKeyRemovesWidget() throws Exception {
        JsonNode doc = baseDoc();
        var ops = List.of(Map.<String, Object>of(
            "op", "remove", "path", "/widgets[id=chart_w_aaaa]"
        ));
        JsonNode patched = applier.apply(doc, 1L, ops);
        assertThat(patched.path("widgets")).hasSize(0);
    }

    @Test
    void atomicityRollsBackOnAnyOpFailure() throws Exception {
        JsonNode doc = baseDoc();
        var ops = List.of(
            Map.<String, Object>of("op", "replace",
                "path", "/widgets[id=chart_w_aaaa]/query/sql", "value", "SELECT 2"),
            Map.<String, Object>of("op", "replace",
                "path", "/widgets[id=does_not_exist]/query/sql", "value", "fails")
        );
        assertThatThrownBy(() -> applier.apply(doc, 1L, ops))
            .isInstanceOf(JsonPatchApplier.PatchRejectException.class)
            .hasMessageContaining("does_not_exist");
        // baseDoc unchanged is implicit since apply returns new tree
    }

    @Test
    void baseVersionMismatchThrows409Like() throws Exception {
        JsonNode doc = baseDoc();
        var ops = List.<Map<String, Object>>of();
        assertThatThrownBy(() -> applier.apply(doc, 99L, ops))
            .isInstanceOf(JsonPatchApplier.VersionConflictException.class)
            .hasMessageContaining("baseVersion 99 != current 1");
    }
}
```

- [ ] **Step 2: Confirm failing**

Run: `cd server && mvn test -pl data-talk-application -Dtest=JsonPatchApplierTest -q`
Expected: COMPILE FAIL。

- [ ] **Step 3: Implement applier**

`PatchPath.java`:
```java
package com.datatalk.application.dashboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface PatchPath {
    record Index(String key, int index) implements PatchPath {}
    record Append(String key) implements PatchPath {}
    record MatchKey(String key, String matchKey, String matchValue) implements PatchPath {}
    record Plain(String key) implements PatchPath {}

    Pattern MATCH_KEY = Pattern.compile("^([^\\[]+)\\[([\\w-]+)=([^\\]]+)\\]$");

    static PatchPath parseSegment(String segment) {
        if ("-".equals(segment)) return new Append("");
        if (segment.matches("\\d+")) return new Index("", Integer.parseInt(segment));
        Matcher m = MATCH_KEY.matcher(segment);
        if (m.matches()) return new MatchKey(m.group(1), m.group(2), m.group(3));
        return new Plain(segment);
    }
}
```

`JsonPatchApplier.java`（核心结构）：
```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JsonPatchApplier {

    private final DashboardSchemaValidator validator;
    private final ObjectMapper mapper;

    public JsonPatchApplier(DashboardSchemaValidator validator, ObjectMapper mapper) {
        this.validator = validator;
        this.mapper = mapper;
    }

    public JsonNode apply(JsonNode original, long baseVersion, List<Map<String, Object>> ops) {
        long currentVersion = original.path("version").asLong();
        if (currentVersion != baseVersion) {
            throw new VersionConflictException(
                "baseVersion " + baseVersion + " != current " + currentVersion);
        }

        ObjectNode working = (ObjectNode) original.deepCopy();
        for (var op : ops) {
            String opType = (String) op.get("op");
            String path = (String) op.get("path");
            Object value = op.get("value");
            switch (opType) {
                case "add" -> applyAdd(working, path, value);
                case "remove" -> applyRemove(working, path);
                case "replace" -> applyReplace(working, path, value);
                default -> throw new PatchRejectException("op '" + opType + "' not allowed");
            }
        }
        working.put("version", currentVersion + 1);
        working.put("updatedAt", System.currentTimeMillis());

        var validation = validator.validate(working);
        if (!validation.ok()) {
            throw new PatchRejectException("validation failed: " + validation.errors());
        }
        return working;
    }

    private void applyReplace(ObjectNode doc, String path, Object value) {
        var location = resolvePath(doc, path, false);
        var asJson = mapper.valueToTree(value);
        location.parent.set(location.key, asJson);
    }

    private void applyAdd(ObjectNode doc, String path, Object value) {
        var asJson = mapper.valueToTree(value);
        if (path.endsWith("/-")) {
            String parentPath = path.substring(0, path.length() - 2);
            JsonNode parent = parentPath.isEmpty() ? doc : pointerLookup(doc, parentPath);
            if (!(parent instanceof ArrayNode arr)) {
                throw new PatchRejectException("/- requires array at " + parentPath);
            }
            arr.add(asJson);
            return;
        }
        var location = resolvePath(doc, path, true);
        location.parent.set(location.key, asJson);
    }

    private void applyRemove(ObjectNode doc, String path) {
        var location = resolvePath(doc, path, false);
        location.parent.remove(location.key);
    }

    /** 把 RFC 6902 path（含 matchKey 扩展）解析为 (parent container, leaf key) 二元组，便于 set/remove. */
    private Location resolvePath(ObjectNode doc, String path, boolean allowMissing) {
        String[] segments = path.substring(1).split("/");
        JsonNode current = doc;
        for (int i = 0; i < segments.length - 1; i++) {
            current = descend(current, segments[i], false);
            if (current == null) throw new PatchRejectException("path not found: " + path);
        }
        String last = segments[segments.length - 1];
        var parsed = PatchPath.parseSegment(last);
        return switch (parsed) {
            case PatchPath.Plain p when current instanceof ObjectNode obj -> new Location(obj, p.key());
            case PatchPath.Index idx when current instanceof ArrayNode arr -> {
                if (idx.index() >= arr.size() && !allowMissing) {
                    throw new PatchRejectException("index out of bounds: " + path);
                }
                yield new Location(null, String.valueOf(idx.index())) {
                    // ArrayNode 处理通过 indexed wrapper（实现略；实际需要 ArrayLocation 子类）
                };
            }
            case PatchPath.MatchKey mk when current instanceof ArrayNode arr -> {
                int idx = findByMatchKey(arr, mk.matchKey(), mk.matchValue());
                if (idx < 0) throw new PatchRejectException("not found: " + path);
                yield new Location(null, String.valueOf(idx));
            }
            default -> throw new PatchRejectException("invalid path segment: " + last);
        };
    }

    private JsonNode descend(JsonNode current, String segment, boolean allowMissing) {
        var parsed = PatchPath.parseSegment(segment);
        return switch (parsed) {
            case PatchPath.Plain p -> current.path(p.key());
            case PatchPath.Index idx -> current.path(idx.index());
            case PatchPath.MatchKey mk when current instanceof ArrayNode arr -> {
                int idx = findByMatchKey(arr, mk.matchKey(), mk.matchValue());
                yield idx < 0 ? null : arr.get(idx);
            }
            default -> null;
        };
    }

    private int findByMatchKey(ArrayNode arr, String key, String value) {
        for (int i = 0; i < arr.size(); i++) {
            if (value.equals(arr.get(i).path(key).asText(null))) return i;
        }
        return -1;
    }

    private JsonNode pointerLookup(ObjectNode doc, String path) {
        JsonNode current = doc;
        for (String seg : path.substring(1).split("/")) {
            current = descend(current, seg, false);
            if (current == null) return null;
        }
        return current;
    }

    record Location(ObjectNode parent, String key) {}

    public static class PatchRejectException extends RuntimeException {
        public PatchRejectException(String msg) { super(msg); }
    }
    public static class VersionConflictException extends RuntimeException {
        public VersionConflictException(String msg) { super(msg); }
    }
}
```

> 注：上面 ArrayNode 上 set / remove 需调用 `ArrayNode.set(int, JsonNode)` / `remove(int)`，最终实现 `Location` 应区分 `ObjectLocation` 与 `ArrayLocation` 两个子类。本步骤实现时按真实 Jackson API 调整；测试覆盖三种 path 形态后 API 选择即明朗。

- [ ] **Step 4: Run tests，确认 5 个全 PASS**

Run: `cd server && mvn test -pl data-talk-application -Dtest=JsonPatchApplierTest -q`
Expected: 5 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/dashboard/JsonPatchApplier.java \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/PatchPath.java \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/JsonPatchApplierTest.java
git commit -m "feat(dashboard): RFC 6902 patch applier with matchKey path + atomicity (P1)

- Supports add/remove/replace ops only (matches existing UiPatchAction)
- /widgets[id=<id>]/... path syntax via PatchPath sealed interface
- Atomic apply with validator gate; baseVersion conflict throws specific exception

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B4: DashboardArtifactService（file_artifact 集成 + 版本管理）

**Goal:** 把 dashboard 文档持久化到 file_artifact 系统（kind=`dashboard`，路径 `~/.data-talk/dashboards/<id>.dashboard.json`），提供 promote / load / patch 三个语义。

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardIds.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/dashboard/DashboardArtifactServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.datatalk.application.fileartifact.FileArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DashboardArtifactServiceTest {

    @Autowired DashboardArtifactService service;
    @Autowired ObjectMapper mapper;

    @Test
    void promoteCreatesFileArtifactWithVersionOne() throws Exception {
        JsonNode doc = mapper.readTree("""
          { "schemaVersion": 1, "title": "test",
            "parameters": [], "widgets": [],
            "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 } }""");
        var promoted = service.promote(doc, "session_test");
        assertThat(promoted.path("id").asText()).startsWith("dash_");
        assertThat(promoted.path("version").asLong()).isEqualTo(1L);
        assertThat(promoted.path("createdAt").asLong()).isPositive();
        // load round-trip
        JsonNode loaded = service.load(promoted.path("id").asText());
        assertThat(loaded).isEqualTo(promoted);
    }

    @Test
    void patchAppliesWithBaseVersionAndBumpsVersion() throws Exception {
        JsonNode doc = mapper.readTree("""
          { "schemaVersion": 1, "title": "test", "parameters": [], "widgets": [],
            "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 } }""");
        var promoted = service.promote(doc, "session_test");
        String id = promoted.path("id").asText();
        var ops = java.util.List.of(java.util.Map.<String, Object>of(
            "op", "replace", "path", "/title", "value", "renamed"
        ));
        JsonNode patched = service.patch(id, 1L, ops);
        assertThat(patched.path("title").asText()).isEqualTo("renamed");
        assertThat(patched.path("version").asLong()).isEqualTo(2L);
    }

    @Test
    void patchWithStaleBaseVersionThrows() throws Exception {
        JsonNode doc = mapper.readTree("""
          { "schemaVersion": 1, "title": "x", "parameters": [], "widgets": [],
            "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 } }""");
        var promoted = service.promote(doc, "session_test");
        String id = promoted.path("id").asText();
        assertThatThrownBy(() -> service.patch(id, 99L, java.util.List.of()))
            .isInstanceOf(JsonPatchApplier.VersionConflictException.class);
    }
}
```

- [ ] **Step 2: Confirm failing**

Run: `cd server && mvn test -pl data-talk-application -Dtest=DashboardArtifactServiceTest -q`
Expected: COMPILE FAIL。

- [ ] **Step 3: Implement service**

`DashboardIds.java`:
```java
package com.datatalk.application.dashboard;
import java.security.SecureRandom;
public final class DashboardIds {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    public static String newDashboardId() {
        StringBuilder sb = new StringBuilder("dash_");
        for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
```

`DashboardArtifactService.java`:
```java
package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.FileArtifactKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DashboardArtifactService {

    static final long MAX_PAYLOAD_BYTES = 256L * 1024;

    private final FileArtifactService fileArtifactService;
    private final DashboardSchemaValidator validator;
    private final JsonPatchApplier applier;
    private final ObjectMapper mapper;

    public DashboardArtifactService(
        FileArtifactService fileArtifactService,
        DashboardSchemaValidator validator,
        JsonPatchApplier applier,
        ObjectMapper mapper
    ) {
        this.fileArtifactService = fileArtifactService;
        this.validator = validator;
        this.applier = applier;
        this.mapper = mapper;
    }

    public JsonNode promote(JsonNode incoming, String originSessionId) {
        ObjectNode doc = (ObjectNode) incoming.deepCopy();
        String id = DashboardIds.newDashboardId();
        long now = System.currentTimeMillis();
        doc.put("id", id);
        doc.put("version", 1L);
        doc.put("createdAt", now);
        doc.put("updatedAt", now);
        if (!doc.has("parameters")) doc.putArray("parameters");
        if (!doc.has("widgets")) doc.putArray("widgets");

        var validation = validator.validate(doc);
        if (!validation.ok()) {
            throw new JsonPatchApplier.PatchRejectException(validation.errors().toString());
        }

        byte[] bytes = mapper.writeValueAsBytes(doc);
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("dashboard payload exceeds 256 KB");
        }

        String connectionId = doc.path("defaultConnectionId").asText(null);
        fileArtifactService.create(
            id, FileArtifactKind.DASHBOARD,
            connectionId, /* sessionId */ null,
            "dashboards/" + id + ".dashboard.json", bytes,
            originSessionId
        );
        return doc;
    }

    public JsonNode load(String dashboardId) {
        byte[] bytes = fileArtifactService.readBytes(dashboardId);
        try { return mapper.readTree(bytes); }
        catch (Exception e) { throw new IllegalStateException("dashboard unreadable: " + dashboardId, e); }
    }

    public JsonNode patch(String dashboardId, long baseVersion, List<Map<String, Object>> ops) {
        JsonNode current = load(dashboardId);
        JsonNode patched = applier.apply(current, baseVersion, ops);
        byte[] bytes;
        try { bytes = mapper.writeValueAsBytes(patched); }
        catch (Exception e) { throw new IllegalStateException("encode failed", e); }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("patched dashboard exceeds 256 KB");
        }
        fileArtifactService.replaceBytes(dashboardId, bytes);
        return patched;
    }
}
```

> 注：`FileArtifactKind.DASHBOARD` enum 值需在 `data-talk-application` 模块的 `FileArtifactKind` 中追加；如已有同名 enum，加 `DASHBOARD` 一项。`FileArtifactService.create` / `readBytes` / `replaceBytes` 是已有 API（Part 1-5 已 ship）；签名以现有为准，本步骤实现时如签名不同需调整。

- [ ] **Step 4: Run tests，确认 PASS（含 promote / load / patch / stale baseVersion）**

Run: `cd server && mvn verify -pl data-talk-application -Dtest=DashboardArtifactServiceTest -q`
Expected: 3 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardIds.java \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/DashboardArtifactServiceTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactKind.java
git commit -m "feat(dashboard): DashboardArtifactService promote/load/patch with file_artifact (P1)

- Promote validates schema + assigns id/version/timestamps
- File path ~/.data-talk/dashboards/<id>.dashboard.json (not connection-scoped)
- Patch enforces baseVersion + 256 KB upper bound
- Adds FileArtifactKind.DASHBOARD enum

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B5: REST 端点 + UI Object Protocol object enum 扩展

**Goal:** 暴露 dashboard 给前端 via REST（chat fence promotion）+ via UIRouter（ui_read / ui_patch / ui_exec 类型 `dashboard`）。

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DashboardPromoteRequest.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DashboardPatchRequest.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java:35` — `object` enum 加 `dashboard`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java` — 同上
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` — 同上 + `action` 加 `dashboard.create`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardControllerTest.java`

- [ ] **Step 1: Write failing controller test**

```java
package com.datatalk.adapter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void promoteCreatesDashboardAndReturnsId() throws Exception {
        String body = """
          { "dashboard": {
              "schemaVersion": 1, "title": "demo",
              "parameters": [], "widgets": [],
              "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 }
          }, "originSessionId": "session_test" }""";
        mvc.perform(post("/api/dashboards/promote").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("dash_")))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchWith409OnStaleVersion() throws Exception {
        // promote first (略 — 用 promoted id 做 patch with baseVersion=99)
        // expect status().isConflict() and body contains "EditConflict"
    }

    @Test
    void rejectsLargePayload() throws Exception {
        StringBuilder big = new StringBuilder("{\"description\":\"");
        big.append("x".repeat(300_000));
        big.append("\",\"schemaVersion\":1,\"title\":\"x\",\"parameters\":[],\"widgets\":[]," +
                   "\"layout\":{\"engine\":\"grid\",\"cols\":12,\"rowHeight\":32,\"gap\":8}}");
        String body = "{\"dashboard\":" + big + ",\"originSessionId\":\"session_test\"}";
        mvc.perform(post("/api/dashboards/promote").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isPayloadTooLarge());
    }
}
```

- [ ] **Step 2: Confirm failing**

`mvn test -pl data-talk-adapter -Dtest=DashboardControllerTest -q` → COMPILE FAIL。

- [ ] **Step 3: Implement controller + DTOs + enum updates**

`DashboardPromoteRequest.java`:
```java
package com.datatalk.adapter.dto;
import com.fasterxml.jackson.databind.JsonNode;
public record DashboardPromoteRequest(JsonNode dashboard, String originSessionId) {}
```

`DashboardPatchRequest.java`:
```java
package com.datatalk.adapter.dto;
import java.util.List;
import java.util.Map;
public record DashboardPatchRequest(long baseVersion, List<Map<String, Object>> patches) {}
```

`DashboardController.java`:
```java
package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.DashboardPatchRequest;
import com.datatalk.adapter.dto.DashboardPromoteRequest;
import com.datatalk.application.dashboard.DashboardArtifactService;
import com.datatalk.application.dashboard.JsonPatchApplier;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardArtifactService service;

    public DashboardController(DashboardArtifactService service) { this.service = service; }

    @PostMapping("/promote")
    public ResponseEntity<JsonNode> promote(@RequestBody DashboardPromoteRequest req) {
        try {
            JsonNode result = service.promote(req.dashboard(), req.originSessionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("256 KB")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            throw e;
        }
    }

    @GetMapping("/{id}")
    public JsonNode load(@PathVariable String id) { return service.load(id); }

    @PatchMapping("/{id}")
    public ResponseEntity<JsonNode> patch(@PathVariable String id, @RequestBody DashboardPatchRequest req) {
        try {
            JsonNode patched = service.patch(id, req.baseVersion(), req.patches());
            return ResponseEntity.ok(patched);
        } catch (JsonPatchApplier.VersionConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorMarkdown("EditConflict: " + e.getMessage()));
        } catch (JsonPatchApplier.PatchRejectException e) {
            return ResponseEntity.unprocessableEntity()
                .body(errorMarkdown("PatchReject: " + e.getMessage()));
        }
    }

    private JsonNode errorMarkdown(String msg) {
        return new com.fasterxml.jackson.databind.node.ObjectMapper()
            .createObjectNode().put("error", msg);
    }
}
```

`UiPatchAction.java` 修改 `object` enum：
```java
// :35 改为：
"enum", List.of("query_editor", "er_inspector", "er_designer", "dashboard"),
```

`UiReadAction.java` 与 `UiExecAction.java` 同步加 `dashboard`；`UiExecAction` 的 `action` 描述补 `dashboard.create / dashboard.archive` 两个动词（其余 P5/P6 才上）。

- [ ] **Step 4: Run tests + 编译**

```bash
cd server && mvn compile -q && mvn test -pl data-talk-adapter -Dtest=DashboardControllerTest -q
```
Expected: 3 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DashboardController.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/Dashboard*.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/Ui{Patch,Read,Exec}Action.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardControllerTest.java
git commit -m "feat(dashboard): REST endpoints + UI Object Protocol type 'dashboard' (P1)

- POST /api/dashboards/promote · GET /api/dashboards/{id} · PATCH /api/dashboards/{id}
- 409 on stale baseVersion (EditConflict markdown body)
- 422 on schema/cross-widget validation failure
- 413 on payload > 256 KB
- UiPatchAction/UiReadAction/UiExecAction object enum extended with 'dashboard'
- UiExecAction action vocab adds dashboard.create + dashboard.archive (P1 subset)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task B6: AGENTS.md Dashboards 节

**Goal:** 让 AI 知道 ` ```dashboard ` 围栏 + ui_patch 协议 + path 寻址规则 + P1 限定的 widget 集（chart / markdown）。

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Test: 通过 `RealAgentsPromptIT` 或现有 prompt 校验测试间接确认（如有）

- [ ] **Step 1: 在 AGENTS.md "Charts" 节后追加 Dashboards 节**

新内容（追加到现有 Charts 节后）：

```markdown
## Dashboards

DataTalk 支持把多个图表 / 文本 widget 组合到一个持久化 Tab，便于跨 session 接力修改。

### 何时用 ```dashboard 围栏（首次生成）

用户说"帮我生成一个销售看板"或"做一个销售概览的报表" → 在 markdown 流式输出中写：

\```dashboard
{
  "schemaVersion": 1,
  "title": "销售看板",
  "parameters": [],
  "widgets": [
    {
      "id": "chart_w_aaaa",
      "type": "chart",
      "position": { "x": 0, "y": 0, "w": 6, "h": 8 },
      "query": { "sql": "SELECT date_trunc('day', created_at) AS d, SUM(amount) AS gmv FROM orders GROUP BY 1", "paramRefs": {} },
      "options": { "echartsOption": { ... }, "dataMapping": { "rowsAsDataset": true } }
    },
    {
      "id": "markdown_w_bbbb",
      "type": "markdown",
      "position": { "x": 6, "y": 0, "w": 6, "h": 8 },
      "options": { "text": "## 说明\n本看板覆盖 …" }
    }
  ],
  "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 }
}
\```

聊天会内联渲染骨架 → 预览；用户点工具栏"打开到工作台"才创建持久化 Tab。

### 何时用 ui_patch（后续增量改）

dashboard 已 promote 后，**不要**再写 ```dashboard 围栏复写整张。改单个字段调用 datatalk_ui_patch：

```jsonc
{
  "object": "dashboard",
  "target": "<dashboardId>",
  "baseVersion": 7,
  "ops": [
    { "op": "replace", "path": "/widgets[id=chart_w_aaaa]/query/sql",
      "value": "<新的 SQL>" }
  ]
}
```

### path 寻址约定

- widget：`/widgets[id=<widgetId>]/...`（matchKey 扩展，与 ER tab 协议同源）
- 追加 widget：`/widgets/-`（RFC 6902 标准 append；value 必须自带 id 字段）
- 不允许：`/widgets/<index>` 数字寻址

### P1 widget 集（其他 widget 类型在后续 phase 上线）

- `chart`：query 必填；options.echartsOption 是完整 ECharts option
- `markdown`：无 query；options.text 是 markdown 字符串

### 错误处理

- 409 EditConflict：dashboard 已被改过；ui_read 取最新再 patch
- 422：schema 错误；按 path 修补单个 op
- 413：payload 超 256 KB；拆分或精简

### 与 ```chart 单图围栏的关系

- 单张独立图 → 继续用 ```chart 围栏
- 多图组合 / 跨 session 复用 / 用户要在工作台改 → 用 ```dashboard 围栏
```

- [ ] **Step 2: 跑现有 prompt-related 测试确认未破坏**

```bash
cd server && mvn test -q 2>&1 | tail -20
```
Expected: 全部 PASS（AGENTS.md 改动通常不破任何测试，但需确认）。

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): add Dashboards section to AGENTS.md (P1)

Documents \`\`\`dashboard fence (first-generation) vs datatalk_ui_patch
(incremental) discipline, /widgets[id=<id>] matchKey path addressing,
P1 widget subset (chart + markdown), error codes (409/422/413).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task F1: 前端类型 + zod schema 镜像

**Goal:** 把后端 dashboard JSON Schema 在前端用 zod 重写一份，类型自动推导出 TS 类型；构建期跑结构对齐校验。

**Files:**
- Create: `client/src/features/dashboard/types.ts`
- Create: `client/src/features/dashboard/schema.ts`
- Test: `client/src/features/dashboard/__tests__/schema.test.ts`

- [ ] **Step 1: Write failing zod schema test**

`schema.test.ts`:
```typescript
import { describe, expect, it } from 'vitest'
import { dashboardSchema } from '../schema'

describe('dashboardSchema', () => {
  it('accepts a minimal valid dashboard', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 1, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(true)
  })

  it('rejects schemaVersion != 1', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 2, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('rejects non-https image src', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 1, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [{
        id: 'image_w_aaaa', type: 'image',
        position: { x: 0, y: 0, w: 4, h: 4 },
        options: { src: 'http://insecure', alt: 'x', fit: 'cover' },
      }],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })
})
```

- [ ] **Step 2: Confirm failing**

Run: `cd client && npx vitest run src/features/dashboard/__tests__/schema.test.ts`
Expected: FAIL（schema not defined）。

- [ ] **Step 3: Implement schema + types**

`schema.ts`:
```typescript
import { z } from 'zod'

const gridPosition = z.object({
  x: z.number().int().min(0).max(11),
  y: z.number().int().min(0),
  w: z.number().int().min(1).max(12),
  h: z.number().int().min(1),
  z: z.number().int().nullable().optional(),
})

const widgetQuery = z.object({
  connectionId: z.string().nullable().optional(),
  sql: z.string(),
  paramRefs: z.record(z.string(), z.string()),
})

const parameterDef = z.object({
  id: z.string().regex(/^(global|local):[a-zA-Z0-9_:]+$/),
  scope: z.enum(['global', 'local']),
  ownerWidgetId: z.string().nullable().optional(),
  name: z.string(),
  type: z.enum(['date', 'date_range', 'string', 'number', 'string_list']),
  default: z.unknown(),
})

const chartOptions = z.object({
  title: z.string().optional(),
  echartsOption: z.record(z.string(), z.unknown()),
  dataMapping: z.object({ rowsAsDataset: z.literal(true) }),
  emphasis: z.enum(['cobalt', 'amber', 'neutral']).optional(),
})

const markdownOptions = z.object({
  text: z.string().max(32768),
  textAlign: z.enum(['left', 'center', 'right']).optional(),
})

const imageOptions = z.object({
  src: z.string().url().refine((u) => u.startsWith('https://'), 'image src must be https://'),
  alt: z.string().min(1),
  fit: z.enum(['cover', 'contain', 'fill']),
})

const widget = z.object({
  id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9]{4,16}$/),
  type: z.enum(['chart', 'kpi', 'table', 'markdown', 'filter', 'section', 'divider', 'image']),
  position: gridPosition,
  parameters: z.array(parameterDef).optional(),
  query: widgetQuery.optional(),
  options: z.union([chartOptions, markdownOptions, imageOptions, z.object({}).passthrough()]),
})

const gridLayout = z.object({
  engine: z.literal('grid'),
  cols: z.literal(12),
  rowHeight: z.number().int().min(8).max(128),
  gap: z.number().int().min(0).max(32),
})

export const dashboardSchema = z.object({
  schemaVersion: z.literal(1),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  title: z.string().min(1).max(256),
  description: z.string().max(32768).optional(),
  defaultConnectionId: z.string().nullable().optional(),
  parameters: z.array(parameterDef),
  widgets: z.array(widget),
  layout: gridLayout,
  version: z.number().int().min(1),
  createdAt: z.number().int().min(0),
  updatedAt: z.number().int().min(0),
})

export type Dashboard = z.infer<typeof dashboardSchema>
export type Widget = z.infer<typeof widget>
export type WidgetQuery = z.infer<typeof widgetQuery>
export type GridPosition = z.infer<typeof gridPosition>
export type GridLayout = z.infer<typeof gridLayout>
export type ParameterDef = z.infer<typeof parameterDef>
```

`types.ts`：仅 re-export 给 import 路径整洁
```typescript
export type { Dashboard, Widget, WidgetQuery, GridPosition, GridLayout, ParameterDef } from './schema'
```

- [ ] **Step 4: Run tests**

`cd client && npx vitest run src/features/dashboard/__tests__/schema.test.ts`
Expected: 3 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add client/src/features/dashboard/types.ts client/src/features/dashboard/schema.ts \
        client/src/features/dashboard/__tests__/schema.test.ts
git commit -m "feat(dashboard): zod schema + TS types mirror of backend dashboard schema (P1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task F2: LayoutEngine 接口 + GridLayoutEngine 实现

**Goal:** 抽象布局引擎接口（v1 仅 grid，v2 留 canvas 接入位）；GridLayoutEngine 封装 react-grid-layout，提供包装方法。

**Files:**
- Modify: `client/package.json` — 加 `react-grid-layout` + `@types/react-grid-layout`
- Create: `client/src/features/dashboard/engines/layout-engine.ts`
- Create: `client/src/features/dashboard/engines/grid-layout-engine.ts`
- Test: `client/src/features/dashboard/engines/__tests__/grid-layout-engine.test.ts`

- [ ] **Step 1: 安装依赖**

```bash
cd client && npm i react-grid-layout @types/react-grid-layout
```
预期：`package.json` 新增 `"react-grid-layout": "^1.5.0"` 与 `"@types/react-grid-layout": "^1.3.5"`（具体版本以 npm 当时 latest 为准）。

- [ ] **Step 2: Write failing tests**

`grid-layout-engine.test.ts`:
```typescript
import { describe, expect, it } from 'vitest'
import { GridLayoutEngine } from '../grid-layout-engine'
import type { Widget } from '../../schema'

const engine = new GridLayoutEngine()
const w = (id: string, x: number, y: number, w: number, h: number): Widget => ({
  id, type: 'chart',
  position: { x, y, w, h },
  options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } },
  query: { sql: 'select 1', paramRefs: {} },
})

describe('GridLayoutEngine.validate', () => {
  it('flags overlap', () => {
    const result = engine.validate(
      { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      [w('chart_w_aaaa', 0, 0, 6, 4), w('chart_w_bbbb', 3, 1, 6, 4)],
    )
    expect(result.errors[0].message).toContain('overlap')
  })

  it('passes non-overlapping layout', () => {
    const result = engine.validate(
      { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      [w('chart_w_aaaa', 0, 0, 6, 4), w('chart_w_bbbb', 6, 0, 6, 4)],
    )
    expect(result.errors).toHaveLength(0)
  })
})

describe('GridLayoutEngine.defaultPosition', () => {
  it('chart default w=6 h=8', () => {
    expect(engine.defaultPosition('chart')).toMatchObject({ w: 6, h: 8 })
  })
  it('markdown default w=12 h=4', () => {
    expect(engine.defaultPosition('markdown')).toMatchObject({ w: 12, h: 4 })
  })
})

describe('GridLayoutEngine.autoPackPosition', () => {
  it('places new widget at first available row gap', () => {
    const existing = [w('chart_w_aaaa', 0, 0, 6, 4)]
    // request width=6 → fit at x=6,y=0
    const next = engine.autoPackPosition({ w: 6, h: 4 }, existing)
    expect(next).toEqual({ x: 6, y: 0 })
  })

  it('wraps to next row when no horizontal space', () => {
    const existing = [w('chart_w_aaaa', 0, 0, 12, 4)]
    const next = engine.autoPackPosition({ w: 6, h: 4 }, existing)
    expect(next).toEqual({ x: 0, y: 4 })
  })
})
```

- [ ] **Step 3: Confirm failing**

`cd client && npx vitest run src/features/dashboard/engines/__tests__/grid-layout-engine.test.ts`
Expected: FAIL。

- [ ] **Step 4: Implement engine**

`layout-engine.ts`:
```typescript
import type { Widget, GridLayout, GridPosition } from '../schema'

export interface ContainerSize { width: number; height: number }
export interface RenderedPosition {
  widgetId: string
  rect: { left: number; top: number; width: number; height: number; zIndex: number }
}
export interface ValidationResult { errors: { path: string; message: string }[] }

export interface LayoutEngine<L = GridLayout> {
  kind: string
  validate(layout: L, widgets: Widget[]): ValidationResult
  pack(widgets: Widget[], container: ContainerSize): RenderedPosition[]
  defaultPosition(widgetType: Widget['type']): GridPosition
  autoPackPosition(size: { w: number; h: number }, existing: Widget[]): { x: number; y: number }
}
```

`grid-layout-engine.ts`:
```typescript
import type { LayoutEngine, RenderedPosition, ValidationResult, ContainerSize } from './layout-engine'
import type { Widget, GridLayout, GridPosition } from '../schema'

const DEFAULT_SIZE_BY_TYPE: Record<Widget['type'], { w: number; h: number }> = {
  chart: { w: 6, h: 8 },
  kpi: { w: 3, h: 3 },
  table: { w: 12, h: 10 },
  markdown: { w: 12, h: 4 },
  filter: { w: 3, h: 2 },
  section: { w: 12, h: 2 },
  divider: { w: 12, h: 1 },
  image: { w: 4, h: 4 },
}

export class GridLayoutEngine implements LayoutEngine<GridLayout> {
  kind = 'grid' as const

  validate(layout: GridLayout, widgets: Widget[]): ValidationResult {
    const errors: ValidationResult['errors'] = []
    for (let i = 0; i < widgets.length; i++) {
      for (let j = i + 1; j < widgets.length; j++) {
        if (this.rectsOverlap(widgets[i].position, widgets[j].position)) {
          errors.push({
            path: `/widgets[id=${widgets[j].id}]/position`,
            message: `widget '${widgets[j].id}' overlaps with '${widgets[i].id}'`,
          })
        }
      }
      const z = widgets[i].position.z ?? 0
      if (z !== 0) {
        errors.push({
          path: `/widgets[id=${widgets[i].id}]/position/z`,
          message: 'z must be 0 in grid mode',
        })
      }
    }
    return { errors }
  }

  pack(widgets: Widget[], container: ContainerSize): RenderedPosition[] {
    // 仅供首屏渲染前的快速 layout 估算；真实渲染由 react-grid-layout 接管。
    const cellWidth = container.width / 12
    const rowHeight = 32
    return widgets.map((w) => ({
      widgetId: w.id,
      rect: {
        left: w.position.x * cellWidth,
        top: w.position.y * rowHeight,
        width: w.position.w * cellWidth,
        height: w.position.h * rowHeight,
        zIndex: w.position.z ?? 0,
      },
    }))
  }

  defaultPosition(widgetType: Widget['type']): GridPosition {
    const size = DEFAULT_SIZE_BY_TYPE[widgetType]
    return { x: 0, y: 0, ...size }
  }

  autoPackPosition(
    size: { w: number; h: number },
    existing: Widget[],
  ): { x: number; y: number } {
    const occupied = new Set<string>()
    for (const w of existing) {
      for (let dy = 0; dy < w.position.h; dy++) {
        for (let dx = 0; dx < w.position.w; dx++) {
          occupied.add(`${w.position.x + dx},${w.position.y + dy}`)
        }
      }
    }
    const maxY = existing.reduce(
      (m, w) => Math.max(m, w.position.y + w.position.h),
      0,
    )
    for (let y = 0; y <= maxY; y++) {
      for (let x = 0; x + size.w <= 12; x++) {
        let fits = true
        for (let dy = 0; dy < size.h && fits; dy++) {
          for (let dx = 0; dx < size.w && fits; dx++) {
            if (occupied.has(`${x + dx},${y + dy}`)) fits = false
          }
        }
        if (fits) return { x, y }
      }
    }
    return { x: 0, y: maxY }
  }

  private rectsOverlap(a: GridPosition, b: GridPosition): boolean {
    return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
  }
}
```

- [ ] **Step 5: Run tests + commit**

`cd client && npx vitest run src/features/dashboard/engines/`
Expected: 6 tests PASS。

```bash
git add client/package.json client/package-lock.json \
        client/src/features/dashboard/engines/
git commit -m "feat(dashboard): LayoutEngine interface + GridLayoutEngine v1 (P1)

- 12-col grid + overlap detection + z=0 invariant + auto-pack new widget position
- react-grid-layout dependency added; canvas v2 engine slot reserved

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task F3: dashboard-tabs-store (Zustand)

**Goal:** Tab 内的 dashboard 内存态：hydrate（从 file_artifact 加载）+ 修改（应用 ui_patch ops）+ 当前 tab 的 working copy（未保存改动）。

**Files:**
- Create: `client/src/features/dashboard/stores/dashboard-tabs-store.ts`
- Test: `client/src/features/dashboard/stores/__tests__/dashboard-tabs-store.test.ts`

- [ ] **Step 1: Write failing test**

```typescript
import { describe, expect, it, beforeEach } from 'vitest'
import { useDashboardTabsStore } from '../dashboard-tabs-store'
import type { Dashboard } from '../../schema'

const sampleDashboard: Dashboard = {
  schemaVersion: 1, id: 'dash_aaaa', title: 'x',
  parameters: [], widgets: [
    { id: 'chart_w_aaaa', type: 'chart',
      position: { x: 0, y: 0, w: 6, h: 4 },
      query: { sql: 'select 1', paramRefs: {} },
      options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } } },
  ],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
  version: 1, createdAt: 0, updatedAt: 0,
}

describe('dashboardTabsStore', () => {
  beforeEach(() => useDashboardTabsStore.setState({ tabs: new Map() }))

  it('hydrates a tab', () => {
    useDashboardTabsStore.getState().hydrateTab('tab_1', sampleDashboard)
    expect(useDashboardTabsStore.getState().tabs.get('tab_1')?.dashboard.title).toBe('x')
  })

  it('applies replace patch op', () => {
    useDashboardTabsStore.getState().hydrateTab('tab_1', sampleDashboard)
    useDashboardTabsStore.getState().applyPatch('tab_1', [
      { op: 'replace', path: '/title', value: 'renamed' },
    ])
    expect(useDashboardTabsStore.getState().tabs.get('tab_1')?.dashboard.title).toBe('renamed')
  })

  it('applies replace via matchKey path', () => {
    useDashboardTabsStore.getState().hydrateTab('tab_1', sampleDashboard)
    useDashboardTabsStore.getState().applyPatch('tab_1', [
      { op: 'replace', path: '/widgets[id=chart_w_aaaa]/query/sql', value: 'select 2' },
    ])
    const w = useDashboardTabsStore.getState().tabs.get('tab_1')?.dashboard.widgets[0]
    expect(w?.query?.sql).toBe('select 2')
  })

  it('applies add via /widgets/-', () => {
    useDashboardTabsStore.getState().hydrateTab('tab_1', sampleDashboard)
    useDashboardTabsStore.getState().applyPatch('tab_1', [
      { op: 'add', path: '/widgets/-', value: {
        id: 'markdown_w_bbbb', type: 'markdown',
        position: { x: 6, y: 0, w: 6, h: 4 },
        options: { text: '# hello' },
      }},
    ])
    expect(useDashboardTabsStore.getState().tabs.get('tab_1')?.dashboard.widgets).toHaveLength(2)
  })
})
```

- [ ] **Step 2: Confirm failing**

`cd client && npx vitest run src/features/dashboard/stores/`
Expected: FAIL（store not defined）。

- [ ] **Step 3: Implement store**

`dashboard-tabs-store.ts`:
```typescript
import { create } from 'zustand'
import type { Dashboard } from '../schema'
import { applyJsonPatch, type JsonPatchOp } from '@/services/ui-router/jsonPatch'

interface TabState {
  dashboard: Dashboard
  dirtySinceVersion: number
}

interface DashboardTabsState {
  tabs: Map<string, TabState>
  hydrateTab: (tabId: string, dashboard: Dashboard) => void
  applyPatch: (tabId: string, ops: JsonPatchOp[]) => void
  drop: (tabId: string) => void
}

export const useDashboardTabsStore = create<DashboardTabsState>((set, get) => ({
  tabs: new Map(),

  hydrateTab(tabId, dashboard) {
    const next = new Map(get().tabs)
    next.set(tabId, { dashboard, dirtySinceVersion: dashboard.version })
    set({ tabs: next })
  },

  applyPatch(tabId, ops) {
    const tab = get().tabs.get(tabId)
    if (!tab) return
    const patched = applyJsonPatch(tab.dashboard, ops) as Dashboard
    const next = new Map(get().tabs)
    next.set(tabId, { dashboard: patched, dirtySinceVersion: tab.dirtySinceVersion })
    set({ tabs: next })
  },

  drop(tabId) {
    const next = new Map(get().tabs)
    next.delete(tabId)
    set({ tabs: next })
  },
}))
```

> 注：依赖现有 `client/src/services/ui-router/jsonPatch.ts` 的 `applyJsonPatch`，它已支持 RFC 6902 + matchKey 扩展（pathResolver 已用）。如果 jsonPatch.ts 不导出该函数，本步骤需补实现 — 实际跑测时确认。

- [ ] **Step 4: Run tests**

Expected: 4 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add client/src/features/dashboard/stores/
git commit -m "feat(dashboard): Zustand dashboard-tabs-store with patch apply (P1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task F4: ChartWidget + MarkdownWidget renderers

**Goal:** P1 两个 widget renderer，用统一 widget-shell 包外（标题 / loading / error），内部分别复用 chart fence 的 ChartRenderer 与 chat 的 markdown 渲染器。

**Files:**
- Create: `client/src/features/dashboard/widgets/widget-shell.tsx`
- Create: `client/src/features/dashboard/widgets/chart-widget.tsx`
- Create: `client/src/features/dashboard/widgets/markdown-widget.tsx`
- Test: `client/src/features/dashboard/widgets/__tests__/chart-widget.test.tsx`
- Test: `client/src/features/dashboard/widgets/__tests__/markdown-widget.test.tsx`

- [ ] **Step 1: Write failing tests**

`chart-widget.test.tsx`:
```typescript
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { ChartWidget } from '../chart-widget'
import type { Widget } from '../../schema'

const widget: Widget = {
  id: 'chart_w_aaaa', type: 'chart',
  position: { x: 0, y: 0, w: 6, h: 4 },
  query: { sql: 'select 1', paramRefs: {} },
  options: {
    echartsOption: { series: [{ type: 'bar', data: [1, 2, 3] }] },
    dataMapping: { rowsAsDataset: true },
  },
}

describe('ChartWidget', () => {
  it('renders shell + ChartRenderer container', () => {
    const { container } = render(<ChartWidget widget={widget} />)
    expect(container.querySelector('[data-component="dashboard-widget-shell"]')).toBeInTheDocument()
    expect(container.querySelector('[data-testid="chart-canvas-host"]')).toBeInTheDocument()
  })

  it('shows skeleton when loading=true', () => {
    const { container } = render(<ChartWidget widget={widget} loading />)
    expect(container.querySelector('[data-testid="chart-skeleton"]')).toBeInTheDocument()
  })

  it('shows error card when error provided', () => {
    const { container } = render(<ChartWidget widget={widget} error="boom" />)
    expect(container.textContent).toContain('boom')
  })
})
```

`markdown-widget.test.tsx`:
```typescript
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { MarkdownWidget } from '../markdown-widget'

const widget = {
  id: 'markdown_w_aaaa', type: 'markdown' as const,
  position: { x: 0, y: 0, w: 6, h: 4 },
  options: { text: '# Hello' },
}

describe('MarkdownWidget', () => {
  it('renders markdown text', () => {
    const { container } = render(<MarkdownWidget widget={widget} />)
    expect(container.querySelector('h1')?.textContent).toBe('Hello')
  })

  it('does not render embedded ```chart fences', () => {
    const w = { ...widget, options: { text: '\`\`\`chart\n{}\n\`\`\`' } }
    const { container } = render(<MarkdownWidget widget={w} />)
    expect(container.querySelector('[data-component="chart-block"]')).toBeNull()
  })
})
```

- [ ] **Step 2: Confirm failing**

`cd client && npx vitest run src/features/dashboard/widgets/`
Expected: FAIL。

- [ ] **Step 3: Implement widgets**

`widget-shell.tsx`:
```typescript
import type { ReactNode } from 'react'

interface Props {
  title?: string
  children: ReactNode
  toolbar?: ReactNode
  selected?: boolean
}

export function WidgetShell({ title, children, toolbar, selected }: Props) {
  return (
    <div
      data-component="dashboard-widget-shell"
      className={[
        'flex h-full w-full flex-col overflow-hidden rounded-md border bg-[var(--dt-bg-canvas)]',
        selected
          ? 'border-[var(--dt-accent-primary)]'
          : 'border-[var(--dt-border-subtle)]',
      ].join(' ')}
    >
      {(title || toolbar) && (
        <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-3 py-1.5">
          <span className="text-[13px] leading-[18px] text-[var(--dt-text-muted)]">{title}</span>
          {toolbar}
        </div>
      )}
      <div className="min-h-0 flex-1 overflow-hidden p-3">{children}</div>
    </div>
  )
}
```

`chart-widget.tsx`:
```typescript
import type { Widget } from '../schema'
import { WidgetShell } from './widget-shell'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'

interface Props {
  widget: Widget & { options: { echartsOption: Record<string, unknown>; title?: string } }
  loading?: boolean
  error?: string
}

export function ChartWidget({ widget, loading, error }: Props) {
  if (error) {
    return (
      <WidgetShell title={widget.options.title ?? widget.id}>
        <div className="rounded border border-[var(--dt-status-danger)] bg-[var(--dt-status-danger-surface)] p-3 text-[13px] text-[var(--dt-status-danger)]">
          {error}
        </div>
      </WidgetShell>
    )
  }
  if (loading) {
    return (
      <WidgetShell title={widget.options.title ?? widget.id}>
        <div data-testid="chart-skeleton" className="h-full w-full animate-pulse bg-[var(--dt-bg-subtle)]" />
      </WidgetShell>
    )
  }
  return (
    <WidgetShell title={widget.options.title ?? widget.id}>
      <div data-testid="chart-canvas-host" className="h-full w-full">
        <ChartRenderer option={widget.options.echartsOption} />
      </div>
    </WidgetShell>
  )
}
```

`markdown-widget.tsx`:
```typescript
import type { Widget } from '../schema'
import { WidgetShell } from './widget-shell'
import { renderMarkdown } from '@/features/chat/components/markdown/markdown'

interface Props {
  widget: Widget & { options: { text: string; textAlign?: 'left' | 'center' | 'right' } }
}

export function MarkdownWidget({ widget }: Props) {
  return (
    <WidgetShell>
      <div
        className={`prose prose-sm max-w-none text-${widget.options.textAlign ?? 'left'}`}
        dangerouslySetInnerHTML={{
          __html: renderMarkdown(widget.options.text, {
            excludeBlockDecorators: ['chart', 'dashboard'],
          }),
        }}
      />
    </WidgetShell>
  )
}
```

> 注：`renderMarkdown` 的签名按 `client/src/features/chat/components/markdown/markdown.tsx` 现有 export 调整；如未导出 `excludeBlockDecorators` 选项，需在 markdown.tsx 加该参数（仅一行：透传给 decorator 跳过 type 列表）。

- [ ] **Step 4: Run tests**

Expected: 5 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add client/src/features/dashboard/widgets/
git commit -m "feat(dashboard): WidgetShell + ChartWidget + MarkdownWidget renderers (P1)

- WidgetShell unified outer chrome (title bar, border, padding)
- ChartWidget reuses chart-renderer.tsx + chart-theme.ts (no new deps)
- MarkdownWidget reuses chat markdown renderer with excludeBlockDecorators=[chart,dashboard]

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task F5: DashboardCanvas（栅格 + widget 渲染 + 工具栏）

**Goal:** Tab 内容主体：左侧编辑模式工具栏 + 顶部标题栏 + react-grid-layout 渲染 widget 集 + 拖拽改 layout 后调 ui_patch。

**Files:**
- Create: `client/src/features/dashboard/dashboard-canvas.tsx`
- Test: `client/src/features/dashboard/__tests__/dashboard-canvas.test.tsx`

- [ ] **Step 1: Write failing test**

```typescript
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DashboardCanvas } from '../dashboard-canvas'
import { useDashboardTabsStore } from '../stores/dashboard-tabs-store'
import type { Dashboard } from '../schema'

const sampleDashboard: Dashboard = {
  schemaVersion: 1, id: 'dash_aaaa', title: 'demo',
  parameters: [],
  widgets: [
    { id: 'chart_w_aaaa', type: 'chart',
      position: { x: 0, y: 0, w: 6, h: 4 },
      query: { sql: 'select 1', paramRefs: {} },
      options: { echartsOption: {}, dataMapping: { rowsAsDataset: true } } },
    { id: 'markdown_w_aaaa', type: 'markdown',
      position: { x: 6, y: 0, w: 6, h: 4 },
      options: { text: '# hello' } },
  ],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
  version: 1, createdAt: 0, updatedAt: 0,
}

describe('DashboardCanvas', () => {
  it('renders all widgets', () => {
    useDashboardTabsStore.getState().hydrateTab('tab_1', sampleDashboard)
    render(<DashboardCanvas tabId="tab_1" mode="viewer" />)
    expect(screen.getByText('demo')).toBeInTheDocument()
    expect(screen.getAllByTestId(/^dashboard-widget-/)).toHaveLength(2)
  })
})
```

- [ ] **Step 2-5: Implement / Run / Commit**

`dashboard-canvas.tsx`:
```typescript
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { ChartWidget } from './widgets/chart-widget'
import { MarkdownWidget } from './widgets/markdown-widget'
import { Responsive, WidthProvider } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

const ResponsiveGridLayout = WidthProvider(Responsive)

interface Props {
  tabId: string
  mode: 'viewer' | 'editor'
}

export function DashboardCanvas({ tabId, mode }: Props) {
  const tab = useDashboardTabsStore((s) => s.tabs.get(tabId))
  if (!tab) return null
  const { dashboard } = tab

  const layouts = {
    lg: dashboard.widgets.map((w) => ({
      i: w.id, x: w.position.x, y: w.position.y, w: w.position.w, h: w.position.h,
      static: mode === 'viewer',
    })),
  }

  return (
    <div className="flex h-full w-full flex-col bg-[var(--dt-bg-canvas)]">
      <div className="border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-6 py-3">
        <h1 className="text-[20px] font-semibold leading-[28px] text-[var(--dt-text-strong)]">
          {dashboard.title}
        </h1>
      </div>
      <div className="flex-1 overflow-auto p-6">
        <ResponsiveGridLayout
          className="layout"
          layouts={layouts}
          breakpoints={{ lg: 1024, md: 768, sm: 0 }}
          cols={{ lg: 12, md: 6, sm: 1 }}
          rowHeight={dashboard.layout.rowHeight}
          margin={[dashboard.layout.gap, dashboard.layout.gap]}
          isDraggable={mode === 'editor'}
          isResizable={mode === 'editor'}
          compactType="vertical"
        >
          {dashboard.widgets.map((widget) => (
            <div key={widget.id} data-testid={`dashboard-widget-${widget.id}`}>
              {widget.type === 'chart' && <ChartWidget widget={widget as any} />}
              {widget.type === 'markdown' && <MarkdownWidget widget={widget as any} />}
            </div>
          ))}
        </ResponsiveGridLayout>
      </div>
    </div>
  )
}
```

Tests + commit (`feat(dashboard): DashboardCanvas with react-grid-layout responsive (P1)`)。

---

### Task F6: DashboardTab + tab-type-registry 注册

**Goal:** Stage Tab 内容外壳：viewer ⇄ editor 模式切换、加载 file_artifact、注册 `dashboard` 到 `tab-type-registry`，支持 ui_find 全文检索。

**Files:**
- Create: `client/src/features/dashboard/dashboard-tab.tsx`
- Modify: `client/src/features/stage/registry/tab-type-registry.ts`
- Modify: `client/src/i18n/messages.ts` — 加 `tabType.dashboard` / `tabType.dashboard.short` / 工具栏 i18n
- Test: `client/src/features/stage/registry/__tests__/tab-type-registry.test.ts` — 加 dashboard case

- [ ] **Step 1: Tests first**

新增 case 到 `tab-type-registry.test.ts`:
```typescript
it('extracts dashboard content for FTS', () => {
  const desc = TAB_TYPE_REGISTRY.dashboard
  const content = desc.extractContent({
    title: '销售看板',
    description: '本看板覆盖每日 GMV',
    widgets: [
      { id: 'chart_w_aaaa', type: 'chart',
        options: { title: '上周趋势' },
        query: { sql: 'SELECT * FROM orders', paramRefs: {} } },
    ],
    parameters: [{ id: 'global:date', name: 'date', scope: 'global', type: 'date', default: null }],
  })
  expect(content).toContain('销售看板')
  expect(content).toContain('上周趋势')
  expect(content).toContain('SELECT * FROM orders')
  expect(content).toContain('date')
})

it('truncates extractContent at 4 KB', () => {
  const desc = TAB_TYPE_REGISTRY.dashboard
  const longSql = 'SELECT '.repeat(2000)  // ~14 KB
  const content = desc.extractContent({
    title: 't', widgets: [{
      id: 'chart_w_aaaa', type: 'chart',
      options: { title: 'titleA' },
      query: { sql: longSql, paramRefs: {} },
    }],
  })
  expect(content.length).toBeLessThanOrEqual(4096)
  expect(content).toContain('t')
  expect(content).toContain('titleA')   // 高优先字段保留
})
```

- [ ] **Step 2-5: Implement / Run / Commit**

`tab-type-registry.ts` 加：
```typescript
import { LayoutDashboardIcon } from 'lucide-react'   // 或 GaugeIcon
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import type { Dashboard } from '@/features/dashboard/schema'

// ... existing TAB_TYPE_REGISTRY
dashboard: {
  type: 'dashboard',
  persistent: true,
  scope: 'workspace',
  payloadSource: 'stage_tab',
  icon: LayoutDashboardIcon,
  labelKey: 'tabType.dashboard',
  extractContent: (p) => {
    const doc = p as Dashboard | { fileArtifactId?: string } | null | undefined
    if (!doc || !('title' in doc)) return ''
    const HIGH_PRIORITY: string[] = []
    HIGH_PRIORITY.push(doc.title ?? '')
    for (const w of doc.widgets ?? []) {
      const opts = (w.options ?? {}) as { title?: string; label?: string }
      if (opts.title) HIGH_PRIORITY.push(opts.title)
      if (opts.label) HIGH_PRIORITY.push(opts.label)
    }
    for (const p of doc.parameters ?? []) HIGH_PRIORITY.push(p.name)
    let acc = HIGH_PRIORITY.filter(Boolean).join('\n')
    const remaining = 4096 - acc.length
    if (remaining > 0) {
      const tail: string[] = []
      if (doc.description) tail.push(doc.description)
      for (const w of doc.widgets ?? []) {
        if (w.query?.sql) tail.push(w.query.sql)
        if ((w.options as any)?.text) tail.push((w.options as any).text)
      }
      const tailJoined = tail.join('\n').slice(0, remaining)
      acc = acc + '\n' + tailJoined
    }
    return acc.slice(0, 4096)
  },
  rehydrate: (tabId, p) => {
    const payload = p as { fileArtifactId?: string; displayTitle?: string } | Dashboard
    if ('fileArtifactId' in payload && payload.fileArtifactId) {
      // 实际 hydrate 由 DashboardTab 组件在 mount 时通过 fetch /api/dashboards/{id} 拿到 dashboard JSON 后调 hydrateTab
      // 这里仅占位 — 持久化的 stage_tab payload 只存 pointer，真正 dashboard JSON 走文件系统
    } else {
      useDashboardTabsStore.getState().hydrateTab(tabId, payload as Dashboard)
    }
  },
},
```

`dashboard-tab.tsx`:
```typescript
import { useEffect, useState } from 'react'
import { useStageStore } from '@/stores/stage-store'
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { DashboardCanvas } from './dashboard-canvas'
import { fetchDashboard } from './services/dashboard-api'

interface Props { tabId: string }

export function DashboardTab({ tabId }: Props) {
  const tab = useStageStore((s) => s.tabs.find((t) => t.tabId === tabId))
  const hydrateTab = useDashboardTabsStore((s) => s.hydrateTab)
  const [mode, setMode] = useState<'viewer' | 'editor'>('viewer')

  useEffect(() => {
    const fileArtifactId = (tab?.payload as { fileArtifactId?: string } | undefined)?.fileArtifactId
    if (!fileArtifactId) return
    fetchDashboard(fileArtifactId).then((doc) => hydrateTab(tabId, doc))
  }, [tab?.payload, tabId, hydrateTab])

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-end gap-2 border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-3 py-1.5">
        <button
          onClick={() => setMode((m) => (m === 'viewer' ? 'editor' : 'viewer'))}
          className="rounded px-2 py-1 text-[13px] text-[var(--dt-text-muted)] hover:bg-[var(--dt-interaction-hover)]"
        >
          {mode === 'viewer' ? '编辑' : '完成编辑'}
        </button>
      </div>
      <div className="min-h-0 flex-1">
        <DashboardCanvas tabId={tabId} mode={mode} />
      </div>
    </div>
  )
}
```

`services/dashboard-api.ts`:
```typescript
import type { Dashboard } from '../schema'

export async function fetchDashboard(id: string): Promise<Dashboard> {
  const r = await fetch(`/api/dashboards/${encodeURIComponent(id)}`)
  if (!r.ok) throw new Error(`fetchDashboard ${r.status}`)
  return r.json()
}

export async function promoteDashboard(dashboard: unknown, originSessionId: string): Promise<Dashboard> {
  const r = await fetch('/api/dashboards/promote', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dashboard, originSessionId }),
  })
  if (r.status === 413) throw new Error('Dashboard payload exceeds 256 KB')
  if (!r.ok) throw new Error(`promote failed: ${r.status}`)
  return r.json()
}

export async function patchDashboard(id: string, baseVersion: number, patches: unknown[]): Promise<Dashboard> {
  const r = await fetch(`/api/dashboards/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ baseVersion, patches }),
  })
  if (r.status === 409) throw new Error('EditConflict')
  if (!r.ok) throw new Error(`patch failed: ${r.status}`)
  return r.json()
}
```

i18n keys 加到 `client/src/i18n/messages.ts`：
```typescript
'tabType.dashboard': '看板',
'tabType.dashboard.short': '看板',
'dashboard.editorMode': '编辑',
'dashboard.viewerMode': '完成编辑',
```

Tests pass + commit (`feat(dashboard): DashboardTab + tab-type-registry registration with FTS extraction (P1)`)。

---

### Task F7: ```dashboard chat 围栏 + decorator + promote 按钮

**Goal:** AI 在聊天里写 ` ```dashboard ` 围栏 → 内联渲染 streaming 骨架 / preview / error 三态 → "打开到工作台" 按钮调 promoteDashboard 创建 file_artifact + Stage Tab。

**Files:**
- Create: `client/src/features/chat/components/markdown/dashboard-block.tsx`
- Create: `client/src/features/chat/components/markdown/dashboard-block-toolbar.tsx`
- Modify: `client/src/features/chat/components/markdown/markdown.tsx` — 加 `decorateDashboardBlocks`
- Test: `client/src/features/chat/components/markdown/__tests__/dashboard-block.test.tsx`

- [ ] **Step 1: Write failing tests**

```typescript
import { describe, expect, it } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DashboardBlock } from '../dashboard-block'

const validJson = JSON.stringify({
  schemaVersion: 1, title: '销售看板', parameters: [], widgets: [],
  layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
})

describe('DashboardBlock', () => {
  it('renders skeleton when streaming', () => {
    render(<DashboardBlock json={validJson.slice(0, 30)} streaming />)
    expect(screen.getByTestId('dashboard-skeleton')).toBeInTheDocument()
  })

  it('renders preview when complete and valid', () => {
    render(<DashboardBlock json={validJson} streaming={false} />)
    expect(screen.getByText('销售看板')).toBeInTheDocument()
  })

  it('renders error card when invalid JSON', () => {
    render(<DashboardBlock json="{ broken" streaming={false} />)
    expect(screen.getByTestId('dashboard-error')).toBeInTheDocument()
  })

  it('shows "打开到工作台" button when stable', () => {
    render(<DashboardBlock json={validJson} streaming={false} />)
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2-5: Implement / Run / Commit**

`dashboard-block.tsx`：
```typescript
import { useMemo, useState } from 'react'
import { dashboardSchema } from '@/features/dashboard/schema'
import { promoteDashboard } from '@/features/dashboard/services/dashboard-api'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { showErrorToast, normalizeError } from '@/services/http-error'

interface Props { json: string; streaming: boolean }

export function DashboardBlock({ json, streaming }: Props) {
  const [busy, setBusy] = useState(false)
  const sessionId = useSessionStore((s) => s.activeSessionId)

  const parsed = useMemo(() => {
    try {
      const obj = JSON.parse(json)
      return { ok: true as const, doc: obj }
    } catch (e) {
      return { ok: false as const, error: (e as Error).message }
    }
  }, [json])

  if (streaming && !parsed.ok) {
    return (
      <div data-testid="dashboard-skeleton" className="my-2 h-[180px] rounded-lg border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)] p-4">
        <div className="text-[13px] text-[var(--dt-text-muted)]">正在生成看板…</div>
      </div>
    )
  }

  if (!parsed.ok) {
    return (
      <div data-testid="dashboard-error" className="my-2 rounded-lg border border-[var(--dt-status-danger)] bg-[var(--dt-status-danger-surface)] p-3 text-[13px] text-[var(--dt-status-danger)]">
        Dashboard JSON 解析失败：{parsed.error}
      </div>
    )
  }

  const validation = dashboardSchema.safeParse({
    ...parsed.doc, id: 'dash_preview', version: 0, createdAt: 0, updatedAt: 0,
  })

  return (
    <div className="my-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]">
      <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-3 py-1.5">
        <span className="text-[13px] text-[var(--dt-text-muted)]">{(parsed.doc as any).title ?? '看板'}</span>
        {!streaming && validation.success && (
          <button
            disabled={busy || !sessionId}
            onClick={async () => {
              if (!sessionId) return
              setBusy(true)
              try {
                const result = await promoteDashboard(parsed.doc, sessionId)
                useStageStore.getState().openTab({
                  type: 'dashboard',
                  title: result.title,
                  payload: { fileArtifactId: result.id, displayTitle: result.title },
                })
              } catch (e) {
                showErrorToast(normalizeError(e))
              } finally {
                setBusy(false)
              }
            }}
            className="rounded px-2 py-1 text-[13px] text-[var(--dt-accent-primary)] hover:bg-[var(--dt-interaction-hover)]"
          >
            打开到工作台
          </button>
        )}
      </div>
      <div className="bg-[var(--dt-bg-canvas)] p-4">
        {validation.success ? (
          <DashboardPreview doc={validation.data as any} />
        ) : (
          <div data-testid="dashboard-error" className="text-[13px] text-[var(--dt-status-danger)]">
            Schema validation failed: {validation.error.issues[0]?.message ?? 'unknown'}
          </div>
        )}
      </div>
    </div>
  )
}

function DashboardPreview({ doc }: { doc: { title: string; widgets: unknown[] } }) {
  return (
    <div className="text-[13px] text-[var(--dt-text-muted)]">
      预览：{doc.widgets.length} widgets · 完整渲染请打开到工作台
    </div>
  )
}
```

`markdown.tsx` 加 `decorateDashboardBlocks` —— 与现有 `decorateChartBlocks` 同构，把 ` ```dashboard ` code block 替换为 `<dashboard-block ...>` 占位 div，由 effect 中扫描 + createRoot 接管渲染（参考 chart-block.tsx 接入手法）。新增参数 `excludeBlockDecorators?: ('chart' | 'dashboard')[]` 透传给 markdown widget 用。

测试 + commit (`feat(dashboard): \`\`\`dashboard chat fence with streaming/preview/error states + promote action (P1)`)。

---

### Task F8: DashboardAdapter + UIRouter 接入

**Goal:** 把 dashboard Tab 暴露给 ui_read / ui_patch / ui_exec，使 AI 能跨 session 增量修改。

**Files:**
- Create: `client/src/features/dashboard/adapters/DashboardAdapter.ts`
- Create: `client/src/features/dashboard/adapters/__tests__/DashboardAdapter.test.ts`
- Modify: 注册位置（参考现有 `ErDesignerAdapter` / `WorkspaceAdapter` 注册），加 `dashboard` 到 UIRouter

- [ ] **Step 1: Write failing test**

参考 `ErDesignerAdapter.test.ts` 结构。验证：
- read 返回当前 tab 的 dashboard JSON + summary
- patch（replace /title）返回 newVersion + 旧版本 conflict 报错
- exec dashboard.create 创建新 Tab

- [ ] **Step 2-5: Implement / Run / Commit**

`DashboardAdapter.ts`（结构骨架）：
```typescript
import type { ActionDef, ExecResult, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { patchDashboard, promoteDashboard } from '@/features/dashboard/services/dashboard-api'

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/title', ops: ['replace'] },
  { pathPattern: '/description', ops: ['replace'] },
  { pathPattern: '/defaultConnectionId', ops: ['replace'] },
  { pathPattern: '/widgets/-', ops: ['add'] },
  { pathPattern: '/widgets[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/widgets[id=<id>]/position', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/options', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/query', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/query/sql', ops: ['replace'] },
  { pathPattern: '/widgets[id=<id>]/query/paramRefs', ops: ['replace'] },
  { pathPattern: '/layout', ops: ['replace'] },
]

const ACTIONS: ActionDef[] = [
  { name: 'create', description: 'Create a new empty dashboard',
    paramsSchema: { type: 'object', properties: { title: { type: 'string' }, defaultConnectionId: { type: 'string' } } } },
  { name: 'archive', description: 'Archive this dashboard via file_artifact',
    paramsSchema: { type: 'object', properties: {} } },
]

export function createDashboardAdapter() {
  return {
    type: 'dashboard',
    capabilities: PATCH_CAPABILITIES,
    actions: ACTIONS,

    async read(target: string): Promise<UIObject> {
      const tab = useDashboardTabsStore.getState().tabs.get(target)
      if (!tab) throw new Error(`dashboard tab ${target} not loaded`)
      return {
        type: 'dashboard',
        objectId: tab.dashboard.id,
        version: tab.dashboard.version,
        ...tab.dashboard,
        summary: {
          widgetCount: tab.dashboard.widgets.length,
          parameterCount: tab.dashboard.parameters.length,
        },
      }
    },

    async patch(target: string, baseVersion: number, ops: unknown[]): Promise<PatchResult> {
      const tab = useDashboardTabsStore.getState().tabs.get(target)
      if (!tab) return { ok: false, error: 'dashboard not loaded' }
      try {
        const patched = await patchDashboard(tab.dashboard.id, baseVersion, ops as any)
        useDashboardTabsStore.getState().hydrateTab(target, patched)
        return { ok: true, newVersion: patched.version }
      } catch (e) {
        return { ok: false, error: (e as Error).message }
      }
    },

    async exec(target: string | undefined, action: string, params: Record<string, unknown>): Promise<ExecResult> {
      if (action === 'create') {
        const empty = {
          schemaVersion: 1, title: (params.title as string) ?? '新看板',
          defaultConnectionId: (params.defaultConnectionId as string) ?? null,
          parameters: [], widgets: [],
          layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
        }
        const sessionId = useStageStore.getState().getOriginSessionId() ?? ''
        const promoted = await promoteDashboard(empty, sessionId)
        useStageStore.getState().openTab({
          type: 'dashboard',
          title: promoted.title,
          payload: { fileArtifactId: promoted.id, displayTitle: promoted.title },
        })
        return { success: true, data: { dashboardId: promoted.id } }
      }
      if (action === 'archive') {
        // call file_artifact archive endpoint; simplified P1
        return { success: false, error: 'archive deferred to P6 file_artifact archive UI' }
      }
      return { success: false, error: `unknown action ${action}` }
    },
  }
}
```

注册：在 UIRouter 启动注册位置（搜 `ErDesignerAdapter` 注册处的同位）加 `createDashboardAdapter()`。

测试 + commit。

---

### Task I1: 后端 Spring 集成测试（happy path）

**Goal:** End-to-end 后端：promote → load → patch → load 二次校验，包括 baseVersion 冲突分支与 schema 拒绝分支。

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/dashboard/DashboardE2ETest.java`

- [ ] **5 个步骤同前：测试覆盖 promote→load→patch→load round trip + 409 + 422 + 413**

Commit (`test(dashboard): backend end-to-end happy path + error branches (P1)`)。

---

### Task I2: 前端 Playwright E2E

**Goal:** 真实跑：用户在 chat 输入 prompt → AI 输出 ```dashboard fence → promote → Stage Tab 渲染 chart + markdown widget。

**Files:**
- Create: `client/playwright/dashboard.spec.ts` (或现有 e2e 目录)
- Test fixtures：seed dashboard JSON via mock OpenCode response

- [ ] **Step 1-5: Playwright spec + fixture + run + commit**

```typescript
import { test, expect } from '@playwright/test'

test('dashboard fence renders + promote opens Stage Tab', async ({ page }) => {
  await page.goto('/')
  // ... seed mock OpenCode to emit ```dashboard fence
  // wait for dashboard preview in chat
  await page.locator('[data-testid="dashboard-skeleton"]').waitFor({ state: 'detached' })
  await page.getByRole('button', { name: /打开到工作台/ }).click()
  // expect Stage Tab opens
  await expect(page.locator('[data-component="dashboard-widget-shell"]').first()).toBeVisible()
})
```

具体 fixture / mock 与现有 chat E2E 同源（参考 SQL Editor MCP E2E 设计中的 fixture pattern）。

按 BUG-Tracking-Gate：跑测发现任何偏差 **必须** 登 `docs/bugs/`。0 BUG 也要在最终验证日志中明确写"本次 0 BUG"。

Commit (`test(dashboard): Playwright E2E chat fence -> promote -> Stage Tab (P1)`)。

---

## Verification Gate（P1 关闭条件）

P1 close criteria — 全部 PASS 才能登 Completed：

- [ ] `cd server && mvn clean verify` 全绿（含新增 5 个 test class）
- [ ] `cd client && npx tsc --noEmit` 0 错误
- [ ] `cd client && npx vitest run` 全绿
- [ ] Playwright dashboard.spec.ts 在本地 Tauri 跑 + 在 CI 跑都通过
- [ ] 手动验收：开 client → 在 chat 让 AI 生成销售看板 → ```dashboard fence 流式 + 完整预览 → 打开到工作台 → Stage Tab 出现 chart + markdown widget → 编辑模式拖拽 widget → 保存
- [ ] AGENTS.md 中 Dashboards 节存在，AI 能根据 prompt 主动写出合法 ```dashboard fence
- [ ] 跨 session 验证：A session 创建 dashboard → 切到 B session → ui_find 能搜到 → 打开同 Tab，内容一致
- [ ] 0 BUG 或登记的 BUG 全部为 P2 及以下并已分流；P0/P1 必须 fix 才能 close

## Documentation Housekeeping（关闭时）

- [ ] 把本 plan 在 `docs/exec-plans/index.md` 从 Active 移到 Completed
- [ ] 把 P1 子项目在 `docs/product-specs/2026-05-08-report-dashboard-design.md` §13 标 Completed
- [ ] 在 `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` Task 8 状态 update：dashboard P1 shipped
- [ ] 如有新 convention（例如 layout-engine 接口模式）值得 propagate，更新 docs/DESIGN.md
- [ ] tab-type-registry 新增 dashboard type 的事实在 CLAUDE.md "Stage state" 段隐含，不需改

---

## Risks & Open Questions

- **react-grid-layout 与 React 19 兼容性**：v1.5+ 已支持 React 18，需在 install 时验证。如不兼容，降级到 react-resizable + 自实现简化布局（成本上升）
- **markdown 渲染器 excludeBlockDecorators 参数**：现有 `markdown.tsx` 可能未导出该参数，需 1 行改动。如改动副作用大，改为新建 `dashboard-markdown.tsx` 复刻一个简化版
- **JsonPatchApplier 复杂度**：matchKey path 与 ArrayNode location 的 set/remove 真实 API 需要在 Step 3 实现时按 Jackson 当前版本 ArrayNode 签名调整；测试覆盖足够即可放心调
- **FileArtifactService API 签名**：本计划假设 `create` 接受 (id, kind, connectionId, sessionId, relativePath, bytes, originSessionId)；如 Part 1-5 实际签名不同，B4 实施时按现有签名调
- **widgetId 格式 `<type>_w_<token>`**：与现有 ER 不一致（ER 用 `t_xxx` / `c_xxx`），但更具自描述性。如团队偏好与 ER 一致，可改为 `w_xxx` 简化
- **Open Questions（spec §15）**：本 plan 不解决，留作 phase 之间的产品决策点

## Out of Scope（P1 不做，留 P2-P6）

- KPI / table / filter / section / divider / image widget — P2/P3/P4
- 全局 + 局部参数系统、ParameterStore、依赖图、ParameterizedSqlExecutor — P3-P4
- dashboard_revisions 表、自动快照、手动命名版本、回滚 UI、历史抽屉 — P5
- 导出 PNG / PDF、chart fence "添加到当前 Dashboard"、Files Library Dashboard 视图、connection 删除两阶段集成 — P6
- 自由画布 layout engine / auto-refresh / kiosk / theme override / iframe / fork — Out of Spec
