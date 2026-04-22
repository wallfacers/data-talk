import { useEffect, useState, type CSSProperties } from 'react'
import Editor, { type BeforeMount } from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import type { FilePreviewPayload } from '@/features/chat/components/tools/renderers/read-file-output'
import { Badge } from '@/components/ui/badge'
import { useThemeStore } from '@/stores/theme-store'
import { DARK_MONACO_THEME, LIGHT_MONACO_THEME, registerMonacoThemes } from './monaco-theme'
import type { StageTab } from '@/stores/stage-store'

const SYSTEM_MEDIA_QUERY = '(prefers-color-scheme: dark)'

type FilePreviewTabProps = {
  tab: StageTab
}

export function FilePreviewTab({ tab }: FilePreviewTabProps) {
  const payload = parseFilePreviewPayload(tab.payload)
  if (!payload) return null
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
    if (themePreference === 'dark') return DARK_MONACO_THEME
    if (themePreference === 'light') return LIGHT_MONACO_THEME
    return systemPrefersDark ? DARK_MONACO_THEME : LIGHT_MONACO_THEME
  })()

  const handleBeforeMount: BeforeMount = (monaco) => {
    registerMonacoThemes(monaco)
  }

  return (
    <div
      data-testid="file-preview-tab"
      className="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-border/50 bg-background"
    >
      <div className="flex flex-wrap items-center gap-2 border-b border-border/50 px-4 py-3">
        <Badge variant="outline">Language</Badge>
        <span className="text-sm text-foreground">{payload.language}</span>
        <Badge variant="outline">Type</Badge>
        <span className="text-sm text-foreground">{payload.fileType}</span>
        {payload.truncated ? <Badge variant="destructive">truncated</Badge> : null}
      </div>
      <div className="border-b border-border/50 px-4 py-2 text-sm text-muted-foreground">
        {payload.filePath ?? payload.filename}
      </div>
      <div className="min-h-0 flex-1" style={editorShellStyle}>
        <Editor
          height="100%"
          language={payload.language}
          beforeMount={handleBeforeMount}
          theme={editorTheme}
          value={payload.content}
          options={readOnlyEditorOptions}
        />
      </div>
    </div>
  )
}

const editorShellStyle: CSSProperties = { minHeight: 0 }

const readOnlyEditorOptions: Monaco.editor.IStandaloneEditorConstructionOptions = {
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
  readOnly: true,
}

function resolveSystemTheme() {
  if (typeof window === 'undefined') return false
  return window.matchMedia(SYSTEM_MEDIA_QUERY).matches
}

function parseFilePreviewPayload(payload: unknown): FilePreviewPayload | null {
  if (typeof payload !== 'object' || payload === null) return null

  const candidate = payload as Partial<FilePreviewPayload>
  if (
    typeof candidate.sourceKey !== 'string' ||
    (candidate.filePath !== null && typeof candidate.filePath !== 'string') ||
    typeof candidate.filename !== 'string' ||
    typeof candidate.fileType !== 'string' ||
    typeof candidate.content !== 'string' ||
    typeof candidate.truncated !== 'boolean' ||
    typeof candidate.language !== 'string'
  ) {
    return null
  }

  return candidate as FilePreviewPayload
}
