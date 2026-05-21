---
name: concurrency-contract
description: Use when an LLM agent edits workbench tabs (query_editor, artifact_preview, er_inspector, er_designer, future report_designer) and encounters version conflicts or must guard against concurrent writes. Triggers on 版本冲突 / 409 / version_conflict / anchor_not_found / anchor_ambiguous / baseVersion / expectedVersion / optimistic locking / multi-edit batch / concurrent edit / 乐观锁 / 并发编辑 / 重试. Defines the optimistic-locking contract for workspace-wide, persisted tabs that any session (including parallel agents) may have mutated since your last read, plus mandatory recovery steps on conflict.
---

# Concurrency Contract Skill

## When to use

- You are about to call a tool that mutates a workbench tab (`query_editor`, `artifact_preview`, `er_inspector`, `er_designer`, future `report_designer`).
- A previous tool call returned `error.code = "version_conflict"`, `"anchor_not_found"`, `"anchor_ambiguous"`, `"tab_not_found"`, or `"tab_archived"`.
- You are planning a multi-edit batch and need the transactional semantics.
- The user reports "my changes disappeared" or "another agent rewrote my tab" and you must decide whether to claim a concurrent edit.
- You see a `version` value in your read sequence that does not match what your own edits should have produced.

## When NOT to use

- Read-only tool calls (`datatalk_ui_read`, `datatalk_ui_find`) — no version guard needed.
- Tool-signature questions for `datatalk_ui_patch` or `apply_text_edits` — see `[[ui-contract]]`.
- ER tab structural patch path whitelists — see `[[er-tabs]]`.
- Query editor `/content` edit workflow (when to use patch vs apply_text_edits, idempotent reuse, etc.) — see `[[query-editor-workflow]]`.

## Why concurrency matters

Workbench tabs (`query_editor`, `artifact_preview`, `er_inspector`, `er_designer`, future `report_designer`) are workspace-wide objects shared across all chat sessions. They are shared across all sessions and persisted across app restarts. Any session, including a parallel agent, may have edited a tab since your last read. Treat every patch and text edit as optimistic and conflict-aware.

The server enforces this two ways. For **whole-document** writes (`/content`) it uses strict optimistic locking: the mutation must declare the `baseVersion` the client believes is current, and a stale value is rejected. For **`apply_text_edits`** it uses **content anchoring**: each edit names the `oldText` it wants to replace, and the server locates that snippet in the live content — so the edit survives benign concurrent changes elsewhere in the tab (auto-rebase) and only fails when the anchored text itself is gone or ambiguous.

## Required guard fields

- `datatalk_ui_patch` with `path=/content` requires top-level `baseVersion: number` (strict — stale value rejected with `version_conflict`).
- `datatalk_ui_patch` on ER designer structural paths requires top-level `baseVersion: number`.
- `datatalk_ui_exec apply_text_edits`: `baseVersion` is **optional and advisory**. Each entry in `params.edits` requires `oldText` (the snippet to find) and `newText` (its replacement). Whitespace is matched flexibly; non-whitespace tokens must match exactly.

`baseVersion` is the optimistic-lock version number the agent obtained from its most recent `datatalk_ui_read mode=state` (or the `currentState.version` echoed by a prior successful mutation). For `/content` it is a hard guard; for `apply_text_edits` it only labels the result `rebased: true` when the tab advanced underneath you — the edit still applies as long as every `oldText` uniquely matches.

For tool-call shape and parameter schema, see `[[ui-contract]]` — this skill is the source of truth for **what to do when the guards fail**, not how to call the tools.

## Conflict response shape

```json
{
  "error": {
    "code": "version_conflict" | "anchor_not_found" | "anchor_ambiguous" | "tab_not_found" | "tab_archived",
    "message": "<one-line machine summary>",
    "currentState": { "version": 14, "tabId": "qe-1", "content": "<live content>" },
    "markdown": "<human-and-LLM-readable explanation>",
    "details": { "editIndex": 0, "oldText": "...", "matchCount": 2 }
  }
}
```

- `version_conflict` — only from `/content` patches: the `baseVersion` you sent is stale; another writer advanced the tab.
- `anchor_not_found` — the `oldText` of at least one edit does not appear in the live content. This is **not** about line numbers — it means the snippet itself is wrong (whitespace tolerated, but non-whitespace tokens must match) or the text has since changed. Re-read the live content and copy `oldText` from it. `details.oldText` echoes what you sent.
- `anchor_ambiguous` — the `oldText` matches `details.matchCount` locations. Add more surrounding context so it identifies exactly one spot, or pass `hint.line` (1-based line of the intended match).
- `tab_not_found` / `tab_archived` — the target tab has been removed from the active workspace; do not auto-recreate it.

`error.markdown` is the human-and-LLM-readable explanation; render or read it before retrying. `error.currentState.version` is your new baseline when present.

## Conflict recovery steps

On any conflict error you MUST follow these steps in order:

1. **Stop.** Do not retry the same edit unchanged.
2. **Read `error.markdown` / `error.currentState.content`.** It carries the live content and a hint about what failed.
3. **For `anchor_not_found`**: re-read with `datatalk_ui_read mode='state'` if needed, then rebuild `oldText` so it is a verbatim snippet of the current content. **For `anchor_ambiguous`**: keep the same intent but widen `oldText` with surrounding context (or add `hint.line`) so it matches exactly once. **For `version_conflict`** (`/content` only): re-read to get the new `version`, then resend with that `baseVersion`.
4. **Submit a single fresh `apply_text_edits`** (or `ui_patch`).

For query-editor-specific recovery nuances (when to fall back to `ui_patch /content` vs re-issuing `apply_text_edits`), see `[[query-editor-workflow]]`.

## Multi-edit batches

`apply_text_edits` (see `[[ui-contract]]`) accepts multiple edits in `params.edits`. All `oldText` anchors are resolved against the **same content snapshot**, then applied as a single transactional unit. Either all edits apply or none do. If two anchors resolve to overlapping spans the batch is rejected with `invalid_params`.

If the batch fails (`anchor_not_found` / `anchor_ambiguous` / `invalid_params`), `error.details.editIndex` is the 0-based index of the failing edit in the request array. No edit in the batch was applied — the entire batch is rolled back. Plan your retry as a fresh single-batch `apply_text_edits`; do not pick out only the failing edit.

Because anchors are resolved against one snapshot and applied by descending offset, edits do not shift each other's positions. Author `params.edits` in any order you find readable; the server handles ordering deterministically.

## What you MUST NOT do

- Do not loop the same edit hoping the conflict clears.
- Do not assume `version_conflict` means your edit is wrong; it usually means another session reached the tab first.
- Do not trash or archive a tab to force a clean slate unless the user explicitly asked you to.
- Do not claim "another session reverted the tab" or any similar concurrent-edit narrative without direct evidence. An `anchor_not_found` / `anchor_ambiguous` error is **not** evidence of concurrency — it usually means your `oldText` did not match the live content. Direct evidence of concurrency means a `version_conflict` error, a `rebased: true` result, or a `version` value in your own read sequence that jumped beyond what your edits could explain. Without that evidence, treat the discrepancy as a stale or wrong-tab read on your side, re-read with `datatalk_ui_read mode=state`, and verify the `target` tab id before retrying.

## Multi-session etiquette

- After mutating, the change is visible to subsequent `datatalk_ui_find` calls in any session before your next tool call returns.
- Treat workbench tabs as collaborative artifacts: read fresh state at the start of any task that resumes earlier work, and prefer narrow `apply_text_edits` over wholesale `/content` replacement so concurrent edits from other sessions are preserved.
- When you observe a tab whose `version` advanced without your involvement, do not panic-revert. Read it, decide whether the user's intent still applies, and continue from the new baseline.
