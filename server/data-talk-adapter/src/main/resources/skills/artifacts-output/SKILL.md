---
name: artifacts-output
description: Use when producing files, deliverables, or large tool outputs in a DataTalk session — covers artifact lifecycle (temporary vs archive candidate), `datatalk_archive_artifact` / `datatalk_supersede_artifact` / `datatalk_pin_artifact` tools, and the `saved file path` continuation protocol for oversized responses. Triggers on 工件 / 产物 / 归档 / 落盘 / saved file path / supersede / pin artifact / large output / archive artifact / 链接旧产物 / 替换工件.
---

# Artifacts Output Skill

## When to use

- The assistant has produced a deliverable file the user will likely want to keep (analysis report, ER diagram Markdown, SQL script, exported dataset)
- An MCP tool response is unusually large and the server has spilled it to disk, returning a `saved file path` instead of inlining the payload
- A newer artifact replaces an older one and the chain needs to be made explicit
- The user asks to pin / star / 置顶 a previously rendered artifact on their timeline
- The assistant wants to emit a YAML frontmatter hint on a Markdown / SQL file as a fallback in case the archive tool is forgotten

## When NOT to use

- Schema-level paging where `datatalk_read_schema` returns `truncated=true` for metadata — that is a schema-narrowing decision, not a saved-file continuation; see `[[sql-execution]]`
- Two-phase confirmable mutations (`confirm=true` / `confirmationToken`) — see `[[connection-management]]`
- Rendering a chart or dashboard payload — see `[[charts-and-dashboards]]`; `datatalk_render_chart` already accepts its own `supersedes` parameter and there is no need to call `datatalk_supersede_artifact` afterwards
- Ephemeral scratch files (CSV samples, debug logs) that the user has no reason to keep — leave them as Default (Temporary) and skip archiving

## Artifact lifecycle

DataTalk artifacts move through three tiers:

### Default (Temporary)

Files written via `write`, `edit`, or `bash` into the active session subdirectory (as required by the active-session-dir rule in AGENTS.md core) are auto-tracked but treated as ephemeral. They are cleaned up when the session is deleted. Use this tier for intermediate scratch files: CSV samples, debug logs, throwaway scripts.

### Promote to Archive Candidate

When the file is a deliverable the user will plausibly want to keep — analysis reports, ER diagrams, SQL scripts, datasets — promote it by calling `datatalk_archive_artifact`. The user then decides in their UI whether to permanently archive it to the connection's asset library. The promotion is a candidate, not a final commit.

### Rules

- Always write into the session subdirectory (as required by the active-session-dir rule in AGENTS.md core), not the parent cwd
- Never write into directories prefixed with `_` (system reserved)
- Never use symlinks
- For Markdown / SQL deliverables, a YAML frontmatter hint may be added as a fallback when the tool call is forgotten — but the tool is always the primary mechanism

## datatalk_archive_artifact

Promote a file produced in the active session from Default (Temporary) to Archive Candidate.

Required input: `path` (relative to the session subdir), `kind` (e.g. `er_diagram`, `sql_script`, `report_markdown`, `dataset_csv`). Recommended: `title`, `summary`.

Example call:

```
datatalk_archive_artifact(
  path="orders-er.md",
  kind="er_diagram",
  title="Orders domain ER",
  summary="Covers orders/order_items/payments relationships"
)
```

The handler is idempotent: re-archiving an already-archived path returns ok with a warn flag, not an error. Path-safety violations (traversal, symlink, system reserved, non-existent, directory-not-file) surface as typed errors — do not retry the same path; surface the failure to the user.

## datatalk_supersede_artifact

Explicitly link an older artifact to the newer artifact that replaces it. The wire keyword for this link is `supersedes` — the new artifact `supersedes` the old one.

Required input: `newArtifactId`, `oldArtifactId`.

Use only when both artifacts already exist as separate persisted records and the supersession link was not established at creation time. If the newer artifact was produced by a tool that already accepts a `supersedes` parameter (notably `datatalk_render_chart`), pass the old id at creation time and do NOT call `datatalk_supersede_artifact` again — that would double-write the link.

Typical flow: the user iterates on a chart, you render v2 via `datatalk_render_chart` with `supersedes=<v1Id>` — done. Only fall back to `datatalk_supersede_artifact` when v2 was created in a separate prior turn without the link, and the user now asks to mark v1 as replaced.

## datatalk_pin_artifact

Pin an artifact in the current client timeline so the user can scroll back to it quickly. This is a client-side timeline action, NOT durable server persistence — it does not change the artifact's archive state, and it does not affect the connection's asset library.

Required input: `artifactId`.

Use when the user says "pin this", "置顶这个图", "keep this visible", or similar. Do not pin proactively without a user signal.

## Large output handling (saved file path)

When a tool response says the output was too large to inline and provides a `saved file path` (typically alongside a partial preview and a length / row-count summary), treat it as a large-output continuation, NOT a tool failure.

How to continue:

1. Do not retry the same call hoping for a smaller response — the size is a property of the data, not the call.
2. Read or search the saved file directly to extract the facts you actually need (specific rows, specific columns, specific sections).
3. If the user asked a narrow analytical question, answer from a representative sample plus aggregate statistics; do not echo the full file back into chat.
4. Summarize only what matters for the user's question. Cite that the full payload lives at the saved file path so the user can inspect it independently if they want.
5. Never describe the saved-file outcome as "the tool failed" or "the data is unavailable" — it succeeded; the payload is on disk by design.

Schema-level metadata truncation is a different mechanism and lives in `[[sql-execution]]`; do not conflate the two.

## Frontmatter fallback

For Markdown or SQL files where the archive tool call was forgotten, the watcher will pick up a YAML frontmatter hint:

```
---
artifact: true
kind: report_markdown
title: Q2 2026 revenue summary
---
```

This is a fallback only. The tool call (`datatalk_archive_artifact`) remains the primary mechanism; rely on frontmatter only when re-issuing a tool call is not practical (e.g., the file was produced as a side effect of another action and the archive moment has already passed).
