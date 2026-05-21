export const ALLOWED_DIRECT_STAGE_STORE_MUTATION_FILES = [
  'src/stores/stage-store.ts',
  'src/features/stage/stores/sql-workbench-store.ts',
  'src/stores/stage-store.test.ts',
  'src/__tests__/forbidden-direct-mutation.test.ts',
  // Tests may seed store state directly.
  'src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts',
  'src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts',
  'src/features/actions/__tests__/ui-handlers.test.ts',
  'src/features/stage/utils/query-editor-actions.test.ts',
  'src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts',
  'src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts',
  'src/features/stage/utils/open-or-focus-file-preview-tab.test.ts',
  'src/features/stage/components/activity-rail/stage-activity-rail.test.tsx',
  'src/features/stage/components/sql-workbench-tab.test.tsx',
  'src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts',
  'src/features/stage/components/stage-tab-content.test.tsx',
  'src/features/stage/components/stage-toggle-button.test.tsx',
  'src/features/stage/components/stage-ui-object-registry.test.tsx',
  'src/features/stage/components/stage-window.test.tsx',
  'src/features/stage/components/left-rail/stage-left-rail.test.tsx',
  'src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts',
  'src/features/stage/persistence/__tests__/stage-persistence-bootstrap.query-editor.test.ts',
  'src/features/stage/persistence/__tests__/stage-persistence-bootstrap.stage-tab-payload.test.ts',
  // Stage store tests.
  'src/features/stage/stores/sql-workbench-store.test.ts',
  'src/features/stage/use-stage-auto-open.test.ts',
  // Session / settings / workspace tests.
  'src/features/session/split-view.test.tsx',
  'src/features/settings/general/general-panel.test.tsx',
  'src/features/chat/components/tools/__tests__/read-file.test.tsx',
  'src/features/stage/components/left-rail/stage-rail-row-menu.test.tsx',
  // DML summary panel / undo tests — need setState to seed undo states.
  'src/features/stage/components/sql-dml-summary-panel.test.tsx',
  'src/features/stage/stores/sql-workbench-store-undo.test.ts',
]

export function isAllowedDirectStageStoreMutationFile(filename) {
  const normalized = filename.split('/').join('/')
  return ALLOWED_DIRECT_STAGE_STORE_MUTATION_FILES.some((allowed) => normalized.endsWith(allowed))
}
