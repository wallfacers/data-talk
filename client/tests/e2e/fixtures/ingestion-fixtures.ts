import http, { type IncomingMessage, type ServerResponse, type Server } from 'node:http'
import type { APIRequestContext, Page } from '@playwright/test'
import { adapterClient } from './adapter-client'

/** Fixtures intentionally avoid TLS / hostname resolution — only 127.0.0.1 binding is supported. */

export interface MockServer {
  server: Server
  port: number
  baseUrl: string
  /** Request log indexed by path; lets tests assert auth headers, pagination params. */
  hits: Array<{ method: string; url: string; headers: Record<string, string>; bodyB64?: string }>
  /** Reset between tests inside the same describe block. */
  reset(): void
  stop(): Promise<void>
}

export async function startMockIngestionServer(): Promise<MockServer> {
  const hits: MockServer['hits'] = []
  const server = http.createServer((req, res) => onRequest(req, res, hits))
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()))
  const address = server.address()
  if (typeof address !== 'object' || address === null) throw new Error('mock server address missing')
  const port = address.port
  return {
    server,
    port,
    baseUrl: `http://127.0.0.1:${port}`,
    hits,
    reset() { hits.length = 0 },
    stop: () => new Promise<void>((r) => server.close(() => r())),
  }
}

function onRequest(req: IncomingMessage, res: ServerResponse, hits: MockServer['hits']) {
  const headers: Record<string, string> = {}
  for (const [k, v] of Object.entries(req.headers)) {
    if (typeof v === 'string') headers[k.toLowerCase()] = v
    else if (Array.isArray(v)) headers[k.toLowerCase()] = v.join(',')
  }
  const url = req.url ?? '/'

  let bodyChunks: Buffer[] = []
  req.on('data', (chunk: Buffer) => bodyChunks.push(chunk))
  req.on('end', () => {
    const body = Buffer.concat(bodyChunks)
    hits.push({ method: req.method ?? 'GET', url, headers, bodyB64: body.length ? body.toString('base64') : undefined })
    route(req, res, url, headers)
  })
}

function route(req: IncomingMessage, res: ServerResponse, url: string, headers: Record<string, string>) {
  // ── JSON array (3 rows) ──
  if (url === '/json/users') return json(res, [
    { id: 1, name: 'Alice', score: 12.5, active: true,  joined: '2024-01-01T00:00:00Z' },
    { id: 2, name: 'Bob',   score: 9.0,  active: false, joined: '2024-02-15T10:30:00Z' },
    { id: 3, name: 'Carol', score: null, active: true,  joined: '2024-03-20T14:00:00Z' },
  ])

  // ── Heterogeneous id values (mixed types → STRING fallback) ──
  if (url === '/json/heterogeneous-id') return json(res, [{ id: 1 }, { id: 2 }, { id: '3-abc' }])

  // ── Large integer > 2^31 (INTEGER_64 promotion) ──
  if (url === '/json/large-int') return json(res, [{ v: 3000000000 }, { v: 2147483648 }, { v: 9007199254740991 }])

  // ── JSON envelope (Object with .data array) ──
  if (url === '/json/envelope') return json(res, { meta: { total: 2 }, data: [{ id: 1 }, { id: 2 }] })

  // ── JSONL ──
  if (url === '/jsonl/events') {
    res.writeHead(200, { 'Content-Type': 'application/x-ndjson' })
    res.end('{"id":1,"kind":"click"}\n{"id":2,"kind":"view"}\n{"id":3,"kind":"click"}\n')
    return
  }

  // ── CSV ──
  if (url === '/csv/orders') {
    res.writeHead(200, { 'Content-Type': 'text/csv' })
    res.end('id,name,total\n1,alice,100.50\n2,bob,75.25\n')
    return
  }

  // ── HTML table ──
  if (url === '/html/leaderboard') {
    res.writeHead(200, { 'Content-Type': 'text/html' })
    res.end(`<html><body><table>
      <thead><tr><th>rank</th><th>player</th><th>score</th></tr></thead>
      <tbody><tr><td>1</td><td>Ada</td><td>3000</td></tr><tr><td>2</td><td>Bea</td><td>2200</td></tr></tbody>
    </table></body></html>`)
    return
  }

  // ── Bearer auth required ──
  if (url === '/auth/bearer') {
    if (headers.authorization === 'Bearer test-token-42') return json(res, [{ id: 1, msg: 'ok' }])
    return error(res, 401, { message: 'Unauthorized' })
  }
  // ── API key header ──
  if (url === '/auth/api-key-header') {
    if (headers['x-api-key'] === 'secret-key-7') return json(res, [{ id: 1 }])
    return error(res, 401, { message: 'Unauthorized' })
  }
  // ── API key query ──
  if (url.startsWith('/auth/api-key-query')) {
    const u = new URL(url, 'http://x')
    if (u.searchParams.get('api_key') === 'secret-key-7') return json(res, [{ id: 1 }])
    return error(res, 401, { message: 'Unauthorized' })
  }
  // ── Basic auth ──
  if (url === '/auth/basic') {
    if (headers.authorization === `Basic ${Buffer.from('alice:p@ss').toString('base64')}`) return json(res, [{ id: 1 }])
    return error(res, 401, { message: 'Unauthorized' })
  }

  // ── Pagination — page param ──
  if (url.startsWith('/paged/page')) {
    const page = parseInt(new URL(url, 'http://x').searchParams.get('page') ?? '1', 10)
    if (page > 3) return json(res, { items: [] })
    return json(res, {
      items: [{ id: page * 10 + 1 }, { id: page * 10 + 2 }],
      page,
      hasMore: page < 3,
    })
  }
  // ── Pagination — offset param ──
  if (url.startsWith('/paged/offset')) {
    const offset = parseInt(new URL(url, 'http://x').searchParams.get('offset') ?? '0', 10)
    const all = Array.from({ length: 6 }, (_, i) => ({ id: i + 1 }))
    return json(res, { items: all.slice(offset, offset + 2), nextOffset: offset + 2 < 6 ? offset + 2 : null })
  }
  // ── Pagination — cursor param ──
  if (url.startsWith('/paged/cursor')) {
    const cursor = new URL(url, 'http://x').searchParams.get('cursor') ?? 'A'
    const seq: Record<string, { items: Array<{ id: number }>; next: string | null }> = {
      A: { items: [{ id: 1 }, { id: 2 }], next: 'B' },
      B: { items: [{ id: 3 }, { id: 4 }], next: 'C' },
      C: { items: [{ id: 5 }], next: null },
    }
    return json(res, seq[cursor] ?? { items: [], next: null })
  }

  // ── Synthetic 401 / 413 / slow-loris ──
  if (url === '/error/401') return error(res, 401, { message: 'Unauthorized' })
  if (url === '/error/413') return error(res, 413, { message: 'Payload too large' })
  if (url === '/error/oversized') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end('[' + '{"x":1},'.repeat(200_000) + '{"x":1}]')   // ~2 MB so 1 MB e2e cap trips
    return
  }

  res.writeHead(404, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ error: 'not found' }))
}

function json(res: ServerResponse, body: unknown) {
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}
function error(res: ServerResponse, status: number, body: unknown) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

// ── Seeding helpers ──

export async function seedH2Connection(request: APIRequestContext, name = 'e2e_h2_target'): Promise<string> {
  const client = adapterClient(request)
  // BUG-0032: backend DTO field is `databaseName`, not `database`. Pass an explicit
  // in-memory H2 URL with DB_CLOSE_DELAY=-1 so the DB survives connection churn
  // between create_ingestion_table and ingest_payload (each opens a fresh
  // DriverManager connection — without DB_CLOSE_DELAY, H2 disposes the in-memory
  // database when the last connection closes, and the second open sees an empty DB).
  const res = await client.createConnection({
    name,
    kind: 'h2',
    host: 'mem',
    port: 0,
    databaseName: `mem:e2e_${Date.now()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL`,
    username: 'sa',
    password: '',
  })
  if (!res.ok()) throw new Error(`seed connection failed: ${res.status()}`)
  const body = await res.json()
  return body.id as string
}

export async function seedCredential(
  request: APIRequestContext,
  scheme: 'none' | 'bearer' | 'api_key_header' | 'api_key_query' | 'basic',
  payload: { name?: string; secret?: string; configNonSecret?: Record<string, string> } = {},
): Promise<string> {
  const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'
  const res = await request.post(`${BASE}/api/ingestion/credentials`, {
    data: {
      name: payload.name ?? `e2e_cred_${scheme}_${Date.now()}`,
      authScheme: scheme,
      configNonSecret: payload.configNonSecret ?? {},
      secret: payload.secret ?? null,
    },
  })
  if (!res.ok()) throw new Error(`seed credential failed: ${res.status()} ${await res.text()}`)
  const body = await res.json()
  return body.id as string
}

export async function waitForJobStatus(
  request: APIRequestContext,
  jobId: string,
  expected: string,
  timeoutMs = 30_000,
): Promise<unknown> {
  const start = Date.now()
  const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'
  while (Date.now() - start < timeoutMs) {
    const res = await request.get(`${BASE}/api/ingestion/jobs/${jobId}`)
    if (res.ok()) {
      const job = await res.json() as { status?: string }
      if (job.status === expected) return job
      if (job.status === 'failed' || job.status === 'cancelled') {
        if (job.status === expected) return job
        throw new Error(`job ${jobId} ended in ${job.status}, expected ${expected}: ${JSON.stringify(job)}`)
      }
    }
    await new Promise((r) => setTimeout(r, 250))
  }
  throw new Error(`job ${jobId} did not reach ${expected} within ${timeoutMs} ms`)
}
