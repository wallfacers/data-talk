---
id: BUG-0050
title: 大屏 JSON 模式 widget 仅渲染骨架，未调接口取数 + 文本乱码
status: fixed
priority: P1
source: manual-report
modules: [dashboard, stage]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

bezel skill 产物中的 dashboard JSON 在 stage 渲染时，widget 区只显示骨架（标题占位 / 加载 spinner / 空网格），没有触发 widget data endpoint 拉取实际数据，ECharts 也没有渲染实际图表。同时显示出的中文文本（标题、占位提示等）伴随乱码。

注意：与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 区别——本 BUG 描述的是 dashboard **JSON 模式渲染链路**（widget 通过 `GET /api/dashboards/{id}/widgets/{wid}/data` 取数 + 客户端 ECharts 渲染），不是 iframe srcDoc 直接渲染的 HTML 模式。

## Reproduction Steps

1. chat 端要求 AI 生成 dashboard JSON（仅 JSON，不附 HTML，或后端选择 JSON 模式渲染）
2. promote 后 stage 打开 dashboard tab
3. 观察 widget 区域：每个 widget 卡片只显示骨架边框 / 加载态，无数据
4. 打开 DevTools Network 面板：没有看到对 `/api/dashboards/{id}/widgets/{wid}/data` 的请求（或请求发出但响应未被消费）
5. widget 标题中的中文字符显示乱码

## Expected vs Actual

- **Expected**:
  - 每个 widget 挂载后自动调用 `GET /api/dashboards/{id}/widgets/{wid}/data?...` 拿到数据
  - 数据回写后 ECharts / KPI / markdown 等 renderer 完成渲染
  - 中文标题、轴 label、tooltip 等正常显示
- **Actual**:
  - widget 仅渲染骨架结构（卡片框 + 占位文本），无 ECharts canvas、无 KPI 数字、无真实内容
  - 中文文本显示乱码

## Environment

- Backend commit: 3f67eb44 (develop)
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium

## Evidence

待补充：
- widget 骨架态截图
- DevTools Network 面板截图（确认有 / 无 widget data 请求）
- 后端日志（确认 widget data endpoint 是否被调用 + 响应 status）
- widget 标题 i18n key 与 JSON title 字段的乱码对比

## Root Cause

两个独立成因叠加：

1. **前端 `decodeUtf8Base64` typo 导致 chat → promote 链路双编码**：与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 同根。`markdown.tsx` 的 `decodeUtf8Base64` 误调用 module-local `escape`（HTML 实体转义器，行 42-46）代替全局 `escape`（URL `%xx` 转义器），导致解 base64 后 UTF-8 字节被双重编码后随 promote 落盘。HTML 中 inline `<script>` 的中文字面量（widget 标题、`legend.data: ['GMV', '订单量']` 等）变为非法 UTF-8 字节，浏览器 JS parser 抛 SyntaxError → **整段 polling scheduler 不执行** → ECharts 实例不创建 → fetch 不发出 → widget 容器空荡荡（"仅渲染骨架"），DevTools Network 也看不到 widget data 请求。详见 BUG-0049 Root Cause。

2. **widget endpoint 用相对路径，sandbox srcdoc iframe 解析失败**：AI 实际生成的 HTML 把 `window.__BEZEL_CONFIG__.widgets[].endpoint` 写成 `/api/dashboards/.../widgets/.../data`。`DashboardIframeShell` 用 `sandbox="allow-scripts"`（无 `allow-same-origin`）加 `srcDoc=html` 加载 iframe，iframe origin 为 `null`，base URL 为 `about:srcdoc`。相对 URL 在 `about:srcdoc` 下不可解析，`fetch` 直接抛错（被 catch 静默吞掉，或在第一种成因仍存在时根本走不到这里）。即使第一种成因修复后，仍会显示 "加载失败" 而非真实数据。

## Fix

**主修复（前端）**：`client/src/features/chat/components/markdown/markdown.tsx` 的 `decodeUtf8Base64` 改为基于 `TextDecoder('utf-8')` 的字节解码，参见 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) Fix 段。

**辅修复（后端）**：`DashboardController.serveHtml` 一处合并改动：

```java
String body = new String(maybe.get(), StandardCharsets.UTF_8)
    .replace("__BEZEL_SERVER_ORIGIN__", origin)
    .replace("\"/api/dashboards/", "\"" + origin + "/api/dashboards/")
    .replace("'/api/dashboards/", "'" + origin + "/api/dashboards/");
return ResponseEntity.ok()
    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
    .body(body);
```

- `charset=UTF-8` 是深度防御层：即使将来某处又往磁盘塞了非 ASCII 字节，serve 时不会再被 ISO-8859-1 重编
- 把字面量出现的 `"/api/dashboards/` 和 `'/api/dashboards/` 重写为 `"<origin>/api/dashboards/`，让 null-origin srcdoc iframe 中的 fetch 能拿到绝对 URL；CSP 替换后 `connect-src` 也指向同一 origin，widget data endpoint 自身已带 `@CrossOrigin(origins = "null")` 允许 null-origin 跨域

## Verification

端到端 curl 验证（与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 同一回合）：

```
$ curl -s .../api/dashboards/<id>/html | grep endpoint
window.__BEZEL_CONFIG__ = {
  "widgets":[
    {"id":"w_a","endpoint":"http://localhost:8080/api/dashboards/dash_x/widgets/w_a/data"},
    {"id":"w_b","endpoint":"http://localhost:8080/api/dashboards/dash_x/widgets/w_b/data"}
  ]
};
```

相对路径被改写为绝对 URL。配合 Content-Type charset，scheduler 不再因 SyntaxError 死掉，能真实发出 POST 拉到数据。

IT 测试：`DashboardControllerIT#serveHtmlPreservesUtf8AndRewritesRelativeEndpoints` 同时断言 charset 与 endpoint 重写。

## Notes

- 与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 区分：BUG-0049 是 HTML iframe 渲染链路（已能渲染真图，仅中文乱码）；本 BUG 是 JSON widget 渲染链路（连图都渲染不出 + 中文乱码）
- bezel skill 当前实际链路以 [BUG-0048](BUG-0048-dashboard-promote-v1-misses-html.md) 修复后的双 fenced block（JSON + HTML）为主，HTML 模式已能正常加载；JSON 模式因本 BUG 暂不可用
- 优先级 P1，因为 JSON 模式是 dashboard skill 的官方设计契约之一，HTML 模式只是 v1 兜底；JSON 模式失效意味着大屏长期依赖 AI 端 HTML 编译（≥7 分钟），无法走 server 编译加速路径
