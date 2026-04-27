import { describe, it, expect } from 'vitest'
import { Project } from 'ts-morph'
import path from 'node:path'

const ALLOWED_FILES = new Set([
  'src/stores/stage-store.ts',
  'src/features/stage/stores/sql-workbench-store.ts',
  'src/stores/stage-store.test.ts',
  'src/__tests__/forbidden-direct-mutation.test.ts',
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

      for (const callExpr of sf.getDescendantsOfKind(31 /* SyntaxKind.CallExpression */)) {
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
