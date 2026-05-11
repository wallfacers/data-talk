# 03 Manufacturing — 工业制造智能监控中心

## 业务上下文
- **核心 KPI**: OEE 设备综合效率(%), 质量合格率(%), 安全生产天数, 待维护设备数
- **分析维度**: 产线产能趋势(早/中/晚班), 产线总览&效率监控, 产线拓扑, 设备数字孪生面板, 故障报警统计, SPC 质量控制图, 能耗趋势(电/水/气), 安全监控指数
- **典型用户**: 工厂厂长、生产主管、设备维护工程师、质量工程师

## 视觉签名
- Primary color `#f97316` (orange) / Secondary color `#22c55e` (green) / Background `#1a1a1e`
- Scan-line texture overlay (2px repeating gradient, 8% black)
- LED pulse animation on KPI indicators
- Card background: `linear-gradient(145deg, #1f1f24, #18181c)`, border: `#2a2a32`
- KPI boxes with top-edge color bar (currentColor, 2px), LED dot with pulse animation
- ECharts theme: transparent bg, `#9ca3af` text, `#25252c` grid lines, `#6b7280` labels
- Tooltip: `rgba(26,26,30,0.95)` bg, `#2e2e36` border

## 推荐布局骨架
- Header: ~50px — rivet-decorated title + clock
- KPI row: 4 boxes with LED dots, ~90px
- Body: 3-column grid `1fr 1.4fr 1fr`
- Bottom marquee: 32px — alert feed with red label tag

## 典型 widget pattern（6 个）

### manufacturing.stacked-bar-shifts
- **When to use**: Production capacity by shift (early/mid/late) over time periods.
- **ECharts option fragment**:
```js
{
  legend: { data: ['早班', '中班', '晚班'] },
  yAxis: { type: 'value', name: '件' },
  series: [
    { name: '早班', type: 'bar', stack: 'total', itemStyle: { color: '#f97316' } },
    { name: '中班', type: 'bar', stack: 'total', itemStyle: { color: '#fb923c' } },
    { name: '晚班', type: 'bar', stack: 'total', itemStyle: { color: '#fdba74' } }
  ]
}
```
- **SQL output columns**: `time_period TEXT, shift_name TEXT, output_count INT`
- **refresh.intervalMs**: 10000

### manufacturing.dual-axis-capacity-efficiency
- **When to use**: Production output bars + efficiency line on dual Y-axis.
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: '件', position: 'left' },
    { type: 'value', name: '%', position: 'right', max: 100 }
  ],
  series: [
    { name: '产能', type: 'bar', itemStyle: { color: '#f97316', borderRadius: [3, 3, 0, 0] } },
    { name: '效率', type: 'line', yAxisIndex: 1, smooth: true, lineStyle: { color: '#22c55e', width: 3 } }
  ]
}
```
- **SQL output columns**: `time_period TEXT, capacity INT, efficiency_pct NUMERIC`
- **refresh.intervalMs**: 10000

### manufacturing.spc-control-chart
- **When to use**: Statistical Process Control chart with UCL/CL/LCL lines.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'line', symbol: 'circle', symbolSize: 5,
    lineStyle: { color: '#22d3ee', width: 2 },
    markLine: {
      silent: true, lineStyle: { type: 'dashed', width: 1 },
      data: [
        { yAxis: UCL, lineStyle: { color: '#ef4444' }, label: { formatter: 'UCL', color: '#ef4444' } },
        { yAxis: CL, lineStyle: { color: '#22c55e' }, label: { formatter: 'CL', color: '#22c55e' } },
        { yAxis: LCL, lineStyle: { color: '#ef4444' }, label: { formatter: 'LCL', color: '#ef4444' } }
      ]
    }
  }]
}
```
- **SQL output columns**: `sample_id INT, measurement NUMERIC, ucl NUMERIC, cl NUMERIC, lcl NUMERIC`
- **refresh.intervalMs**: 5000

### manufacturing.stacked-area-energy
- **When to use**: Energy consumption stacked area (electric/water/gas).
- **ECharts option fragment**:
```js
{
  legend: { data: ['电', '水', '气'] },
  series: [
    { name: '电', type: 'line', stack: 'total', areaStyle: { opacity: 0.25 }, itemStyle: { color: '#f97316' } },
    { name: '水', type: 'line', stack: 'total', areaStyle: { opacity: 0.25 }, itemStyle: { color: '#3b82f6' } },
    { name: '气', type: 'line', stack: 'total', areaStyle: { opacity: 0.25 }, itemStyle: { color: '#6b7280' } }
  ]
}
```
- **SQL output columns**: `time_period TEXT, energy_type TEXT, consumption NUMERIC`
- **refresh.intervalMs**: 10000

### manufacturing.topology-line
- **When to use**: Production line node topology (machine -> machine -> machine).
- **HTML fragment**:
```html
<div class="topology-line">
  <div class="topo-node green"><span class="node-name">冲压机</span><span class="node-id">P01</span></div>
  <div class="topo-conn"></div>
  <div class="topo-node yellow"><span class="node-name">喷涂线</span><span class="node-id">S03</span></div>
  <!-- repeat -->
</div>
```
- Node colors: `.green` (ok), `.yellow` (warning), `.red` (fault)
- **SQL output columns**: `node_id TEXT, node_name TEXT, status TEXT('ok'|'warning'|'fault'), position INT`
- **refresh.intervalMs**: 5000

### manufacturing.digital-twin-panel
- **When to use**: Grid of device cards showing real-time temperature, vibration, RPM.
- **HTML fragment**:
```html
<div class="twin-card">
  <div class="twin-name">{device_name}</div>
  <div class="twin-row"><span class="twin-label">温度</span><span class="twin-val {warn|danger}">{temp}°C</span></div>
  <div class="twin-row"><span class="twin-label">振动</span><span class="twin-val">{vibration} mm/s</span></div>
  <div class="twin-row"><span class="twin-label">转速</span><span class="twin-val">{rpm} rpm</span></div>
</div>
```
- Threshold coloring: temp>80 danger, >70 warn; vib>1.5 danger, >1.0 warn
- **SQL output columns**: `device_id TEXT, temperature NUMERIC, vibration NUMERIC, rpm INT`
- **refresh.intervalMs**: 2000

## 触发线索（AI 用）
- User mentions "OEE", "产能", "产线", "设备", "故障", "SPC", "质量控制", "制造", "工厂", "PLC", "SCADA" -> select this industry
- Table name contains `equipment`, `production`, `shift`, `oee`, `fault`, `alarm`, `sensor`, `plc` -> strong signal
- Columns like `temperature`, `vibration`, `rpm`, `defect_rate`, `shift_id` -> strong signal
