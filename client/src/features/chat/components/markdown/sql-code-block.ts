import { classifySqlRisk, stripComments } from '../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { highlightSql } from './sql-highlight'

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

    // Async syntax highlighting — apply when Shiki bundle loads
    applySqlHighlight(code)
  }
}

function applySqlHighlight(code: HTMLElement) {
  const raw = code.textContent ?? ''
  if (!raw.trim()) return

  // Theme is driven by CSS — Shiki emits dual-theme CSS variables, and
  // markdown.css flips them based on the ancestor `.dark` class.
  highlightSql(raw).then((html) => {
    // Shiki returns a full <pre class="shiki ...">...</pre> wrapper.
    // Replace the existing <pre><code>...</code></pre> with Shiki's output
    // while keeping the markdown-code wrapper structure intact.
    const shikiPre = document.createRange().createContextualFragment(html).firstElementChild
    if (!shikiPre || shikiPre.tagName !== 'PRE') return

    const outerPre = code.parentElement
    if (!outerPre) return

    // Transfer Shiki classes and styles onto the existing <pre> to preserve
    // the parent wrapper expectations (data-component, etc.)
    outerPre.className = shikiPre.className
    outerPre.removeAttribute('style') // remove any inline style from marked
    outerPre.style.cssText = shikiPre.getAttribute('style') ?? ''

    // Replace <code> children with Shiki's token spans
    code.innerHTML = shikiPre.innerHTML
    code.style.background = 'transparent'
    code.style.padding = '0'
  }).catch(() => {
    // Shiki failed to load — leave plain text as fallback
  })
}
