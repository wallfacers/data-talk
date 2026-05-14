---
name: concurrency-contract
description: Use when an LLM agent edits workbench tabs (query_editor, artifact_preview, er_inspector, er_designer, future report_designer) and encounters version conflicts or must guard against concurrent writes. Triggers on 版本冲突 / 409 / version_conflict / expected_text_mismatch / baseVersion / expectedVersion / optimistic locking / multi-edit batch / concurrent edit / 乐观锁 / 并发编辑 / 重试. Defines the optimistic-locking contract for workspace-wide, persisted tabs that any session (including parallel agents) may have mutated since your last read, plus mandatory recovery steps on conflict.
---

# Concurrency Contract Skill

## When to use

- You are about to call a tool that mutates a workbench tab (`query_editor`, `artifact_preview`, `er_inspector`, `er_designer`, future `report_designer`).
- A previous tool call returned `error.code = "version_conflict"`, `"expected_text_mismatch"`, `"out_of_range_lines"`, `"tab_not_found"`, or `"tab_archived"`.
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

The server enforces this with optimistic locking: every mutation must declare the `baseVersion` (and, for text edits, the `expectedText`) the client believes is current. On mismatch the server rejects the request with a structured conflict error rather than silently overwriting another session's work.

## Required guard fields

- `datatalk_ui_patch` with `path=/content` requires top-level `baseVersion: number`.
- `datatalk_ui_patch` on ER designer structural paths requires top-level `baseVersion: number`.
- `datatalk_ui_exec apply_text_edits` requires `params.baseVersion: number`.
- Each entry in `params.edits` requires `expectedText: string`, the exact text currently occupying `range`. The server compares it after line-ending normalization (`\r\n` to `\n`).

`baseVersion` is the optimistic-lock version number the agent obtained from its most recent `datatalk_ui_read mode=state` (or the `currentState.version` echoed by a prior successful mutation). `expectedVersion` is the same concept surfaced in some `ui_patch` server-side error fields when the server reports which version it expected vs the one the client sent. `expectedText` is the byte-exact text the agent believes occupies `range` at `baseVersion`. All three are part of the same family of optimistic-locking guards.

For tool-call shape and parameter schema, see `[[ui-contract]]` — this skill is the source of truth for **what to do when the guards fail**, not how to call the tools.

## Conflict response shape

```json
{
  "error": {
    "code": "version_conflict" | "expected_text_mismatch" | "out_of_range_lines" | "tab_not_found" | "tab_archived",
    "message": "<one-line machine summary>",
    "currentState": { "version": 14, "tabId": "qe-1" },
    "markdown": "<human-and-LLM-readable explanation>",
    "details": { "editIndex": 0, "expected": "...", "actual": "..." }
  }
}
```

- `version_conflict` — the `baseVersion` you sent is stale; another writer advanced the tab.
- `expected_text_mismatch` — your `baseVersion` may be current but the `expectedText` of at least one edit no longer matches the actual range content (e.g. line endings, whitespace, or a sibling edit).
- `out_of_range_lines` — the requested `range` exceeds the document line count at `baseVersion`.
- `tab_not_found` / `tab_archived` — the target tab has been removed from the active workspace; do not auto-recreate it.

`error.markdown` is the human-and-LLM-readable explanation; render or read it before retrying. `error.currentState.version` is your new baseline when present.

## Conflict recovery steps

On any conflict error you MUST follow these five steps in order:

1. **Stop.** Do not retry with the same `baseVersion` or `expectedText`.
2. **Read `error.markdown`.** It includes the current content for the affected range and a hint about who likely changed it.
3. **Call `datatalk_ui_read`** on the same `target` with `mode='state'` to get the new `version` and `content`. See `[[ui-contract]]` for the read call signature.
4. **Re-plan your edit** against the new content. Your new range and `expectedText` must match the freshly read snapshot exactly.
5. **Submit a single fresh `apply_text_edits`** (or `ui_patch`) with the new `baseVersion`.

For query-editor-specific recovery nuances (when to fall back to `ui_patch /content` vs re-issuing `apply_text_edits`), see `[[query-editor-workflow]]`.

## Multi-edit batches

`apply_text_edits` (see `[[ui-contract]]`) accepts multiple edits in `params.edits`. The server applies them in **reverse line order** against the snapshot at `baseVersion`, as a single transactional unit. Either all edits apply or none do, with a single `error.markdown`.

If `error.code='expected_text_mismatch'`, `error.details.editIndex` is the 0-based index of the failing edit in the request array. Earlier edits in the same batch were **not** applied — the entire batch is rolled back. Plan your retry as a fresh single-batch `apply_text_edits` against the new `baseVersion`; do not pick out only the failing edit.

The reverse-line-order application means edits at higher line numbers do not shift the ranges of edits at lower line numbers within the same batch. Author `params.edits` in any order you find readable; the server handles ordering deterministically.

## What you MUST NOT do

- Do not loop the same edit hoping the conflict clears.
- Do not assume `version_conflict` means your edit is wrong; it usually means another session reached the tab first.
- Do not trash or archive a tab to force a clean slate unless the user explicitly asked you to.
- Do not claim "another session reverted the tab" or any similar concurrent-edit narrative without direct evidence. Direct evidence means a `version_conflict` / `expected_text_mismatch` error, or a `version` value in your own read sequence that jumped beyond what your edits could explain. If you do not have that evidence, treat the discrepancy as a stale or wrong-tab read on your side, re-read with `datatalk_ui_read mode=state`, and verify the `target` tab id before retrying.

## Multi-session etiquette

- After mutating, the change is visible to subsequent `datatalk_ui_find` calls in any session before your next tool call returns.
- Treat workbench tabs as collaborative artifacts: read fresh state at the start of any task that resumes earlier work, and prefer narrow `apply_text_edits` over wholesale `/content` replacement so concurrent edits from other sessions are preserved.
- When you observe a tab whose `version` advanced without your involvement, do not panic-revert. Read it, decide whether the user's intent still applies, and continue from the new baseline.
