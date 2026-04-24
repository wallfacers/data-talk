import { marked, type Tokens } from 'marked'

export type Block = {
  raw: string
  src: string
  mode: 'full' | 'live' | 'stream-code'
  language?: string
  code?: string
  fence?: '```' | '~~~'
}

function refs(text: string) {
  return /^\[[^\]]+\]:\s+\S+/m.test(text) || /^\[\^[^\]]+\]:\s+/m.test(text)
}

function open(raw: string) {
  const match = raw.match(/^[ \t]{0,3}(`{3,}|~{3,})/)
  if (!match) return false
  const mark = match[1]
  if (!mark) return false
  const char = mark[0]
  const size = mark.length
  const last = raw.trimEnd().split('\n').at(-1)?.trim() ?? ''
  return !new RegExp(`^[\\t ]{0,3}${char}{${size},}[\\t ]*$`).test(last)
}

function heal(text: string): string {
  // 简化版：不引入 remend；未闭合强调/链接保持原样，交给 marked 自行处理
  return text
}

function stripPartialClosingFence(code: string, fenceMarker: '```' | '~~~'): string {
  // Streaming deltas may deliver a not-yet-complete closing fence one
  // character at a time ("`", "``"). If we leave those characters at the tail
  // of the visible code body, they render as an extra line. Then when the
  // final "`" arrives and the fence closes, marked drops the whole closing
  // line at once and the code container shrinks by a line, shifting all
  // content below upward. Strip a trailing line that is only 1..fenceLen-1
  // of the same fence character so the visible line count stays stable.
  const lastNewlineIdx = code.lastIndexOf('\n')
  if (lastNewlineIdx < 0) return code
  const trailing = code.slice(lastNewlineIdx + 1)
  const fenceChar = fenceMarker[0]
  const fenceLen = fenceMarker.length
  const pattern = new RegExp(`^[ \\t]{0,3}\\${fenceChar}{1,${fenceLen - 1}}$`)
  if (!pattern.test(trailing)) return code
  return code.slice(0, lastNewlineIdx + 1)
}

function parseOpenFence(raw: string): { language: string; code: string; fence: '```' | '~~~' } | null {
  const match = raw.match(/^[ \t]{0,3}(`{3,}|~{3,})([^\n]*)\n?([\s\S]*)$/)
  if (!match) return null
  const marker = match[1]!.startsWith('`') ? '```' : '~~~'
  const info = (match[2] ?? '').trim()
  const language = info.split(/\s+/)[0]?.toLowerCase() ?? ''
  const rawCode = match[3] ?? ''
  return { language, code: stripPartialClosingFence(rawCode, marker), fence: marker }
}

export function stream(text: string, live: boolean): Block[] {
  if (!live) return [{ raw: text, src: text, mode: 'full' }]
  const src = heal(text)
  if (!text) return [{ raw: text, src, mode: 'live' }]
  if (refs(text)) return [{ raw: text, src, mode: 'live' }]
  const tokens = marked.lexer(text)
  let tail = -1
  for (let i = tokens.length - 1; i >= 0; i--) {
    if ((tokens[i] as any).type !== 'space') { tail = i; break }
  }
  if (tail < 0) return [{ raw: text, src, mode: 'live' }]
  const last = tokens[tail]
  if (!last || last.type !== 'code') return [{ raw: text, src, mode: 'live' }]
  const code = last as Tokens.Code
  if (!open(code.raw)) return [{ raw: text, src, mode: 'live' }]
  const openFence = parseOpenFence(code.raw)
  if (!openFence) return [{ raw: text, src, mode: 'live' }]
  const head = tokens.slice(0, tail).map((t: any) => t.raw).join('')
  const streamCode: Block = {
    raw: code.raw,
    src: code.raw,
    mode: 'stream-code',
    language: openFence.language,
    code: openFence.code,
    fence: openFence.fence,
  }
  if (!head) return [streamCode]
  return [
    { raw: head, src: heal(head), mode: 'live' },
    streamCode,
  ]
}
