---
id: BUG-0012
title: Widget data endpoint 缺少 dashboard 级 database / schema 解析回路
status: fixed
priority: P1
source: e2e-playwright
modules: [dashboard]
discovered: 2026-05-12
discoveredBy: agent
testRunId: pw-2026-05-12-test-store
fixCommit: b8060d9
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
`POST /api/dashboards/{id}/widgets/{wid}/data` 执行 widget SQL 时，dashboard 没有任何字段可以声明 SQL 跑在哪个 database / schema 上。当 connection 是「服务器级」（`databaseName=null`）、且同一 MySQL 实例上有多个库都含同名表时，`TableContextAutoResolver` 抛出歧义错误，整面板罢工。

## Reproduction Steps
1. 创建 MySQL 连接 `local-mysql-test-store`（不绑定具体 database）
2. Promote v2 dashboard，`defaultConnectionId` 指向该连接
3. Widget SQL 引用 `orders` 等 test_store 表（同实例下 `test_project` 也有同名表）
4. 调用 `POST /api/dashboards/{id}/widgets/{wid}/data`

## Expected vs Actual
- **Expected**: dashboard 可声明 `defaultDatabase`（widget 可声明 `query.database`）来锁定 SQL 执行库，返回 columns + rows
- **Actual**: 返回 400 `{"code":"bad_request","message":"表 orders 命中多个候选：test_project, test_store..."}`

## Environment
- Backend commit: 33af7d2
- Frontend commit: 33af7d2
- OS / Browser: WSL2 / Playwright Chromium
- Data source: MySQL 8.0 (同实例上 test_store + test_project 两库均含 `orders` 等同名表)

## Evidence
```
curl -X POST http://localhost:8080/api/dashboards/dash_st3s3fkk/widgets/chart_w_gmvtrend/data \
  -H "Content-Type: application/json" -d '{"params":{}}'
→ 400 {"code":"bad_request","message":"表 orders 命中多个候选：test_project, test_store..."}
```

## Root Cause
原 BUG 摘要把根因写成「`WidgetDataService` 不读 `defaultConnectionId`」是误判。深入追踪后实际是两段问题叠加：

1. **架构缺口（主因）**：`WidgetDataService.fetchWidgetData()` 旧逻辑只取 `connection.databaseName()` 作 SQL 执行库，dashboard 协议层完全没有 `defaultDatabase` / `defaultSchema` 字段、widget 协议层也没有 `query.database` / `query.schema`。一旦 connection 是「服务器级」（合法配置），widget 数据接口就没有任何位面可以指定库；走到 `TableContextAutoResolver.requiresLocation()` 判定需要自动定位，多 DB 同名表场景必然抛歧义。
2. **e2e 测试夹具污染**：`test-store-dashboard.spec.ts` 第 28 行写 `database: 'test_store'`，但后端 DTO `ConnectionCreateRequest` 字段名是 `databaseName`，Jackson 默认大小写敏感，所以创建出来的连接 `databaseName` 一直是 `null` —— 这把架构缺口直接暴露给 e2e。

## Fix
1. **`WidgetDataService.fetchWidgetData()`**：新增三档优先级解析 `widget.query.database/schema > dashboard.defaultDatabase/defaultSchema > connection.databaseName`（blank 视同 null 向下穿透）。代码在 `server/data-talk-application/.../WidgetDataService.java` 第 107–119 行，附 `firstNonBlank` 工具方法。
2. **e2e fixture 修复**：`MYSQL_CONNECTION.database` → `databaseName`；`makeTestStoreDashboard` 注入 `defaultDatabase: 'test_store'`；widget data 断言从 `[200, 400]` 收紧为 `200`。
3. **dashboard JSON Schema**：未启用 `additionalProperties: false`，新字段 `defaultDatabase` / `defaultSchema` 与 `widgetQuery.database` / `widgetQuery.schema` 通过透传生效，无需 schema 声明改动；如后续把 schema 收严，需补声明。
4. **单测覆盖**：新增 `WidgetDataServiceTest`（5 用例）覆盖 dashboard 覆盖 connection、widget 覆盖 dashboard、双空 fallback、schema 透传、空串等同缺省五条路径。

## Verification
1. `cd server && mvn -pl data-talk-application -am test -Dtest=WidgetDataServiceTest` → 5 passed
2. `cd server && mvn clean verify` → 全量绿（见编译验证）
3. Playwright `tests/e2e/test-store-dashboard.spec.ts` 的 widget data endpoint 用例从 `[200, 400]` 收紧为 200，连通真实 MySQL `test_store` 后必须返回 columns + rows

## Notes
- 修复后，「服务器级 connection + 多库共存」是合法且能跑通的用户路径（dashboard 用 `defaultDatabase` 来 scope）。
- Widget 级别 `query.database` / `query.schema` 留作高级 override，跨库 dashboard 时可用。
- 如果后续把 `dashboard-schema.json` 收严为 `additionalProperties: false`，必须把这 4 个字段补到 schema 声明中。
