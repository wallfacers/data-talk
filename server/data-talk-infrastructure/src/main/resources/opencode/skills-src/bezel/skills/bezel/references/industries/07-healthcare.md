# 07 Healthcare — 医疗健康大数据中心

## 业务上下文
- **核心 KPI**: 门诊量(人/日), 住院量(床), 手术量(台), 急救响应时间(分钟), 患者满意度(%)
- **分析维度**: 科室门诊量排行 TOP10, 药品耗材 TOP10, 患者年龄分布, 床位使用率, 科室效率热力图, 手术排程日历, 满意度趋势, 急诊分级等候时间, 慢病管理漏斗
- **典型用户**: 院长, 医务科主任, 护理部主任, 运营管理

## 视觉签名
- Primary color `#10b981` (emerald) / Secondary color `#3b82f6` (blue) / Background `#0f172a`
- Cross badge logo (CSS-only green cross)
- Heartbeat SVG line animation along header bottom edge
- KPI pills: rounded 16px, `rgba(30,41,59,0.7)` bg, icon badges (green/blue/yellow/red)
- Card title left bars: green default, `.blue`, `.yellow`, `.red` variants
- ECharts theme: transparent bg, `#cbd5e1` text, `#64748b` dim, `#f1f5f9` values
- Heatmap gradient: `#0f172a -> #10b981 -> #f59e0b -> #ef4444`

## 推荐布局骨架
- Header: 64px — cross badge + title + heartbeat SVG + clock
- KPI bar: 5 pills with icon badges, ~90px
- Body: 3-column grid 30% / 40% / 30%
- Bottom story bar: ~36px insight text

## 典型 widget pattern（7 个）

### healthcare.h-bar-dept-rank
- **When to use**: Department outpatient volume ranking TOP10.
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: deptNames },
  series: [{
    type: 'bar', barWidth: 14,
    itemStyle: { borderRadius: [0, 7, 7, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#10b981' }, { offset: 1, color: '#34d399' }]) }
  }]
}
```
- **SQL output columns**: `dept_name TEXT, patient_count INT`
- **refresh.intervalMs**: 60000

### healthcare.heatmap-dept-efficiency
- **When to use**: Department x hour heatmap for workload/waiting patients.
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: hours },
  yAxis: { type: 'category', data: departments },
  visualMap: { min: 0, max: 70, inRange: { color: ['#0f172a', '#10b981', '#f59e0b', '#ef4444'] } },
  series: [{ type: 'heatmap', data: [[hourIdx, deptIdx, count], ...] }]
}
```
- **SQL output columns**: `department TEXT, hour INT, patient_count INT`
- **refresh.intervalMs**: 30000

### healthcare.gauge-bed-occupancy
- **When to use**: Large bed occupancy rate gauge (0-100%).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', radius: '90%', startAngle: 210, endAngle: -30,
    axisLine: { lineStyle: { width: 18, color: [[0.6, '#ef4444'], [0.8, '#f59e0b'], [1, '#10b981']] } },
    detail: { formatter: '{value}%', fontSize: 36, fontWeight: 700, offsetCenter: [0, '60%'] }
  }]
}
```
- **SQL output columns**: `ward_name TEXT, occupancy_pct NUMERIC`
- **refresh.intervalMs**: 30000

### healthcare.h-bar-er-wait
- **When to use**: Emergency triage level waiting time (Level 5-1).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: ['五级', '四级', '三级', '二级', '一级'] },
  series: [{
    type: 'bar', barWidth: 18,
    data: waitTimes.map((v, i) => ({ value: v, itemStyle: { color: erColors[i] } }))
  }]
}
```
- **SQL output columns**: `triage_level TEXT, avg_wait_minutes NUMERIC`
- **refresh.intervalMs**: 10000

### healthcare.line-area-satisfaction
- **When to use**: Patient satisfaction trend over quarters.
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'value', min: 85, max: 100 },
  series: [{
    type: 'line', smooth: true, symbol: 'circle', symbolSize: 8,
    lineStyle: { width: 3, color: '#10b981' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(16,185,129,0.25)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }]) }
  }]
}
```
- **SQL output columns**: `period TEXT, satisfaction_pct NUMERIC`
- **refresh.intervalMs**: 300000

### healthcare.funnel-horizontal-chronic
- **When to use**: Chronic disease management funnel (screening -> diagnosis -> filing -> follow-up -> controlled).
- **HTML fragment**:
```html
<div class="funnel-step">
  <span class="funnel-label">{step}</span>
  <div class="funnel-bar-wrap"><div class="funnel-bar" style="width:{pct}%;background:{color}">{pct}%</div></div>
  <span class="funnel-pct">{pct}%</span>
</div>
```
- **SQL output columns**: `stage_name TEXT, patient_count INT, conversion_pct NUMERIC`
- **refresh.intervalMs**: 300000

### healthcare.stacked-bar-surgery
- **When to use**: Weekly surgery schedule by type (elective/emergency/minimally invasive).
- **ECharts option fragment**:
```js
{
  legend: { data: ['择期手术', '急诊手术', '微创手术'] },
  xAxis: { type: 'category', data: weekDays },
  series: [
    { name: '择期手术', type: 'bar', stack: 'total', itemStyle: { color: '#3b82f6' } },
    { name: '急诊手术', type: 'bar', stack: 'total', itemStyle: { color: '#ef4444' } },
    { name: '微创手术', type: 'bar', stack: 'total', itemStyle: { color: '#10b981' } }
  ]
}
```
- **SQL output columns**: `weekday TEXT, surgery_type TEXT, count INT`
- **refresh.intervalMs**: 60000

## 触发线索（AI 用）
- User mentions "门诊", "住院", "手术", "床位", "急诊", "患者", "科室", "慢病", "药品", "医疗", "医院", "挂号" -> select this industry
- Table name contains `patient`, `appointment`, `surgery`, `department`, `ward`, `prescription`, `diagnosis`, `medical` -> strong signal
- Columns like `patient_id`, `dept_id`, `triage_level`, `bed_id`, `icd_code`, `admission_date` -> strong signal
