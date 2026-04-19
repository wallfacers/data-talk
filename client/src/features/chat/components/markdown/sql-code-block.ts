import { classifySqlRisk, stripComments } from '../helpers/risk'

export const SQL_EXECUTE_EVENT = 'datatalk.sql.execute'
export const SQL_EXPLAIN_EVENT = 'datatalk.sql.explain'

export function statementType(sql: string): string | null {
  const clean = stripComments(sql).replace(/^\s+/, '')
  const m = clean.match(/^([A-Z]+)/i)
  return m?.[1]?.toUpperCase() ?? null
}

export function decorateSqlBlocks(
  root: HTMLElement,
  opts: { onExecute: (sql: string) => void; onExplain: (sql: string) => void },
) {
  const codes = Array.from(root.querySelectorAll('pre > code.language-sql')) as HTMLElement[]
  for (const code of codes) {
    const pre = code.parentElement
    if (!pre) continue
    const wrapper = pre.parentElement
    if (!wrapper || wrapper.getAttribute('data-component') !== 'markdown-code') continue
    if (wrapper.querySelector('[data-slot="sql-header"]')) continue

    const sql = code.textContent ?? ''
    const kind = statementType(sql)
    const risk = classifySqlRisk(sql)

    const header = document.createElement('div')
    header.setAttribute('data-slot', 'sql-header')
    header.className = 'flex items-center gap-2 border-b bg-muted/40 px-2 py-1 text-xs'
    header.innerHTML = `
      <span class="rounded border px-1.5 font-mono text-[10px]">SQL${kind ? ' · ' + kind : ''}</span>
      ${risk === 'L1' ? '<button data-slot="sql-execute" type="button" class="rounded border px-2 hover:bg-background">执行</button>' : ''}
      <button data-slot="sql-explain" type="button" class="rounded border px-2 hover:bg-background">解释</button>
    `
    wrapper.insertBefore(header, pre)

    const execBtn = wrapper.querySelector('[data-slot="sql-execute"]') as HTMLButtonElement | null
    execBtn?.addEventListener('click', (e) => {
      e.stopPropagation()
      opts.onExecute(sql)
    })
    const explainBtn = wrapper.querySelector('[data-slot="sql-explain"]') as HTMLButtonElement | null
    explainBtn?.addEventListener('click', (e) => {
      e.stopPropagation()
      opts.onExplain(sql)
    })
  }
}
