# 11 Agriculture — 智慧农业大数据中心

## 业务上下文
- **核心 KPI**: 种植面积(亩), 预计产量(吨), 土壤健康指数(分), 气象预警(个区域)
- **分析维度**: 土壤墒情监测(湿度/温度/pH 三仪表), 气象数据组合(温度/降雨/光照), 农场 GIS 地图(作物分布), 作物长势趋势(NDVI 近30天), 农产品价格走势, 积温积雨累积曲线, 病虫害预警等级, 灌溉用水效率
- **典型用户**: 农场主, 农业技术员, 气象局, 农业局

## 风格与颜色覆盖

- **映射风格:** Terrain (`references/styles/terrain.md`)
- `--bezel-bg-app`: `#1a1209`
- `--bezel-accent-primary`: `#f59e0b` (amber)
- `--bezel-accent-secondary`: `#22c55e` (green)

## 典型 widget pattern（7 个）

### agriculture.multi-gauge-soil
- **When to use**: 3 small gauges for soil moisture, temperature, pH side by side.
- **ECharts option fragment**:
```js
series: [
  { type: 'gauge', center: ['25%', '55%'], radius: '70%', min: 0, max: 100,
    axisLine: { lineStyle: { width: 8, color: [[0.68, '#22c55e'], [1, 'rgba(255,255,255,0.08)']] } },
    detail: { formatter: '{value}%', offsetCenter: [0, '65%'] }, data: [{ value: 68, name: '湿度' }] },
  { type: 'gauge', center: ['50%', '55%'], radius: '70%', min: 0, max: 50,
    axisLine: { lineStyle: { color: [[0.44, '#f59e0b'], [1, 'rgba(255,255,255,0.08)']] } },
    detail: { formatter: '{value}°C', offsetCenter: [0, '65%'] }, data: [{ value: 22, name: '温度' }] },
  { type: 'gauge', center: ['75%', '55%'], radius: '70%', min: 0, max: 14,
    axisLine: { lineStyle: { color: [[0.486, '#8b5cf6'], [1, 'rgba(255,255,255,0.08)']] } },
    detail: { offsetCenter: [0, '65%'] }, data: [{ value: 6.8, name: 'pH' }] }
]
```
- **SQL output columns**: `sensor_type TEXT, value NUMERIC, unit TEXT`
- **refresh.intervalMs**: 5000

### agriculture.line-bar-triple-weather
- **When to use**: Weather combo chart (temperature line + rainfall bars + sunshine line on 3 Y-axes).
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: '°C' },
    { type: 'value', name: 'mm' },
    { type: 'value', name: 'h' }
  ],
  series: [
    { name: '温度', type: 'line', smooth: true, yAxisIndex: 0, lineStyle: { color: '#f59e0b' } },
    { name: '降雨量', type: 'bar', yAxisIndex: 1, itemStyle: { color: gradient('#3b82f6') } },
    { name: '光照', type: 'line', smooth: true, yAxisIndex: 2, lineStyle: { color: '#eab308', type: 'dashed' } }
  ]
}
```
- **SQL output columns**: `day TEXT, temperature NUMERIC, rainfall_mm NUMERIC, sunshine_hours NUMERIC`
- **refresh.intervalMs**: 60000

### agriculture.map-crop-distribution
- **When to use**: China map with crop type coloring per province.
- **ECharts option fragment**:
```js
{
  visualMap: { type: 'piecewise', categories: ['小麦', '水稻', '玉米', '大豆', '其他'],
    inRange: { color: ['#f59e0b', '#22c55e', '#eab308', '#8b5cf6', '#64748b'] } },
  geo: { map: 'china', roam: true, itemStyle: { areaColor: 'rgba(255,255,255,0.06)' } },
  series: [{ type: 'map', map: 'china', geoIndex: 0, data: [{ name: '河南', value: '小麦' }, ...] }]
}
```
- **SQL output columns**: `province TEXT, primary_crop TEXT`
- **refresh.intervalMs**: 300000

### agriculture.line-area-ndvi
- **When to use**: NDVI crop growth trend line with green area fill over 30 days.
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'value', min: 0.3, max: 0.9 },
  series: [{
    type: 'line', smooth: true, symbol: 'none',
    lineStyle: { width: 2, color: '#22c55e' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(34,197,94,0.4)' }, { offset: 1, color: 'rgba(34,197,94,0.02)' }
    ]) }
  }]
}
```
- **SQL output columns**: `day_offset INT, ndvi_value NUMERIC`
- **refresh.intervalMs**: 86400000

### agriculture.line-dual-accumulation
- **When to use**: Cumulative growing degree days + cumulative rainfall on dual Y-axis.
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: '°C·d' },
    { type: 'value', name: 'mm' }
  ],
  series: [
    { name: '积温', type: 'line', smooth: true, yAxisIndex: 0, lineStyle: { color: '#f59e0b' }, areaStyle: { gradient amber } },
    { name: '积雨', type: 'line', smooth: true, yAxisIndex: 1, lineStyle: { color: '#3b82f6' }, areaStyle: { gradient blue } }
  ]
}
```
- **SQL output columns**: `day_offset INT, cumulative_temp NUMERIC, cumulative_rain NUMERIC`
- **refresh.intervalMs**: 86400000

### agriculture.h-bar-pest-alert
- **When to use**: Pest/disease alert level by crop (normal/yellow/orange/red).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: cropNames },
  series: [{
    type: 'bar', barWidth: 14,
    data: levels.map(v => ({ value: v, itemStyle: { color: pestColors[v - 1] } })),
    label: { show: true, formatter: p => ['正常', '黄色预警', '橙色预警', '红色预警'][p.value - 1] }
  }]
}
```
- **SQL output columns**: `crop_name TEXT, alert_level INT(1-4)`
- **refresh.intervalMs**: 60000

### agriculture.h-bar-irrigation
- **When to use**: Irrigation water efficiency by zone with benchmark line.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'bar',
    itemStyle: { color: p => p.value > benchmark ? 'rgba(239,68,68,0.7)' : 'rgba(34,197,94,0.7)' },
    markLine: { data: [{ xAxis: benchmark }], lineStyle: { color: '#f59e0b', type: 'dashed' },
      label: { formatter: '标杆 {c}', color: '#f59e0b' } }
  }]
}
```
- **SQL output columns**: `zone_name TEXT, water_per_acre NUMERIC, benchmark NUMERIC`
- **refresh.intervalMs**: 60000

## 触发线索（AI 用）
- User mentions "种植", "产量", "土壤", "气象", "病虫害", "灌溉", "NDVI", "农场", "农业", "积温", "墒情" -> select this industry
- Table name contains `crop`, `farm`, `soil`, `weather`, `irrigation`, `pest`, `harvest`, `agriculture` -> strong signal
- Columns like `ndvi`, `soil_moisture`, `temperature`, `rainfall`, `crop_type`, `acreage` -> strong signal
