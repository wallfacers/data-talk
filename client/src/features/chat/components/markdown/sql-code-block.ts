import { classifySqlRisk, stripComments } from '../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

export const SQL_EXECUTE_EVENT = 'datatalk.sql.execute'
export const SQL_EXPLAIN_EVENT = 'datatalk.sql.explain'

export function statementType(sql: string): string | null {
  const clean = stripComments(sql).replace(/^\s+/, '')
  const m = clean.match(/^([A-Z]+)/i)
  return m?.[1]?.toUpperCase() ?? null
}

export function decorateSqlBlocks(root: HTMLElement) {
  const codes = Array.from(root.querySelectorAll('pre > code.language-sql')) as HTMLElement[]
  for (const code of codes) {
    const pre = code.parentElement
    if (!pre) continue
    const wrapper = pre.parentElement
    if (!wrapper || wrapper.getAttribute('data-component') !== 'markdown-code') continue
    if (wrapper.getAttribute('data-streaming-code') === 'true') continue
    if (wrapper.querySelector('[data-slot="sql-execute"], [data-slot="sql-explain"]')) continue

    const sql = code.textContent ?? ''
    const kind = statementType(sql)
    const risk = classifySqlRisk(sql)
    const bar = wrapper.querySelector('[data-slot="markdown-code-bar"]')
    const actions = wrapper.querySelector('[data-slot="markdown-code-actions"]')
    if (!bar || !actions) continue

    if (kind) {
      const badge = document.createElement('span')
      badge.setAttribute('data-slot', 'sql-kind')
      badge.textContent = kind
      bar.insertBefore(badge, actions)
    }

    const executeLabel = translateMessage(getCurrentLanguage(), 'chat.executeSql')
    const explainLabel = translateMessage(getCurrentLanguage(), 'chat.explainSql')
    const createButton = (slot: string, label: string) => {
      const button = document.createElement('button')
      button.setAttribute('type', 'button')
      button.setAttribute('data-slot', slot)
      button.textContent = label
      return button
    }
    const copyButton = actions.querySelector('[data-slot="markdown-copy-button"]')
    const insertAction = (button: HTMLButtonElement) => {
      if (copyButton) {
        actions.insertBefore(button, copyButton)
      } else {
        actions.appendChild(button)
      }
    }

    if (risk === 'L1') {
      insertAction(createButton('sql-execute', executeLabel))
    }
    insertAction(createButton('sql-explain', explainLabel))
  }
}
