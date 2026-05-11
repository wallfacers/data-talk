---
id: BUG-0012
title: Widget data endpoint 不使用 dashboard defaultConnectionId 解析连接上下文
status: open
priority: P1
source: e2e-playwright
modules: [dashboard]
discovered: 2026-05-12
discoveredBy: agent
testRunId: pw-2026-05-12-test-store
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
`POST /api/dashboards/{id}/widgets/{wid}/data` 执行 widget SQL 时，未从 dashboard JSON 的 `defaultConnectionId` 字段解析数据库连接上下文，导致即使 dashboard 已绑定连接，SQL 执行仍报「命中多个候选数据库」错误（400）。

## Reproduction Steps
1. 创建 MySQL 连接 `local-mysql-test-store`（database=test_store）
2. Promote v2 dashboard，设置 `defaultConnectionId` 指向该连接
3. Widget SQL 引用 `orders` 等 test_store 表
4. 调用 `POST /api/dashboards/{id}/widgets/{wid}/data`

## Expected vs Actual
- **Expected**: 使用 dashboard 的 `defaultConnectionId` 执行 SQL，返回 columns + rows
- **Actual**: 返回 400 `{"code":"bad_request","message":"表 orders 命中多个候选：test_project, test_store。请先明确选择 database/schema"}`

## Environment
- Backend commit: 33af7d2
- Frontend commit: 33af7d2
- OS / Browser: WSL2 / Playwright Chromium
- Data source: MySQL 8.0 (test_store, 10 tables, real data)

## Evidence
```
curl -X POST http://localhost:8080/api/dashboards/dash_st3s3fkk/widgets/chart_w_gmvtrend/data \
  -H "Content-Type: application/json" -d '{"params":{}}'
→ 400 {"code":"bad_request","message":"表 orders 命中多个候选：test_project, test_store..."}
```

## Root Cause
`WidgetDataService.fetchWidgetData()` 未读取 dashboard 的 `defaultConnectionId` 来确定 SQL 执行的目标连接。当前实现可能依赖 session-scoped connection context（如 `SessionDataContext`），但 widget data 调用是无状态的 HTTP 请求，没有 session 绑定。

## Fix
TBD

## Verification
TBD

## Notes
N/A
