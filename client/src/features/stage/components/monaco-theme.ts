import type * as Monaco from 'monaco-editor'

export const LIGHT_MONACO_THEME = 'datatalk-sql-light'
export const DARK_MONACO_THEME = 'datatalk-sql-dark'

/** Colors sourced from client/DESIGN.md primitive palette.
 *  keyword + type → cobalt (accent.primary) per DESIGN.md §5 "cobalt carries focus".
 *  variable → amber (accent.warn) for parameter emphasis.
 *  string / comment / operator → neutral tokens per semantic text hierarchy.
 */
const LIGHT = {
  keyword: '1D4ED8',    // cobalt.700 — accent.primary
  type: '1D4ED8',       // cobalt.700 — accent.primary
  variable: 'D97706',   // amber.600 — accent.warn hover
  function_: '34322D',  // neutral.800 — text.strong (warm)
  string: '5E5E5B',     // neutral.600 — text.muted
  number: '34322D',     // neutral.800 — text.base
  comment: '858481',    // neutral.500 — text.soft
  identifier: '34322D', // neutral.800 — text.base
  operator: '5E5E5B',   // neutral.600 — text.muted
  delimiter: 'B9B9B7',  // neutral.400 — text.muted lighter
  // backgrounds — warm-neutral spine; Monaco cannot read CSS vars, so values
  // are hand-mirrored to globals.css :root --dt-bg-* tokens.
  bg: 'FCFCFB',         // bg.canvas — near-white with hint of warmth (oklch 0.995 0.002 80)
  gutterBg: 'F1F1EF',   // bg.subtle (chrome stripe behind line numbers)
  textFg: '34322D',     // neutral.800 — text.strong
  lineNum: 'D1D1CD',    // neutral.300
  lineNumActive: '34322D',
  lineHighlight: 'F1F1EF',  // bg.subtle
  selection: 'E5E5E2',  // neutral.200
  cursor: '34322D',
  indentGuide: 'E5E5E2',
  indentGuideActive: 'D1D1CD',
  widgetBg: 'FCFCFB',   // bg.canvas — matches editor body
  widgetBorder: 'E5E5E2',
  suggestSelected: 'F1F1EF',
  listHover: 'F1F1EF',
  listActive: 'F1F1EF',
  scrollbar: 'D1D1CD70',
  scrollbarHover: 'B9B9B780',
  menuBorder: 'E5E5E2',
  menuSeparator: 'E5E5E2',
  overviewRuler: '#00000000',
}

const DARK = {
  keyword: '60A5FA',    // cobalt.400 — accent.primary (dark)
  type: '60A5FA',       // cobalt.400 — accent.primary (dark)
  variable: 'FBBF24',   // amber.400 — accent.warn (dark)
  function_: 'FCFDFE',  // neutral.25 — text.strong
  string: '94A3B8',     // neutral.400 — text.muted
  number: 'DBEAFE',     // neutral.100 — text.base (dark)
  comment: '64748B',    // neutral.500 — text.soft (dark)
  identifier: 'FCFDFE', // neutral.25 — text.strong
  operator: '94A3B8',   // neutral.400 — text.muted
  delimiter: '64748B',  // neutral.500 — text.soft
  // backgrounds
  bg: '171717',         // original zinc dark — do not shift to blue-tinted slate
  gutterBg: '141414',   // original slightly darker zinc
  textFg: 'FCFDFE',     // neutral.25 — text.strong
  lineNum: '737373',    // original zinc line number
  lineNumActive: 'FCFDFE',
  lineHighlight: '262626',  // original
  selection: '404040',  // original
  cursor: 'FCFDFE',
  indentGuide: '404040',
  indentGuideActive: '525252',
  widgetBg: '1f1f1f',   // original dark elevated panel
  widgetBorder: '404040',
  suggestSelected: '262626',
  listHover: '262626',
  listActive: '262626',
  scrollbar: '40404070',
  scrollbarHover: '52525280',
  menuBorder: '404040',
  menuSeparator: '404040',
  overviewRuler: '#00000000',
}

export function registerMonacoThemes(monaco: typeof Monaco) {
  monaco.editor.defineTheme(LIGHT_MONACO_THEME, {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: LIGHT.keyword, fontStyle: 'bold' },
      { token: 'type', foreground: LIGHT.type, fontStyle: 'bold' },
      { token: 'variable', foreground: LIGHT.variable },
      { token: 'function', foreground: LIGHT.function_ },
      { token: 'string', foreground: LIGHT.string },
      { token: 'number', foreground: LIGHT.number },
      { token: 'comment', foreground: LIGHT.comment, fontStyle: 'italic' },
      { token: 'identifier', foreground: LIGHT.identifier },
      { token: 'operator', foreground: LIGHT.operator },
      { token: 'delimiter', foreground: LIGHT.delimiter },
    ],
    colors: {
      'editor.background': `#${LIGHT.bg}`,
      'editor.foreground': `#${LIGHT.textFg}`,
      'editorGutter.background': `#${LIGHT.gutterBg}`,
      'editorLineNumber.foreground': `#${LIGHT.lineNum}`,
      'editorLineNumber.activeForeground': `#${LIGHT.lineNumActive}`,
      'editor.lineHighlightBackground': `#${LIGHT.lineHighlight}`,
      'editor.lineHighlightBorder': LIGHT.overviewRuler,
      'editor.selectionBackground': `#${LIGHT.selection}`,
      'editor.inactiveSelectionBackground': `#${LIGHT.lineHighlight}`,
      'editorCursor.foreground': `#${LIGHT.cursor}`,
      'editorIndentGuide.background1': `#${LIGHT.indentGuide}`,
      'editorIndentGuide.activeBackground1': `#${LIGHT.indentGuideActive}`,
      'editorWidget.background': `#${LIGHT.widgetBg}`,
      'editorWidget.border': `#${LIGHT.widgetBorder}`,
      'editorOverviewRuler.border': LIGHT.overviewRuler,
      'editorSuggestWidget.background': `#${LIGHT.widgetBg}`,
      'editorSuggestWidget.border': `#${LIGHT.widgetBorder}`,
      'editorSuggestWidget.selectedBackground': `#${LIGHT.suggestSelected}`,
      'list.hoverBackground': `#${LIGHT.listHover}`,
      'list.activeSelectionBackground': `#${LIGHT.listActive}`,
      'scrollbarSlider.background': `#${LIGHT.scrollbar}`,
      'scrollbarSlider.hoverBackground': `#${LIGHT.scrollbarHover}`,
      'menu.background': `#${LIGHT.widgetBg}`,
      'menu.foreground': `#${LIGHT.textFg}`,
      'menu.selectionBackground': `#${LIGHT.listHover}`,
      'menu.selectionForeground': `#${LIGHT.textFg}`,
      'menu.separatorBackground': `#${LIGHT.menuSeparator}`,
      'menu.border': `#${LIGHT.menuBorder}`,
    },
  })
  monaco.editor.defineTheme(DARK_MONACO_THEME, {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: DARK.keyword, fontStyle: 'bold' },
      { token: 'type', foreground: DARK.type, fontStyle: 'bold' },
      { token: 'variable', foreground: DARK.variable },
      { token: 'function', foreground: DARK.function_ },
      { token: 'string', foreground: DARK.string },
      { token: 'number', foreground: DARK.number },
      { token: 'comment', foreground: DARK.comment, fontStyle: 'italic' },
      { token: 'identifier', foreground: DARK.identifier },
      { token: 'operator', foreground: DARK.operator },
      { token: 'delimiter', foreground: DARK.delimiter },
    ],
    colors: {
      'editor.background': `#${DARK.bg}`,
      'editor.foreground': `#${DARK.textFg}`,
      'editorGutter.background': `#${DARK.gutterBg}`,
      'editorLineNumber.foreground': `#${DARK.lineNum}`,
      'editorLineNumber.activeForeground': `#${DARK.lineNumActive}`,
      'editor.lineHighlightBackground': `#${DARK.lineHighlight}`,
      'editor.lineHighlightBorder': DARK.overviewRuler,
      'editor.selectionBackground': `#${DARK.selection}`,
      'editor.inactiveSelectionBackground': `#${DARK.lineHighlight}`,
      'editorCursor.foreground': `#${DARK.cursor}`,
      'editorIndentGuide.background1': `#${DARK.indentGuide}`,
      'editorIndentGuide.activeBackground1': `#${DARK.indentGuideActive}`,
      'editorWidget.background': `#${DARK.widgetBg}`,
      'editorWidget.border': `#${DARK.widgetBorder}`,
      'editorOverviewRuler.border': DARK.overviewRuler,
      'editorSuggestWidget.background': `#${DARK.widgetBg}`,
      'editorSuggestWidget.border': `#${DARK.widgetBorder}`,
      'editorSuggestWidget.selectedBackground': `#${DARK.suggestSelected}`,
      'list.hoverBackground': `#${DARK.listHover}`,
      'list.activeSelectionBackground': `#${DARK.listActive}`,
      'scrollbarSlider.background': `#${DARK.scrollbar}`,
      'scrollbarSlider.hoverBackground': `#${DARK.scrollbarHover}`,
      'menu.background': `#${DARK.widgetBg}`,
      'menu.foreground': `#${DARK.textFg}`,
      'menu.selectionBackground': `#${DARK.listHover}`,
      'menu.selectionForeground': `#${DARK.textFg}`,
      'menu.separatorBackground': `#${DARK.menuSeparator}`,
      'menu.border': `#${DARK.menuBorder}`,
    },
  })
}
