# AI 设置中心（对齐 OpenCode Desktop）

**日期**：2026-04-17
**状态**：设计已定稿，待实施计划
**作者**：wallfacers

## 1. 背景与动机

data-talk 当前的 AI 能力依赖 OpenCode 服务，但用户侧缺少对 AI provider 与 model 的可视化配置能力：

- 前端 `features/model-config/` 仅为空壳（Zustand 纯内存、无持久化）
- 后端未持久化任何 provider / model 偏好，OpenCode base-url 走 Spring property 硬编码
- 数据源（业务数据库连接）UI 同样是占位组件，后端 CRUD 端点残缺
- `ChannelService.forwardUserMessage` 不携带任何 model 信息

OpenCode Desktop v1.4.6 已经提供了一个成熟的"设置中心"形态（侧边栏 + 分页：通用 / 提供商 / 模型）。本次目标：**把 OpenCode Desktop 的设置中心搬进 data-talk，并把原本就残缺的"数据源"也顺势补齐**，形成统一的 `/settings` 体验。

## 2. 范围

### 2.1 v1 In Scope

- 全新 `/settings` 路由，侧边栏 + 分页布局
  - 桌面 · 通用（复用现有 `GeneralSettingsPanel`）
  - 服务器 · 数据源（补齐 CRUD + 测试连接）
  - 服务器 · 提供商（对齐 OpenCode Desktop）
  - 服务器 · 模型（对齐 OpenCode Desktop）
- 后端 `AiSettingsController` 代理 OpenCode 的 provider / model / config 接口
- 后端 `ConnectionController` 补齐 update / delete / test 端点
- data-talk SQLite 新增 `ai_user_prefs` + `ai_model_prefs` 两张表
- Chat prompt composer 新增紧凑模型选择器（`[icon][name][▼]`）
- 删除旧的 `features/model-config/settings-dialog.tsx` / `settings-page.tsx`

### 2.2 v1 Out of Scope（有意推迟）

- OAuth 类 provider 的登录（Anthropic Claude Pro / GitHub Copilot 等）——UI 置灰并提示使用 opencode CLI
- 移除凭证按钮——OpenCode HTTP 文档未提供 `DELETE /auth/:id`
- "测试连接"按钮（Provider 页）——OpenCode 未提供凭证校验接口
- 模型启禁写回 OpenCode（写回会与 OpenCode 自己的 `disabled_providers` 语义冲突）
- OpenCode 连接 Profile（多远端切换）
- 快捷键设置页

## 3. 架构决策

| 决策点 | 选型 | 理由 |
|--------|------|------|
| 凭证归属 | OpenCode 侧存 | OpenCode 已有 `auth.json` + `PUT /auth/:id`，避免双份事实源 |
| 偏好归属 | data-talk SQLite 存 | 当前模型、模型启禁 mask 是 data-talk UI 的偏好，与 OpenCode 无关 |
| 调用路径 | 客户端 → Spring Boot → OpenCode | 和现有三层架构一致，shared-secret 不落前端，未来可换远端 OpenCode |
| OpenCode 源码 | 不改 | 只用官方文档 [opencode.ai/docs/zh-cn/server/](https://opencode.ai/docs/zh-cn/server/) 列出的 HTTP 接口 |
| OAuth | v1 不做 | 需要 Tauri 本地回调监听，单独立项 |

### 3.1 OpenCode 文档 API 清单（本次使用）

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/provider` | 列 provider（`all` / `default` / `connected`） |
| GET | `/provider/auth` | 列每个 provider 的认证方式 |
| PUT | `/auth/:id` | 写凭证 |
| POST | `/session/:id/message` | 发消息（支持 `model` 参数，现有流程补参） |

## 4. 信息架构

**布局**：左竖侧边栏 + 右内容区。

```
桌面
  · 通用        → /settings?section=general
服务器
  · 数据源      → /settings?section=data-sources
  · 提供商      → /settings?section=providers
  · 模型        → /settings?section=models
```

底部显示 data-talk 版本号。

### 4.1 提供商页

- Section「已连接的提供商」：数据源为 `GET /provider.connected[] ∩ all[]`。每行 `[logo][名称][描述]`，右侧按钮「重新配置」。空态文案：「没有已连接的提供商」。
- Section「热门提供商」：`GET /provider.all[]` 扣除已连接部分。每行 `[logo][名称][可选"推荐"徽标][描述]`，右侧按钮「+ 连接」。"推荐"由前端常量清单决定（OpenCode Zen / OpenCode Go / Anthropic 等）。
- 点击连接 / 重新配置 → 对话框，按 `GET /provider/auth` 响应渲染：
  - `api` 类：API Key 输入 + 可选 Base URL → `PUT /auth/:id`
  - `oauth` 类：v1 按钮置灰，tooltip：「请在 opencode CLI 执行 `opencode auth login <provider>`」

### 4.2 模型页

- 顶部搜索框（前端过滤）
- 按 provider 分组，**仅展示 connected 的 provider**
- 每行 `[模型名]` 右侧启禁 switch
- switch 状态写 `PATCH /api/ai/models/{id}`，仅写 data-talk SQLite，不回写 OpenCode
- 无"当前默认模型"UI——当前模型选择移到 chat composer

### 4.3 数据源页

- 列表：`id / kind / host:port / database / username / 创建时间`
- 按钮：新增、编辑、删除、测试连接
- 表单按 `kind` 渲染差异字段（MySQL / PostgreSQL / H2）
- 密码沿用 `connections.password_enc` 加密字段

### 4.4 Chat composer 模型选择器

- 触发器：紧凑 inline 元素 `[provider 图标][模型名][▼]`
- 点击弹 Popover：按 provider 分组列出"enabled ∧ provider connected"的模型；顶部搜索；当前项高亮
- 选中 → `PATCH /api/ai/current-model`；下一次 `POST /session/:id/message` 带 `model` 字段透传 OpenCode
- 无选择：占位图标 + "选择模型"文案；无可用模型：禁用发送 + 提示跳 Settings

## 5. 数据模型

新增 Flyway 迁移 `V2__ai_prefs.sql`：

```sql
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
  enabled     INTEGER NOT NULL,  -- 0/1
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (provider_id, model_id)
);
```

**设计要点**：
- `ai_user_prefs` 单行，固定 `id='default'`，桌面单用户场景
- `ai_model_prefs` 默认视角：凡不在表里的模型视为启用；仅在禁用时插/更新——随 OpenCode 模型清单变化不产生孤儿记录

`connections` 表沿用 V1，本次不改 schema。

## 6. 后端接口设计

### 6.1 新增 `AiSettingsController`（`/api/ai/**`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/providers` | 代理 `GET /provider`，透传原字段 |
| GET | `/api/ai/providers/auth` | 代理 `GET /provider/auth` |
| PUT | `/api/ai/providers/{id}/credentials` | 代理 `PUT /auth/{id}` |
| GET | `/api/ai/models` | 聚合 OpenCode `provider.all[].models` 与 SQLite `ai_model_prefs`，默认启用 |
| PATCH | `/api/ai/models/{id}` | 写 `ai_model_prefs.enabled` |
| GET | `/api/ai/current-model` | 读 `ai_user_prefs.current_model` |
| PATCH | `/api/ai/current-model` | 写 `ai_user_prefs.current_model` |

`GET /api/ai/models` 响应示例：

```json
{
  "providers": [
    {
      "id": "openai", "name": "OpenAI", "connected": true,
      "models": [
        {"id": "gpt-5", "name": "GPT-5", "enabled": true},
        {"id": "gpt-5-nano", "name": "GPT-5 Nano", "enabled": false}
      ]
    }
  ]
}
```

### 6.2 `ConnectionController` 补齐

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/connections/{id}` | 更新连接 |
| DELETE | `/api/connections/{id}` | 删除连接 |
| POST | `/api/connections/{id}/test` | 测试连接，`DriverManager.getConnection` + `isValid(3)` |

### 6.3 `OpenCodeHttpClient` 扩展

```java
ProviderListDto listProviders();
Map<String, AuthMethodDto[]> getProviderAuth();
void putAuth(String providerId, AuthPayload payload);
```

OAuth / `/config` 相关方法 v1 不加，等有使用场景再引入。

### 6.4 消息发送携带 model

`ChannelService.forwardUserMessage` 在调用 OpenCode `POST /session/:id/message` 时读取 `ai_user_prefs.current_model`：

- 非空 → 附加到请求 body 的 `model` 字段
- 空 → 不附加该字段，由 OpenCode 使用其默认模型（`GET /provider.default`）

注：前端 composer 在无可用模型时已禁用发送按钮（见 4.4），正常流程不会走到空值分支；空值分支是兼容兜底。

## 7. 前端结构

```
client/src/features/settings/
├─ settings-layout.tsx             侧边栏 + 内容壳，响应 ?section= query
├─ shared/
│  └─ provider-icon.tsx            从 model-config 搬过来
├─ general/
│  └─ general-page.tsx             复用 GeneralSettingsPanel
├─ data-sources/
│  ├─ data-sources-page.tsx
│  ├─ connection-form-dialog.tsx
│  └─ test-connection-button.tsx
├─ providers/
│  ├─ providers-page.tsx
│  ├─ connect-dialog.tsx
│  └─ recommended-providers.ts     "推荐"徽标白名单
└─ models/
   ├─ models-page.tsx
   └─ models-group.tsx

client/src/features/session/
└─ prompt-composer.tsx             新增 <ModelPicker />

client/src/features/session/model-picker/
├─ model-picker.tsx                [icon][name][▼] 触发器
└─ model-picker-popover.tsx        分组列表 + 搜索
```

**删除**：`features/model-config/` 整包。
**保留**：`features/connection/` 的 store/hooks/types 作为 shared data layer；`features/settings/data-sources/` 仅作页面壳。

## 8. 错误处理与降级

| 场景 | 行为 |
|------|------|
| OpenCode 不可达 / 超时（3s 连接 / 10s 读） | Spring 回 503 `{"error":"OPENCODE_UNAVAILABLE"}`；前端 Provider / Model 页空态 + 重试；不阻塞 Settings 整体打开 |
| 凭证写入 4xx/5xx | Spring 透传状态码 + body；前端对话框红色文案显示 |
| 偏好写入失败 | 前端 toast + UI 状态回滚，不做重试队列 |
| 数据源测试失败 | 归一化 `{"ok":false,"reason":"..."}`，3s 内 fail-fast |

**安全边界**：
- API Key：仅 `PUT /auth/:id` 一次性透传，data-talk 不落盘；Spring 日志禁止打印相关 request body
- 数据源密码：沿用现有 `password_enc` 加密路径
- 桌面单机威胁模型：不引入本地服务 TLS（单独立项）

**UI 明示的 v1 限制**：
1. oauth 类 provider 置灰 + CLI 提示
2. 无"移除凭证"按钮，提示编辑 `~/.local/share/opencode/auth.json` 或 opencode CLI
3. 无"测试连接"按钮（Provider 页），状态仅依据 OpenCode `connected[]`
4. 模型启禁仅影响 data-talk UI，不写回 OpenCode

## 9. 测试策略

### 后端
- `AiSettingsController` 集成测试：WireMock（复用 FakeOpenCodeServer 模式）模拟 `/provider` / `/provider/auth` / `/auth/:id`
  - 聚合、透传、503 降级、4xx 透传、偏好合并（启/禁/默认启用三种样态）
- `ConnectionController` F1 新增端点测试：H2 真连接做 happy-path，错端口做 fail-fast
- `OpenCodeHttpClient` 新增方法：每方法 happy-path + 错误码映射

### 前端
- `features/settings/*` vitest 组件测试，mock `/api/**`
- `prompt-composer` 模型选择器：触发器展示、Popover 过滤、选中 PATCH
- `settings-layout` 侧边栏 `?section=` URL 同步

### 人工端到端
本地 OpenCode + `mvn spring-boot:run` + `npm run tauri dev`，走通：配 OpenAI API Key → Models 页看到 gpt-5 系列 → Chat 切模型 → 发消息 → OpenCode session 收到带 `model` 的消息。

**覆盖率**：后端新增代码 ≥80%（与现有模块一致）；前端核心交互路径覆盖。

## 10. 非目标 / 未来工作

- OAuth provider 登录流程（anthropic / github）
- DELETE 凭证（等 OpenCode 接口或走直接编辑 auth.json）
- Provider 凭证有效性校验（等 OpenCode 接口）
- OpenCode 远端连接 Profile
- 模型启禁写回 OpenCode（需先解决语义冲突）
- 快捷键设置页

## 11. 相关文档

- 架构：[../../ARCHITECTURE.md](../../ARCHITECTURE.md)
- 产品规格总表：[./index.md](./index.md)
- 后端开发指南：[../BACKEND.md](../BACKEND.md)
- 前端开发指南：[../FRONTEND.md](../FRONTEND.md)
- OpenCode HTTP API 文档：[opencode.ai/docs/zh-cn/server/](https://opencode.ai/docs/zh-cn/server/)
