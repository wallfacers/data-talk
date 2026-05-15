---
id: BUG-0049
title: bezel 大屏 HTML iframe 内中文字符显示乱码
status: fixed
priority: P2
source: manual-report
modules: [dashboard, chat, opencode]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

AI 通过 bezel skill 生成的 dashboard HTML 在 stage iframe 渲染后，所有中文字符（widget 标题、KPI 描述、轴 label、tooltip 等）显示成乱码（占位方块 / 问号 / 非预期字符）。HTML 本身的 ECharts 渲染、布局、颜色、动画都正常，仅 UTF-8 中文字面量被破坏。

## Reproduction Steps

1. chat 端要求 AI "做一个 ecommerce 大屏，按 bezel skill 的 Delivery contract，输出 dashboard 和 dashboard-html 两个 fenced block，HTML 部分基于 assets/templates/02-ecommerce.html"
2. AI 生成包含中文标题（如 "电商运营实时监控中心"）的 dashboard JSON + HTML
3. 点击 preview "打开到工作台" 触发 promote
4. stage tab 打开 → DashboardIframeShell 渲染 iframe srcDoc=HTML
5. 观察 iframe 内中文字符全部乱码

## Expected vs Actual

- **Expected**: iframe 内 widget 标题（如 "GMV 总额"、"实时订单监控"）等中文字符正常显示
- **Actual**: 中文位置显示为方块 / 问号 / 破坏的多字节序列，看起来像 UTF-8 字符被按单字节截断或重编码

## Environment

- Backend commit: 3f67eb44 (develop)
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium via playwright-cli

## Evidence

- BUG-0048 Verification 截图 `tmp/dashboard-fullscreen.png` — iframe 内 widget 排版完整、ECharts 真实渲染但中文标题乱码
- mount 的 `data-dashboard-html-b64` 编解码已确认走 `encodeUtf8Base64` / `decodeUtf8Base64`（兼容多字节，单字节 0xff 不会触发 atob 异常）
- 后端 `GET /api/dashboards/{id}/html` 返回 23524 字节 HTML，长度正常
- 因 iframe sandbox + srcDoc 注入，普通 HTML `<meta charset="UTF-8">` 应已被识别

## Root Cause

**真正根因在前端**：`client/src/features/chat/components/markdown/markdown.tsx` 第 65 行 `decodeUtf8Base64` 误用本地 `escape` 函数。

文件顶部行 42-46 定义了 module-local `escape(text)` —— HTML 实体转义器（`& < > " '` → 实体）。`decodeUtf8Base64` 的经典写法本应是 `decodeURIComponent(escape(atob(text)))`，但这里的 `escape` 指**全局 `escape()`**（把字节字符串中每个 codepoint 转 `%xx`，已 deprecated 但仍存在），不是 HTML 转义器。本地定义 shadow 了全局，于是：

1. `atob(b64)` 解出原始 UTF-8 字节串（每个 char codepoint 0–255）
2. 本地 HTML `escape` 对 ≥0x80 字节无作用（中文字节里没有 `& < > " '`）
3. `decodeURIComponent` 期待 `%xx` 形式，看到裸字节 0xE7 → 抛 URIError
4. catch 分支 fallback `return atob(text)` 直接返回字节字符串
5. promote 时该 latin1-风格字符串经 `JSON.stringify` → fetch 发出，浏览器把 codepoint 0xE7/0x94/0xB5 各自当独立字符按 UTF-8 编码 → 落盘双编码字节 `C3 A7 C2 94 C2 B5`

Node 复现（用同样的本地 escape 定义）：base64 = `55S15ZWG...`（电商运营 的 UTF-8 base64），OLD decode 字节 `c3a7c294c2b5...`，完全匹配磁盘 `dash_ksnn4qiu.dashboard.json` title 字段的字节。

**附带的后端薄弱点**：`DashboardController.serveHtml` 用 `MediaType.TEXT_HTML`（无 charset），Spring 6.2.7 `StringHttpMessageConverter.DEFAULT_CHARSET = ISO_8859_1`（已 javap 确认），即使前端发出干净 UTF-8 字节，serve 时仍会再被按 latin1 编码一次。属于深度防御层面也需修。

## Fix

**主修复（前端）**：`client/src/features/chat/components/markdown/markdown.tsx:65-72`

```tsx
function decodeUtf8Base64(text: string): string {
  try {
    const bin = atob(text)
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    return atob(text)
  }
}
```

用 `TextDecoder('utf-8')` 直接吃字节，避开 `escape` / `unescape` 与本地同名函数冲突的陷阱。

**辅修复（后端，深度防御 + 解决 BUG-0050 widget 取数）**：`DashboardController.serveHtml`

```java
String body = new String(maybe.get(), StandardCharsets.UTF_8)
    .replace("__BEZEL_SERVER_ORIGIN__", origin)
    .replace("\"/api/dashboards/", "\"" + origin + "/api/dashboards/")
    .replace("'/api/dashboards/", "'" + origin + "/api/dashboards/");
return ResponseEntity.ok()
    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
    .body(body);
```

## Verification

端到端 curl 验证（commit pending，本地 spring-boot:run）：

```
$ curl -i .../api/dashboards/<id>/html | head -8
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8     ← 修复后
...
$ curl -s .../api/dashboards/<id>/html | xxd | grep '<title>' -A1
<title>...
00000130: 746c 653e e794 b5e5 9586 e8bf 90e8 90a5  tle>............
```

`<title>` 后 12 字节 `E7 94 B5 / E5 95 86 / E8 BF 90 / E8 90 A5` = `电 / 商 / 运 / 营` 的 UTF-8 三字节序列，完整未损坏。

IT 测试覆盖：`DashboardControllerIT#serveHtmlPreservesUtf8AndRewritesRelativeEndpoints` 断言：
- 响应头 `Content-Type` 含 `charset=UTF-8`
- 中文字符在 body 字节流中以原始 UTF-8 出现，无 `?` 代换

## Notes

- 不阻塞 dashboard 主链路（iframe 真实渲染、widget 布局、ECharts 都工作）。属于 cosmetic 但用户感知严重
- 在 [BUG-0048](BUG-0048-dashboard-promote-v1-misses-html.md) Notes 中明确标记为独立 cosmetic 问题待 BUG-0049 收口
- 与 [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md) 共享"中文乱码"症状，但发生在不同渲染链路（HTML iframe 渲染 vs JSON widget 渲染），应独立追踪
