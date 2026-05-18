---
name: exploring-data
description: Use when the AI is about to write SQL touching tables it has not yet read — multi-table JOIN / cross-table aggregation / first-time operation on an unfamiliar table — OR when the user's keyword does not yet map to a known table name. Triggers on 分析销售/统计/探索/不知道表名/where is the table for X/which table stores/汇总数据/give me trend of. Defines the Pre-Action Exploration Protocol: ordering get_data_context → schema_search → read_schema → execute_sql, budget 3 misses → question tool.
---

# Exploring Data Skill

The mantra: **gather enough context first, then act**. Writing SQL on an unfamiliar schema without first reading it is the single most common cause of wasted tokens and user frustration.

## When to use

- "分析销售趋势" — the AI does not yet know which table holds 销售 data.
- "Top 10 customers by spend" — multi-table JOIN with no prior schema read.
- `datatalk_execute_sql` just failed with `noSuchTable` or `unknownColumn`.
- The user gave a Chinese / pinyin / abbreviated term that is unlikely to match an English table name 1:1.
- About to write any SQL with JOIN, GROUP BY across tables, or subqueries that touch multiple tables not all previously read in this session.

## When NOT to use

- The user is in the query editor workflow ("open SQL for users", "show 10 rows from orders") — that is the query editor's job; do not pre-fetch via this skill. See `[[query-editor-workflow]]`.
- The user is doing a session-attribution question ("which connection am I on") — that goes through `[[connection-management]]`, not exploration.
- The SQL is a simple single-table SELECT against a table the AI already read this session — no need to re-explore.
- DELETE confirmation flow — that is `[[sql-execution]]` plus the conversational confirmation contract.

## Pre-Action Exploration Protocol

Hard-ordered sequence. Each step MUST complete before the next runs.

1. `datatalk_get_data_context` — confirm active connection / database / schema. If `<no active session>`, stop and ask the user to bind one.
2. `datatalk_schema_search` *if you do not yet know the exact table name* — pass the user's keyword (Chinese / English / pinyin). Read the returned candidates' table / column / comment matches and pick the highest-scoring candidate. Do NOT repeatedly call `datatalk_read_schema` to guess a table name; that is exactly the failure mode this skill exists to prevent.
3. `datatalk_read_schema` — describe every table you plan to reference. For multi-table SQL, read every involved table BEFORE writing the SQL. Repeat reads on already-read tables in the same session are unnecessary.
4. `datatalk_execute_sql` — write the SQL with all columns matching the schema you just verified. DELETE follows the `requires_confirmation` flow described in `[[sql-execution]]`.

## Exploration budget

If the same exploration target (same keyword OR same table name) misses 3 times in the current session, you MUST escalate to the user via the `question` tool.

A "miss" is any of:

- `datatalk_schema_search` returns empty `candidates`.
- `datatalk_read_schema` returns `noSuchTable`.
- `datatalk_execute_sql` fails with table-not-found or unknown-column.

The 4th identical exploration is forbidden. Phrase the escalation as a concise question: state what you tried, what came back empty, and ask the user to confirm the table or column name. Then stop — do not "try one more time before asking".

## Recent context awareness

Two prompt placeholders carry session memory the AI MUST consult:

- `recent active connection summary` — current bound connection (kind / db / schema) plus the 3 most recent successful SQL queries on that connection. Reuse the user's style.
- `recent failed queries digest` — up to 3 recent failures with errorCode + message. If the user asks something similar, address the root cause instead of re-issuing the broken SQL.

The placeholder names are visible in the system prompt — read their content, do not call any tool to "open" them.

## Boundaries with adjacent skills

- `[[sql-execution]]` — once you know the table and columns, hand off the actual `datatalk_execute_sql` call there. That skill covers SELECT/DML/DDL, truncation, DELETE confirmation, and result-shaping. This skill ends at "I know what to query".
- `[[connection-management]]` — if the user is switching connection / database / schema, route there. This skill assumes a bound session.
- `[[query-editor-workflow]]` — if the user wants to open a SQL editor, browse rows, or run a single-table preview, route there. This skill is for the analytical / cross-table case where the AI itself writes the SQL.

## Good vs Bad examples

### Example 1 — Chinese keyword for an English-named table

**Bad** — user says "分析销售趋势". AI guesses table name and runs `datatalk_execute_sql("SELECT sum(amount) FROM sales GROUP BY month")`. Tool errors with `noSuchTable: sales`. AI retries `datatalk_read_schema(pattern="sale")` — empty. AI retries 3 more times with `pattern="orders"`, `pattern="revenue"`, `pattern="business"` — all empty. Six wasted tool calls, no answer.

**Good** — user says "分析销售趋势". AI runs `datatalk_get_data_context` (active conn confirmed), then `datatalk_schema_search(keyword="销售")`. Result: `t_sales_order` (score 5, comment `"销售订单主表"`). AI runs `datatalk_read_schema(tables=["t_sales_order"])` — columns confirmed: `order_date`, `amount`, `status`. AI runs the analytical SQL on the first try.

### Example 2 — keyword that does not exist anywhere

**Bad** — user says "查 wuwu 表的数据". AI runs `datatalk_read_schema(pattern="wuwu")` — empty. AI tries again `pattern="wu"` — empty. AI tries `pattern="ww"` — empty. AI tries `datatalk_execute_sql("SELECT * FROM wuwu")` — fails. AI tries a 4th `read_schema` with a different fuzzy guess.

**Good** — user says "查 wuwu 表的数据". AI runs `datatalk_schema_search(keyword="wuwu")` — empty. AI runs `datatalk_schema_search(keyword="ww")` — empty. AI runs `datatalk_read_schema(pattern="wuwu")` — empty. Budget exhausted (3 misses). AI calls the `question` tool: "I searched for a table named 'wuwu' and similar variants but found nothing in the current connection. Could you confirm the exact table name, or describe what data it should hold?"

### Example 3 — reusing recent successful query patterns

**Bad** — the `recent active connection summary` shows the user's last 3 successful queries used `WHERE created_at >= date_trunc('month', now())` style. AI ignores this and writes `WHERE created_at > '2026-05-01'::date`, mixing styles inconsistently.

**Good** — AI reads the recent successful queries digest, sees the user's style preference, and continues with `date_trunc('month', now())` for date filtering. Style stays consistent.

### Example 4 — heeding recent failures

**Bad** — the `recent failed queries digest` shows the last 2 SQL attempts failed with `unknownColumn: status_code`. User now asks "再查一下". AI rewrites the same SQL with `status_code` again — same failure.

**Good** — AI reads the failures digest, notices `status_code` does not exist. AI runs `datatalk_read_schema(tables=["the_table"])` to find the right column name (it is `state` not `status_code`), then writes correct SQL.

## Anti-patterns to avoid

- "Read first, ask later" with no budget — looping `datatalk_read_schema` with slightly different patterns forever.
- Searching using English keywords when the user spoke Chinese.
- Skipping `datatalk_get_data_context` and assuming the session has a connection bound.
- Re-running a SQL that already failed in this session without changing the failing identifier.
- Calling the `question` tool *before* exhausting the exploration budget — wastes the user's turn when a simple search would have answered it.

## Quick reference

| Situation | First call |
|---|---|
| User keyword is Chinese / pinyin / abbreviation | `datatalk_schema_search(keyword=<as-said>)` |
| User keyword is English and likely matches table name | `datatalk_read_schema(pattern=<keyword>, mode=discover)` |
| Active session unbound | `datatalk_get_data_context` first; then ask user to bind |
| 3 misses on same target | `question` tool — stop searching |
| About to write multi-table JOIN | `datatalk_read_schema(tables=[all involved])` before writing |
