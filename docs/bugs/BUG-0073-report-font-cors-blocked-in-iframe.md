# BUG-0073: Report Viewer iframe 字体 CORS 加载失败

**Status:** fixed
**Priority:** P1
**Discovered:** 2026-05-19
**Module:** report
**Source:** e2e-playwright
**fixCommit:** 4f7cfffe
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

- 修改 `ReportController.serveAsset()` 在所有返回路径（fonts/CSS/JS/fallback）显式 set `Access-Control-Allow-Origin: *` + `Vary: Origin`。
- 新增 `ReportControllerCorsTest`（@WebMvcTest）断言 `.otf` 与 `.css` 端点响应头包含上述两个 header（2/2 pass）。
- Runtime curl 验证（commit 4f7cfffe 部署后）：
  ```
  $ curl -sI http://localhost:8080/api/reports/_assets/fonts/NotoSerifSC-Regular.otf
  HTTP/1.1 200
  Vary: Origin
  Access-Control-Allow-Origin: *
  Content-Type: font/otf
  ```
  同理 `_assets/styles/ledger.css` 与 `_assets/scripts/echarts.min.js` 均返回 `Access-Control-Allow-Origin: *`。
- 仅 `/_assets/**` 路径打开 CORS，其他 API 端点不受影响（per spec）。
