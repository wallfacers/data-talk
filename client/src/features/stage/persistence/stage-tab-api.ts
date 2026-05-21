const BASE = '/api/stage/tabs'

export interface UpsertRequest {
  id: string
  type: string
  title: string
  connectionId?: string | null
  database?: string | null
  schema?: string | null
  originSessionId?: string | null
  pinned?: boolean
  archived?: boolean
  createdAt: number
  lastTouchedAt: number
  payload?: unknown
  contentText?: string
  ifMatch?: number
}

export interface UpsertResponse {
  id: string
  payloadVersion: number
}

export interface PayloadResponse {
  payload: unknown
  contentText: string
  payloadVersion: number
}

export interface ListResponse {
  items: Array<Record<string, unknown>>
}

export interface StageTabApi {
  listAll(opts?: { archived?: boolean; originSessionId?: string }): Promise<ListResponse>
  upsert(req: UpsertRequest): Promise<UpsertResponse>
  putPayload(req: UpsertRequest): Promise<UpsertResponse>
  delete(id: string): Promise<void>
  getPayload(id: string): Promise<PayloadResponse>
  setArchived(id: string, archived: boolean): Promise<void>
}

export const stageTabApi: StageTabApi = {
  async listAll(opts) {
    const params = new URLSearchParams()
    if (opts?.archived !== undefined) {
      params.set('archived', String(opts.archived))
    }
    if (opts?.originSessionId) {
      params.set('originSessionId', opts.originSessionId)
    }
    const query = params.size > 0 ? `?${params.toString()}` : ''
    const r = await fetch(`${BASE}${query}`)
    if (!r.ok) throw httpError(r)
    return r.json()
  },
  async upsert(req) {
    return doPut(req)
  },
  async putPayload(req) {
    return doPut(req)
  },
  async delete(id) {
    const r = await fetch(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' })
    if (!r.ok && r.status !== 404) throw httpError(r)
  },
  async getPayload(id) {
    const r = await fetch(`${BASE}/${encodeURIComponent(id)}/payload`)
    if (!r.ok) throw httpError(r)
    return r.json()
  },
  async setArchived(id, archived) {
    const r = await fetch(`${BASE}/${encodeURIComponent(id)}/archive`, {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ archived }),
    })
    if (!r.ok) throw httpError(r)
  },
}

async function doPut(req: UpsertRequest): Promise<UpsertResponse> {
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (req.ifMatch !== undefined) headers['If-Match'] = String(req.ifMatch)
  const r = await fetch(`${BASE}/${encodeURIComponent(req.id)}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(req),
  })
  if (!r.ok) throw httpError(r)
  return r.json()
}

function httpError(r: Response) {
  const e = new Error(`stage-tab-api ${r.status}`) as Error & { status?: number }
  e.status = r.status
  return e
}
