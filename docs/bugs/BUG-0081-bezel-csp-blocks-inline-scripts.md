# BUG-0081: Bezel CSP blocks inline scripts (config + scheduler)

| Field | Value |
|-------|-------|
| ID | BUG-0081 |
| Status | verified |
| Severity | high |
| Module | bezel-compiler |
| Discovered | 2026-05-21 |
| Discoverer | E2E test (Playwright) |
| FixCommit | c149569b |

## Summary

The compiled dashboard HTML contains inline `<script>` tags (`window.__BEZEL_CONFIG__` and the scheduler IIFE), but the Content-Security-Policy `script-src` directive does not include `'unsafe-inline'`. This causes the browser to block both scripts, preventing ECharts initialization and widget rendering.

## Reproduction

1. Promote a dashboard via `POST /api/dashboards/promote`
2. Open `GET /api/dashboards/{id}/html` in a browser
3. Console errors:
   - `Executing inline script violates the following Content Security Policy directive 'script-src 'self' http://localhost:8080'`
   - Both the config script (line ~260) and scheduler IIFE (line ~261) are blocked

## Root Cause

`CspInjector` generates `script-src 'self' ${origin}` without `'unsafe-inline'`.
`DashboardCompiler` (via `WidgetCompiler` + `SchedulerBundler`) emits:
1. `<script>window.__BEZEL_CONFIG__ = {...};</script>` — inline config
2. `<script>(function() { ... scheduler code ... })();</script>` — inline IIFE

Both violate the CSP.

## Fix Plan

1. Change config injection to `<script type="application/json" id="__BEZEL_CONFIG__">...</script>` (CSP does not block JSON data blocks)
2. Externalize scheduler: emit `<script src="${origin}/bezel/scheduler.js"></script>` instead of inlining
3. Update `scheduler.js` to read config via `JSON.parse(document.getElementById('__BEZEL_CONFIG__').textContent)`

## Fix Applied

- 6 template HTML files: replaced inline scripts with JSON data block + external scheduler script
- `scheduler.js`: reads config from `document.getElementById('__BEZEL_CONFIG__')`
- `DashboardCompiler.java`: removed `__SCHEDULER_IIFE__` placeholder replacement
- `HtmlValidator.java`: updated patterns for new format
- `DashboardGoldenFileTest.java`: updated assertions
- All 18 golden-file tests pass
- **Follow-up fix (was missing)**: externalizing the scheduler reference alone left `/bezel/scheduler.js` returning 404 — the file was never served (only `static/bezel/echarts.min.js` + `geo/` existed, and there was no resource handler). Added `WebMvcConfig.addResourceHandlers` mapping `/bezel/**` → `classpath:/static/bezel/` + `classpath:/dashboard/renderers/`. The handler pattern must use `/**` wildcard so Spring's `PathMatcher.extractPathWithinPattern()` strips the prefix correctly. Without this the runtime was still broken (skeleton renders, no charts/polling). Golden/unit tests did not catch it because they never load the HTML in a browser.
- Server origin for `scheduler.js` is now carried CSP-safely in the `__BEZEL_CONFIG__` JSON data block (`serverOrigin`, replaced at serve time), since an inline `<script>` assigning `window.__BEZEL_SERVER_ORIGIN__` would itself violate the CSP.

## Evidence

- Console output from Playwright browser at `http://localhost:8080/api/dashboards/dash_e08ri2v2/html`
- After fix: zero console errors, zero CSP violations
- Dashboard renders correctly: ecommerce dark theme, KPI cards, ECharts charts
- CSP header: `script-src 'self' http://localhost:8080` (no `'unsafe-inline'`)

## Verification

**独立复验（2026-05-21，playwright-cli，后端重建 `mvn install -pl data-talk-adapter -am` + 重启后）**：经当前编译器 promote 一个 KPI+chart+table 大屏（`dash_eaj4eeze`，三列模板，H2 常量 SELECT），浏览器加载 `GET /{id}/html`。`playwright-cli console` 断言 **0 errors / 0 warnings**——外置 `scheduler.js` 正常执行（发起了 widget data 轮询请求），`__BEZEL_CONFIG__` JSON 数据块未被 CSP 拦截，无任何 CSP 违规。截图 `tmp/bezel-verify/dash_eaj4eeze-verified.png`。
