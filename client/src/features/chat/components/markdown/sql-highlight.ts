import { createHighlighter, type HighlighterGeneric, type BundledLanguage, type BundledTheme } from 'shiki'

type SqlHighlighter = HighlighterGeneric<BundledLanguage, BundledTheme>

let highlighterPromise: Promise<SqlHighlighter> | null = null

export async function getSqlHighlighter() {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ['github-light', 'github-dark'],
      langs: ['sql'],
    })
  }
  return highlighterPromise
}

export function highlightSql(code: string, dark: boolean): Promise<string> {
  return getSqlHighlighter().then((hl) =>
    hl.codeToHtml(code, {
      lang: 'sql',
      theme: dark ? 'github-dark' : 'github-light',
    }),
  )
}
