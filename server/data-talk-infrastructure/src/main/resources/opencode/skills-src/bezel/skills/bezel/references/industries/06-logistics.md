# 06 Logistics — 物流供应链监控中心

## 业务上下文
- **核心 KPI**: 在途运单, 准时送达率(%), 今日发货(万), 平均时效(h)
- **分析维度**: 运输方式占比(公路/铁路/航空/水运), 异常订单分析(延误/破损/丢失/拒收), 配送满意度(仪表盘), 全国物流节点分布(地图), 发货量&签收时效趋势, 仓库利用率, 最后一公里时效分布, 供应链风险预警矩阵
- **典型用户**: 物流总监, 供应链经理, 仓储主管, 配送站长

## 视觉签名
- Primary color `#0ea5e9` (sky blue) / Secondary color `#f97316` (orange) / Background `#0a0e1a`
- Grid background: dual-layer cyan grid (20px + 80px spacing, 6%/3% opacity)
- Header: gradient title shine animation `#0ea5e9 -> #38bdf8`
- KPI cards: hexagonal clip-path `polygon(0 0, calc(100% - 20px) 0, 100% 50%, ...)`
- Panel top-edge: 2px gradient line `transparent -> #0ea5e9 -> transparent`
- Story bar: data insight strip with colored highlights
- ECharts theme: transparent bg, `#cbd5e1` text, `#94a3b8` labels, cyan tooltip border

## 推荐布局骨架
- Header: 72px — gradient title + time
- Story bar: 36px — insight highlights
- KPI bar: 100px — 4 hexagonal cards
- Body: flex, left 26% + center 74%
- Bottom bar: 180px — 3 charts in row

## 典型 widget pattern（8 个）

### logistics.pie-transport
- **When to use**: Transport mode share (road/rail/air/water).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
    data: [
      { value: 58, name: '公路', itemStyle: { color: '#0ea5e9' } },
      { value: 22, name: '铁路', itemStyle: { color: '#22c55e' } },
      { value: 12, name: '航空', itemStyle: { color: '#f97316' } },
      { value: 8, name: '水运', itemStyle: { color: '#8b5cf6' } }
    ]
  }]
}
```
- **SQL output columns**: `transport_mode TEXT, shipment_pct NUMERIC`
- **refresh.intervalMs**: 60000

### logistics.h-bar-abnormal
- **When to use**: Abnormal order analysis by type (delay/damaged/lost/refused).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: ['延误', '破损', '丢失', '拒收'] },
  series: [{
    type: 'bar', barWidth: 14,
    data: [
      { value: 342, itemStyle: { color: '#f97316' } },
      { value: 128, itemStyle: { color: '#ef4444' } },
      { value: 56, itemStyle: { color: '#dc2626' } },
      { value: 89, itemStyle: { color: '#f59e0b' } }
    ]
  }]
}
```
- **SQL output columns**: `anomaly_type TEXT, order_count INT`
- **refresh.intervalMs**: 30000

### logistics.gauge-satisfaction
- **When to use**: Delivery satisfaction gauge (0-100).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', radius: '90%', startAngle: 200, endAngle: -20,
    axisLine: { lineStyle: { color: [[0.6, '#ef4444'], [0.8, '#f97316'], [0.9, '#0ea5e9'], [1, '#22c55e']] } },
    detail: { formatter: '{value}', color: '#22c55e', fontSize: 24 },
    data: [{ value: 96.8, name: '满意度评分' }]
  }]
}
```
- **SQL output columns**: `metric_name TEXT, score NUMERIC`
- **refresh.intervalMs**: 60000

### logistics.map-route-nodes
- **When to use**: China map with effect scatter nodes + animated route lines.
- **ECharts option fragment**:
```js
{
  geo: { map: 'china', roam: true, itemStyle: { areaColor: 'rgba(14,165,233,0.08)', borderColor: 'rgba(14,165,233,0.3)' } },
  series: [
    { type: 'effectScatter', coordinateSystem: 'geo', symbolSize: v => Math.sqrt(v[2]) / 1.8,
      rippleEffect: { brushType: 'stroke', scale: 3 }, itemStyle: { color: '#0ea5e9' } },
    { type: 'lines', effect: { show: true, trailLength: 0.6, color: '#f97316' },
      lineStyle: { color: '#f97316', width: 0.8, curveness: 0.2 } }
  ]
}
```
- **SQL output columns**: `city_name TEXT, lng NUMERIC, lat NUMERIC, volume INT, from_city TEXT, to_city TEXT`
- **refresh.intervalMs**: 30000

### logistics.bar-line-shipment-trend
- **When to use**: Monthly shipment bars + delivery time line (inverted Y-axis).
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: '发货量(万)' },
    { type: 'value', name: '时效(h)', inverse: true }
  ],
  series: [
    { name: '发货量', type: 'bar', itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#0ea5e9' }, { offset: 1, color: 'rgba(14,165,233,0.3)' }]) } },
    { name: '签收时效', type: 'line', yAxisIndex: 1, smooth: true, lineStyle: { color: '#f97316' } }
  ]
}
```
- **SQL output columns**: `month TEXT, shipment_count NUMERIC, delivery_hours NUMERIC`
- **refresh.intervalMs**: 300000

### logistics.bar-warehouse
- **When to use**: Warehouse utilization rate by region.
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: warehouseNames },
  yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
  series: [{ type: 'bar',
    data: warehouses.map(w => ({ value: w.pct, itemStyle: { color: w.pct > 85 ? gradient('#ef4444') : gradient('#22c55e') } }))
  }]
}
```
- **SQL output columns**: `warehouse_name TEXT, utilization_pct NUMERIC`
- **refresh.intervalMs**: 60000

### logistics.h-bar-lastmile
- **When to use**: Last-mile delivery time distribution (same-day/next-day/2-3day/3+).
- **SQL output columns**: `delivery_window TEXT, order_pct NUMERIC`
- **refresh.intervalMs**: 60000

### logistics.scatter-risk-matrix
- **When to use**: Supply chain risk bubble chart (probability x impact x loss).
- **ECharts option fragment**:
```js
{
  xAxis: { name: '发生概率' }, yAxis: { name: '影响度' },
  series: [{
    type: 'scatter', symbolSize: d => Math.max(8, d[2] / 10000),
    data: [[prob, impact, loss, riskName], ...],
    itemStyle: { color: p => (p.data[0]*p.data[1] > 60 ? '#ef4444' : p.data[0]*p.data[1] > 30 ? '#f97316' : '#0ea5e9') }
  }]
}
```
- **SQL output columns**: `risk_name TEXT, probability NUMERIC(0-10), impact NUMERIC(0-10), potential_loss INT`
- **refresh.intervalMs**: 300000

## 触发线索（AI 用）
- User mentions "运单", "发货", "签收", "物流", "仓库", "时效", "配送", "供应链", "快递", "最后一公里" -> select this industry
- Table name contains `shipment`, `warehouse`, `logistics`, `delivery`, `route`, `order_tracking`, `supply_chain` -> strong signal
- Columns like `tracking_no`, `origin`, `destination`, `delivery_time`, `warehouse_id`, `transport_mode` -> strong signal
