import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { SqlMonacoEditor } from './sql-monaco-editor'

type MountedEditor = {
  getModel: () => MountedModel
}
type MountedModel = {
  getValue: ReturnType<typeof vi.fn>
  getFullModelRange: ReturnType<typeof vi.fn>
  pushEditOperations: ReturnType<typeof vi.fn>
}

const editorHarness = vi.hoisted(() => {
  const model = {
    storedValue: 'SELECT * FROM users LIMIT 5;',
    getValue: vi.fn(() => model.storedValue),
    getFullModelRange: vi.fn(() => ({ startLine: 1, startColumn: 1, endLine: 1, endColumn: 1 })),
    pushEditOperations: vi.fn((_before: unknown, edits: Array<{ text: string }>) => {
      const last = edits[edits.length - 1]
      if (last) model.storedValue = last.text
      return null
    }),
  }
  const editor = {
    addCommand: vi.fn(),
    onDidChangeCursorPosition: vi.fn(() => ({ dispose: vi.fn() })),
    onDidChangeCursorSelection: vi.fn(() => ({ dispose: vi.fn() })),
    deltaDecorations: vi.fn(() => []),
    getModel: vi.fn(() => model),
    getPosition: vi.fn(() => ({ lineNumber: 1, column: 1 })),
    setPosition: vi.fn(),
    revealLineNearTop: vi.fn(),
    focus: vi.fn(),
  }
  const monaco = {
    KeyMod: { CtrlCmd: 1024, Shift: 2048 },
    KeyCode: { Enter: 13, KeyF: 33 },
    editor: { defineTheme: vi.fn() },
  }
  return { model, editor, monaco }
})

vi.mock('@monaco-editor/react', () => ({
  default: ({
    value,
    onMount,
    beforeMount,
  }: {
    value?: string
    onMount?: (editor: MountedEditor, monaco: typeof editorHarness.monaco) => void
    beforeMount?: (monaco: typeof editorHarness.monaco) => void
  }) => {
    beforeMount?.(editorHarness.monaco)
    onMount?.(editorHarness.editor as unknown as MountedEditor, editorHarness.monaco)
    return <div data-testid="monaco" data-value={value ?? ''} />
  },
}))

vi.mock('@/stores/theme-store', () => ({
  useThemeStore: (selector: (state: { theme: 'light' | 'dark' | 'system' }) => unknown) =>
    selector({ theme: 'light' }),
}))

vi.mock('./monaco-theme', () => ({
  DARK_MONACO_THEME: 'dark',
  LIGHT_MONACO_THEME: 'light',
  registerMonacoThemes: vi.fn(),
}))

describe('SqlMonacoEditor external value sync', () => {
  beforeEach(() => {
    editorHarness.model.storedValue = 'SELECT * FROM users LIMIT 5;'
    editorHarness.model.pushEditOperations.mockClear()
    editorHarness.model.getValue.mockClear()
    editorHarness.editor.getModel.mockClear()
  })

  it('pushes the new value into the editor model when the value prop changes externally', () => {
    const onChange = vi.fn()
    const onRun = vi.fn()
    const { rerender } = render(
      <SqlMonacoEditor value="SELECT * FROM users LIMIT 5;" onChange={onChange} onRun={onRun} />,
    )

    editorHarness.model.pushEditOperations.mockClear()
    rerender(
      <SqlMonacoEditor value="SELECT * FROM orders LIMIT 50;" onChange={onChange} onRun={onRun} />,
    )

    expect(editorHarness.model.pushEditOperations).toHaveBeenCalledTimes(1)
    const [, edits] = editorHarness.model.pushEditOperations.mock.calls[0] ?? []
    expect((edits as Array<{ text: string }>)[0].text).toBe('SELECT * FROM orders LIMIT 50;')
    expect(editorHarness.model.storedValue).toBe('SELECT * FROM orders LIMIT 50;')
  })

  it('does not re-push when the value prop already matches the model', () => {
    const onChange = vi.fn()
    const onRun = vi.fn()
    editorHarness.model.storedValue = 'SELECT 1;'
    render(<SqlMonacoEditor value="SELECT 1;" onChange={onChange} onRun={onRun} />)

    expect(editorHarness.model.pushEditOperations).not.toHaveBeenCalled()
  })
})
