export type HostToIframe =
  | { type: 'params/update'; params: Record<string, unknown> }
  | { type: 'refresh/pause' }
  | { type: 'refresh/resume' }
  | { type: 'theme/preview'; theme: string }

export type IframeToHost =
  | { type: 'ready'; jsonHash: string | null }
  | { type: 'error'; widgetId: string; message: string }
  | { type: 'metric'; name: string; value: number }

export function isIframeToHost(x: unknown): x is IframeToHost {
  if (!x || typeof x !== 'object') return false
  const t = (x as { type?: unknown }).type
  return t === 'ready' || t === 'error' || t === 'metric'
}
