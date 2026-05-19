# BUG-0073: Report Viewer iframe 字体 CORS 加载失败

**Status:** fixed
**Priority:** P1
**Discovered:** 2026-05-19
**Module:** report
**Source:** e2e-playwright
**fixCommit:** 45e9b44e + 6fc75c0b（CorsConfig 加 null-origin 规则 + ReportController 移除重复 ACAO 头）
**fixPlanRef:** openspec/changes/ledger-report-quality-fixes/

## Description

Report Viewer 的 iframe (sandbox=allow-scripts, srcdoc) 中加载 `/api/reports/_assets/fonts/NotoSerifSC-Regular.otf` 和 `NotoSansSC-Regular.otf` 被 CORS 策略阻止，控制台报错：

```
Access to font at 'http://localhost:1420/api/reports/_assets/fonts/NotoSerifSC-Regular.otf'
from origin 'null' has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

iframe 使用 srcdoc 模式时 origin 为 `null`，服务端 `_assets/**` 端点没有返回 `Access-Control-Allow-Origin: *` 头。

## Impact

- 报告 HTML 回退到系统默认字体，不使用思源宋体/黑体
- 当前中文字体仍可渲染（浏览器 fallback），但排版效果与设计语言 spec 不符
- PDF 生成使用的是服务端 Chromium 直接加载本地文件，不受 CORS 影响，所以 PDF 字体正常

## Steps to Reproduce

1. 生成一份 ledger 报告（或直接访问 `/api/reports/{id}/download/html`）
2. 在 Report Viewer tab 中打开报告
3. 浏览器控制台出现 4 条 CORS 错误

## Expected Behavior

iframe 中字体正常加载，报告使用思源宋体/黑体渲染。

## Suggested Fix

在 `ReportController.serveAsset()` 响应头中添加：

```java
headers.set("Access-Control-Allow-Origin", "*");
```

## Regression Risk

与 BUG-0049（bezel 大屏中文乱码）同源风险，均与 iframe + 字体加载有关。

## Fix Verification

**修复历经两步**，第一步不完整：

1. **`4f7cfffe`（不完整）**：`ReportController.serveAsset` 在 controller 内手动 set `Access-Control-Allow-Origin: *` + `Vary: Origin`。**这一步在浏览器场景下不生效** —— Spring `CorsFilter`（`CorsConfig`）在 controller 前已拦截 `Origin: null` 请求，全局 `/api/**` 的 `uiConfig` 只 allow `http://localhost:*`，对 null origin 直接返回 403。Controller 设的头根本到不了客户端。
2. **`45e9b44e`（真正修复）**：在 `CorsConfig` 中新增 `/api/reports/_assets/**` → iframeConfig 规则（GET/POST/OPTIONS，allow `Origin: null`，无 credentials）。CorsFilter 现在 set `Access-Control-Allow-Origin: null` 通过。
3. **`6fc75c0b`（清理双 ACAO）**：从 `ReportController.serveAsset` 移除 `4f7cfffe` 加的 controller-level ACAO + Vary —— 否则 CorsFilter set `null` 与 controller set `*` 叠加，浏览器报 `multiple values 'null, *'`。

**Runtime curl 验证（`6fc75c0b` 部署后）：**

```
$ curl -s -o /dev/null -w "%{http_code} %{header_json}" \
       -H 'Origin: null' \
       http://localhost:8080/api/reports/_assets/fonts/NotoSerifSC-Regular.otf
200 {"vary":["Origin","Access-Control-Request-Method","Access-Control-Request-Headers"],
     "access-control-allow-origin":["null"],
     "content-type":["font/otf"], ...}
```

仅一个 `Access-Control-Allow-Origin: null`。CSS 与 JS endpoint 同。

**Playwright E2E 验证**（fixture: `tmp/e2e-bug-0073/host.html`，sandbox iframe srcdoc 加载 `@font-face` 与 `fetch`）：

- 三个 `_assets` endpoint（font / css / js）在 origin=null iframe 中均 `status: 200, ok: true`
- iframe body `font-family: NotoSerifSC, serif` —— 字体真正加载
- iframe console 仅 1 个无关 favicon 404 error，0 个 CORS error

**单元测试**（`ReportControllerCorsTest`）：用 @WebMvcTest + `addFilters=false`，不走 CorsFilter，因此只断言 content-type 等 controller 直接行为；CORS 行为由 runtime + Playwright 验证。

**Curl quirk**：`curl -I`（HEAD）会被 iframeConfig 拒（allowed methods 不含 HEAD）。这不影响生产 — 浏览器 fetch font 用 GET，不用 HEAD。
