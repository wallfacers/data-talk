import { describe, expect, it } from 'vitest'
import ts from 'typescript'
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

const SRC_ROOT = join(process.cwd(), 'src')
const DOM_PROP_COMPONENTS = new Set(['TableCell'])
const COMPONENT_TITLE_PROPS = new Set(['ChartError', 'ChartErrorBoundary', 'RailPanelShell', 'Section'])

function walk(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === '__tests__' || entry.name === 'node_modules') return []
      return walk(path)
    }
    if (!entry.name.endsWith('.tsx') || entry.name.endsWith('.test.tsx')) return []
    return [path]
  })
}

function tagName(node: ts.JsxOpeningElement | ts.JsxSelfClosingElement): string {
  const name = node.tagName
  return ts.isIdentifier(name) ? name.text : name.getText()
}

function isDomTag(name: string): boolean {
  return /^[a-z]/.test(name)
}

function location(sourceFile: ts.SourceFile, node: ts.Node): string {
  const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile))
  return `${relative(process.cwd(), sourceFile.fileName)}:${pos.line + 1}`
}

describe('i18n and tooltip regressions', () => {
  it('does not use native title attributes for hover hints', () => {
    const violations: string[] = []

    for (const file of walk(SRC_ROOT)) {
      const source = readFileSync(file, 'utf8')
      const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)

      function visit(node: ts.Node): void {
        if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
          const name = tagName(node)
          const titleAttr = node.attributes.properties.find(
            (property): property is ts.JsxAttribute =>
              ts.isJsxAttribute(property) && property.name.getText(sourceFile) === 'title',
          )

          if (titleAttr && (isDomTag(name) || DOM_PROP_COMPONENTS.has(name)) && !COMPONENT_TITLE_PROPS.has(name)) {
            violations.push(`${location(sourceFile, titleAttr)} ${name}`)
          }
        }
        ts.forEachChild(node, visit)
      }

      visit(sourceFile)
    }

    expect(violations).toEqual([])
  })

  it('keeps audited UI labels in the i18n message table', () => {
    const auditedStrings = [
      'aria-label="Copy"',
      "setAttribute('aria-label', 'Copy')",
      '<span className="sr-only">Close</span>',
      '<SheetTitle>Sidebar</SheetTitle>',
      'Displays the mobile sidebar.',
      'Toggle Sidebar',
      'Resize SQL result panel',
      'AI 上下文已压缩，早期消息可能不再可用',
      '请输入要切换的数据源名称',
      '当前数据源下未找到',
      '`${kind} artifact`',
    ]

    const violations = walk(SRC_ROOT)
      .flatMap((file) => {
        const source = readFileSync(file, 'utf8')
        return auditedStrings
          .filter((value) => source.includes(value))
          .map((value) => `${relative(process.cwd(), file)}: ${value}`)
      })

    expect(violations).toEqual([])
  })
})
