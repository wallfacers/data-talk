import { useEffect, useState } from 'react'
import Editor, { type BeforeMount, type OnMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { useThemeStore } from '@/stores/theme-store'

const LIGHT_THEME = 'datatalk-sql-light'
const DARK_THEME = 'datatalk-sql-dark'
const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)'

type SqlMonacoEditorProps = {
  value: string
  onChange: (value: string) => void
  onRun: () => void
}

export function SqlMonacoEditor({ value, onChange, onRun }: SqlMonacoEditorProps) {
  const themePreference = useThemeStore((state) => state.theme)
  const [systemPrefersDark, setSystemPrefersDark] = useState(resolveSystemTheme)

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
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onRun()
    })
  }

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
}

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
}

function resolveSystemTheme() {
  if (typeof window === 'undefined') return false
  return window.matchMedia(SYSTEM_MEDIA_QUERY).matches
}
