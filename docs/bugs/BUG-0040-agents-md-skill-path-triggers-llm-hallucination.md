---
id: BUG-0040
title: AGENTS.md 引用 `skills/data-ingestion/SKILL.md` 触发 LLM 幻觉绝对路径 Read 卡住
status: fixed
priority: P1
source: manual-report
modules: [opencode, ingestion]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: b2c4f1ca
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

在 DataTalk 对话中触发 data-ingestion 流程时，AI 子进程（OpenCode）的 Read 工具调用试图读 `/home/<user>/.agents/skills/data-ingestion/SKILL.md`，文件不存在，调用失败/超时，对话卡住。

DataTalk 把 data-ingestion skill 解压到 `~/.data-talk/opencode/.opencode/skills/data-ingestion/SKILL.md`（OpenCode cwd 内的 local skill 位置），并不会装到 `~/.agents/skills/`（OpenCode 全局 skill 缓存，由 `~/.agents/.skill-lock.json` 管理）。OpenCode 1.14.x 加载 skill 时会自动从 cwd 内 `.opencode/skills/` 找到 data-ingestion，**无需 AI 主动 Read SKILL.md**。

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 第 938 / 954 行明文给出相对路径 `skills/data-ingestion/SKILL.md`，LLM 收到 prompt 后用启发式把它补全成绝对路径——机器 `~/.agents/skills/` 下确实有其他 skill（playwright-cli / skill-creator / data-visualization 等），LLM 自然推断 data-ingestion 也在那里。

## Reproduction Steps

1. 在 DataTalk 对话里说："把 https://api.example.com/orders 的数据导入到我的数据库"（任意触发 data-ingestion 流程的措辞）。
2. AI 决定使用 data-ingestion skill，发起 Read 工具调用 `/home/<user>/.agents/skills/data-ingestion/SKILL.md`。
3. 对话停在该工具调用上，转圈不结束。

## Expected vs Actual

- **Expected**：AI 不应主动 Read SKILL.md。skill 由 OpenCode 自动加载，其 MCP 工具表（`datatalk_http_request` 等）应当直接可用。
- **Actual**：AI 试图 Read 不存在的 `~/.agents/skills/data-ingestion/SKILL.md`，Read 失败/超时，整轮 AI 工具调用卡住。

## Environment

- Backend commit: 640c03e1
- Frontend commit: 640c03e1
- OpenCode version: 1.14.41
- OS / Browser: Linux WSL2 / Tauri v2 webview
- Data source: N/A（OpenCode prompt → AI 行为）

## Evidence

OpenCode 1.14.41 skill loader（反汇编 `~/.data-talk/opencode/v1.14.41/opencode` 二进制偏移 107617959 起的 `Bq` 函数）顺序：
1. Global：`$HOME/.claude/skills/`、`$HOME/.agents/skills/`（不存在静默跳过，**不会卡住**）
2. Project：cwd 向上 walk 找 `.claude/skills/`、`.agents/skills/`
3. Config directories：`~/.config/opencode/`、cwd 向上 walk 的 `.opencode/`、`~/.opencode/`
4. `opencode.json` 显式声明的 `skills.paths` / `skills.urls`

DataTalk 解压目标 `~/.data-talk/opencode/.opencode/skills/data-ingestion/` 命中第 3 条，OpenCode 启动时已自动加载，工具表注册无误。AI 的 Read 调用属于 LLM 自发行为，不是 OpenCode 强制要求。

`~/.agents/.skill-lock.json` schema v3 不包含 data-ingestion 条目，里面只有 `find-skills`、`skill-creator`、`self-improving-agent`、`playwright-cli`、`playwright-best-practices`、`data-visualization` —— 印证 LLM 是看到这些近邻 skill 后推断绝对路径。

`~/.data-talk/opencode/AGENTS.md`（运行时产物，源自 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`）第 938 / 954 行：

```
**Full skill reference**: `skills/data-ingestion/SKILL.md`
... See `skills/data-ingestion/SKILL.md` for the full error → action mapping.
```

这是 LLM 路径补全的输入信号。

## Root Cause

AGENTS.md 文案给了 SKILL.md 相对路径，让 LLM 误以为应当主动加载这份文档。LLM 在 `$HOME/.agents/skills/...` 启发式下把相对路径补全成不存在的绝对路径，触发 Read 卡住。本质问题：**skill 文档不是 AI 该 Read 的资源——它已经在 OpenCode 启动时被自动加载，其工具表就是 skill 的对外接口**。

不是 OpenCode 行为问题（二进制里没有 `agents/skills` 字面量，也没强制要求 AI Read SKILL.md）。

## Fix

修改 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`：

- **L938**：原 `**Full skill reference**: \`skills/data-ingestion/SKILL.md\`` → 改为 `Skill auto-loaded` 提示，明确说明 OpenCode 自动加载，**禁止 AI 按任意路径 Read SKILL.md**，且不在文案里硬编码任何反例路径（避免 LLM 反向当作 hint）。
- **L954**：原 `... See \`skills/data-ingestion/SKILL.md\` for the full error → action mapping.` → 删除 SKILL.md 文件引用，把完整 `INGESTION_*` 错误码 → user-actionable response 表 inline 进 AGENTS.md（共 8 条：`SSRF_BLOCKED` / `PAYLOAD_TOO_LARGE` / `AUTH_FAILED` / `TOKEN_INVALID` / `DIALECT_UNSUPPORTED` / `FETCH_FAILED` / `FORMAT_UNSUPPORTED` / `INFER_FAILED`）。

同时核查 AGENTS.md 内是否还有其他 `skills/<name>/SKILL.md` 引用——`grep -n "skills/.*SKILL.md"` 仅匹配这两行，bezel 等其他 skill 无类似引用，无需扩展修复。

## Verification

- `AgentsTemplateContractTest` line 73 原断言依赖 `skills/data-ingestion/SKILL.md` 字符串，已更新：
  - `.doesNotContain("skills/data-ingestion/SKILL.md")`
  - `.contains("Skill auto-loaded")`
  - 补齐 4 个新错误码断言：`PAYLOAD_TOO_LARGE` / `FETCH_FAILED` / `FORMAT_UNSUPPORTED` / `INFER_FAILED`
- `cd server && mvn test -pl data-talk-adapter -Dtest=AgentsTemplateContractTest`：8/8 通过，BUILD SUCCESS。

## Notes

- 评估过两条替代方向：
  - **A. 把 data-ingestion 也解压到 `~/.agents/skills/data-ingestion/`**：会污染用户全局 skill 目录、与 `.skill-lock.json` schema v3 冲突（无 source/sourceUrl 字段），还需每次启动 reconcile，治标不治本。
  - **C. 保留 SKILL.md 引用但换成绝对路径 `~/.data-talk/opencode/.opencode/skills/data-ingestion/SKILL.md`**：让 Read 可成功，但 AI 仍会做不必要的文件读，多一次工具往返。次优。
- 最终采纳 B：从源头去掉文件引用 + inline 必要信息，让 LLM 没有路径补全的入口。
- 历史 BUG-0036 修复了 skill 解压目标的 cwd 错位（`Paths.get("")` → `opencodeWorkingDir(homeDir)`），让 OpenCode 真正能找到 skill；本 BUG 是另一条独立路径——即使 OpenCode 自己能加载，prompt 文案仍可能误导 LLM 做不必要的 Read。两者互补。
