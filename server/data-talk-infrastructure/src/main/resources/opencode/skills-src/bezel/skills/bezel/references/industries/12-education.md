# 12 Education — 在线教育数据中心

## 业务上下文
- **核心 KPI**: 总学员数, 活跃学员, 课程完成率(%), 平均学习时长(h/周), 教师满意度(%)
- **分析维度**: 课程分类学习时长, 热门课程排行 TOP10, 学习时段分布热力图, 课程完成率(仪表盘), 学习路径阶段(注册->选课->学习->作业->考试->证书), 在线人数趋势(24h), 满意度趋势, 学习活跃度七日留存, 知识点掌握度热力图, 完课率漏斗
- **典型用户**: 教育平台运营, 课程产品经理, 教师, 教务处

## 风格与颜色覆盖

- **映射风格:** Organic (`references/styles/organic.md`)
- `--bezel-bg-app`: `#0f172a`
- `--bezel-accent-primary`: `#facc15` (yellow)
- `--bezel-accent-secondary`: `#3b82f6` (blue)

## 典型 widget pattern（5 个）

### education.gauge-completion
- **When to use**: Large course completion rate gauge (0-100%).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', radius: '90%', startAngle: 210, endAngle: -30,
    itemStyle: { color: '#facc15' },
    progress: { show: true, width: 18, roundCap: true },
    axisLine: { lineStyle: { color: [[0.5, '#ef4444'], [0.7, '#facc15'], [1, '#22c55e']] } },
    detail: { fontSize: 42, fontWeight: 700, offsetCenter: [0, '-10%'], formatter: '{value}%' },
    data: [{ value: 68, name: '课程完成率' }]
  }]
}
```
- **SQL output columns**: `course_name TEXT, completion_pct NUMERIC`
- **refresh.intervalMs**: 60000

### education.h-bar-category-hours
- **When to use**: Learning hours by course category (horizontal bars).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: categories },
  series: [{
    type: 'bar', barWidth: 14,
    data: categories.map((c, i) => ({ value: hours[i], itemStyle: { color: categoryColors[i], borderRadius: [0, 4, 4, 0] } })),
    label: { show: true, position: 'right', formatter: '{c}h' }
  }]
}
```
- **SQL output columns**: `category_name TEXT, total_hours NUMERIC`
- **refresh.intervalMs**: 300000

### education.heatmap-retention
- **When to use**: 7-day learner retention heatmap (registration date x day offset).
- **ECharts option fragment**:
```js
{
  visualMap: { min: 0, max: 100, inRange: { color: ['#1e293b', '#3b82f6', '#22c55e', '#facc15'] } },
  xAxis: { type: 'category', data: dates },
  yAxis: { type: 'category', data: ['Day 0', 'Day 1', 'Day 2', 'Day 3', 'Day 4', 'Day 5', 'Day 6'] },
  series: [{ type: 'heatmap', data: [[dateIdx, dayIdx, retentionPct], ...] }]
}
```
- **SQL output columns**: `cohort_date DATE, day_offset INT, retention_pct NUMERIC`
- **refresh.intervalMs**: 300000

### education.heatmap-knowledge
- **When to use**: Knowledge point mastery heatmap (learner segment x knowledge point).
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: knowledgePoints, axisLabel: { rotate: 30 } },
  yAxis: { type: 'category', data: learnerSegments },
  visualMap: { inRange: { color: ['#1e293b', '#3b82f6', '#22c55e', '#facc15'] } },
  series: [{ type: 'heatmap', label: { show: true, fontSize: 9 } }]
}
```
- **SQL output columns**: `knowledge_point TEXT, learner_segment TEXT, mastery_pct NUMERIC`
- **refresh.intervalMs**: 300000

### education.funnel-horizontal-completion
- **When to use**: Course completion funnel (enroll -> start -> 50% -> 100% -> certificate).
- **HTML fragment**:
```html
<div class="funnel-row">
  <div class="funnel-label">{step}</div>
  <div class="funnel-bar-wrap">
    <div class="funnel-bar" style="width:{widthPct}%;background:{color};opacity:0.9;">{pct}%</div>
  </div>
  <div class="funnel-pct">{pct}%</div>
</div>
```
- **SQL output columns**: `stage_name TEXT, student_count INT, conversion_pct NUMERIC`
- **refresh.intervalMs**: 60000

## 触发线索（AI 用）
- User mentions "学员", "课程", "学习", "完课率", "留存", "满意度", "考试", "教育", "培训", "知识点", "证书" -> select this industry
- Table name contains `course`, `student`, `enrollment`, `lesson`, `exam`, `education`, `learning`, `training` -> strong signal
- Columns like `student_id`, `course_id`, `completion_rate`, `score`, `study_hours`, `enroll_date` -> strong signal
