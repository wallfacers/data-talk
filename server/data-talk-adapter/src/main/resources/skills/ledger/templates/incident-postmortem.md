# 问题复盘模板（`ledger.incident-postmortem.v1`）

## 适用场景

- 数据库性能事件复盘
- 线上故障 / 事故 postmortem
- 大型项目延期归因
- 数据质量事件复盘

## 必备 sections（required sections）

| 顺序 | section type | heading | 内容 |
|---|---|---|---|
| 1 | `cover` | （封面） | 事件名称、副标题（事件等级）、复盘日期 |
| 2 | `executive-summary` | 摘要 | 事件 1 句话定性 + 关键影响数字 + 行动结论 |
| 3 | `chapter` | 事件概况 | `timeline` + `narrative` |
| 4 | `chapter` | 影响范围 | `kpi-strip` + `table` |
| 5 | `chapter` | 根因分析 | `narrative` + `chart` |
| 6 | `chapter` | 行动项 | `risk-list` |
| 7 | `appendix(glossary)` | 术语表 | 事件涉及的专有名词解释 |

## 完整示例 JSON

```json
{
  "schemaVersion": 1,
  "kind": "report",
  "meta": {
    "title": "Q1 数据库性能事件复盘",
    "subtitle": "P2 级别 · 2026-04-15 14:23~16:48",
    "author": "DBA 团队",
    "generatedAt": "2026-05-10T15:00:00+08:00",
    "templateId": "ledger.incident-postmortem.v1",
    "templateVersion": "v1",
    "userPrompt": "做一份 Q1 数据库性能事件的复盘报告"
  },
  "theme": {
    "accent": "#B33A3A"
  },
  "sections": [
    {
      "type": "cover",
      "title": "Q1 数据库性能事件复盘",
      "subtitle": "P2 级别 · 主从同步延迟 2h 25min",
      "author": "DBA 团队",
      "date": "2026-05-10"
    },
    {
      "type": "executive-summary",
      "bullets": [
        "事件等级 P2，主从同步最大延迟 35s，持续 2h 25min",
        "影响 4 个下游业务：BI 报表、推荐召回、风控规则、运营看板",
        "根因：大事务 binlog 写入堆积 + 从库 SQL 线程单线程瓶颈",
        "行动项 3 项：大事务拆分规范、从库并行复制开启、监控阈值收紧"
      ]
    },
    {
      "type": "chapter",
      "heading": "事件概况",
      "blocks": [
        {
          "type": "timeline",
          "events": [
            {
              "at": "2026-04-15T10:23:00+08:00",
              "title": "首次报警",
              "description": "主从延迟从 1s 升至 35s，监控触发 P3 报警"
            },
            {
              "at": "2026-04-15T10:45:00+08:00",
              "title": "DBA 介入",
              "description": "确认是促销活动配置变更触发的大事务（涉及 800w 行的 UPDATE）"
            },
            {
              "at": "2026-04-15T11:30:00+08:00",
              "title": "升级 P2",
              "description": "BI 报表团队反馈数据滞后影响早会决策，事件升级"
            },
            {
              "at": "2026-04-15T12:48:00+08:00",
              "title": "恢复",
              "description": "大事务执行完毕，从库追上主库，延迟回到 1s 以内"
            },
            {
              "at": "2026-04-15T14:00:00+08:00",
              "title": "复盘启动",
              "description": "DBA / 业务方 / SRE 联合复盘会议"
            }
          ]
        },
        {
          "type": "narrative",
          "markdown": "事件从 10:23 首次报警到 12:48 恢复，持续 **2 小时 25 分钟**。期间主从延迟峰值 35s，导致所有读从库的下游业务出现数据滞后。\n\n事件触发原因是促销活动配置变更触发了一个 800 万行的 UPDATE 事务，binlog 写入速度远超从库 SQL 线程的应用速度。"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "影响范围",
      "blocks": [
        {
          "type": "kpi-strip",
          "items": [
            { "label": "持续时长", "value": "2h 25min" },
            { "label": "峰值延迟", "value": "35s" },
            { "label": "影响业务", "value": "4 个" },
            { "label": "用户感知", "value": "BI 报表滞后" }
          ]
        },
        {
          "type": "table",
          "columns": ["业务方", "影响", "严重度", "应对"],
          "rows": [
            ["BI 报表", "早会数据滞后 2h", "中", "口头通报"],
            ["推荐召回", "5 分钟前数据缺失", "低", "降级到 T-1 数据"],
            ["风控规则", "无影响（独立 OLAP）", "无", "—"],
            ["运营看板", "实时大盘卡顿", "中", "暂停 30 分钟"]
          ],
          "caption": "下游业务影响清单",
          "source": "internal · incident_log as of 2026-04-15"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "根因分析",
      "blocks": [
        {
          "type": "narrative",
          "markdown": "**直接原因**：促销配置变更脚本直接 `UPDATE sku SET promo_id = X WHERE category_id IN (...)`，影响 800w 行，未分批。\n\n**深层原因**：(1) 缺乏大事务拆分规范，开发可以随手提交全表更新；(2) 从库 SQL 线程单线程复制（5.7 默认配置），无法并行追赶；(3) 监控阈值 60s 过于宽松，错过早期介入窗口。"
        },
        {
          "type": "chart",
          "id": "ch-replication-lag",
          "echartsOption": {
            "tooltip": { "trigger": "axis" },
            "xAxis": { "type": "category", "data": ["10:00", "10:20", "10:40", "11:00", "11:20", "11:40", "12:00", "12:20", "12:40", "13:00"] },
            "yAxis": { "type": "value", "name": "delay (s)" },
            "series": [
              { "type": "line", "data": [1, 35, 28, 32, 25, 18, 12, 6, 2, 1], "smooth": true }
            ]
          },
          "caption": "主从延迟曲线（事件期间）",
          "source": "internal · mysql_replication_metrics as of 2026-04-15"
        }
      ]
    },
    {
      "type": "chapter",
      "heading": "行动项",
      "blocks": [
        {
          "type": "risk-list",
          "items": [
            {
              "severity": "critical",
              "description": "制定并落地大事务拆分规范（任何 > 10w 行的 DML 必须分批，单批 ≤ 1w 行）",
              "owner": "DBA + 开发团队",
              "dueDate": "2026-05-30"
            },
            {
              "severity": "high",
              "description": "MySQL 从库开启并行复制（slave_parallel_workers = 8）",
              "owner": "DBA",
              "dueDate": "2026-05-20"
            },
            {
              "severity": "medium",
              "description": "主从延迟监控阈值收紧：P2 触发降至 10s（当前 60s）",
              "owner": "SRE",
              "dueDate": "2026-05-25"
            }
          ]
        }
      ]
    }
  ],
  "appendix": [
    {
      "type": "appendix",
      "subType": "glossary",
      "title": "术语表",
      "items": [
        { "term": "binlog", "definition": "MySQL 二进制日志，记录所有数据变更，主库写入后从库读取并重放" },
        { "term": "SQL 线程", "definition": "从库上重放主库 binlog 的工作线程，5.7 默认单线程，瓶颈来源" },
        { "term": "主从延迟", "definition": "从库重放进度落后主库的时间差，单位秒" }
      ]
    }
  ]
}
```

## 写作要点

- **timeline 是核心**：复盘报告的"事件概况"章节必须有 `timeline` block，按时间顺序列出关键节点
- **risk-list 必含 owner + dueDate**：行动项不带 owner 与时限就是空话
- **根因要分层**：直接原因 + 深层原因（系统性 / 流程性 / 工具性），不要停在"开发写错 SQL"这种表层
- **theme.accent 用红色系**：复盘报告 accent 建议 `#B33A3A`，区别于业务月报的蓝色，视觉上传达"事件性"信号
