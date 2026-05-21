function record(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === 'object' && !Array.isArray(v)
}

function parse(v: string): unknown {
  try { return JSON.parse(v) } catch { return undefined }
}

export function unwrap(message: string): string {
  const text = message.replace(/^Error:\s*/, '').trim()
  const read = (v: string) => {
    const first = parse(v)
    if (typeof first !== 'string') return first
    return parse(first.trim())
  }
  let json = read(text)
  if (json === undefined) {
    const start = text.indexOf('{'); const end = text.lastIndexOf('}')
    if (start !== -1 && end > start) json = read(text.slice(start, end + 1))
  }
  if (!record(json)) return message
  const err = record(json.error) ? json.error : undefined
  if (err) {
    const type = typeof err.type === 'string' ? err.type : undefined
    const msg = typeof err.message === 'string' ? err.message : undefined
    if (type && msg) return `${type}: ${msg}`
    if (msg) return msg
    if (type) return type
  }
  if (typeof json.message === 'string') return json.message
  if (typeof json.error === 'string') return json.error
  return message
}

export function ErrorCard(props: { message: string }) {
  return (
    <div className="my-2 rounded border border-red-500/50 bg-red-50 dark:bg-red-950/20 p-3 text-sm text-red-700 dark:text-red-300">
      {unwrap(props.message)}
    </div>
  )
}
