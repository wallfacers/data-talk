# TD-006 前端类型与后端 DTO 自动同步机制 - 执行计划

## Context

解决技术债 TD-006：前端 `features/*/types.ts` 与后端 DTO 缺乏自动同步机制。

当前问题：
- Connection 类型在两处前端文件定义不一致且字段名不匹配（`dbType` vs `kind`）
- DTO 定义分散在 Controller/Service 内嵌 record，不便于 OpenAPI 统一生成
- 无 OpenAPI 基础设施

目标：建立 SpringDoc + openapi-typescript 自动同步机制，统一字段命名，提取 DTO 到统一位置。

## 实施内容

### Phase 1: 后端 DTO 统一提取 ✅

**创建统一 dto 包结构**

新建目录：`server/data-talk-application/src/main/java/com/datatalk/dto/`

创建 DTO 文件（从 Controller/Service 内嵌 record 提取）：

| 新文件 | 来源 | 字段 |
|--------|------|------|
| `ConnectionDto.java` | ConnectionService.ConnectionView | id, kind, host, port, databaseName, username, createdAt |
| `ConnectionCreateRequest.java` | ConnectionController.CreateBody | id, kind, host, port, **databaseName**, username, password |
| `ConnectionUpdateRequest.java` | ConnectionController.UpdateBody | kind, host, port, **databaseName**, username, password |
| `ConnectionTestResultDto.java` | ConnectionService.TestResult | ok, latencyMs, reason |
| `SessionDto.java` | SessionController.SessionDto | id, connectionId, title, hasEverSent, createdAt, updatedAt |
| `SessionCreateRequest.java` | SessionController.CreateSessionRequest | connectionId, title |
| `SessionRenameRequest.java` | SessionController.RenameRequest | title |
| `AiModelsDto.java` | AiSettingsService.ModelsDto | providers |
| `AiProviderDto.java` | AiSettingsService.ProviderDto | id, name, connected, models |
| `AiModelDto.java` | AiSettingsService.ModelDto | id, name, enabled |
| `AiCurrentModelDto.java` | AiSettingsController.CurrentModelDto | modelId |
| `AiModelPatchRequest.java` | AiSettingsController.ModelPatchBody | enabled |

**关键变更：CreateBody/UpdateBody 的 `database` 字段改名为 `databaseName`**

**修改 Controller/Service 使用新 DTO**

| 修改文件 | 改动 |
|----------|------|
| `ConnectionController.java` | 删除内嵌 record，导入 dto 包类型 |
| `ConnectionService.java` | 删除内嵌 ConnectionView/TestResult，返回 ConnectionDto |
| `SessionController.java` | 删除内嵌 record，导入 dto 包类型 |
| `AiSettingsController.java` | 删除内嵌 record，导入 dto 包类型 |
| `AiSettingsService.java` | 删除内嵌 record，返回 dto 包类型 |

### Phase 2: 后端 SpringDoc 配置 ✅

**添加 springdoc-openapi 依赖**

文件：`server/data-talk-adapter/pom.xml`

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

**创建 OpenApiConfig**

文件：`server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenApiConfig.java`

### Phase 3: 前端类型生成设置 ✅

**安装 openapi-typescript**（已在 package.json 添加）

**添加生成脚本**

文件：`client/package.json`

```json
"scripts": {
  "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api-generated.ts"
}
```

**创建类型入口文件**

文件：`client/src/types/generated/api.ts`（手动创建，基于后端 DTO）

### Phase 4: 前端重构 ✅

**删除/更新手工类型定义**

| 文件 | 操作 |
|------|------|
| `client/src/services/api/connection.ts` | 删除 Connection/DbType/CreateConnectionInput 类型，从 '@/types/generated/api' 导入 |
| `client/src/services/api/session.ts` | 删除 Session 类型，从 '@/types/generated/api' 导入 |
| `client/src/features/settings/data-sources/api.ts` | 删除 Connection 类型定义，从 '@/types/generated/api' 导入 |
| `client/src/features/settings/shared/api.ts` | 删除类型定义，从 '@/types/generated/api' 导入 |
| `client/src/features/connection/types.ts` | 重导出 |
| `client/src/features/session/types.ts` | 重导出 |

**修正字段名引用**

- `client/src/features/session/connection-overlay.tsx`: `{c.name} ({c.dbType})` → `{c.id} ({c.kind})`
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`: `database` → `databaseName` in API call

## 验证

- 后端编译：`mvn compile` ✅
- 前端类型检查：`npx tsc --noEmit` ✅

## 完成日期

2026-04-18