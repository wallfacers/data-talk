# 10 Cybersecurity — 网络安全态势感知中心

## 业务上下文
- **核心 KPI**: 攻击次数, 拦截率(%), 待修复漏洞, 安全评分
- **分析维度**: 24h 攻击趋势, 攻击类型分布(玫瑰图), TOP 攻击源国家, 漏洞修复状态(已修复/修复中/待修复), ATT&CK 覆盖矩阵, Kill Chain 漏斗, 系统日志实时流
- **典型用户**: CISO, SOC 分析师, 安全运维工程师

## 风格与颜色覆盖

- **映射风格:** Orbital (`references/styles/orbital.md`)
- `--bezel-bg-app`: `#000a00`
- `--bezel-accent-primary`: `#00ff41` (terminal green)
- `--bezel-accent-secondary`: `#ff0040` (red)

## 典型 widget pattern（6 个）

### cybersecurity.line-area-attack-trend
- **When to use**: 24h attack volume trend with red area fill.
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'line', smooth: true, symbol: 'none',
    lineStyle: { color: '#ff0040', width: 2 },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(255,0,64,0.5)' },
      { offset: 1, color: 'rgba(255,0,64,0.02)' }
    ]) }
  }]
}
```
- **SQL output columns**: `hour INT, attack_count INT`
- **refresh.intervalMs**: 5000

### cybersecurity.rose-pie-attack-type
- **When to use**: Attack type distribution as rose chart (DDoS/SQLi/XSS/phishing/malware).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'pie', radius: ['20%', '70%'], roseType: 'area',
    itemStyle: { borderRadius: 3, borderColor: '#000a00', borderWidth: 2 },
    data: [
      { value: 820, name: 'DDoS', itemStyle: { color: '#ff0040' } },
      { value: 640, name: 'SQL注入', itemStyle: { color: '#ff3366' } },
      { value: 480, name: 'XSS', itemStyle: { color: '#ffd700' } },
      { value: 390, name: '钓鱼', itemStyle: { color: '#00bfff' } },
      { value: 310, name: '恶意软件', itemStyle: { color: '#00ff41' } }
    ]
  }]
}
```
- **SQL output columns**: `attack_type TEXT, count INT`
- **refresh.intervalMs**: 10000

### cybersecurity.h-bar-attack-source
- **When to use**: Top attack source countries (horizontal bars with gradient).
- **ECharts option fragment**:
```js
{
  yAxis: { type: 'category', data: countryNames },
  series: [{
    type: 'bar',
    itemStyle: { color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
      { offset: 0, color: '#ff0040' }, { offset: 1, color: 'rgba(255,0,64,0.3)' }
    ]) }
  }]
}
```
- **SQL output columns**: `country_name TEXT, attack_count INT`
- **refresh.intervalMs**: 30000

### cybersecurity.stacked-bar-vuln
- **When to use**: Vulnerability fix status stacked bar by category.
- **ECharts option fragment**:
```js
{
  legend: { data: ['已修复', '修复中', '待修复'] },
  series: [
    { name: '已修复', type: 'bar', stack: 'total', itemStyle: { color: '#00ff41' } },
    { name: '修复中', type: 'bar', stack: 'total', itemStyle: { color: '#00bfff' } },
    { name: '待修复', type: 'bar', stack: 'total', itemStyle: { color: '#ffd700' } }
  ]
}
```
- **SQL output columns**: `category TEXT, fixed INT, in_progress INT, pending INT`
- **refresh.intervalMs**: 60000

### cybersecurity.heatmap-attack-matrix
- **When to use**: ATT&CK coverage matrix heatmap (tactics x techniques).
- **ECharts option fragment**:
```js
{
  visualMap: { min: 0, max: 100, inRange: { color: ['#000a00', '#003300', '#006600', '#00aa33', '#00ff41'] } },
  series: [{ type: 'heatmap', data: [[techIdx, tacticIdx, coveragePct], ...] }]
}
```
- **SQL output columns**: `tactic TEXT, technique TEXT, coverage_pct NUMERIC`
- **refresh.intervalMs**: 300000

### cybersecurity.funnel-kill-chain
- **When to use**: Cyber Kill Chain funnel (recon -> weaponize -> deliver -> exploit -> install -> C2 -> objective).
- **ECharts option fragment**:
```js
{
  series: [{
    type: 'funnel', sort: 'descending', gap: 2,
    label: { position: 'inside', color: '#000a00', fontWeight: 'bold', formatter: '{b}\n{c}%' },
    data: [
      { value: 100, name: '侦察', itemStyle: { color: '#00ff41' } },
      { value: 85, name: '武器化', itemStyle: { color: '#66ff66' } },
      { value: 72, name: '投递', itemStyle: { color: '#99ff66' } },
      { value: 58, name: '利用', itemStyle: { color: '#ccff66' } },
      { value: 45, name: '安装', itemStyle: { color: '#ffd700' } },
      { value: 32, name: 'C2', itemStyle: { color: '#ff9900' } },
      { value: 18, name: '达成目标', itemStyle: { color: '#ff0040' } }
    ]
  }]
}
```
- **SQL output columns**: `stage_name TEXT, event_count INT, percentage NUMERIC`
- **refresh.intervalMs**: 10000

## 触发线索（AI 用）
- User mentions "攻击", "漏洞", "防火墙", "安全", "入侵", "DDoS", "SQL注入", "ATT&CK", "Kill Chain", "态势感知", "SOC", "CISO", "恶意软件", "钓鱼" -> select this industry
- Table name contains `attack`, `vulnerability`, `alert`, `security`, `firewall`, `incident`, `threat`, `log` -> strong signal
- Columns like `source_ip`, `attack_type`, `severity`, `cve_id`, `port`, `protocol` -> strong signal
