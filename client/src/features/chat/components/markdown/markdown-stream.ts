import { marked, type Tokens } from 'marked'

export type Block = {
  raw: string
  src: string
  mode: 'full' | 'live'
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

export function stream(text: string, live: boolean): Block[] {
  if (!live) return [{ raw: text, src: text, mode: 'full' }]
  const src = heal(text)
  if (!text) return [{ raw: text, src, mode: 'live' }]
  if (refs(text)) return [{ raw: text, src, mode: 'live' }]
  const tokens = marked.lexer(text)
  const tail = tokens.findLastIndex((token: any) => token.type !== 'space')
  if (tail < 0) return [{ raw: text, src, mode: 'live' }]
  const last = tokens[tail]
  if (!last || last.type !== 'code') return [{ raw: text, src, mode: 'live' }]
  const code = last as Tokens.Code
  if (!open(code.raw)) return [{ raw: text, src, mode: 'live' }]
  const head = tokens.slice(0, tail).map((t: any) => t.raw).join('')
  if (!head) return [{ raw: code.raw, src: code.raw, mode: 'live' }]
  return [
    { raw: head, src: heal(head), mode: 'live' },
    { raw: code.raw, src: code.raw, mode: 'live' },
  ]
}
