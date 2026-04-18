function clean(s: string): string {
  return s
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[*_~]+/g, '')
    .trim()
}

export function extractHeading(text: string): string | undefined {
  if (!text) return undefined
  const md = text.replace(/\r\n?/g, '\n')

  const html = md.match(/<h[1-6][^>]*>([\s\S]*?)<\/h[1-6]>/i)
  if (html?.[1]) {
    const v = clean(html[1].replace(/<[^>]+>/g, ' '))
    if (v) return v
  }
  const atx = md.match(/^\s{0,3}#{1,6}[ \t]+(.+?)(?:[ \t]+#+[ \t]*)?$/m)
  if (atx?.[1]) {
    const v = clean(atx[1])
    if (v) return v
  }
  const setext = md.match(/^([^\n]+)\n(?:=+|-+)\s*$/m)
  if (setext?.[1]) {
    const v = clean(setext[1])
    if (v) return v
  }
  const strong = md.match(/^\s*(?:\*\*|__)(.+?)(?:\*\*|__)\s*$/m)
  if (strong?.[1]) {
    const v = clean(strong[1])
    if (v) return v
  }
  return undefined
}
