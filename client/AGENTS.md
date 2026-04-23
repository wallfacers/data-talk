# DataTalk Client Local Rules

These rules apply to work scoped to `client/`.

## Design Contract Gate

- Before writing or modifying frontend requirements, product specs, design docs, execution plans, implementation proposals, or UI code in `client/`, the agent **MUST** read `DESIGN.md`.
- The agent **MUST** explicitly state which constraints from `DESIGN.md` apply before proposing or implementing `client/` changes.
- If a requested frontend direction conflicts with `DESIGN.md`, the agent **MUST** stop and ask whether to:
  1. follow `DESIGN.md`
  2. update `DESIGN.md` first
  3. intentionally diverge with written justification
- Frontend plans/specs/proposals that do not cite `DESIGN.md` are incomplete and **MUST NOT** be treated as ready.

## Frontend Plan Gate

- Before entering `/plan` for any UI, UX, visual, layout, component, page-shell, interaction, or design-system change in `client/`, the agent **MUST**:
  1. read `DESIGN.md`
  2. summarize the applicable design constraints
  3. only then draft the plan/spec
- Frontend `/plan` output **MUST** include a `Design Inputs` section that cites `DESIGN.md` and the constraints applied.

## Implementation Expectations

- Prefer semantic tokens and the vocabulary defined in `DESIGN.md`; do not introduce raw color values or ad hoc visual language unless `DESIGN.md` is updated first.
- If `DESIGN.md` and existing implementation diverge, treat that as a contract issue to resolve explicitly rather than silently following whichever is more convenient.
