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

// Emits dual-theme HTML using CSS variables so theme switches are driven by
// the ancestor `.dark` class via CSS, not a JS re-render. With
// `defaultColor: false` Shiki sets only `--shiki-light` / `--shiki-dark`
// (and `--shiki-light-bg` / `--shiki-dark-bg`) instead of a fixed `color`.
export function highlightSql(code: string): Promise<string> {
  return getSqlHighlighter().then((hl) =>
    hl.codeToHtml(code, {
      lang: 'sql',
      themes: {
        light: 'github-light',
        dark: 'github-dark',
      },
      defaultColor: false,
    }),
  )
}
