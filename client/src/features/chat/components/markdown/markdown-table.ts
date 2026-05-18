import type { TranslationFn } from '@/i18n/provider'

const PIPE_ALIGN_RE = /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$/

function looksLikePipeRow(line: string): boolean {
  const trimmed = line.trim()
  if (!trimmed.includes('|')) return false
  if (trimmed.startsWith('|') || trimmed.endsWith('|')) return true
  return trimmed.split('|').length >= 3
}

export function normalizePipeTables(text: string): string {
  const lines = text.split('\n')
  const out: string[] = []
  let inFence = false

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/^\s*(```|~~~)/.test(line)) inFence = !inFence
    if (inFence) {
      out.push(line)
      continue
    }

    const blank = lines[i + 1] ?? ''
    const separator = lines[i + 2] ?? ''
    if (looksLikePipeRow(line) && !blank.trim() && PIPE_ALIGN_RE.test(separator)) {
      out.push(line, separator)
      i += 2
      const afterSeparator = lines[i + 1] ?? ''
      if (!afterSeparator.trim()) {
        i += 1
      }
      continue
    }

    out.push(line)
  }

  return out.join('\n')
}

export function decorateTables(root: HTMLElement, t: TranslationFn) {
  for (const table of Array.from(root.querySelectorAll('table'))) {
    if (table.closest('[data-component="markdown-table"]')) continue

    const shell = document.createElement('div')
    shell.setAttribute('data-component', 'markdown-table')

    const bar = document.createElement('div')
    bar.setAttribute('data-slot', 'markdown-table-bar')

    const label = document.createElement('span')
    label.setAttribute('data-slot', 'markdown-table-label')
    label.textContent = t('table.label')

    const actions = document.createElement('div')
    actions.setAttribute('data-slot', 'markdown-table-actions')

    const copy = document.createElement('button')
    copy.setAttribute('type', 'button')
    copy.setAttribute('data-slot', 'markdown-table-copy')
    copy.setAttribute('aria-label', t('table.copyAria'))
    copy.textContent = t('table.copy')

    const csv = document.createElement('button')
    csv.setAttribute('type', 'button')
    csv.setAttribute('data-slot', 'markdown-table-csv')
    csv.setAttribute('aria-label', t('table.csvAria'))
    csv.textContent = t('table.csv')

    const more = document.createElement('button')
    more.setAttribute('type', 'button')
    more.setAttribute('data-slot', 'markdown-table-more')
    more.setAttribute('aria-label', t('table.moreAria'))
    more.setAttribute('aria-expanded', 'false')
    more.textContent = t('table.more')

    const menu = document.createElement('div')
    menu.setAttribute('data-slot', 'markdown-table-menu')
    menu.hidden = true

    for (const [format, labelText] of [
      ['tsv', 'TSV'],
      ['markdown', 'Markdown'],
      ['json', 'JSON'],
      ['download-csv', t('table.downloadCsv')],
      ['sql-insert', t('table.downloadSqlInsert')],
      ['download-xlsx', t('table.downloadXlsx')],
    ]) {
      const item = document.createElement('button')
      item.setAttribute('type', 'button')
      item.setAttribute('data-slot', 'markdown-table-action')
      item.setAttribute('data-format', format)
      item.textContent = labelText
      menu.appendChild(item)
    }

    actions.append(copy, csv, more, menu)
    bar.append(label, actions)

    const scroll = document.createElement('div')
    scroll.setAttribute('data-slot', 'markdown-table-scroll')

    table.parentNode?.replaceChild(shell, table)
    shell.appendChild(bar)
    shell.appendChild(scroll)
    scroll.appendChild(table)
  }
}
