# 09 Energy — 能源环保监控中心

## 业务上下文
- **核心 KPI**: 总能耗(万kWh), 碳排放(吨CO2), 清洁能源占比(%), 环保评分
- **分析维度**: 能耗趋势(火电/水电/风电/光伏/核电堆叠面积), 各工厂能耗对比, 能源流拓扑(发电->传输->分配->消耗), 能源类型占比, 节能减排目标达成追踪, 碳排放趋势(实际vs目标), 空气质量指数热力图, 环保指标达标率(4 仪表盘), 异常排放事件时间轴
- **典型用户**: 能源部长, 环保总监, 碳排放管理师, 厂长

## 风格与颜色覆盖

- **映射风格:** Terrain (`references/styles/terrain.md`)
- `--bezel-bg-app`: `#0a1f14`
- `--bezel-accent-primary`: `#22c55e` (green)
- `--bezel-accent-secondary`: `#0ea5e9` (sky blue)

## 典型 widget pattern（7 个）

### energy.stacked-area-consumption
- **When to use**: Energy consumption by source stacked area (thermal/hydro/wind/solar/nuclear).
- **ECharts option fragment**:
```js
{
  legend: { data: ['火电', '水电', '风电', '光伏', '核电'] },
  series: [
    { name: '火电', type: 'line', stack: 'Total', areaStyle: {}, smooth: true, itemStyle: { color: '#64748b' } },
    { name: '水电', type: 'line', stack: 'Total', areaStyle: {}, smooth: true, itemStyle: { color: '#0ea5e9' } },
    { name: '风电', type: 'line', stack: 'Total', areaStyle: {}, smooth: true, itemStyle: { color: '#22c55e' } },
    { name: '光伏', type: 'line', stack: 'Total', areaStyle: {}, smooth: true, itemStyle: { color: '#f59e0b' } },
    { name: '核电', type: 'line', stack: 'Total', areaStyle: {}, smooth: true, itemStyle: { color: '#a855f7' } }
  ]
}
```
- **SQL output columns**: `month TEXT, source_type TEXT, consumption NUMERIC`
- **refresh.intervalMs**: 300000

### energy.h-bar-factory
- **When to use**: Factory energy consumption comparison (horizontal bars).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: factoryNames },
  series: [{
    type: 'bar', barWidth: 14,
    data: factories.map(f => ({ value: f.consumption, itemStyle: { color: colorByLevel(f.consumption) } }))
  }]
}
```
- **SQL output columns**: `factory_name TEXT, consumption_kwh NUMERIC`
- **refresh.intervalMs**: 60000

### energy.line-target-carbon
- **When to use**: Carbon emission actual vs target trend with dashed target line.
- **ECharts option fragment**:
```js
{
  series: [
    { name: '实际排放', type: 'line', smooth: true, itemStyle: { color: '#ef4444' }, areaStyle: { color: 'rgba(239,68,68,0.1)' } },
    { name: '目标线', type: 'line', symbol: 'none', lineStyle: { type: 'dashed', color: '#22c55e', width: 2 } }
  ]
}
```
- **SQL output columns**: `month TEXT, actual_emission NUMERIC, target_emission NUMERIC`
- **refresh.intervalMs**: 300000

### energy.pie-source
- **When to use**: Energy source share donut.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['38%', '65%'], center: ['38%', '50%'],
    animationType: 'scale', animationEasing: 'elasticOut',
    data: [
      { value: 35, name: '火电', itemStyle: { color: '#64748b' } },
      { value: 22, name: '水电', itemStyle: { color: '#0ea5e9' } },
      { value: 15, name: '风电', itemStyle: { color: '#22c55e' } },
      { value: 13, name: '光伏', itemStyle: { color: '#f59e0b' } },
      { value: 15, name: '核电', itemStyle: { color: '#a855f7' } }
    ]
  }]
}
```
- **SQL output columns**: `source_type TEXT, share_pct NUMERIC`
- **refresh.intervalMs**: 300000

### energy.heatmap-aqi
- **When to use**: Air Quality Index heatmap (monitoring point x time).
- **ECharts option fragment**:
```js
{
  visualMap: { min: 0, max: 150, inRange: { color: ['#22c55e', '#f59e0b', '#ef4444'] } },
  series: [{ type: 'heatmap', data: [[timeIdx, pointIdx, aqi], ...],
    label: { show: true, fontSize: 10 } }]
}
```
- **SQL output columns**: `monitoring_point TEXT, time_slot TEXT, aqi_value INT`
- **refresh.intervalMs**: 30000

### energy.multi-gauge-compliance
- **When to use**: 4 small gauges for emission/energy/water/waste compliance rates.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', radius: '90%', startAngle: 210, endAngle: -30,
    axisLine: { lineStyle: { width: 8, color: [[value/100, color], [1, 'rgba(255,255,255,0.08)']] } },
    detail: { fontSize: 18, fontWeight: 700, color: gaugeColor, offsetCenter: [0, '60%'], formatter: '{value}%' }
  }]
}
```
- **SQL output columns**: `metric_name TEXT, compliance_pct NUMERIC`
- **refresh.intervalMs**: 60000

### energy.topology-energy-flow
- **When to use**: Energy flow topology with animated connector pulses.
- **HTML fragment**:
```html
<div class="flow-wrap">
  <div class="flow-node">发电<div class="flow-label">Plant</div></div>
  <div class="flow-connector"></div>
  <div class="flow-node">传输<div class="flow-label">Grid</div></div>
  <div class="flow-connector"></div>
  <div class="flow-node">分配<div class="flow-label">Dist</div></div>
  <div class="flow-connector"></div>
  <div class="flow-node">消耗<div class="flow-label">Load</div></div>
</div>
```
- **SQL output columns**: `node_name TEXT, node_label TEXT, flow_value NUMERIC`
- **refresh.intervalMs**: 5000

## 触发线索（AI 用）
- User mentions "能耗", "碳排放", "清洁能源", "环保", "AQI", "工厂", "光伏", "风电", "节能减排", "发电", "排放" -> select this industry
- Table name contains `energy`, `emission`, `carbon`, `aqi`, `factory`, `power_plant`, `environmental` -> strong signal
- Columns like `kwh`, `co2_tons`, `aqi`, `source_type`, `emission_rate`, `compliance_pct` -> strong signal
