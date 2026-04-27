import { registerClientHandler } from './registry'
import { uiRouter } from '@/services/ui-router'
import type { UIRequest, UIResponse } from '@/services/ui-router'

type ReadInput = { object: string; target?: string; mode?: 'state' | 'schema' | 'actions' | 'full' }
type PatchInput = { object: string; target?: string; ops: unknown[]; reason?: string }
type ExecInput = { object: string; target?: string; action: string; params?: unknown }

type ClientActionErrorDetail = {
  code?: string
  message?: string
  retriable?: boolean
  details?: unknown
}

class ClientActionHandlerError extends Error {
  code: string
  retriable?: boolean
  details?: unknown

  constructor(message: string, options?: { code?: string; retriable?: boolean; details?: unknown }) {
    super(message)
    this.name = 'ClientActionHandlerError'
    this.code = options?.code ?? 'client_action_error'
    this.retriable = options?.retriable
    this.details = options?.details
  }
}

function getStructuredErrorDetail(data: unknown): ClientActionErrorDetail | undefined {
  if (typeof data !== 'object' || data === null) return undefined
  const detail = data as ClientActionErrorDetail
  if (typeof detail.code !== 'string' || typeof detail.message !== 'string') return undefined
  return detail
}

function toClientActionHandlerError(resp: UIResponse): ClientActionHandlerError {
  const detail = getStructuredErrorDetail(resp.data)
  const message = resp.error ?? detail?.message ?? 'Client action failed'
  return new ClientActionHandlerError(message, {
    code: detail?.code ?? 'client_action_error',
    retriable: detail?.retriable,
    details: detail ?? resp.data,
  })
}

async function forward(req: UIRequest): Promise<unknown> {
  const resp = await uiRouter.handle(req)
  if (resp.error) throw toClientActionHandlerError(resp)
  return resp.data
}

registerClientHandler('datatalk.ui.read', async (input) => {
  const i = input as ReadInput
  return forward({ tool: 'ui_read', object: i.object, target: i.target ?? 'active', payload: { mode: i.mode } })
})

registerClientHandler('datatalk.ui.patch', async (input) => {
  const i = input as PatchInput
  return forward({ tool: 'ui_patch', object: i.object, target: i.target ?? 'active', payload: { ops: i.ops, reason: i.reason } })
})

registerClientHandler('datatalk.ui.exec', async (input) => {
  const i = input as ExecInput
  return forward({ tool: 'ui_exec', object: i.object, target: i.target ?? 'active', payload: { action: i.action, params: i.params } })
})
