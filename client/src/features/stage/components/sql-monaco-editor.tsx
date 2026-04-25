import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'
import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useThemeStore } from '@/stores/theme-store'
import { cn } from '@/lib/utils'
import { DARK_MONACO_THEME, LIGHT_MONACO_THEME, registerMonacoThemes } from './monaco-theme'
const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)'

type SqlEditorSelection = {
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
}

export type SqlMonacoEditorHandle = {
  insertAtCursor: (text: string) => void
  revealLineNearTop: (line: number) => void
  setPosition: (line: number, column: number) => void
}

type SqlMonacoEditorProps = {
  value: string
  onChange: (value: string) => void
  onRun: () => void
  onFormat?: () => void
  onCursorChange?: (cursor: { line: number; column: number }) => void
  onSelectionChange?: (selection: SqlEditorSelection | null) => void
  currentStatementRange?: { startLine: number; endLine: number } | null
  shellMode?: 'standalone' | 'connected'
}

export const SqlMonacoEditor = forwardRef<SqlMonacoEditorHandle, SqlMonacoEditorProps>(function SqlMonacoEditor(
  { value, onChange, onRun, onFormat, onCursorChange, onSelectionChange, currentStatementRange, shellMode = 'standalone' },
  ref,
) {
  const themePreference = useThemeStore((state) => state.theme)
  const [systemPrefersDark, setSystemPrefersDark] = useState(resolveSystemTheme)
  const editorRef = useRef<Monaco.editor.IStandaloneCodeEditor | null>(null)
  const decorationIdsRef = useRef<string[]>([])
  const latestValueRef = useRef(value)

  useEffect(() => {
    latestValueRef.current = value
  }, [value])

  useEffect(() => {
    if (themePreference !== 'system') return
    const mediaQuery = window.matchMedia(SYSTEM_MEDIA_QUERY)
    const handleChange = (event: MediaQueryListEvent) => {
      setSystemPrefersDark(event.matches)
    }
    setSystemPrefersDark(mediaQuery.matches)
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [themePreference])

  const editorTheme = (() => {
    if (themePreference === 'dark') return DARK_MONACO_THEME
    if (themePreference === 'light') return LIGHT_MONACO_THEME
    return systemPrefersDark ? DARK_MONACO_THEME : LIGHT_MONACO_THEME
  })()

  const handleBeforeMount: BeforeMount = (monaco) => {
    registerMonacoThemes(monaco)
  }

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onRun()
    })
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KeyF, () => {
      onFormat?.()
    })
    editor.onDidChangeCursorPosition((event) => {
      const position = event.position
      onCursorChange?.({ line: position.lineNumber, column: position.column })
    })
    editor.onDidChangeCursorSelection((event) => {
      const selection = event.selection
      if (!selection || selection.isEmpty()) {
        onSelectionChange?.(null)
        return
      }
      onSelectionChange?.({
        startLine: selection.startLineNumber,
        startColumn: selection.startColumn,
        endLine: selection.endLineNumber,
        endColumn: selection.endColumn,
      })
    })
  }

  useImperativeHandle(ref, () => ({
    insertAtCursor(text: string) {
      const editor = editorRef.current
      const position = editor?.getPosition()
      if (!editor || !position) return

      const nextValue = insertTextAtPosition(latestValueRef.current, text, position.lineNumber, position.column)
      onChange(nextValue)

      const insertedPosition = resolveInsertedPosition(position.lineNumber, position.column, text)
      editor.setPosition(insertedPosition)
      onCursorChange?.({ line: insertedPosition.lineNumber, column: insertedPosition.column })
    },
    revealLineNearTop(line: number) {
      editorRef.current?.revealLineNearTop(line)
    },
    setPosition(line: number, column: number) {
      const position = { lineNumber: line, column }
      editorRef.current?.setPosition(position)
      onCursorChange?.({ line, column })
    },
  }))

  useEffect(() => {
    const editor = editorRef.current
    if (!editor) return
    if (!currentStatementRange) {
      decorationIdsRef.current = editor.deltaDecorations(decorationIdsRef.current, [])
      return
    }

    decorationIdsRef.current = editor.deltaDecorations(decorationIdsRef.current, [
      {
        range: {
          startLineNumber: currentStatementRange.startLine,
          startColumn: 1,
          endLineNumber: currentStatementRange.endLine,
          endColumn: 1,
        },
        options: {
          isWholeLine: true,
          className: 'sql-current-statement-line',
        },
      },
    ])
  }, [currentStatementRange])

  return (
    <div
      data-testid="sql-monaco-editor"
      className={cn(
        'h-full min-h-[260px] overflow-hidden bg-background',
        shellMode === 'connected'
          ? 'rounded-b-none border-x border-t border-border/50'
          : 'rounded-b-xl border border-border/50',
      )}
    >
      <Editor
        height="100%"
        defaultLanguage="sql"
        beforeMount={handleBeforeMount}
        theme={editorTheme}
        value={value}
        onChange={(next) => onChange(next ?? '')}
        onMount={handleMount}
        options={monacoOptions}
      />
    </div>
  )
})

const monacoOptions: Monaco.editor.IStandaloneEditorConstructionOptions = {
  minimap: { enabled: false },
  overviewRulerBorder: false,
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true,
  fontSize: 13,
  lineNumbersMinChars: 3,
  scrollBeyondLastLine: false,
  automaticLayout: true,
  tabSize: 2,
  wordWrap: 'on',
  renderLineHighlight: 'line',
  padding: { top: 12, bottom: 12 },
  smoothScrolling: true,
  cursorBlinking: 'smooth',
  bracketPairColorization: { enabled: true },
  autoClosingBrackets: 'always',
  autoIndent: 'full',
}

function resolveSystemTheme() {
  if (typeof window === 'undefined') return false
  return window.matchMedia(SYSTEM_MEDIA_QUERY).matches
}

function insertTextAtPosition(value: string, insertedText: string, lineNumber: number, column: number) {
  const lines = value.split(/\r\n|\r|\n/)
  while (lines.length < lineNumber) {
    lines.push('')
  }

  const lineIndex = Math.max(0, lineNumber - 1)
  const line = lines[lineIndex] ?? ''
  const before = line.slice(0, Math.max(0, column - 1))
  const after = line.slice(Math.max(0, column - 1))
  const insertedLines = insertedText.split(/\r\n|\r|\n/)

  if (insertedLines.length === 1) {
    lines[lineIndex] = `${before}${insertedText}${after}`
    return lines.join('\n')
  }

  const firstLine = `${before}${insertedLines[0]}`
  const lastLine = `${insertedLines[insertedLines.length - 1]}${after}`
  const middleLines = insertedLines.slice(1, -1)
  lines.splice(lineIndex, 1, firstLine, ...middleLines, lastLine)
  return lines.join('\n')
}

function resolveInsertedPosition(lineNumber: number, column: number, insertedText: string) {
  const insertedLines = insertedText.split(/\r\n|\r|\n/)
  if (insertedLines.length === 1) {
    return {
      lineNumber,
      column: column + insertedText.length,
    }
  }

  return {
    lineNumber: lineNumber + insertedLines.length - 1,
    column: insertedLines[insertedLines.length - 1].length + 1,
  }
}
