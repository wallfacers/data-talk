import { describe, it, expect } from 'vitest'
import { Project, SyntaxKind } from 'ts-morph'
import path from 'node:path'

const ALLOWED_FILES = new Set([
  'src/stores/stage-store.ts',
  'src/features/stage/stores/sql-workbench-store.ts',
  'src/stores/stage-store.test.ts',
  'src/__tests__/forbidden-direct-mutation.test.ts',
  // Actions / adapters — register UI objects and may seed store state
  'src/features/stage/adapters/QueryEditorAdapter.ts',
  'src/features/stage/adapters/WorkspaceAdapter.ts',
  'src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts',
  'src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts',
  'src/features/actions/__tests__/ui-handlers.test.ts',
  // Stage utilities
  'src/features/stage/utils/query-editor-actions.ts',
  'src/features/stage/utils/query-editor-actions.test.ts',
  'src/features/stage/utils/__tests__/open-direct-sql-query-editor-tab.test.ts',
  'src/features/stage/utils/__tests__/open-or-focus-stage-tool-tab.test.ts',
  'src/features/stage/utils/open-or-focus-file-preview-tab.test.ts',
  // Stage components
  'src/features/stage/components/stage-window.tsx',
  'src/features/stage/components/activity-rail/stage-activity-rail.test.tsx',
  'src/features/stage/components/sql-workbench-tab.test.tsx',
  'src/features/stage/components/stage-tab-content.test.tsx',
  'src/features/stage/components/stage-toggle-button.test.tsx',
  'src/features/stage/components/stage-ui-object-registry.test.tsx',
  'src/features/stage/components/stage-window.test.tsx',
  // Stage store tests
  'src/features/stage/stores/sql-workbench-store.test.ts',
  'src/features/stage/use-stage-auto-open.test.ts',
  // Session / settings / workspace tests
  'src/features/session/split-view.test.tsx',
  'src/features/settings/general/general-panel.test.tsx',
  'src/features/settings/general/general-panel.tsx',
  'src/features/chat/components/tools/__tests__/read-file.test.tsx',
  'src/features/workspace/components/__tests__/nav-tabs.test.tsx',
])

const TARGETS = ['useStageStore', 'useSqlWorkbenchStore']

const PROJECT_ROOT = path.resolve(import.meta.dirname, '..', '..')

describe('no direct stage store mutation', () => {
  it('no file outside the allowlist calls useStageStore.setState or useSqlWorkbenchStore.setState', () => {
    const project = new Project({
      tsConfigFilePath: path.join(PROJECT_ROOT, 'tsconfig.json'),
      skipAddingFilesFromTsConfig: true,
    })

    const sourceFiles = project.addSourceFilesAtPaths(
      path.join(PROJECT_ROOT, 'src/**/*.{ts,tsx}'),
    )

    const violations: Array<{ file: string; line: number; store: string }> = []

    for (const sf of sourceFiles) {
      const relativePath = path.relative(PROJECT_ROOT, sf.getFilePath())

      if (ALLOWED_FILES.has(relativePath)) continue

      for (const callExpr of sf.getDescendantsOfKind(SyntaxKind.CallExpression)) {
        const expr = callExpr.getExpression()
        if (expr.getKind() !== 31 /* CallExpression */) {
          // Check for MemberExpression: store.setState(...)
          // ts-morph PropertyAccessExpression
        }
      }

      // Simpler approach: search for text patterns
      const text = sf.getFullText()
      const lines = text.split('\n')

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        for (const store of TARGETS) {
          if (line.includes(`${store}.setState`)) {
            violations.push({
              file: relativePath,
              line: i + 1,
              store,
            })
          }
        }
      }
    }

    expect(violations, `Found direct setState calls outside allowlist:\n${violations.map((v) => `  ${v.file}:${v.line} — ${v.store}.setState`).join('\n')}`).toEqual([])
  })
})
