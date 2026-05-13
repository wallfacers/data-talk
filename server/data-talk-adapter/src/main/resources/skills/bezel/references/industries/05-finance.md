# 05 Finance — 财务数据分析中心

## 业务上下文
- **核心 KPI**: 年度总收入(亿), 净利润(亿), 净利润率(%), 应收账款(亿), 预算执行率(%)
- **分析维度**: 月度收入&利润趋势, 杜邦分析(ROE 分解), 收入结构, 费用构成明细(树图), 资产负债结构(堆叠面积), 现金流监控(瀑布), 应收账款账龄分布, 预算执行仪表盘
- **典型用户**: CFO, 财务总监, 财务分析师

## 风格与颜色覆盖

- **映射风格:** Monument (`references/styles/monument.md`)
- `--bezel-bg-app`: `#0c1929`
- `--bezel-accent-primary`: `#c9a84c` (gold)
- `--bezel-accent-secondary`: `#4a90d9` (blue)

## 典型 widget pattern（8 个）

### finance.bar-line-revenue-profit
- **When to use**: Monthly revenue bars + profit margin line on dual Y-axis.
- **ECharts option fragment**:
```js
{
  yAxis: [
    { type: 'value', name: '收入(亿)' },
    { type: 'value', name: '利润率(%)' }
  ],
  series: [
    { name: '收入', type: 'bar', itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#e8d5a3' }, { offset: 1, color: '#c9a84c' }]) } },
    { name: '利润率', type: 'line', yAxisIndex: 1, smooth: true, lineStyle: { color: '#4a90d9' } }
  ]
}
```
- **SQL output columns**: `month TEXT, revenue NUMERIC, profit_margin_pct NUMERIC`
- **refresh.intervalMs**: 300000

### finance.treemap-dupont
- **When to use**: DuPont analysis ROE decomposition as nested treemap.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'treemap', roam: false, nodeClick: 'zoomToNode',
    itemStyle: { borderColor: '#0c1929', borderWidth: 2, gapWidth: 2 },
    data: [{ name: 'ROE 18.6%', value: 18.6, itemStyle: { color: '#c9a84c' }, children: [...] }]
  }]
}
```
- **SQL output columns**: `node_name TEXT, value NUMERIC, parent TEXT`
- **refresh.intervalMs**: 0 (static analysis)

### finance.tree-expense
- **When to use**: Hierarchical expense breakdown as tree diagram (LR orientation).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'tree', orient: 'LR',
    symbol: 'emptyCircle', symbolSize: 8,
    lineStyle: { color: 'rgba(201,168,76,0.35)' },
    itemStyle: { color: '#c9a84c' },
    data: [{ name: '期间费用 1.27亿', children: [...] }]
  }]
}
```
- **SQL output columns**: `category TEXT, subcategory TEXT, amount NUMERIC`
- **refresh.intervalMs**: 0 (static)

### finance.stacked-area-balance-sheet
- **When to use**: Asset/liability/equity stacked area over quarters.
- **ECharts option fragment**:
```js
{
  series: [
    { name: '流动资产', type: 'line', stack: 'assets', areaStyle: { opacity: 0.35 }, smooth: true, itemStyle: { color: '#c9a84c' } },
    { name: '非流动资产', type: 'line', stack: 'assets', areaStyle: { opacity: 0.35 }, itemStyle: { color: '#e8d5a3' } },
    { name: '流动负债', type: 'line', stack: 'liab', areaStyle: { opacity: 0.35 }, itemStyle: { color: '#d94a4a' } },
    { name: '所有者权益', type: 'line', stack: 'equity', areaStyle: { opacity: 0.35 }, itemStyle: { color: '#4a90d9' } }
  ]
}
```
- **SQL output columns**: `quarter TEXT, category TEXT, amount NUMERIC`
- **refresh.intervalMs**: 300000

### finance.waterfall-cashflow
- **When to use**: Cash flow waterfall (opening -> operating -> investing -> financing -> net -> closing).
- **ECharts option fragment**:
```js
{
  xAxis: { type: 'category', data: ['期初余额', '经营活动', '投资活动', '筹资活动', '净现金流', '期末余额'] },
  series: [{
    type: 'bar',
    data: [
      { value: 1.85, itemStyle: { color: '#4a90d9' } },
      { value: 1.42, itemStyle: { color: '#4caf7a' } },
      { value: -0.68, itemStyle: { color: '#d94a4a' } },
      // ...
    ]
  }]
}
```
- **SQL output columns**: `item_name TEXT, amount NUMERIC, is_positive BOOLEAN`
- **refresh.intervalMs**: 300000

### finance.h-bar-aging
- **When to use**: Accounts receivable aging distribution (horizontal bars).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: ['3年以上', '2-3年', '1-2年', '1年内'] },
  series: [{
    type: 'bar',
    data: [
      { value: 2.8, itemStyle: { color: '#d94a4a' } },
      { value: 5.2, itemStyle: { color: '#c97a4a' } },
      { value: 12.5, itemStyle: { color: '#c9a84c' } },
      { value: 42.5, itemStyle: { color: '#4caf7a' } }
    ]
  }]
}
```
- **SQL output columns**: `aging_bucket TEXT, amount NUMERIC`
- **refresh.intervalMs**: 300000

### finance.gauge-budget
- **When to use**: Budget execution rate gauge (0-100%).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'gauge', radius: '90%', startAngle: 200, endAngle: -20,
    axisLine: { lineStyle: { color: [[0.6, '#d94a4a'], [0.8, '#c9a84c'], [1, '#4caf7a']] } },
    detail: { formatter: '{value}%', color: '#c9a84c', fontSize: 22 }
  }]
}
```
- **SQL output columns**: `budget_name TEXT, execution_pct NUMERIC`
- **refresh.intervalMs**: 300000

### finance.pie-revenue-structure
- **When to use**: Revenue structure pie (main business, investment, other).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['42%', '68%'], center: ['38%', '50%'],
    label: { formatter: '{b}\n{d}%' },
    data: [
      { value: 7.12, name: '主营业务', itemStyle: { color: '#c9a84c' } },
      { value: 0.98, name: '投资收益', itemStyle: { color: '#4a90d9' } },
      { value: 0.32, name: '其他收入', itemStyle: { color: '#1e3a5f' } }
    ]
  }]
}
```
- **SQL output columns**: `revenue_type TEXT, amount NUMERIC`
- **refresh.intervalMs**: 300000

## 触发线索（AI 用）
- User mentions "收入", "利润", "ROE", "杜邦", "资产负债", "现金流", "预算", "账龄", "财务", "净利润", "毛利", "费用" -> select this industry
- Table name contains `revenue`, `profit`, `balance_sheet`, `cashflow`, `budget`, `invoice`, `finance`, `accounting` -> strong signal
- Columns like `debit`, `credit`, `revenue`, `expense`, `asset`, `liability`, `equity` -> strong signal
