import { useEffect, useRef, useState } from 'react'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import morphdom from 'morphdom'
import { stream } from './markdown-stream'
import { decorateSqlBlocks } from './sql-code-block'
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

function decorateCodeBlocks(root: HTMLElement) {
  const pres = Array.from(root.querySelectorAll('pre'))
  for (const pre of pres) {
    if (pre.parentElement?.getAttribute('data-component') === 'markdown-code') continue
    const wrapper = document.createElement('div')
    wrapper.setAttribute('data-component', 'markdown-code')
    pre.parentNode?.replaceChild(wrapper, pre)
    wrapper.appendChild(pre)
    const btn = document.createElement('button')
    btn.setAttribute('data-slot', 'markdown-copy-button')
    btn.setAttribute('type', 'button')
    btn.setAttribute('aria-label', 'Copy')
    btn.textContent = 'Copy'
    wrapper.appendChild(btn)
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
      const parsed = marked.parse(block.src, { async: false }) as string
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
  const [, setTick] = useState(0)

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
    decorateSqlBlocks(temp, {
      onExecute: (sql) => window.dispatchEvent(new CustomEvent('datatalk.sql.execute', { detail: { sql } })),
      onExplain: (sql) => window.dispatchEvent(new CustomEvent('datatalk.sql.explain', { detail: { sql } })),
    })
    try {
      morphdom(container, temp, { childrenOnly: true })
    } catch (err) {
      // 第 6 节：raise 到 ErrorBoundary，这里允许向上抛
      throw err
    }
    setTick((t) => t + 1)

    const onClick = async (e: MouseEvent) => {
      const btn = (e.target as Element)?.closest?.('[data-slot="markdown-copy-button"]')
      if (!btn) return
      const code = btn.closest('[data-component="markdown-code"]')?.querySelector('code')
      const content = code?.textContent ?? ''
      if (!content) return
      await navigator.clipboard?.writeText?.(content)
      btn.setAttribute('data-copied', 'true')
      setTimeout(() => btn.removeAttribute('data-copied'), 2000)
    }
    container.addEventListener('click', onClick)
    return () => container.removeEventListener('click', onClick)
  }, [props.text, props.cacheKey, props.streaming])

  return <div ref={ref} data-component="markdown" className={props.className} />
}
