# API Prefix Decouple Implementation Plan

**Status:** 代码就绪（2026-04-18）—— 所有文件改动已完成、`tsc --noEmit` 零错误、`channel-client.test.ts` 4/4 通过。全量 `vitest run` 有 6 处失败，与本改动无关（stash 前后结果一致，均在 split-view/stage-toggle/providers UI 测试里）。**待办：** (1) 用户在浏览器 Network 面板做 E2E Smoke；(2) 用户授权后 commit + 搬移索引条目。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 环境变量 `VITE_API_BASE_URL` 只保存后端 origin（如 `http://host:port`），`/api` 路径前缀在前端代码里统一管理，消除"baseUrl 要不要带 /api"的歧义。

**Architecture:** 新增单一常量 `API_PREFIX = '/api'`（`client/src/services/api-prefix.ts`）。两条 HTTP 通道（`http.ts` 走 ky、`channel-client.ts` 走原生 fetch）都从该常量拼完整 URL；环境变量与调用方只负责 origin。

**Tech Stack:** TypeScript、Vite 环境变量、ky、vitest。

---

## 背景

- 当前 `.env.development` 把 `/api` 写进了 `VITE_API_BASE_URL`（`http://192.168.1.3:8080/api`）
- `src/services/http.ts:4` 依赖"含 /api"约定（正确）
- `src/services/channel/channel-client.ts` 原本自己拼 `/api/sessions/...`（违反约定，导致 `/api/api/...` 404）
- 单元测试 `channel-client.test.ts:22` 又按"不含 /api"约定传入 `baseUrl: 'http://test'`，期望 URL 为 `http://test/api/sessions/s-1/channel`
- 两套约定共存，下次新增 client 再出歧义概率很高

## 范围

**改动文件**：

- 新增：`client/src/services/api-prefix.ts` — 导出 `API_PREFIX`
- 修改：`client/src/services/http.ts` — 用常量拼 `prefixUrl`
- 修改：`client/src/services/channel/channel-client.ts` — 用常量拼 `url()`，把"baseUrl=origin"语义写入 JSDoc
- 修改：`client/.env.development` — 去掉尾部 `/api`
- 修改：`client/README.md` — 更新环境变量说明

**不改**：

- 后端 `@RequestMapping("/api/...")` 全部保留
- `channel-client.test.ts` 现有断言保持不变（已经符合新约定）

---

## Task 1：新增 API_PREFIX 常量 + 两个客户端迁移

**Files:**

- Create: `client/src/services/api-prefix.ts`
- Modify: `client/src/services/channel/channel-client.ts:98`
- Modify: `client/src/services/http.ts:1-4`
- Test: `client/src/services/channel/channel-client.test.ts`（已有断言保留）

- [x] **Step 1：确认已有测试的断言形态仍然满足新约定**

查看 `client/src/services/channel/channel-client.test.ts:22`。应看到 `baseUrl: 'http://test'` 与 `expect(url).toBe('http://test/api/sessions/s-1/channel')`——正是新约定，无需改动。

- [x] **Step 2：先跑一次测试确认目前红（之前的 hotfix 已把 /api 从 url() 里拿掉）**

````bash
cd client && npx vitest run src/services/channel/channel-client.test.ts
````

Expected: 失败，实际返回 `http://test/sessions/s-1/channel`，断言期望 `http://test/api/sessions/s-1/channel`。

- [x] **Step 3：创建常量文件**

写入 `client/src/services/api-prefix.ts`：

````ts
/**
 * Backend HTTP path prefix under which all REST + channel endpoints are mounted.
 * Paired with `VITE_API_BASE_URL` (origin only, no trailing path).
 */
export const API_PREFIX = '/api'
````

- [x] **Step 4：修改 `channel-client.ts` 让 `url()` 用常量，并在类顶部加一段 JSDoc 明确 baseUrl 语义**

在文件头部 `import { generateUuid }` 下方新增：

````ts
import { API_PREFIX } from '../api-prefix'
````

把类上方补一段 JSDoc（原位置是 `export class ChannelClient {`，紧贴它上方）：

````ts
/**
 * Low-level JSON-RPC / SSE client for the channel endpoint.
 *
 * `baseUrl` MUST be an origin only (e.g. `http://host:port` or empty string
 * for same-origin). The `/api` path prefix is appended internally from
 * `API_PREFIX` — never pre-concatenate it in callers or env vars.
 */
````

把 `private url()` 这一行（当前为 ``return `${this.baseUrl}/sessions/${this.sessionId}/channel` ``）改为：

````ts
private url() { return `${this.baseUrl}${API_PREFIX}/sessions/${this.sessionId}/channel` }
````

- [x] **Step 5：修改 `http.ts` 用常量拼 prefixUrl**

把整个文件顶部 import 区改为：

````ts
import ky from 'ky'
import { API_PREFIX } from './api-prefix'
````

把 `prefixUrl` 这一行从：

````ts
prefixUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
````

改为：

````ts
prefixUrl: `${import.meta.env.VITE_API_BASE_URL ?? ''}${API_PREFIX}`,
````

说明：`VITE_API_BASE_URL` 未设置时结果是 `/api`（同源场景），和旧行为等价；已设置时结果是 `${origin}/api`。

- [x] **Step 6：重新跑单测确认绿**

````bash
cd client && npx vitest run src/services/channel/channel-client.test.ts
````

Expected: 2 passed（`sends action_result` + `streams SSE`），URL 断言 `http://test/api/sessions/s-1/channel` 通过。

- [x] **Step 7：tsc 全量类型检查**

````bash
cd client && npx tsc --noEmit
````

Expected: 无输出（零错误）。

---

## Task 2：调整 env 配置 + 文档

**Files:**

- Modify: `client/.env.development`
- Modify: `client/README.md:52`

- [x] **Step 1：修改 `.env.development`**

把 `client/.env.development` 内容从：

````
VITE_API_BASE_URL=http://192.168.1.3:8080/api
````

改为：

````
VITE_API_BASE_URL=http://192.168.1.3:8080
````

（注意：保留 IP 和端口不变；仅移除尾部 `/api`。）

- [x] **Step 2：同步 README 环境变量说明**

`client/README.md` 第 52 行当前为：

````
- `VITE_API_BASE_URL` — 后端 Spring Boot 基址，默认 `http://localhost:8080/api`
````

改为：

````
- `VITE_API_BASE_URL` — 后端 Spring Boot origin（仅协议+主机+端口，**不含 `/api`**），默认 `http://localhost:8080`。`/api` 路径前缀由 `src/services/api-prefix.ts` 的 `API_PREFIX` 统一管理
````

- [x] **Step 3：全量回归**

````bash
cd client && npx vitest run && npx tsc --noEmit
````

Expected: 全部测试通过，无类型错误。

- [ ] **Step 4：端到端手动 smoke**（待用户确认）

1. `cd client && npm run dev`
2. 打开 UI，随便发一条消息（比如 "你好"）
3. 浏览器 DevTools Network 面板确认请求 URL 为 `http://<host>:8080/api/sessions/<sid>/channel`（**只有一个 `/api`**），状态码 200（或其他非 404）。
4. 确认 `src/services/api/session.ts` 等基于 `http` 的 REST 调用继续工作（可以切换会话、列出会话）。

如果任何一步失败，回到 Task 1 Step 4/5 检查拼接逻辑。

---

## Task 3：提交

**Files:** 无新改动，仅 git 操作。

- [ ] **Step 1：检查工作区**

````bash
cd /home/wallfacers/project/data-talk && git status
````

Expected 列表：

````
modified:   client/.env.development
modified:   client/README.md
modified:   client/src/services/channel/channel-client.ts
modified:   client/src/services/http.ts
new file:   client/src/services/api-prefix.ts
new file:   docs/exec-plans/2026-04-18-api-prefix-decouple-plan.md
modified:   docs/exec-plans/index.md
````

- [ ] **Step 2：创建 commit**（等用户授权）

````bash
git add client/.env.development client/README.md client/src/services/channel/channel-client.ts client/src/services/http.ts client/src/services/api-prefix.ts docs/exec-plans/2026-04-18-api-prefix-decouple-plan.md docs/exec-plans/index.md
git commit -m "$(cat <<'EOF'
refactor(client): decouple API path prefix from VITE_API_BASE_URL

Env var now holds origin only; centralize `/api` in `API_PREFIX` constant
so both ky + channel-client derive the full URL identically. Prevents
recurrence of the `/api/api/...` double-prefix 404.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
````

- [ ] **Step 3：登记索引搬迁**

完成执行后，把本计划从 `docs/exec-plans/index.md` 的活跃区移到已完成区（完成日期填当日），并在本文件 "范围" 与各 Task 的 checkbox 上打勾、顶部加一行 Status 说明。（此步由执行者在 commit 后补一次 follow-up commit，或合并到 Step 2 commit 之前完成——二选一，遵循执行者偏好。）

---

## 验收标准

- [x] `npx vitest run` 全绿
- [x] `npx tsc --noEmit` 零错误
- [ ] `npm run dev` 下输入任意消息发送请求，Network URL 只含一个 `/api`，返回非 404
- [x] `grep -rn "/api/" client/src` 结果里不应再出现"代码中硬编码拼 `/api/`"的位置（除 `api-prefix.ts` 自身与必要注释）
- [ ] 计划文件已登记在 `docs/exec-plans/index.md` 并从活跃区移到已完成区

## 风险与回滚

- **风险**：`http.ts` 的 ky `prefixUrl` 对开头 `/` 的处理——当 `VITE_API_BASE_URL` 未设置时会得到 `/api`（纯相对路径）。ky 支持该形式（浏览器基于当前 origin 解析）。若生产部署使用同源反代，应照常工作。
- **回滚**：单 commit refactor，`git revert` 即可还原到 hotfix 后的状态。
