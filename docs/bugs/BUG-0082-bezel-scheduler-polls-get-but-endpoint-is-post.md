# BUG-0082: Bezel scheduler 用 GET 轮询，但 widget data 接口是 POST（405，数据永不加载）

| Field | Value |
|-------|-------|
| ID | BUG-0082 |
| Status | fixed |
| Severity | high |
| Module | bezel-compiler |
| Discovered | 2026-05-21 |
| Discoverer | code review（E2E 前对照接口契约） |
| Source | code-review |
| FixPlanRef | bezel-compiler-redesign |

## Summary

编译后大屏的运行时 `scheduler.js` 用 `XMLHttpRequest` + `GET`（query string 带 params）轮询 widget 数据，但后端 `DashboardController.fetchWidgetData` 是 `@PostMapping("/{id}/widgets/{wid}/data")`，请求体为 `{ "params": {...} }`。方法不匹配 → 每次轮询返回 `405 Method Not Allowed`，图表/表格/KPI 永远拿不到数据，只渲染首屏骨架。

## Root Cause

`scheduler.js` 的 `startPolling()`：

```js
var url = buildUrl(widget.endpoint, widget.params);  // endpoint?key=val
var xhr = new XMLHttpRequest();
xhr.open('GET', url, true);
...
xhr.send();
```

服务端契约（`DashboardController.java:240`）：

```java
@CrossOrigin(origins = "null", allowCredentials = "false")
@PostMapping("/{id}/widgets/{wid}/data")
public ResponseEntity<?> fetchWidgetData(..., @RequestBody Map<String,Object> body) {
    Map<String,Object> params = (Map) body.getOrDefault("params", Map.of());
    ...
}
```

CORS 预检测试（`DashboardControllerIT.widgetDataAcceptsNullOriginCorsPreflightForSandboxedIframe`）已确认沙箱 iframe（origin `null`）走 `OPTIONS → POST + content-type` 预检，即服务端契约就是 POST。客户端 GET 与之冲突。

golden/单元测试只编译 HTML、不在浏览器执行 `scheduler.js`，故 405 未被捕获（同 BUG-0081 的盲区）。

## Fix Applied

- `scheduler.js` `startPolling()` 改为 `POST widget.endpoint`，`Content-Type: application/json`，body = `JSON.stringify({ params: widget.params || {} })`，与服务端 `@RequestBody {params}` 契约对齐。
- 移除随之失效的 `buildUrl()`（仅 GET 轮询使用，已无引用）。
- 沙箱 iframe origin `null` 的 POST 预检由既有 `@CrossOrigin(origins="null")` + WebMvc CORS 配置覆盖（对应 IT 已绿）。

## Evidence

- `DashboardController.java:240` 为 `@PostMapping`，body 取 `params`。
- `scheduler.js` `startPolling` 原为 `xhr.open('GET', ...)`。
