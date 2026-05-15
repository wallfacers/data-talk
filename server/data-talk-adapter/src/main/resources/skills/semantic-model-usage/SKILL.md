---
name: semantic-model-usage
description: Use when the user asks for a business metric, uses business terms ("销售额" / "GMV" / "客单价" / "复购率" / 自然语言度量名), asks to define/lookup semantic model entities/dimensions/measures/metrics, or asks to record a verified query. Covers 6 Actions (semantic_lookup / verified_query_find / verified_query_record / semantic_propose_change / literal_mapping_add / skill_create), L0-L3 Verified Query routing, when to trigger propose_change vs record verified query. 当用户询问业务指标、使用业务术语、或需要查询/定义语义模型时使用。
---

# Semantic Model Usage Skill

## L0-L3 Verified Query Routing

Always call `datatalk_verified_query_find` before writing SQL — it may return a cached, human-confirmed answer:

| Layer | Match | LLM Calls | Action |
|-------|-------|-----------|--------|
| L0 | Exact question match | 0 | Return cached SQL directly |
| L1 | Normalized match (trim/case/synonym) | 0 | Return cached SQL directly |
| L2 | Top-K candidates by hit_count | 1 (LLM decides reuse/modify/new) | Inject candidates into prompt |
| L3 | No match | Multi-turn (fallback) | Full schema exploration |

## 6 Actions

### 1. `datatalk_semantic_lookup`
Search semantic model for entities, dimensions, measures, or metrics by name or label.
- **When**: User asks "这个表有什么指标", "订单的维度有哪些", or you need to find a measure definition.
- **Input**: `query` (string), optional `kind` (entity/measure/metric/dimension)
- **Returns**: Matched items with domain, labels, and definitions.

### 2. `datatalk_verified_query_find`
Find cached verified queries (L0/L1 exact + L2 top-K candidates).
- **When**: User asks a question that might have been answered before.
- **Input**: `question` (string), optional `topK` (default 5)
- **Returns**: `hit` (bool), `layer` (L0/L1/L2/NONE), `candidates` (array of VQ objects)
- **Side effect**: L0/L1 hit automatically increments hit_count.

### 3. `datatalk_verified_query_record`
Record a user-confirmed question-SQL pair.
- **When**: User marks an AI-generated SQL as correct, or explicitly approves a result.
- **Input**: `question`, `sql`, `modelRef` (domain name)
- **Side effect**: Appends to `verified_queries.jsonl`, publishes `VerifiedQueryRecorded` event.

### 4. `datatalk_semantic_propose_change`
Propose a new or modified semantic model YAML.
- **When**: AI infers a new domain, user asks to modify model structure, or AI wants to add entities/dimensions/measures.
- **Input**: `domain` (name), `yaml_text` (full YAML), `reason` (why)
- **Side effect**: Writes to `pending/<domain>.model.yaml`, publishes `SemanticPendingCreated` event.
- **Important**: Structural changes (modifying measures, adding entities) MUST go through this — never modify model files directly.

### 5. `datatalk_literal_mapping_add`
Add a natural-language-to-db-value mapping for a dimension.
- **When**: AI discovers a mapping like "已完成" = "COMPLETED", or user provides one.
- **Input**: `domain`, `dimension`, `natural` (language value), `dbValue` (database value)
- **Side effect**: Appends to `<domain>.model.yaml.patches.jsonl`.

### 6. `datatalk_skill_create`
Create a new business domain Semantic Model skill (AI self-skill).
- **When**: AI wants to create a completely new domain model (equivalent to propose_change for new domains).
- **Input**: `name` (domain name), `yaml_text` (full YAML)
- **Side effect**: Writes to `pending/<name>.model.yaml`.

## Decision Tree

```
User asks a data question
  |
  +--> Is it a business metric / term?
  |      |
  |      +--> Call datatalk_semantic_lookup to find the measure/metric definition
  |      +--> Call datatalk_verified_query_find to check for cached SQL
  |      +--> If L0/L1 hit: use cached SQL directly (no LLM reasoning needed)
  |      +--> If L2 candidates: decide whether to reuse, modify, or write new SQL
  |      +--> If writing new SQL: use discovered measure definitions
  |
  +--> User marks SQL as correct?
  |      +--> Call datatalk_verified_query_record to cache it
  |
  +--> AI discovers new business terms / mappings?
  |      +--> Call datatalk_literal_mapping_add for each mapping
  |
  +--> Need to create a new domain model?
         +--> Call datatalk_skill_create (for new domains)
         +--> Call datatalk_semantic_propose_change (for modifications)
```

## Important Rules

- Call `datatalk_verified_query_find` BEFORE writing any SQL — cached answers save compute and improve accuracy
- Literal mappings are low-risk patches (auto-accepted via patches.jsonl)
- Structural changes (measures, entities, metrics) MUST go through `propose_change` → `pending/` → user review
- All 6 actions require an active connection — if no connection is bound, they return `NO_ACTIVE_CONNECTION`
- The `{{SEMANTIC_MODEL_DIGEST}}` in AGENTS.md provides a live summary of the current connection's semantic model
