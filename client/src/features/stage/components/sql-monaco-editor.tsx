import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'
import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useThemeStore } from '@/stores/theme-store'

const LIGHT_THEME = 'datatalk-sql-light'
const DARK_THEME = 'datatalk-sql-dark'
const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)'

export type SqlMonacoEditorHandle = {
  insertAtCursor: (text: string) => void
  revealLineNearTop: (line: number) => void
  setPosition: (line: number, column: number) => void
}

type SqlMonacoEditorProps = {
  value: string
  onChange: (value: string) => void
  onRun: () => void
  onCursorChange?: (cursor: { line: number; column: number }) => void
  currentStatementRange?: { startLine: number; endLine: number } | null
}

export const SqlMonacoEditor = forwardRef<SqlMonacoEditorHandle, SqlMonacoEditorProps>(function SqlMonacoEditor(
  { value, onChange, onRun, onCursorChange, currentStatementRange },
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
    if (themePreference === 'dark') return DARK_THEME
    if (themePreference === 'light') return LIGHT_THEME
    return systemPrefersDark ? DARK_THEME : LIGHT_THEME
  })()

  const handleBeforeMount: BeforeMount = (monaco) => {
    monaco.editor.defineTheme(LIGHT_THEME, {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: '27272a', fontStyle: 'bold' },
        { token: 'string', foreground: '52525b' },
        { token: 'number', foreground: '3f3f46' },
        { token: 'comment', foreground: 'a1a1aa', fontStyle: 'italic' },
        { token: 'identifier', foreground: '18181b' },
        { token: 'operator', foreground: '52525b' },
        { token: 'delimiter', foreground: '737373' },
      ],
      colors: {
        'editor.background': '#ffffff',
        'editor.foreground': '#171717',
        'editorGutter.background': '#fafafa',
        'editorLineNumber.foreground': '#a3a3a3',
        'editorLineNumber.activeForeground': '#171717',
        'editor.lineHighlightBackground': '#f5f5f5',
        'editor.lineHighlightBorder': '#00000000',
        'editor.selectionBackground': '#e5e5e5',
        'editor.inactiveSelectionBackground': '#f5f5f5',
        'editorCursor.foreground': '#171717',
        'editorIndentGuide.background1': '#e5e5e5',
        'editorIndentGuide.activeBackground1': '#d4d4d4',
        'editorWidget.background': '#ffffff',
        'editorWidget.border': '#e5e5e5',
        'editorSuggestWidget.background': '#ffffff',
        'editorSuggestWidget.border': '#e5e5e5',
        'editorSuggestWidget.selectedBackground': '#f5f5f5',
        'list.hoverBackground': '#f5f5f5',
        'list.activeSelectionBackground': '#f5f5f5',
        'scrollbarSlider.background': '#d4d4d470',
        'scrollbarSlider.hoverBackground': '#a3a3a380',
        'menu.background': '#ffffff',
        'menu.foreground': '#171717',
        'menu.selectionBackground': '#f5f5f5',
        'menu.selectionForeground': '#171717',
        'menu.separatorBackground': '#e5e5e5',
        'menu.border': '#e5e5e5',
      },
    })
    monaco.editor.defineTheme(DARK_THEME, {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: 'f4f4f5', fontStyle: 'bold' },
        { token: 'string', foreground: 'd4d4d8' },
        { token: 'number', foreground: 'e4e4e7' },
        { token: 'comment', foreground: '71717a', fontStyle: 'italic' },
        { token: 'identifier', foreground: 'fafafa' },
        { token: 'operator', foreground: 'a1a1aa' },
        { token: 'delimiter', foreground: 'a1a1aa' },
      ],
      colors: {
        'editor.background': '#171717',
        'editor.foreground': '#fafafa',
        'editorGutter.background': '#141414',
        'editorLineNumber.foreground': '#737373',
        'editorLineNumber.activeForeground': '#fafafa',
        'editor.lineHighlightBackground': '#262626',
        'editor.lineHighlightBorder': '#00000000',
        'editor.selectionBackground': '#404040',
        'editor.inactiveSelectionBackground': '#262626',
        'editorCursor.foreground': '#fafafa',
        'editorIndentGuide.background1': '#404040',
        'editorIndentGuide.activeBackground1': '#525252',
        'editorWidget.background': '#1f1f1f',
        'editorWidget.border': '#404040',
        'editorSuggestWidget.background': '#1f1f1f',
        'editorSuggestWidget.border': '#404040',
        'editorSuggestWidget.selectedBackground': '#262626',
        'list.hoverBackground': '#262626',
        'list.activeSelectionBackground': '#262626',
        'scrollbarSlider.background': '#40404070',
        'scrollbarSlider.hoverBackground': '#52525280',
        'menu.background': '#1f1f1f',
        'menu.foreground': '#fafafa',
        'menu.selectionBackground': '#262626',
        'menu.selectionForeground': '#fafafa',
        'menu.separatorBackground': '#404040',
        'menu.border': '#404040',
      },
    })
  }

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onRun()
    })
    editor.onDidChangeCursorPosition((event) => {
      const position = event.position
      onCursorChange?.({ line: position.lineNumber, column: position.column })
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
      className="h-full min-h-[260px] overflow-hidden rounded-b-xl border border-border/50 bg-background"
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
