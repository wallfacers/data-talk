---
id: BUG-0067
title: data-collection skill 未禁止脚本直连 DB，LLM 走 pymysql / mysql.connector 错误退路，违反 backend write API 合同
status: fixed
priority: P2
source: manual-report
modules: [opencode, data-collection, script-execution]
discovered: 2026-05-19
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: openspec/changes/dialect-aware-import-export-and-friction-fix/
duplicateOf: null
regression: false
---

## Summary

`data-collection` skill `SKILL.md` 已在 "Data Write API" 段明确写明脚本必须通过 `POST {DT_BACKEND_URL}/api/script-data/write` 写入数据，但没有显式"DO NOT"段约束。当 `datatalk_import_data` 因 [BUG-0066](BUG-0066-identifier-quoting-not-dialect-aware.md) 失败、`datatalk_ui_exec` 也失败后，LLM 选择 `datatalk_script_run` 作为退路时主动 `pip install pymysql` / `mysql.connector` 直连用户 MySQL，连续撞墙 6+ 轮（脚本沙箱无法访问用户 DB 网络）。

## Reproduction Steps

1. 启动后端 + Tauri dev，绑定 MySQL 连接。
2. 让 AI 任何失败重试链最终走 `datatalk_script_run`。
3. 观察生成的脚本：含 `import pymysql; conn = pymysql.connect(host=..., user=..., password=...)`。

## Expected vs Actual

- **Expected**：脚本只用 `requests` / `urllib` 抓数据，然后 `POST {DT_BACKEND_URL}/api/script-data/write` 让后端代写。无任何 DB 客户端 import。
- **Actual**：脚本 import `pymysql` / `mysql.connector` / `psycopg2` 等直连库，且尝试用 `DT_CONNECTION_ID` 等环境变量自行拼接 host/user/password 直连 —— 但脚本运行时沙箱根本无法访问用户 DB 网络，必然失败。

## Environment

- Backend commit: c10289fc
- Frontend commit: c10289fc
- OS / Browser: WSL2 Ubuntu / Tauri dev
- Data source: MySQL 8.x

## Evidence

`server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md` 文档结构：

```
# Data Collection Skill
## Tool Surface
## Orchestration Sequence
## Script Environment Variables  ← 提示 DT_BACKEND_URL / DT_SCRIPT_TOKEN / DT_CONNECTION_ID
## Data Write API (called by scripts)  ← 明确指定 POST /api/script-data/write
## Error Handling
## Output Protocol
```

没有任何段落显式禁止直连 DB。`DT_CONNECTION_ID` 反而暗示"可以拿连接信息直连"。LLM 在 `import_data` 失败后选择"自主连 DB"作为合理 fallback。

## Root Cause

skill 文档采用"正向描述"而非"硬约束"：告诉 LLM "正确路径是 backend write API"，但未明示"其他路径全部禁止"。这与 BUG-0065 同源根因 —— prompt 的"应该"不等于"必须"。当主路径失败时，LLM 会自主探索其他路径，可能违反隐含设计意图。

## Fix

参照 BUG-0065 修复套路：

1. 在 `SKILL.md` 顶部、`## Tool Surface` 之前插入 `## ❗ DO NOT` 段：
   - 显式列出至少 4 种禁止的直连库（`pymysql`, `mysql.connector`, `psycopg2`, `sqlite3`）
   - 明确指出"DataTalk 脚本沙箱无法访问用户 DB 网络"
   - 重申唯一支持的写入路径
2. 新增 `DataCollectionSkillContractTest` 合同测试，断言 `SKILL.md` 包含 `DO NOT` / `pymysql` / `psycopg2` / `/api/script-data/write` 关键字。失败即 CI 中断，防止 prompt 退化。

详细方案见 `openspec/changes/dialect-aware-import-export-and-friction-fix/design.md` D7。

## Verification

- 同 prompt 复现："让 AI 把数据导入到 datatalk_ctx" → AI 不再选择 `datatalk_script_run + pip install pymysql`
- `DataCollectionSkillContractTest` 加入 `mvn test` 套件后通过

## Notes

- 运行时层执法（在 `RunScriptActionHandler` 注入网络隔离）作为 follow-up，本 BUG 仅覆盖 prompt + 合同测试
- 与 [BUG-0066](BUG-0066-identifier-quoting-not-dialect-aware.md) 同源（同一次失败 E2E）。BUG-0066 是真 product bug，BUG-0067 是 prompt 漏洞
- 与 [BUG-0065](BUG-0065-pre-action-protocol-bypassed-via-ui-exec-path.md) 同模式（prompt-level 退化防护）
