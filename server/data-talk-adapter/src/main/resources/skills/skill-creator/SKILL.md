---
name: skill-creator
description: Create a new business domain Semantic Model skill (YAML) for DataTalk. Triggered when the user asks to define business metrics, create a semantic model for a new domain, or build a skill for a specific business area. 当用户要求为业务域创建语义模型、定义业务指标、或创建新技能时使用。forked_from: anthropics/skills@<upstream-sha-pending>
---

# Skill Creator for DataTalk

## Purpose

Create business domain Semantic Model YAML skills for DataTalk. Each skill represents a business domain (e.g., orders, subscriptions, users) with entities, dimensions, measures, metrics, and literal mappings.

## Output Channel

**All skill output MUST go through `datatalk_skill_create`** — never use Write or file-system tools directly. This ensures the model goes to `pending/` for user review before becoming active.

## Semantic Model YAML Schema

The output is a YAML document following the DataTalk Semantic Model DSL (dbt MetricFlow subset + DataTalk extensions):

```yaml
name: <domain-name>          # required, snake_case, e.g. "orders"
version: 1                    # required, integer >= 1
description: <string>          # required, human-readable domain description
last_modified: <ISO8601>       # required, e.g. "2026-05-15T10:00:00Z"
authored_by: ai_inferred       # required: ai_inferred | user_authored | hybrid

entities:
  - name: <entity-name>        # required, snake_case
    type: primary              # primary | secondary
    physical:
      database: <db>           # optional
      schema: <schema>         # optional
      table: <table>           # required
    primary_key: [<col>, ...]  # required, non-empty array
    foreign_keys:              # optional
      - column: <col>
        ref: <entity>.<col>
    description: <string>

dimensions:
  - name: <dim-name>           # required
    entity: <entity-name>      # required, must reference declared entity
    expr: <sql-expression>     # required, usually column name
    type: categorical          # categorical | time | numeric
    time_granularity: day      # required if type=time: day|week|month|quarter|year
    label_zh: <中文标签>        # required, non-empty
    label_en: <English Label>  # required, non-empty
    description: <string>

measures:
  - name: <measure-name>       # required
    entity: <entity-name>      # required
    agg: sum                   # sum | count | count_distinct | avg | max | min
    expr: <sql-expression>     # required
    filter: <sql-where>        # optional
    label_zh: <中文标签>        # required
    label_en: <English Label>  # required
    description: <string>

metrics:
  - name: <metric-name>        # required
    type: ratio                # ratio | derived | cumulative
    numerator: <measure-name>   # required for ratio
    denominator: <measure-name> # required for ratio
    base: <measure-or-metric>   # required for derived
    time_offset: <duration>     # e.g. "1y", "1mo"
    label_zh: <中文标签>        # required
    label_en: <English Label>  # required
    description: <string>

literal_mappings:
  <dimension-name>:
    <natural-value>: <db-value>
```

## Workflow

1. **Understand the domain**: Ask the user which business area they want to model, or infer from schema exploration
2. **Explore the schema**: Use `datatalk_read_schema` to discover tables, columns, and relationships
3. **Draft the model**: Write YAML with entities, dimensions, measures, metrics
4. **Submit via `datatalk_skill_create`**: Call the action with `name` and `yaml_text`. The backend writes to `pending/` for user review

## Rules

- domain name must match `^[a-z][a-z0-9_]{0,63}$`
- Every dimension/measure/metric MUST have both `label_zh` and `label_en`
- Measure `entity` and `agg` are required
- Entity `physical.table` is required
- Metric type `ratio` requires `numerator` + `denominator`
- Never modify existing model files directly — always use `datatalk_semantic_propose_change` for modifications
