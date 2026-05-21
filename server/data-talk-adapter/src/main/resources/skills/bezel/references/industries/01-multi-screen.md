# 01 Multi-Screen — 多屏综合监控

## 业务上下文
- **核心 KPI**: 总数据量(PB), API QPS, 服务健康度(%), 在线用户数
- **分析维度**: 行业场景对比(12 个行业), 系统资源(CPU/内存/网络), 24h 服务可用性, API 响应时间(P50/P95/P99), 系统负载趋势, 7 日数据量趋势
- **典型用户**: 运维总监、CIO、多业务线管理者

## 风格与颜色覆盖

- **映射风格:** Orbital (`references/styles/orbital.md`)
- `--bezel-bg-app`: `#060b14`
- `--bezel-accent-primary`: `#00d4ff` (cyan)
- `--bezel-accent-secondary`: `#10e873` (green)

## 典型 widget pattern（6 个）

### multi-screen.radar
- **When to use**: Comparing multiple dimensions/industries on a single radar chart.
- **ECharts option fragment**:
```js
{
  radar: {
    indicator: [{ name: '电商', max: 100 }, ...],
    center: ['50%', '55%'], radius: '65%',
    splitArea: { areaStyle: { color: ['rgba(0,212,255,0.02)', 'rgba(0,212,255,0.05)'] } },
    splitLine: { lineStyle: { color: 'rgba(0,212,255,0.1)' } },
    axisLine: { lineStyle: { color: 'rgba(0,212,255,0.15)' } }
  },
  series: [{
    type: 'radar',
    data: [{ value: [92, 78, 85, ...], areaStyle: { color: 'rgba(0,212,255,0.15)' }, lineStyle: { color: '#00d4ff', width: 2 } }]
  }]
}
```
- **SQL output columns**: `dimension TEXT, score NUMERIC`
- **refresh.intervalMs**: 30000

### multi-screen.multi-gauge
- **When to use**: Showing 2-4 gauge meters side by side (CPU, memory, network, etc.).
- **ECharts option fragment**:
```js
series: [
  { type: 'gauge', center: ['18%', '55%'], radius: '55%', startAngle: 200, endAngle: -20,
    axisLine: { lineStyle: { width: 8, color: [[0.67, '#00d4ff'], [1, 'rgba(148,163,184,0.1)']] } },
    detail: { formatter: '{value}%', color: '#00d4ff', fontSize: 16, fontWeight: 'bold', offsetCenter: [0, '65%'] },
    data: [{ value: 67, name: 'CPU' }] }
  // repeat for each metric...
]
```
- **SQL output columns**: `metric_name TEXT, value NUMERIC, max_value NUMERIC`
- **refresh.intervalMs**: 5000

### multi-screen.heatmap-availability
- **When to use**: 24h x N-services heatmap for uptime/availability.
- **ECharts option fragment**:
```js
{
  visualMap: { min: 95, max: 100, inRange: { color: ['#ef4444', '#f59e0b', '#10e873'] } },
  xAxis: { type: 'category', data: hours },
  yAxis: { type: 'category', data: services },
  series: [{ type: 'heatmap', data: [[hourIdx, serviceIdx, availability], ...] }]
}
```
- **SQL output columns**: `hour INT, service_name TEXT, availability_pct NUMERIC`
- **refresh.intervalMs**: 60000

### multi-screen.timeline-events
- **When to use**: Horizontal scrollable event/alert feed.
- **HTML fragment**:
```html
<div class="timeline-item {level}">
  <div class="timeline-time">{time}</div>
  <div class="timeline-msg">{message}</div>
  <span class="timeline-tag {level}">{level.toUpperCase()}</span>
</div>
```
- Levels: `info` (cyan), `warn` (amber), `alert` (red), `critical` (purple)
- **SQL output columns**: `event_time TIMESTAMP, level TEXT, message TEXT`
- **refresh.intervalMs**: 3000

### multi-screen.trend-multi-line
- **When to use**: 7-day multi-series line chart overlay.
- **ECharts option fragment**:
```js
{
  legend: { data: seriesNames },
  series: seriesNames.map(name => ({
    name, type: 'line', smooth: true, showSymbol: false,
    lineStyle: { width: 2, color: seriesColor },
    emphasis: { focus: 'series' }
  }))
}
```
- **SQL output columns**: `date DATE, series_name TEXT, value NUMERIC`
- **refresh.intervalMs**: 60000

### multi-screen.scene-grid
- **When to use**: 4x3 clickable card grid showing sub-scene thumbnails.
- **HTML fragment**:
```html
<div class="scene-card" style="color:{accentColor}">
  <div class="scene-thumb" style="background:{accentColor}22;border:1px solid {accentColor}44;"></div>
  <div class="scene-name">{name}</div>
  <div class="scene-activity">活跃度<span>{activityPct}%</span></div>
</div>
```
- **SQL output columns**: `scene_name TEXT, color TEXT, activity_pct NUMERIC`
- **refresh.intervalMs**: 30000

## 触发线索（AI 用）
- User mentions "多屏", "综合监控", "多场景", "监控中心", "总览", "overview" -> select this industry
- Table name contains `monitor`, `overview`, `dashboard`, `scene`, `multi` -> strong signal
- Data has 3+ distinct metric categories each with independent dimensions -> moderate signal
- User asks to compare multiple business domains on one screen -> strong signal
