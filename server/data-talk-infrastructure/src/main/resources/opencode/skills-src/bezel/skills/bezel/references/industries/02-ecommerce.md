# 02 Ecommerce — 电商运营实时监控中心

## 业务上下文
- **核心 KPI**: 今日 GMV(万), 订单量(万), 转化率(%), 客单价(元)
- **分析维度**: 转化漏斗(UV->详情页->加购->支付), 渠道 GMV 占比, 品类销售排行 TOP10, 客单价分布, 全国订单热力分布, 用户留存 Cohort, 品类关联购买矩阵, 实时 GMV 趋势
- **典型用户**: 电商运营总监、活动运营、品类经理

## 风格与颜色覆盖

- **映射风格:** Mosaic (`references/styles/mosaic.md`)
- `--bezel-bg-app`: `#0d0a07`
- `--bezel-accent-primary`: `#ff4444` (red)
- `--bezel-accent-secondary`: `#ffc107` (gold)

## 典型 widget pattern（8 个）

### ecommerce.funnel-gradient
- **When to use**: Conversion funnel with gradient bars (UV -> Detail -> Cart -> Pay).
- **HTML fragment**:
```html
<div class="funnel-wrap">
  <div class="funnel-step">
    <div class="funnel-bar" style="height:90%; background:linear-gradient(135deg,rgba(255,68,68,0.85),rgba(255,107,53,0.85))">
      <div>UV</div><small>286万</small>
    </div>
  </div>
  <div class="funnel-arrow"><div class="arr">→</div><div>25.2%</div></div>
  <!-- repeat steps -->
</div>
```
- **SQL output columns**: `step_name TEXT, value NUMERIC, conversion_pct NUMERIC`
- **refresh.intervalMs**: 10000

### ecommerce.pie-channel
- **When to use**: Channel/platform GMV share as donut chart.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['42%', '68%'], center: ['50%', '55%'],
    itemStyle: { borderRadius: 6, borderColor: '#0d0a07', borderWidth: 2 },
    label: { color: '#8899aa', formatter: '{b}\n{d}%' },
    data: [
      { value: 982, name: '淘宝', itemStyle: { color: '#ff4444' } },
      { value: 625, name: '京东', itemStyle: { color: '#ff6b35' } },
      // ...
    ]
  }]
}
```
- **SQL output columns**: `channel_name TEXT, gmv NUMERIC`
- **refresh.intervalMs**: 30000

### ecommerce.heatmap-cohort
- **When to use**: User retention cohort matrix (registration date x retention day).
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: cohortDates },
  yAxis: { type: 'category', data: cohortDays },
  visualMap: { min: 0, max: 100, inRange: { color: ['#1a0f0a', '#3e1a12', '#7a2e1e', '#b83e28', '#e05038', '#ff4444', '#ff6b35', '#ffc107'] } },
  series: [{ type: 'heatmap', data: [[dateIdx, dayIdx, retentionPct], ...],
    label: { show: true, formatter: p => p.value[2] + '%' } }]
}
```
- **SQL output columns**: `cohort_date DATE, retention_day INT, retention_pct NUMERIC`
- **refresh.intervalMs**: 300000

### ecommerce.heatmap-matrix
- **When to use**: Category cross-purchase correlation matrix.
- **ECharts option fragment**: Same structure as cohort heatmap but with category names on both axes.
- **SQL output columns**: `category_a TEXT, category_b TEXT, correlation NUMERIC(0-1)`
- **refresh.intervalMs**: 300000

### ecommerce.bar-category-rank
- **When to use**: Horizontal bar chart for category sales ranking.
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: categories },
  series: [{
    type: 'bar',
    itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#ff6b35' }, { offset: 1, color: '#ff4444' }]) },
    label: { show: true, position: 'right', formatter: '{c}万' }
  }]
}
```
- **SQL output columns**: `category_name TEXT, sales_amount NUMERIC`
- **refresh.intervalMs**: 30000

### ecommerce.bar-line-gmv-trend
- **When to use**: Dual-axis chart — GMV bars + order count line over 24h.
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: 'GMV(万)' },
    { type: 'value', name: '订单量(万)' }
  ],
  series: [
    { name: 'GMV', type: 'bar', itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#ff4444' }, { offset: 1, color: 'rgba(255,68,68,0.15)' }]) } },
    { name: '订单量', type: 'line', yAxisIndex: 1, smooth: true, lineStyle: { color: '#ffc107' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(255,193,7,0.25)' }, { offset: 1, color: 'rgba(255,193,7,0)' }]) } }
  ]
}
```
- **SQL output columns**: `hour INT, gmv NUMERIC, order_count NUMERIC`
- **refresh.intervalMs**: 5000

### ecommerce.gauge-target
- **When to use**: Promotional target achievement gauge (0-100%).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', startAngle: 200, endAngle: -20, min: 0, max: 100,
    itemStyle: { color: '#ff4444' },
    pointer: { itemStyle: { color: '#ffc107' } },
    axisLine: { lineStyle: { color: [[0.5, '#5c6bc0'], [0.8, '#ff6b35'], [1, '#ff4444']] } },
    detail: { formatter: '{value}%', color: '#ffc107', fontSize: 22 }
  }]
}
```
- **SQL output columns**: `target_name TEXT, current_value NUMERIC, target_value NUMERIC`
- **refresh.intervalMs**: 10000

### ecommerce.map-order-heatmap
- **When to use**: China map with province-level order volume coloring.
- **ECharts option fragment**:
```js
{
  visualMap: { inRange: { color: ['#1a0f0a', '#3e1a12', '#7a2e1e', '#b83e28', '#e05038', '#ff4444', '#ff6b35', '#ffc107'] } },
  geo: { map: 'china', itemStyle: { areaColor: '#1a120e', borderColor: 'rgba(255,68,68,0.25)' } },
  series: [{ type: 'map', map: 'china', data: [{ name: '广东', value: 28500 }, ...] }]
}
```
- **SQL output columns**: `province_name TEXT, order_count INT`
- **refresh.intervalMs**: 30000

## 触发线索（AI 用）
- User mentions "GMV", "转化率", "客单价", "漏斗", "留存", "电商", "购物", "订单", "复购", "渠道" -> select this industry
- Table name contains `order`, `gmv`, `cart`, `payment`, `channel`, `ecommerce`, `shop` -> strong signal
- Columns like `sku`, `spu`, `uv`, `pv`, `conversion_rate`, `basket_price` -> strong signal
