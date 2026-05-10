/**
 * Dashboard payload factories for E2E contract tests.
 *
 * Produces well-formed dashboard JSON that matches the adapter's
 * DashboardArtifactService schema (schemaVersion 1, 12-col grid, etc.).
 */

import type { Dashboard } from '../../../src/features/dashboard/schema'

/**
 * Build a minimal valid dashboard payload.
 * Pass `overrides` to replace any top-level field.
 */
export function makeDashboardPayload(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    schemaVersion: 1,
    id: `dash_e2e_${Date.now()}`,
    title: 'E2E Dashboard',
    description: 'Dashboard created by Playwright contract tests',
    defaultConnectionId: null,
    parameters: [],
    widgets: [
      {
        id: 'chart_w_e2e1',
        type: 'chart',
        position: { x: 0, y: 0, w: 6, h: 4 },
        query: { sql: 'SELECT 1 AS value', paramRefs: {} },
        options: {
          title: 'Metric',
          echartsOption: { xAxis: { type: 'category' }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: [1] }] },
          dataMapping: { rowsAsDataset: true },
        },
      },
      {
        id: 'markdown_w_e2e1',
        type: 'markdown',
        position: { x: 6, y: 0, w: 6, h: 3 },
        options: { text: '## Notes' },
      },
    ],
    layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
    version: 1,
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  } as Dashboard
}

/**
 * Build a realistic e-commerce operations dashboard with business KPIs,
 * sales trends, category breakdowns, payment distribution, and regional analysis.
 */
export function makeEcommerceDashboardPayload(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    schemaVersion: 1,
    id: `dash_ecommerce_${Date.now()}`,
    title: '电商运营大屏',
    description: '实时电商核心指标看板 — GMV / 订单 / 品类 / 地域',
    defaultConnectionId: null,
    parameters: [],
    widgets: [
      // ── Header ──
      {
        id: 'markdown_w_header',
        type: 'markdown',
        position: { x: 0, y: 0, w: 12, h: 1 },
        options: { text: '# 🛒 电商运营中心 · 实时看板', textAlign: 'center' },
      },

      // ── KPI Cards (4 × 3-col row) ──
      {
        id: 'markdown_w_gmvcard',
        type: 'markdown',
        position: { x: 0, y: 1, w: 3, h: 1 },
        options: { text: '### 💰 今日 GMV\n**¥1,285,600**\n环比 +12.3%', textAlign: 'center' },
      },
      {
        id: 'markdown_w_orders',
        type: 'markdown',
        position: { x: 3, y: 1, w: 3, h: 1 },
        options: { text: '### 📦 今日订单\n**18,432**\n环比 +8.7%', textAlign: 'center' },
      },
      {
        id: 'markdown_w_buyers',
        type: 'markdown',
        position: { x: 6, y: 1, w: 3, h: 1 },
        options: { text: '### 👤 活跃买家\n**52,108**\n环比 +5.1%', textAlign: 'center' },
      },
      {
        id: 'markdown_w_conversion',
        type: 'markdown',
        position: { x: 9, y: 1, w: 3, h: 1 },
        options: { text: '### 📈 转化率\n**4.82%**\n环比 -0.3pp', textAlign: 'center' },
      },

      // ── GMV Monthly Trend (full-width line chart) ──
      {
        id: 'chart_w_gmvtrend',
        type: 'chart',
        position: { x: 0, y: 2, w: 12, h: 4 },
        query: {
          sql: "SELECT DATE_FORMAT(created_at,'%Y-%m') AS month, SUM(pay_amount)/100 AS gmv, COUNT(*) AS orders FROM orders WHERE created_at >= '2025-01-01' GROUP BY month ORDER BY month",
          paramRefs: {},
        },
        options: {
          title: '月度 GMV 趋势',
          echartsOption: {
            tooltip: { trigger: 'axis' },
            legend: { data: ['GMV', '订单量'], bottom: 0 },
            grid: { top: 32, bottom: 48, left: 64, right: 64 },
            xAxis: {
              type: 'category',
              data: ['2025-01', '2025-02', '2025-03', '2025-04', '2025-05', '2025-06', '2025-07', '2025-08', '2025-09', '2025-10', '2025-11', '2025-12'],
            },
            yAxis: [
              { type: 'value', name: 'GMV (万元)', axisLabel: { formatter: '{value}' } },
              { type: 'value', name: '订单量', axisLabel: { formatter: '{value}' } },
            ],
            series: [
              {
                name: 'GMV',
                type: 'line',
                smooth: true,
                areaStyle: { opacity: 0.25 },
                data: [320, 285, 410, 395, 480, 520, 590, 610, 550, 680, 920, 1050],
                yAxisIndex: 0,
              },
              {
                name: '订单量',
                type: 'bar',
                barWidth: 16,
                data: [4200, 3800, 5500, 5100, 6200, 6800, 7500, 7800, 7100, 8800, 12000, 13500],
                yAxisIndex: 1,
              },
            ],
          },
          dataMapping: { rowsAsDataset: true },
          emphasis: 'cobalt',
        },
      },

      // ── Order Status Distribution (doughnut chart) ──
      {
        id: 'chart_w_orderstatus',
        type: 'chart',
        position: { x: 0, y: 6, w: 6, h: 4 },
        query: {
          sql: "SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status",
          paramRefs: {},
        },
        options: {
          title: '订单状态分布',
          echartsOption: {
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: { orient: 'vertical', left: 'left', top: 'center' },
            series: [
              {
                name: '订单状态',
                type: 'pie',
                radius: ['40%', '70%'],
                avoidLabelOverlap: false,
                itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
                label: { show: true, formatter: '{b}\n{d}%' },
                data: [
                  { value: 12500, name: '已支付' },
                  { value: 3200, name: '待支付' },
                  { value: 8100, name: '已发货' },
                  { value: 6800, name: '已完成' },
                  { value: 850, name: '已取消' },
                ],
              },
            ],
          },
          dataMapping: { rowsAsDataset: true },
          emphasis: 'amber',
        },
      },

      // ── Top Categories (horizontal bar chart) ──
      {
        id: 'chart_w_category',
        type: 'chart',
        position: { x: 6, y: 6, w: 6, h: 4 },
        query: {
          sql: "SELECT c.name, SUM(oi.quantity * oi.unit_price) AS sales FROM order_items oi JOIN products p ON oi.product_id = p.id JOIN categories c ON p.category_id = c.id GROUP BY c.name ORDER BY sales DESC LIMIT 10",
          paramRefs: {},
        },
        options: {
          title: 'TOP10 品类销售额',
          echartsOption: {
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            grid: { top: 16, bottom: 16, left: 80, right: 32 },
            xAxis: { type: 'value' },
            yAxis: {
              type: 'category',
              data: ['数码配件', '家居用品', '食品饮料', '美妆护肤', '运动户外', '母婴用品', '服饰鞋包', '图书文具', '生鲜水果', '手机通讯'],
            },
            series: [
              {
                name: '销售额',
                type: 'bar',
                barWidth: 14,
                itemStyle: { borderRadius: [0, 4, 4, 0] },
                label: { show: true, position: 'right', formatter: '¥{c}' },
                data: [86000, 125000, 158000, 192000, 210000, 245000, 289000, 325000, 386000, 452000],
              },
            ],
          },
          dataMapping: { rowsAsDataset: true },
          emphasis: 'cobalt',
        },
      },

      // ── Payment Methods (pie chart) ──
      {
        id: 'chart_w_payment',
        type: 'chart',
        position: { x: 0, y: 10, w: 4, h: 3 },
        query: {
          sql: "SELECT payment_method, COUNT(*) AS cnt FROM orders WHERE status != 'cancelled' GROUP BY payment_method",
          paramRefs: {},
        },
        options: {
          title: '支付方式占比',
          echartsOption: {
            tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
            legend: { bottom: 0 },
            series: [
              {
                name: '支付方式',
                type: 'pie',
                radius: '65%',
                center: ['50%', '45%'],
                data: [
                  { value: 28500, name: '支付宝' },
                  { value: 22000, name: '微信支付' },
                  { value: 8200, name: '银联' },
                  { value: 5600, name: '花呗分期' },
                  { value: 3100, name: '银行卡' },
                ],
              },
            ],
          },
          dataMapping: { rowsAsDataset: true },
        },
      },

      // ── Regional Sales Map (scatter / map-like bar) ──
      {
        id: 'chart_w_region',
        type: 'chart',
        position: { x: 4, y: 10, w: 4, h: 3 },
        query: {
          sql: "SELECT province, SUM(pay_amount)/100 AS sales FROM orders JOIN users ON orders.user_id = users.id GROUP BY province ORDER BY sales DESC LIMIT 8",
          paramRefs: {},
        },
        options: {
          title: 'TOP8 省份销售额',
          echartsOption: {
            tooltip: { trigger: 'axis' },
            grid: { top: 16, bottom: 32, left: 56, right: 16 },
            xAxis: { type: 'value', name: '万元' },
            yAxis: {
              type: 'category',
              data: ['四川', '湖北', '河南', '山东', '浙江', '江苏', '广东'],
            },
            series: [
              {
                name: '销售额',
                type: 'bar',
                data: [128, 156, 178, 205, 268, 312, 385],
                itemStyle: { color: '#6366f1' },
              },
            ],
          },
          dataMapping: { rowsAsDataset: true },
        },
      },

      // ── Daily Orders Trend (line chart with zoom) ──
      {
        id: 'chart_w_dailyorders',
        type: 'chart',
        position: { x: 8, y: 10, w: 4, h: 3 },
        query: {
          sql: "SELECT DATE(created_at) AS day, COUNT(*) AS orders FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY day ORDER BY day",
          paramRefs: {},
        },
        options: {
          title: '近30天订单趋势',
          echartsOption: {
            tooltip: { trigger: 'axis' },
            grid: { top: 24, bottom: 48, left: 48, right: 16 },
            xAxis: {
              type: 'category',
              data: Array.from({ length: 30 }, (_, i) => `05-${String(i + 1).padStart(2, '0')}`),
            },
            yAxis: { type: 'value', name: '订单' },
            series: [
              {
                name: '日订单',
                type: 'line',
                smooth: true,
                symbol: 'none',
                data: [420, 380, 450, 510, 490, 520, 580, 620, 550, 480, 510, 560, 620, 680, 720, 650, 590, 630, 700, 750, 810, 760, 720, 780, 850, 920, 880, 840, 900, 950],
              },
            ],
            dataZoom: [{ type: 'inside', start: 60, end: 100 }],
          },
          dataMapping: { rowsAsDataset: true },
        },
      },

      // ── Footer ──
      {
        id: 'markdown_w_footer',
        type: 'markdown',
        position: { x: 0, y: 13, w: 12, h: 1 },
        options: { text: '数据来源: DataTalk AI 引擎 · 更新时间: 2026-05-11 14:30:00', textAlign: 'center' },
      },
    ],
    layout: { engine: 'grid', cols: 12, rowHeight: 36, gap: 8 },
    version: 1,
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  } as Dashboard
}

/**
 * Build a dashboard payload that exceeds the 256 KB server limit.
 * Uses many small widgets with padding data to push the serialized JSON
 * past 256 * 1024 bytes while keeping each widget individually valid.
 */
export function makeOversizedDashboardPayload(): Dashboard {
  // Build ~20 widgets each ~15 KB of padding => ~300 KB total, well past 256 KB.
  const widgets: Dashboard['widgets'] = Array.from({ length: 20 }, (_, i) => ({
    id: `markdown_w_ovrsz${String(i).padStart(4, '0')}`,
    type: 'markdown' as const,
    position: { x: (i % 4) * 3, y: Math.floor(i / 4) * 3, w: 3, h: 3 },
    options: { text: `## Block ${i}\n` + 'X'.repeat(14 * 1024) },
  }))

  return makeDashboardPayload({
    id: `dash_e2e_oversized_${Date.now()}`,
    title: 'Oversized E2E Dashboard',
    widgets,
  })
}
