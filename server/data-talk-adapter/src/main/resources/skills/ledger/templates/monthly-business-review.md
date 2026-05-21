# 业务月报模板（`ledger.monthly-business-review.v1`）

## 适用场景

- 业务月报 / 周报 / 季报
- 渠道、品类、区域维度的 GMV / 转化率 / 客单价等核心指标对比
- 周期性正向汇报（不是事件性归因）

## 必备 sections（required sections）

| 顺序 | section type | heading | 内容 |
|---|---|---|---|
| 1 | `cover` | （封面） | 标题、副标题、作者、日期 |
| 2 | `executive-summary` | 摘要 | 3-7 条核心结论 |
| 3 | `toc` | 目录 | 自动生成 |
| 4 | `chapter` | 业务总览 | `kpi-strip` + `narrative` + `chart` |
| 5 | `chapter` | 渠道表现 | `table` + `narrative` |
| 6 | `chapter` | 区域分析 | `chart` + `table` + `narrative` |
| 7 | `chapter` | 风险与建议 | `risk-list` + `narrative` |

## 完整示例 JSON

```json
{
  "schemaVersion": 1,
  "kind": "report",
  "meta": {
    "title": "2026 年 4 月销售月报",
    "subtitle": "全渠道 GMV 与品类表现",
    "author": "数据分析团队",
    "generatedAt": "2026-05-15T10:00:00+08:00",
    "templateId": "ledger.monthly-business-review.v1",
    "templateVersion": "v1",
    "userPrompt": "做一份 2026 年 4 月的销售月报，覆盖渠道、区域、品类三个维度"
  },
  "theme": {
    "primary": "#0F2A4A",
    "accent": "#2F6FBF",
    "surface": "#F4F7FB"
  },
  "sections": [
    {
      "type": "cover",
      "title": "2026 年 4 月销售月报",
      "subtitle": "全渠道 GMV 与品类表现",
      "author": "数据分析团队",
      "date": "2026-05-15"
    },
    {
      "type": "executive-summary",
      "bullets": [
        "总 GMV 3.2M 元，同比 +18%，环比 +9%",
        "自营渠道贡献 38%，抖音渠道环比增长 28%",
        "华东区域 GMV 占比 42%，西南区域同比下降 5% 需关注",
        "鞋服品类拉动主要增长，3C 品类毛利率下滑 2pp"
      ]
    },
    { "type": "toc" },
    {
      "type": "chapter",
      "heading": "业务总览",
      "blocks": [
        {
          "type": "stat-highlight",
          "value": "¥3.2M",
          "label": "本月总 GMV",
          "context": "目标完成率 107%",
          "delta": "+18%"
        },
        {
          "type": "kpi-strip",
          "items": [
            { "label": "总 GMV", "value": "¥3.2M", "delta": "+18%" },
            { "label": "订单数", "value": "12,345", "delta": "+9%" },
            { "label": "客单价", "value": "¥259", "delta": "+8%" },
            { "label": "毛利率", "value": "23.5%", "delta": "+1.2pp" }
          ]
        },
        {
          "type": "narrative",
          "markdown": "本月总 GMV 3.2M 元，同比 +18%，环比 +9%，整体延续 Q1 增长态势。订单数与客单价同步上行，反映出**用户复购**与**客单提升**两个驱动同时生效。毛利率回升 1.2pp，主要来自鞋服品类高毛利新品的拉动。"
        },
        {
          "type": "chart",
          "id": "ch-monthly-trend",
          "echartsOption": {
            "tooltip": { "trigger": "axis" },
            "xAxis": { "type": "category", "data": ["1月", "2月", "3月", "4月"] },
            "yAxis": { "type": "value", "name": "GMV (M)" },
            "series": [
              { "type": "line", "data": [2.4, 2.6, 2.9, 3.2], "smooth": true }
            ]
          },
          "caption": "近 4 个月 GMV 趋势（百万元）"
        },
        {
          "type": "callout",
          "variant": "insight",
          "title": "核心洞察",
          "markdown": "抖音渠道环比 **+28%** 是本月增长主引擎，单渠道贡献了总增量的 62%。"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "渠道表现",
      "blocks": [
        {
          "type": "table",
          "columns": ["渠道", "GMV (万元)", "占比", "同比", "环比"],
          "cellFormats": ["text", "bar", "heat", "delta", "delta"],
          "rows": [
            ["自营", "121.6", "38", "+15%", "-2%"],
            ["抖音", "83.2", "26", "+28%", "+28%"],
            ["天猫", "71.2", "22", "+12%", "+5%"],
            ["京东", "44.0", "14", "+8%", "+3%"]
          ],
          "caption": "Top 渠道 GMV 表现（条形=GMV，热力=占比，delta=同/环比）"
        },
        {
          "type": "narrative",
          "markdown": "**抖音**渠道环比 +28%，源自达人合作覆盖率从 12% 提升到 19%；**自营**渠道环比微降 2%，主要受 4 月中旬大促节奏调整影响。\n\n建议下月：\n- 自营加强会员复购运营，目标环比回正\n- 抖音持续扩量但 ROI 不低于 1.5"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "区域分析",
      "blocks": [
        {
          "type": "chart",
          "id": "ch-region-share",
          "echartsOption": {
            "tooltip": { "trigger": "item" },
            "series": [
              {
                "type": "pie",
                "radius": ["40%", "70%"],
                "data": [
                  { "name": "华东", "value": 42 },
                  { "name": "华南", "value": 23 },
                  { "name": "华北", "value": 18 },
                  { "name": "华中", "value": 10 },
                  { "name": "西南", "value": 7 }
                ]
              }
            ]
          },
          "caption": "4 月 GMV 区域占比（%）"
        },
        {
          "type": "comparison",
          "items": [
            { "label": "华东（增长引擎）", "value": "¥134.4万", "caption": "占比 42% · 同比 +22%" },
            { "label": "西南（需关注）", "value": "¥22.4万", "caption": "占比 7% · 同比 -5%" }
          ]
        },
        {
          "type": "table",
          "columns": ["区域", "GMV (万元)", "同比", "环比"],
          "rows": [
            ["华东", "134.4", "+22%", "+12%"],
            ["华南", "73.6", "+15%", "+8%"],
            ["华北", "57.6", "+18%", "+10%"],
            ["华中", "32.0", "+9%", "+5%"],
            ["西南", "22.4", "-5%", "-3%"]
          ],
          "caption": "区域 GMV 同环比"
        },
        {
          "type": "narrative",
          "markdown": "**华东**区域 GMV 占比 42%，同比 +22%，是核心增长引擎。**西南**区域同比 -5%，已连续 2 个月负增长，需重点排查：(1) 当地仓储发货时效；(2) 区域促销投放力度。"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "风险与建议",
      "blocks": [
        {
          "type": "risk-list",
          "items": [
            {
              "severity": "high",
              "description": "西南区域连续 2 个月负增长，需 5 月排查仓储与营销根因",
              "owner": "区域运营 + BI",
              "dueDate": "2026-05-30"
            },
            {
              "severity": "medium",
              "description": "3C 品类毛利率下滑 2pp，需评估是否调整定价策略",
              "owner": "品类运营",
              "dueDate": "2026-06-15"
            },
            {
              "severity": "low",
              "description": "抖音渠道 ROI 接近 1.5 警戒线，需控制扩量节奏",
              "owner": "渠道运营",
              "dueDate": "2026-05-25"
            }
          ]
        },
        {
          "type": "narrative",
          "markdown": "整体而言，4 月业务延续 Q1 上升态势，但西南区域与 3C 品类的结构性问题需要专项关注。建议 5 月组建跨部门 task force 专项排查。"
        }
      ]
    }
  ]
}
```

## 写作要点

- **数据先于叙事**：每个 chapter 的 `narrative` 必须紧贴前面的 `kpi-strip` / `chart` / `table` 数据，不能写脱离数据的笼统话术
- **结论可执行**：每章节末的建议必须含 owner 与可衡量目标
- **环比 + 同比**：业务月报的核心对比维度，缺一不可
