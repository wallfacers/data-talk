import { z } from 'zod'

export type HostToIframe =
  | { type: 'params/update'; params: Record<string, unknown> }
  | { type: 'refresh/pause' }
  | { type: 'refresh/resume' }
  | { type: 'theme/preview'; theme: string }
  | { type: 'widget/update'; widgetId: string; baseOption: Record<string, unknown>; html?: string }

export type IframeToHost =
  | { type: 'ready'; jsonHash: string | null }
  | { type: 'error'; widgetId: string; message: string }
  | { type: 'metric'; name: string; value: number }

const widgetUpdateSchema = z.object({
  type: z.literal('widget/update'),
  widgetId: z.string().min(1),
  baseOption: z.record(z.string(), z.unknown()),
  html: z.string().optional(),
})

export function isIframeToHost(x: unknown): x is IframeToHost {
  if (!x || typeof x !== 'object') return false
  const t = (x as { type?: unknown }).type
  return t === 'ready' || t === 'error' || t === 'metric'
}

export function parseWidgetUpdate(data: unknown): z.infer<typeof widgetUpdateSchema> | null {
  const result = widgetUpdateSchema.safeParse(data)
  return result.success ? result.data : null
}
