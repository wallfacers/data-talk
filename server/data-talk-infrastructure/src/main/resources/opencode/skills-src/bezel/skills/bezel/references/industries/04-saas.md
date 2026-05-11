# 04 SaaS — SaaS 运营监控中心

## 业务上下文
- **核心 KPI**: WAU (北极星指标), 活跃租户数, SLA(%), MRR(万), NPS
- **分析维度**: MRR/ARR 瀑布图(新增/扩展/收缩/流失), 套餐订阅分布, 用户留存率(多 Cohort 折线), 用户旅程地图(注册->激活->付费->留存->推荐), 功能采用率热力图, API 性能趋势(P50/P95/P99), Churn Rate 预测曲线
- **典型用户**: SaaS CEO, 产品运营, 客户成功经理, SRE

## 视觉签名
- Primary color `#14b8a6` (teal) / Secondary color `#6366f1` (indigo) / Accent `#f472b6` (pink) / Background `#0a0f1a`
- Floating wave circles: teal radial-gradient (60vw) top-left + indigo (55vw) bottom-right, animated float
- Glass morphism cards: `rgba(255,255,255,0.04)` bg, `blur(16px)`, `rgba(255,255,255,0.08)` border
- Hover: teal shadow + teal border glow
- Panel title color-coded dots: `.title-teal`, `.title-purple`, `.title-pink`
- KPI pills: rounded 999px, glass bg
- ECharts theme: transparent bg, `#94a3b8` text, `#0f172a` grid, `#334155` axis lines
- Tooltip: `rgba(15,23,42,0.9)` bg, `rgba(148,163,184,0.25)` border

## 推荐布局骨架
- Header: ~5vh — North Star metric left + KPI pills center + clock right
- Body: 3 columns 28% / 44% / 28%
- Bottom marquee: 3.2vh glass bar with teal/indigo/pink dots

## 典型 widget pattern（6 个）

### saas.waterfall-mrr
- **When to use**: MRR waterfall showing new, expansion, contraction, churn contributions.
- **ECharts option fragment**:
```js
{
  legend: { data: ['新增', '扩展', '收缩', '流失', '净增'] },
  series: [
    { name: '新增', type: 'bar', stack: 'total', itemStyle: { color: '#34d399' } },
    { name: '扩展', type: 'bar', stack: 'total', itemStyle: { color: '#6366f1' } },
    { name: '收缩', type: 'bar', stack: 'total', itemStyle: { color: '#fbbf24' } },
    { name: '流失', type: 'bar', stack: 'total', itemStyle: { color: '#f87171' } },
    { name: '净增', type: 'line', symbol: 'circle', lineStyle: { color: '#14b8a6' } }
  ]
}
```
- **SQL output columns**: `month TEXT, new_mrr NUMERIC, expansion_mrr NUMERIC, contraction_mrr NUMERIC, churn_mrr NUMERIC`
- **refresh.intervalMs**: 300000

### saas.donut-plan
- **When to use**: Subscription plan distribution (Free/Pro/Enterprise/Custom).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['45%', '70%'], center: ['35%', '50%'],
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
    data: [
      { value: 4200, name: 'Free', itemStyle: { color: '#94a3b8' } },
      { value: 6800, name: 'Pro', itemStyle: { color: '#6366f1' } },
      { value: 3100, name: 'Enterprise', itemStyle: { color: '#14b8a6' } },
      { value: 1200, name: 'Custom', itemStyle: { color: '#f472b6' } }
    ]
  }]
}
```
- **SQL output columns**: `plan_name TEXT, subscriber_count INT`
- **refresh.intervalMs**: 60000

### saas.retention-multi-line
- **When to use**: Multi-cohort retention rate lines over time (Day 0 to Day 365).
- **ECharts option fragment**:
```js
{
  legend: { data: cohortLabels },
  xAxis: { type: 'category', data: ['Day 0', 'Day 7', 'Day 30', 'Day 60', 'Day 90', 'Day 180', 'Day 365'] },
  series: cohortLabels.map((label, i) => ({
    name: label, type: 'line', smooth: true, symbol: 'none',
    lineStyle: { width: 2, color: cohortColors[i] },
    areaStyle: { color: cohortColors[i], opacity: 0.08 }
  }))
}
```
- **SQL output columns**: `cohort_label TEXT, day_offset INT, retention_pct NUMERIC`
- **refresh.intervalMs**: 300000

### saas.api-perf-lines
- **When to use**: API latency P50/P95/P99 over 24h.
- **ECharts option fragment**:
```js
{
  legend: { data: ['P50', 'P95', 'P99'] },
  series: [
    { name: 'P50', type: 'line', smooth: true, symbol: 'none', lineStyle: { color: '#34d399' } },
    { name: 'P95', type: 'line', smooth: true, symbol: 'none', lineStyle: { color: '#6366f1' } },
    { name: 'P99', type: 'line', smooth: true, symbol: 'none', lineStyle: { color: '#f472b6' }, areaStyle: { color: '#f472b6', opacity: 0.06 } }
  ]
}
```
- **SQL output columns**: `hour INT, p50_ms NUMERIC, p95_ms NUMERIC, p99_ms NUMERIC`
- **refresh.intervalMs**: 10000

### saas.heatmap-feature-adoption
- **When to use**: Feature adoption rate heatmap (user lifecycle x feature).
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: features },
  yAxis: { type: 'category', data: userSegments },
  visualMap: { inRange: { color: ['#0f172a', '#14b8a6', '#6366f1', '#f472b6'] } },
  series: [{ type: 'heatmap', data: [[featureIdx, segmentIdx, adoptionPct], ...],
    label: { show: true, formatter: p => p.value[2] + '%' } }]
}
```
- **SQL output columns**: `feature_name TEXT, user_segment TEXT, adoption_pct NUMERIC`
- **refresh.intervalMs**: 300000

### saas.churn-forecast
- **When to use**: Churn rate trend with dashed forecast extension.
- **ECharts option fragment**:
```js
{
  series: [
    { name: '历史', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
      lineStyle: { color: '#f472b6' } },
    { name: '预测', type: 'line', smooth: true, symbol: 'diamond', symbolSize: 6,
      lineStyle: { color: '#f472b6', type: 'dashed' } }
  ]
}
```
- **SQL output columns**: `month TEXT, churn_rate NUMERIC, is_forecast BOOLEAN`
- **refresh.intervalMs**: 300000

## 触发线索（AI 用）
- User mentions "MRR", "ARR", "Churn", "NPS", "WAU", "MAU", "SaaS", "订阅", "留存率", "SLA", "租户", "付费转化" -> select this industry
- Table name contains `subscription`, `tenant`, `mrr`, `churn`, `plan`, `retention`, `license` -> strong signal
- Columns like `plan_id`, `subscription_status`, `churn_risk`, `monthly_recurring_revenue` -> strong signal
