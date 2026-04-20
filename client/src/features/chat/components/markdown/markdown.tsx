import { useEffect, useRef } from 'react'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import morphdom from 'morphdom'
import { stream } from './markdown-stream'
import { decorateTables, normalizePipeTables } from './markdown-table'
import { decorateSqlBlocks, SQL_EXECUTE_EVENT, SQL_EXPLAIN_EVENT } from './sql-code-block'
import { copyToClipboard } from '@/lib/utils'
import './markdown.css'

type Entry = { hash: string; html: string }
const MAX_CACHE = 200
const cache = new Map<string, Entry>()

const PURIFY_CONFIG = {
  USE_PROFILES: { html: true, mathMl: true },
  SANITIZE_NAMED_PROPS: true,
  FORBID_TAGS: ['style'],
  FORBID_CONTENTS: ['style', 'script'],
}

function hash(text: string): string {
  let h = 0
  for (let i = 0; i < text.length; i++) h = ((h << 5) - h + text.charCodeAt(i)) | 0
  return h.toString(36)
}

function escape(text: string): string {
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function fallback(text: string): string {
  return escape(text).replace(/\r\n?/g, '\n').replace(/\n/g, '<br>')
}

function sanitize(html: string): string {
  if (!DOMPurify.isSupported) return ''
  return DOMPurify.sanitize(html, PURIFY_CONFIG)
}

function touch(key: string, value: Entry) {
  cache.delete(key)
  cache.set(key, value)
  if (cache.size > MAX_CACHE) {
    const first = cache.keys().next().value
    if (first) cache.delete(first)
  }
}

const COPY_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-copy"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>`
const CHECK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-check"><path d="M20 6 9 17l-5-5"/></svg>`

const LANGUAGE_LABELS: Record<string, string> = {
  bash: 'Bash',
  csharp: 'C#',
  css: 'CSS',
  go: 'Go',
  html: 'HTML',
  javascript: 'JavaScript',
  jsx: 'JSX',
  json: 'JSON',
  markdown: 'Markdown',
  powershell: 'PowerShell',
  python: 'Python',
  rust: 'Rust',
  sh: 'Shell',
  shell: 'Shell',
  sql: 'SQL',
  ts: 'TypeScript',
  tsx: 'TSX',
  typescript: 'TypeScript',
  yaml: 'YAML',
}

function getLanguageLabel(code: Element | null) {
  const className = (code as HTMLElement | null)?.className ?? ''
  const match = className.match(/\blanguage-([^\s]+)/i) ?? className.match(/\blang-([^\s]+)/i)
  const language = match?.[1]?.toLowerCase()
  if (!language) return ''
  return LANGUAGE_LABELS[language] ?? language.replace(/^[a-z]/, (c) => c.toUpperCase())
}

function decorateCodeBlocks(root: HTMLElement) {
  const pres = Array.from(root.querySelectorAll('pre'))
  for (const pre of pres) {
    if (pre.parentElement?.getAttribute('data-component') === 'markdown-code') continue
    const code = pre.querySelector('code')
    const wrapper = document.createElement('div')
    wrapper.setAttribute('data-component', 'markdown-code')
    const bar = document.createElement('div')
    bar.setAttribute('data-slot', 'markdown-code-bar')
    const language = document.createElement('span')
    language.setAttribute('data-slot', 'markdown-code-language')
    language.textContent = getLanguageLabel(code)
    const actions = document.createElement('div')
    actions.setAttribute('data-slot', 'markdown-code-actions')
    const btn = document.createElement('button')
    btn.setAttribute('data-slot', 'markdown-copy-button')
    btn.setAttribute('type', 'button')
    btn.setAttribute('aria-label', 'Copy')
    btn.innerHTML = COPY_SVG
    actions.append(btn)
    bar.append(language, actions)
    pre.parentNode?.replaceChild(wrapper, pre)
    wrapper.append(bar)
    wrapper.appendChild(pre)
  }
}

function renderHtml(text: string, cacheKey: string | undefined, streaming: boolean): string {
  if (!text) return ''
  try {
    const blocks = stream(text, streaming)
    const htmls = blocks.map((block, i) => {
      const blockHash = hash(block.raw)
      const key = cacheKey ? `${cacheKey}:${i}:${block.mode}` : undefined
      if (key) {
        const cached = cache.get(key)
        if (cached && cached.hash === blockHash) {
          touch(key, cached)
          return cached.html
        }
      }
      const normalized = normalizePipeTables(block.src)
      const parsed = marked.parse(normalized, { async: false }) as string
      const safe = sanitize(parsed)
      if (key) touch(key, { hash: blockHash, html: safe })
      return safe
    })
    return htmls.join('')
  } catch {
    return fallback(text)
  }
}

export function Markdown(props: {
  text: string
  cacheKey?: string
  streaming?: boolean
  className?: string
}) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = ref.current
    if (!container) return
    const html = renderHtml(props.text, props.cacheKey, props.streaming ?? false)
    if (!html) {
      container.innerHTML = ''
      return
    }
    const temp = document.createElement('div')
    temp.innerHTML = html
    decorateCodeBlocks(temp)
    decorateSqlBlocks(temp)
    decorateTables(temp)
    morphdom(container, temp, { childrenOnly: true })
  }, [props.text, props.cacheKey, props.streaming])

  useEffect(() => {
    const container = ref.current
    if (!container) return
    const onClick = async (e: MouseEvent) => {
      const btn = (e.target as Element)?.closest?.(
        '[data-slot="markdown-copy-button"], [data-slot="sql-execute"], [data-slot="sql-explain"]',
      ) as HTMLElement | null
      if (!btn) return
      const code = btn.closest('[data-component="markdown-code"]')?.querySelector('code')
      const content = code?.textContent ?? ''
      if (btn.matches('[data-slot="markdown-copy-button"]')) {
        if (!content) return
        const success = await copyToClipboard(content)
        if (success) {
          btn.setAttribute('data-copied', 'true')
          btn.innerHTML = CHECK_SVG
          setTimeout(() => {
            btn.removeAttribute('data-copied')
            btn.innerHTML = COPY_SVG
          }, 2000)
        }
        return
      }
      if (!content) return
      if (btn.matches('[data-slot="sql-execute"]')) {
        window.dispatchEvent(new CustomEvent(SQL_EXECUTE_EVENT, { detail: { sql: content } }))
        return
      }
      if (btn.matches('[data-slot="sql-explain"]')) {
        window.dispatchEvent(new CustomEvent(SQL_EXPLAIN_EVENT, { detail: { sql: content } }))
      }
    }
    container.addEventListener('click', onClick)
    return () => container.removeEventListener('click', onClick)
  }, [])

  return <div ref={ref} data-component="markdown" className={props.className} />
}
