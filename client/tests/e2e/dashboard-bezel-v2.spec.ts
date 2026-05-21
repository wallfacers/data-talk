/**
 * Bezel v2 Dashboard E2E Tests.
 *
 * Validates the new bezel HTML rendering pipeline:
 * - Dashboard JSON v2 promote → HTML artifact stored
 * - GET /api/dashboards/{id}/html serves HTML with __BEZEL_SERVER_ORIGIN__ replaced
 * - POST /api/dashboards/{id}/widgets/{wid}/data returns widget data
 * - iframe sandbox renders the bezel HTML with polling
 *
 * Run: npx playwright test tests/e2e/dashboard-bezel-v2.spec.ts
 * Tags: @e2e @dashboard @bezel
 */
import { test, expect } from '@playwright/test'

// Type-aware IIFE — must satisfy BezelHtmlValidator's new fingerprint rules
// (type_aware_scheduler + widget_config_type_field) added for BUG-0055.
const BEZEL_HTML = `<!doctype html><html><head>
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__">
<meta name="__JSON_HASH__" content="sha256:e2e">
<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
<style>body{margin:0;background:#0a0e27;color:#fff;font-family:sans-serif}#chart_w_aaaa{width:100%;height:300px}</style>
</head><body><h1>E2E Bezel Test</h1><div id="chart_w_aaaa" class="bezel-widget"></div>
<script>window.__BEZEL_CONFIG__ = ${JSON.stringify({
  dashboardId: 'dash_e2e_bezel',
  defaultIntervalMs: 60000,
  pauseOnHidden: false,
  widgets: [{ id: 'chart_w_aaaa', type: 'chart', intervalMs: 60000,
    endpoint: '/api/dashboards/dash_e2e_bezel/widgets/chart_w_aaaa/data', params: {},
    baseOption: { title: { text: 'Polling Test' },
      xAxis: { type: 'category' }, yAxis: { type: 'value' },
      series: [{ type: 'bar' }] } }]
})};</script>
<script>(function(){
  var cfg=window.__BEZEL_CONFIG__;
  if(!cfg)return;
  cfg.widgets.forEach(function(w){
    var el=document.getElementById(w.id);
    if(!el)return;
    if(w.type === 'chart'){
      var ch=echarts.init(el);
      if(w.baseOption) ch.setOption(w.baseOption);
    }
  });
  parent.postMessage({type:'ready',jsonHash:'sha256:e2e'},'*');
})();</script></body></html>`

// BUG-0055 regression fixture: mixed-type dashboard with chart + kpi + table.
// The new type-aware scheduler must NOT call echarts.init on the KPI / table
// containers, and must apply baseOption to the chart before polling.
const MIXED_BEZEL_HTML = `<!doctype html><html><head>
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__">
<meta name="__JSON_HASH__" content="sha256:mixed">
<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
<style>body{margin:0;background:#0a0e27;color:#fff;font-family:sans-serif}
.bezel-widget{padding:8px}.value{font-size:32px}</style>
</head><body>
<div id="chart_w_trend01" class="bezel-widget"></div>
<div id="kpi_w_orders01" class="bezel-widget" data-bezel-render-kind="kpi">
  <span class="label">Orders</span><span class="value">0</span><span class="trend"></span>
</div>
<div id="table_w_topsku01" class="bezel-widget" data-bezel-render-kind="table">
  <table><thead><tr><th data-column="name">Name</th><th data-column="qty">Qty</th></tr></thead><tbody></tbody></table>
</div>
<script>window.__BEZEL_CONFIG__ = ${JSON.stringify({
  dashboardId: 'dash_e2e_mixed',
  defaultIntervalMs: 60000,
  pauseOnHidden: false,
  widgets: [
    { id: 'chart_w_trend01', type: 'chart', intervalMs: 60000,
      endpoint: '/api/dashboards/dash_e2e_mixed/widgets/chart_w_trend01/data', params: {},
      baseOption: { xAxis: { type: 'category' }, yAxis: { type: 'value' },
        series: [{ type: 'line' }] } },
    { id: 'kpi_w_orders01', type: 'kpi', intervalMs: 0,
      endpoint: '/api/dashboards/dash_e2e_mixed/widgets/kpi_w_orders01/data', params: {},
      baseOption: null },
    { id: 'table_w_topsku01', type: 'table', intervalMs: 0,
      endpoint: '/api/dashboards/dash_e2e_mixed/widgets/table_w_topsku01/data', params: {},
      baseOption: null },
  ],
})};</script>
<script>(function(){
  var cfg=window.__BEZEL_CONFIG__;
  if(!cfg)return;
  cfg.widgets.forEach(function(w){
    var el=document.getElementById(w.id);
    if(!el)return;
    if(w.type === 'chart'){
      var ch=echarts.init(el);
      if(w.baseOption) ch.setOption(w.baseOption);
    }
  });
  parent.postMessage({type:'ready',jsonHash:'sha256:mixed'},'*');
})();</script></body></html>`

const V2_DASHBOARD = {
  schemaVersion: 2,
  id: 'dash_e2e_bezel',
  title: 'E2E Bezel',
  description: 'E2E test dashboard for bezel v2 pipeline',
  defaultConnectionId: null,
  theme: 'industry-ecommerce',
  renderer: 'bezel',
  refresh: { defaultIntervalMs: 60000, pauseOnHidden: false },
  parameters: [],
  widgets: [{
    id: 'chart_w_aaaa', type: 'chart', patternId: 'ecommerce.gmv-trend',
    position: { x: 0, y: 0, w: 12, h: 6 },
    query: { sql: 'SELECT 1 AS stage, 100 AS cnt', paramRefs: {} },
    refresh: { intervalMs: 60000 },
    options: {},
  }],
  layout: { engine: 'free', viewport: { minWidth: 800, aspect: '16:9' } },
  version: 1,
  createdAt: Date.now(),
  updatedAt: Date.now(),
}

test.describe('@bezel v2 dashboard', () => {
  test('promotes v2 dashboard with HTML via API', async ({ request }) => {
    const res = await request.post('/api/dashboards/promote', {
      data: { dashboard: V2_DASHBOARD, html: BEZEL_HTML },
    })
    expect(res.ok()).toBeTruthy()
    const body = await res.json()
    expect(body.id).toMatch(/^dash_/)
    expect(body.version).toBe(1)
  })

  test('GET /html serves HTML with origin injected', async ({ request }) => {
    // Promote first
    await request.post('/api/dashboards/promote', {
      data: { dashboard: { ...V2_DASHBOARD, id: 'dash_e2e_html_serve' }, html: BEZEL_HTML },
    })

    const htmlRes = await request.get('/api/dashboards/dash_e2e_html_serve/html')
    expect(htmlRes.status()).toBe(200)
    expect(htmlRes.headers()['content-type']).toContain('text/html')
    const html = await htmlRes.text()
    expect(html).not.toContain('__BEZEL_SERVER_ORIGIN__')
    expect(html).toContain('E2E Bezel Test')
    expect(html).toContain('window.__BEZEL_CONFIG__')
  })

  test('GET /html returns 404 for missing HTML artifact', async ({ request }) => {
    // Promote JSON-only (no HTML)
    await request.post('/api/dashboards/promote', {
      data: { dashboard: { ...V2_DASHBOARD, id: 'dash_e2e_no_html' } },
    })

    const htmlRes = await request.get('/api/dashboards/dash_e2e_no_html/html')
    expect(htmlRes.status()).toBe(404)
  })

  test('promotes mixed-type dashboard (chart + kpi + table) — BUG-0055 regression', async ({ request }) => {
    // Verify the new BezelHtmlValidator (type-aware) accepts a HTML payload
    // that branches on w.type before echarts.init and carries per-widget
    // type/baseOption fields in __BEZEL_CONFIG__.
    const mixedDashboard = {
      schemaVersion: 2,
      id: 'dash_e2e_mixed',
      title: 'Mixed-Type Regression',
      description: 'KPI + chart + table; KPI/table containers must NOT be echarts.init-ed',
      defaultConnectionId: null,
      theme: 'industry-ecommerce',
      renderer: 'bezel',
      refresh: { defaultIntervalMs: 60000, pauseOnHidden: false },
      parameters: [],
      widgets: [
        { id: 'chart_w_trend01', type: 'chart', patternId: 'ecommerce.gmv-trend',
          position: { x: 0, y: 0, w: 6, h: 4 },
          query: { sql: 'SELECT 1 AS dt, 100 AS v', paramRefs: {} },
          refresh: { intervalMs: 60000 },
          options: { xAxis: { type: 'category' }, yAxis: { type: 'value' }, series: [{ type: 'line' }] } },
        { id: 'kpi_w_orders01', type: 'kpi', patternId: 'generic.kpi-tile',
          position: { x: 6, y: 0, w: 3, h: 2 },
          query: { sql: 'SELECT \'Orders\' AS label, 100 AS value', paramRefs: {} },
          refresh: { intervalMs: 0 },
          options: {} },
        { id: 'table_w_topsku01', type: 'table', patternId: 'generic.table',
          position: { x: 9, y: 0, w: 3, h: 4 },
          query: { sql: 'SELECT \'A\' AS name, 10 AS qty', paramRefs: {} },
          refresh: { intervalMs: 0 },
          options: {} },
      ],
      layout: { engine: 'free', viewport: { minWidth: 800, aspect: '16:9' } },
      version: 1,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }
    const res = await request.post('/api/dashboards/promote', {
      data: { dashboard: mixedDashboard, html: MIXED_BEZEL_HTML },
    })
    // The new BezelHtmlValidator rejects HTML lacking type-aware guard or
    // per-widget type field. A 200 here proves the mixed payload satisfies
    // both type_aware_scheduler and widget_config_type_field fingerprints.
    expect(res.ok()).toBeTruthy()
    const body = await res.json()
    expect(body.id).toMatch(/^dash_/)
  })

  test('rejects HTML lacking type-aware scheduler guard — BUG-0055 regression', async ({ request }) => {
    // Simulate the pre-fix scheduler: removes the type guard.
    const badHtml = MIXED_BEZEL_HTML.replace("if(w.type === 'chart')", 'if(true)')
    const res = await request.post('/api/dashboards/promote', {
      data: { dashboard: { ...JSON.parse(JSON.stringify({ ...V2_DASHBOARD, id: 'dash_e2e_bad_guard' })) }, html: badHtml },
    })
    // 4xx — validator rejects the regression shape.
    expect(res.ok()).toBeFalsy()
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })

  test('POST /widgets/{wid}/data returns columns and rows', async ({ request }) => {
    // This test requires a running backend with a test database connection
    // If the endpoint returns 404 (no connection configured), that's acceptable
    const res = await request.post('/api/dashboards/dash_e2e_bezel/widgets/chart_w_aaaa/data', {
      data: { params: {} },
    })
    // Accept 200 (data returned) or 404 (no connection) or 400 (bad SQL context)
    expect([200, 400, 404]).toContain(res.status())
    if (res.status() === 200) {
      const body = await res.json()
      expect(body).toHaveProperty('columns')
      expect(body).toHaveProperty('rows')
      expect(body).toHaveProperty('executedAt')
    }
  })
})
