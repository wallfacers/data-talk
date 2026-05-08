import { useEffect, useRef } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import morphdom from 'morphdom'
import { stream, type Block } from './markdown-stream'
import { decorateTables, normalizePipeTables } from './markdown-table'
import { decorateSqlBlocks, SQL_EXECUTE_EVENT, SQL_EXPLAIN_EVENT } from './sql-code-block'
import { extractTableModel } from './table-model'
import { getDownloadFilename, toCsv, toDownloadableCsv, toJson, toMarkdownTable, toTsv } from './table-serializers'
import { ChartBlock } from './chart-block'
import { DashboardBlock } from './dashboard-block'
import { copyToClipboard } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import './markdown.css'

type Entry = { hash: string; html: string }
type ChartRootEntry = { root: Root; host: HTMLElement }
const MAX_CACHE = 200
const cache = new Map<string, Entry>()
const copiedResetTimers = new WeakMap<HTMLElement, number>()

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

function encodeUtf8Base64(text: string): string {
  try {
    return btoa(unescape(encodeURIComponent(text)))
  } catch {
    return btoa(text)
  }
}

function decodeUtf8Base64(text: string): string {
  try {
    return decodeURIComponent(escape(atob(text)))
  } catch {
    return atob(text)
  }
}

function scheduleRootUnmount(root: Root) {
  queueMicrotask(() => {
    try {
      root.unmount()
    } catch {
      // Root may already be unmounted.
    }
  })
}

async function copyTableHtmlAndText(html: string, text: string): Promise<boolean> {
  if (
    navigator.clipboard &&
    'write' in navigator.clipboard &&
    typeof ClipboardItem !== 'undefined'
  ) {
    try {
      await navigator.clipboard.write([
        new ClipboardItem({
          'text/html': new Blob([html], { type: 'text/html' }),
          'text/plain': new Blob([text], { type: 'text/plain' }),
        }),
      ])
      return true
    } catch {
      // Fallback to plain-text copy below.
    }
  }

  return copyToClipboard(text)
}

function showCopiedState(btn: HTMLElement, onReset?: () => void) {
  const existingTimer = copiedResetTimers.get(btn)
  if (existingTimer) window.clearTimeout(existingTimer)

  if (!btn.hasAttribute('data-copied-original-html')) {
    btn.setAttribute('data-copied-original-html', btn.innerHTML)
  }
  if (!btn.hasAttribute('data-copied-original-width')) {
    btn.setAttribute('data-copied-original-width', btn.style.width)
  }

  btn.style.width = `${btn.getBoundingClientRect().width}px`
  btn.innerHTML = CHECK_SVG
  btn.setAttribute('data-copied', 'true')
  const timer = window.setTimeout(() => {
    btn.removeAttribute('data-copied')
    btn.innerHTML = btn.getAttribute('data-copied-original-html') ?? btn.innerHTML
    btn.style.width = btn.getAttribute('data-copied-original-width') ?? ''
    copiedResetTimers.delete(btn)
    onReset?.()
  }, 2000)
  copiedResetTimers.set(btn, timer)
}

function closeTableMenus(container: HTMLElement, except?: HTMLElement | null) {
  for (const menu of Array.from(container.querySelectorAll('[data-slot="markdown-table-menu"]'))) {
    const owner = menu.parentElement?.querySelector('[data-slot="markdown-table-more"]')
    const isCurrent = except && menu === except
    ;(menu as HTMLElement).hidden = !isCurrent
    if (owner instanceof HTMLElement) {
      owner.setAttribute('aria-expanded', isCurrent ? 'true' : 'false')
    }
  }
}

function downloadTableCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
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

function getStreamingLanguageLabel(language: string): string {
  return LANGUAGE_LABELS[language] ?? language.replace(/^[a-z]/, (c) => c.toUpperCase())
}

// Inline style so the rendered code is locked to the same layout as a
// closed/marked-parsed code block regardless of cascade state. morphdom
// churns `class` and `data-*` attributes on the <code> during the
// stream-code → live transition, and without an inline override the
// element can briefly inherit the markdown container's `word-wrap:
// break-word` and render a commented line like `const a = 1; // 我是张三`
// as two visual rows, then snap back to one row on close.
const STREAMING_CODE_BODY_STYLE =
  'display:block;white-space:pre;word-break:normal;overflow-wrap:normal;min-height:1.5em'

function renderStreamingCodeBlock(block: Block, copyLabel: string): string {
  const language = block.language ?? ''
  const languageClass = language ? `language-${escape(language)}` : ''
  const label = language ? getStreamingLanguageLabel(language) : ''
  const rawCode = block.code ?? ''
  // Marked always emits a trailing `\n` inside `<code>`. Matching that here
  // means the morphdom text-diff across stream-code → live is a zero-op
  // and the visible line count cannot change when the closing fence
  // lands.
  const codeWithTrailingNewline = rawCode.endsWith('\n') ? rawCode : `${rawCode}\n`
  return [
    '<div data-component="markdown-code" data-streaming-code="true">',
    '<div data-slot="markdown-code-bar">',
    `<span data-slot="markdown-code-language">${escape(label)}</span>`,
    '<div data-slot="markdown-code-actions">',
    `<button data-slot="markdown-copy-button" type="button" aria-label="${escape(copyLabel)}">${COPY_SVG}</button>`,
    '</div>',
    '</div>',
    '<pre>',
    `<code class="${languageClass}" data-streaming-code-body="true" style="${STREAMING_CODE_BODY_STYLE}">${escape(codeWithTrailingNewline)}</code>`,
    '</pre>',
    '</div>',
  ].join('')
}

function decorateCodeBlocks(root: HTMLElement, copyLabel: string) {
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
    btn.setAttribute('aria-label', copyLabel)
    btn.innerHTML = COPY_SVG
    actions.append(btn)
    bar.append(language, actions)
    pre.parentNode?.replaceChild(wrapper, pre)
    wrapper.append(bar)
    wrapper.appendChild(pre)
  }
}

function parseChartFenceInfo(code: HTMLElement): { sourceArtifactId?: string } | null {
  const className = code.className ?? ''
  const match = className.match(/(?:^|\s)language-(?:chart|echarts)(?::([A-Za-z0-9_-]+))?(?:\s|$)/i)
  if (!match) return null
  return { sourceArtifactId: match[1] }
}

function decorateChartBlocks(
  root: HTMLElement,
  options: {
    cacheKey?: string
    streaming: boolean
    messageId?: string
    partId?: string
  },
) {
  const codes = Array.from(root.querySelectorAll('pre > code')) as HTMLElement[]
  let blockIndex = 0

  for (const code of codes) {
    const chartInfo = parseChartFenceInfo(code)
    if (!chartInfo) continue

    const pre = code.parentElement
    if (!pre) continue

    const mount = document.createElement('div')
    mount.setAttribute('data-component', 'markdown-chart')
    mount.setAttribute('data-chart-key', `${options.cacheKey ?? 'markdown'}:${blockIndex}`)
    mount.setAttribute('data-chart-json-b64', encodeUtf8Base64(code.textContent ?? ''))
    mount.setAttribute('data-chart-streaming', String(options.streaming))
    mount.setAttribute('data-chart-block-index', String(blockIndex))
    mount.setAttribute('data-chart-message-id', options.messageId ?? options.cacheKey ?? '')
    if (options.partId) mount.setAttribute('data-chart-part-id', options.partId)
    if (chartInfo.sourceArtifactId) {
      mount.setAttribute('data-chart-source-artifact-id', chartInfo.sourceArtifactId)
    }

    pre.parentNode?.replaceChild(mount, pre)
    blockIndex += 1
  }
}

function decorateDashboardBlocks(
  root: HTMLElement,
  options: {
    cacheKey?: string
    streaming: boolean
    messageId?: string
    partId?: string
  },
) {
  const codes = Array.from(root.querySelectorAll('pre > code')) as HTMLElement[]
  let blockIndex = 0

  for (const code of codes) {
    const className = code.className ?? ''
    if (!/(?:^|\s)language-dashboard(?:\s|$)/i.test(className)) continue

    const pre = code.parentElement
    if (!pre) continue

    const mount = document.createElement('div')
    mount.setAttribute('data-component', 'markdown-dashboard')
    mount.setAttribute('data-dashboard-key', `${options.cacheKey ?? 'markdown'}:dashboard:${blockIndex}`)
    mount.setAttribute('data-dashboard-json-b64', encodeUtf8Base64(code.textContent ?? ''))
    mount.setAttribute('data-dashboard-streaming', String(options.streaming))
    mount.setAttribute('data-dashboard-block-index', String(blockIndex))
    mount.setAttribute('data-dashboard-message-id', options.messageId ?? options.cacheKey ?? '')
    if (options.partId) mount.setAttribute('data-dashboard-part-id', options.partId)

    pre.parentNode?.replaceChild(mount, pre)
    blockIndex += 1
  }
}

function renderHtml(text: string, cacheKey: string | undefined, streaming: boolean, copyLabel: string): string {
  if (!text) return ''
  try {
    const blocks = stream(text, streaming)
    const htmls = blocks.map((block, i) => {
      const blockHash = hash(`${copyLabel}\0${block.raw}`)
      const key = cacheKey ? `${cacheKey}:${i}:${block.mode}` : undefined
      if (key) {
        const cached = cache.get(key)
        if (cached && cached.hash === blockHash) {
          touch(key, cached)
          return cached.html
        }
      }
      if (block.mode === 'stream-code') {
        const html = renderStreamingCodeBlock(block, copyLabel)
        if (key) touch(key, { hash: blockHash, html })
        return html
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
  messageId?: string
  partId?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  const chartRootsRef = useRef<Map<string, ChartRootEntry>>(new Map())
  const dashboardRootsRef = useRef<Map<string, ChartRootEntry>>(new Map())
  const { t } = useI18n()
  const tRef = useRef(t)
  tRef.current = t

  useEffect(() => {
    const roots = chartRootsRef.current
    const dashRoots = dashboardRootsRef.current
    return () => {
      for (const { root } of roots.values()) scheduleRootUnmount(root)
      roots.clear()
      for (const { root } of dashRoots.values()) scheduleRootUnmount(root)
      dashRoots.clear()
    }
  }, [])

  useEffect(() => {
    const container = ref.current
    if (!container) return

    const chartRoots = chartRootsRef.current
    const dashboardRoots = dashboardRootsRef.current
    const clearChartRoots = () => {
      for (const { root } of chartRoots.values()) scheduleRootUnmount(root)
      chartRoots.clear()
    }
    const clearDashboardRoots = () => {
      for (const { root } of dashboardRoots.values()) scheduleRootUnmount(root)
      dashboardRoots.clear()
    }

    const copyLabel = tRef.current('common.copy')
    const html = renderHtml(props.text, props.cacheKey, props.streaming ?? false, copyLabel)
    if (!html) {
      container.innerHTML = ''
      clearChartRoots()
      return
    }

    const temp = document.createElement('div')
    temp.innerHTML = html
    decorateChartBlocks(temp, {
      cacheKey: props.cacheKey,
      streaming: props.streaming ?? false,
      messageId: props.messageId,
      partId: props.partId,
    })
    decorateDashboardBlocks(temp, {
      cacheKey: props.cacheKey,
      streaming: props.streaming ?? false,
      messageId: props.messageId,
      partId: props.partId,
    })
    decorateCodeBlocks(temp, copyLabel)
    decorateSqlBlocks(temp)
    decorateTables(temp, tRef.current)

    morphdom(container, temp, {
      childrenOnly: true,
      onBeforeElUpdated(fromEl, toEl) {
        const fromNode = fromEl as HTMLElement
        const toNode = toEl as HTMLElement
        const component = fromNode.getAttribute('data-component')

        if (component === 'markdown-chart') {
          for (const attr of Array.from(fromNode.attributes)) {
            if (attr.name.startsWith('data-chart-') && !toNode.hasAttribute(attr.name)) {
              fromNode.removeAttribute(attr.name)
            }
          }
          for (const attr of Array.from(toNode.attributes)) {
            if (attr.name.startsWith('data-chart-') || attr.name === 'data-component') {
              fromNode.setAttribute(attr.name, attr.value)
            }
          }
          return false
        }

        if (component === 'markdown-dashboard') {
          for (const attr of Array.from(fromNode.attributes)) {
            if (attr.name.startsWith('data-dashboard-') && !toNode.hasAttribute(attr.name)) {
              fromNode.removeAttribute(attr.name)
            }
          }
          for (const attr of Array.from(toNode.attributes)) {
            if (attr.name.startsWith('data-dashboard-') || attr.name === 'data-component') {
              fromNode.setAttribute(attr.name, attr.value)
            }
          }
          return false
        }

        return true
      },
    })

    const mountPoints = Array.from(
      container.querySelectorAll('[data-component="markdown-chart"]'),
    ) as HTMLElement[]
    const liveKeys = new Set<string>()

    for (const mountPoint of mountPoints) {
      const chartKey = mountPoint.dataset.chartKey
      if (!chartKey) continue
      liveKeys.add(chartKey)

      const encodedJson = mountPoint.dataset.chartJsonB64 ?? ''
      const streaming = mountPoint.dataset.chartStreaming === 'true'
      const sourceArtifactId = mountPoint.dataset.chartSourceArtifactId
      const messageId = mountPoint.dataset.chartMessageId ?? ''
      const partId = mountPoint.dataset.chartPartId
      const blockIndex = Number.parseInt(mountPoint.dataset.chartBlockIndex ?? '0', 10)
      const json = decodeUtf8Base64(encodedJson)

      let entry = chartRoots.get(chartKey)
      if (!entry || entry.host !== mountPoint) {
        if (entry) scheduleRootUnmount(entry.root)
        entry = { root: createRoot(mountPoint), host: mountPoint }
        chartRoots.set(chartKey, entry)
      }

      entry.root.render(
        <ChartBlock
          json={json}
          streaming={streaming}
          messageId={messageId}
          partId={partId || undefined}
          blockIndex={Number.isNaN(blockIndex) ? 0 : blockIndex}
          sourceArtifactId={sourceArtifactId || undefined}
        />,
      )
    }

    for (const [key, entry] of chartRoots) {
      if (!liveKeys.has(key)) {
        scheduleRootUnmount(entry.root)
        chartRoots.delete(key)
      }
    }

    // Dashboard blocks
    const dashMountPoints = Array.from(
      container.querySelectorAll('[data-component="markdown-dashboard"]'),
    ) as HTMLElement[]
    const liveDashKeys = new Set<string>()

    for (const mountPoint of dashMountPoints) {
      const dashKey = mountPoint.dataset.dashboardKey
      if (!dashKey) continue
      liveDashKeys.add(dashKey)

      const encodedJson = mountPoint.dataset.dashboardJsonB64 ?? ''
      const streaming = mountPoint.dataset.dashboardStreaming === 'true'
      const messageId = mountPoint.dataset.dashboardMessageId ?? ''
      const partId = mountPoint.dataset.dashboardPartId
      const blockIndex = Number.parseInt(mountPoint.dataset.dashboardBlockIndex ?? '0', 10)
      const json = decodeUtf8Base64(encodedJson)

      let entry = dashboardRoots.get(dashKey)
      if (!entry || entry.host !== mountPoint) {
        if (entry) scheduleRootUnmount(entry.root)
        entry = { root: createRoot(mountPoint), host: mountPoint }
        dashboardRoots.set(dashKey, entry)
      }

      entry.root.render(
        <DashboardBlock
          json={json}
          streaming={streaming}
          messageId={messageId}
          partId={partId || undefined}
        />,
      )
    }

    for (const [key, entry] of dashboardRoots) {
      if (!liveDashKeys.has(key)) {
        scheduleRootUnmount(entry.root)
        dashboardRoots.delete(key)
      }
    }
  }, [props.text, props.cacheKey, props.streaming, props.messageId, props.partId])

  useEffect(() => {
    const container = ref.current
    if (!container) return
    const onClick = async (e: MouseEvent) => {
      const btn = (e.target as Element)?.closest?.(
        '[data-slot="markdown-copy-button"], [data-slot="sql-execute"], [data-slot="sql-explain"], [data-slot="markdown-table-copy"], [data-slot="markdown-table-csv"], [data-slot="markdown-table-more"], [data-slot="markdown-table-action"]',
      ) as HTMLElement | null
      if (!btn) return
      if (btn.matches('[data-slot="markdown-table-more"]')) {
        const menu = btn.parentElement?.querySelector('[data-slot="markdown-table-menu"]') as HTMLElement | null
        const open = !(menu?.hidden ?? true)
        closeTableMenus(container, open ? null : menu)
        return
      }

      const code = btn.closest('[data-component="markdown-code"]')?.querySelector('code')
      const content = code?.textContent ?? ''
      if (btn.matches('[data-slot="markdown-copy-button"]')) {
        if (!content) return
        const success = await copyToClipboard(content)
        if (success) showCopiedState(btn)
        return
      }

      if (btn.matches('[data-slot="markdown-table-copy"], [data-slot="markdown-table-csv"], [data-slot="markdown-table-action"]')) {
        const table = btn.closest('[data-component="markdown-table"]')?.querySelector('table') as HTMLTableElement | null
        if (!table) return
        const model = extractTableModel(table)
        let success = false

        if (btn.matches('[data-slot="markdown-table-copy"]')) {
          success = await copyTableHtmlAndText(model.sourceHtml, toTsv(model))
        } else if (btn.matches('[data-slot="markdown-table-csv"]')) {
          success = await copyToClipboard(toCsv(model))
        } else {
          const format = btn.getAttribute('data-format')
          if (format === 'tsv') success = await copyToClipboard(toTsv(model))
          if (format === 'markdown') success = await copyToClipboard(toMarkdownTable(model))
          if (format === 'json') success = await copyToClipboard(toJson(model))
          if (format === 'download-csv') {
            downloadTableCsv(getDownloadFilename(), toDownloadableCsv(model))
            success = true
          }
        }

        if (success) {
          showCopiedState(
            btn,
            btn.matches('[data-slot="markdown-table-action"]') ? () => closeTableMenus(container) : undefined,
          )
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
