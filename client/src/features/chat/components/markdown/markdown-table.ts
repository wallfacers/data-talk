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

export function decorateTables(root: HTMLElement) {
  for (const table of Array.from(root.querySelectorAll('table'))) {
    if (table.closest('[data-component="markdown-table"]')) continue

    const shell = document.createElement('div')
    shell.setAttribute('data-component', 'markdown-table')

    const scroll = document.createElement('div')
    scroll.setAttribute('data-slot', 'markdown-table-scroll')

    table.parentNode?.replaceChild(shell, table)
    shell.appendChild(scroll)
    scroll.appendChild(table)
  }
}
