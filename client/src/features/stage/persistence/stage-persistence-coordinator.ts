import type { StageTabApi, UpsertRequest } from './stage-tab-api'

type Phase = 'idle' | 'hydrating' | 'live' | 'degraded'

interface ContentWrite {
  payload: unknown
  contentText: string
  expectedVersion?: number
}
type MetadataPatch = Partial<Pick<UpsertRequest, 'title' | 'connectionId' | 'database' | 'schema' | 'pinned' | 'originSessionId'>>

const CONTENT_DEBOUNCE_MS = 1000

export class StagePersistenceCoordinator {
  phase: Phase = 'idle'
  private contentTimers = new Map<string, ReturnType<typeof setTimeout>>()
  private contentPending = new Map<string, ContentWrite>()
  private metaPending = new Map<string, MetadataPatch>()
  private metaInflight = new Map<string, Promise<void>>()
  private hydrationCache = new Map<string, Promise<void>>()
  private queuedDuringHydration: Array<() => void> = []

  constructor(private api: StageTabApi) {}

  async start(): Promise<void> {
    this.phase = 'hydrating'
    try {
      const meta = await this.api.listWorkspaceTabs()
      this.onHydrated?.(meta.items)
    } catch (e) {
      this.phase = 'degraded'
      throw e
    }
    this.phase = 'live'
    const queued = this.queuedDuringHydration
    this.queuedDuringHydration = []
    queued.forEach((fn) => fn())
  }

  onHydrated?: (items: Array<Record<string, unknown>>) => void

  scheduleMetadataWrite(tabId: string, patch: MetadataPatch & { fullSnapshot?: UpsertRequest }): void {
    if (this.phase === 'hydrating') {
      this.queuedDuringHydration.push(() => this.scheduleMetadataWrite(tabId, patch))
      return
    }
    if (this.phase === 'degraded') return
    const merged = { ...this.metaPending.get(tabId), ...patch }
    this.metaPending.set(tabId, merged)
    void this.runMetadataWrite(tabId).catch(() => undefined)
  }

  scheduleContentWrite(tabId: string, write: ContentWrite): void {
    if (this.phase === 'hydrating') {
      this.queuedDuringHydration.push(() => this.scheduleContentWrite(tabId, write))
      return
    }
    if (this.phase === 'degraded') return
    this.contentPending.set(tabId, write)
    const existing = this.contentTimers.get(tabId)
    if (existing) clearTimeout(existing)
    this.contentTimers.set(tabId, setTimeout(() => {
      void this.runContentWrite(tabId).catch(() => undefined)
    }, CONTENT_DEBOUNCE_MS))
  }

  async delete(tabId: string): Promise<void> {
    if (this.phase === 'degraded') return
    if (this.resolveTabSnapshot(tabId)) {
      await this.flush(tabId)
    } else {
      const timer = this.contentTimers.get(tabId)
      if (timer) clearTimeout(timer)
      this.contentTimers.delete(tabId)
      this.contentPending.delete(tabId)
      this.metaPending.delete(tabId)
    }
    await this.api.delete(tabId)
  }

  async flush(tabId: string): Promise<void> {
    const t = this.contentTimers.get(tabId)
    if (t) {
      clearTimeout(t)
      this.contentTimers.delete(tabId)
    }
    const metaInflight = this.metaInflight.get(tabId)
    if (metaInflight) await metaInflight
    if (this.metaPending.has(tabId)) await this.runMetadataWrite(tabId)
    if (this.contentPending.has(tabId)) await this.runContentWrite(tabId)
  }

  async flushAll(): Promise<void> {
    const ids = new Set([...this.contentPending.keys(), ...this.metaPending.keys()])
    await Promise.all([...ids].map((id) => this.flush(id)))
  }

  flushAllSync(): void {
    const ids = [...this.contentPending.keys()]
    for (const id of ids) {
      const w = this.contentPending.get(id)
      if (!w) continue
      const blob = new Blob([JSON.stringify({ id, ...w })], { type: 'application/json' })
      navigator.sendBeacon(`/api/stage/tabs/${encodeURIComponent(id)}/payload-beacon`, blob)
    }
  }

  ensureHydrated(tabId: string): Promise<void> {
    const cached = this.hydrationCache.get(tabId)
    if (cached) return cached
    const p = this.api.getPayload(tabId).then((r) => {
      this.onPayloadHydrated?.(tabId, r.payload, r.payloadVersion)
    })
    this.hydrationCache.set(tabId, p)
    return p
  }

  onPayloadHydrated?: (tabId: string, payload: unknown, version: number) => void
  onPersisted?: (tabId: string, version: number) => void

  private async runMetadataWrite(tabId: string): Promise<void> {
    const inflight = this.metaInflight.get(tabId)
    if (inflight) return inflight
    const snap = this.metaPending.get(tabId)
    if (!snap) return
    this.metaPending.delete(tabId)
    const promise = this.api.upsert(this.materializeUpsert(tabId, snap))
      .then((response) => {
        this.onPersisted?.(tabId, response.payloadVersion)
      })
      .catch((e: unknown) => {
        this.handleError(e)
        throw e
      })
      .finally(() => {
        this.metaInflight.delete(tabId)
      })
    this.metaInflight.set(tabId, promise)
    await promise
    if (this.metaPending.has(tabId)) await this.runMetadataWrite(tabId)
  }

  private async runContentWrite(tabId: string): Promise<void> {
    const w = this.contentPending.get(tabId)
    if (!w) return
    this.contentPending.delete(tabId)
    this.contentTimers.delete(tabId)
    try {
      const response = await this.api.putPayload(this.materializeContent(tabId, w))
      this.onPersisted?.(tabId, response.payloadVersion)
    } catch (e) {
      this.handleError(e)
      throw e
    }
  }

  resolveTabSnapshot: ((tabId: string) => UpsertRequest | null) = () => null

  private materializeUpsert(tabId: string, patch: MetadataPatch): UpsertRequest {
    const base = this.resolveTabSnapshot(tabId)
    if (!base) throw new Error(`no in-memory snapshot for ${tabId} — coordinator cannot serialize`)
    return { ...base, ...patch, lastTouchedAt: Date.now() }
  }

  private materializeContent(tabId: string, w: ContentWrite): UpsertRequest {
    const base = this.resolveTabSnapshot(tabId)
    if (!base) throw new Error(`no in-memory snapshot for ${tabId}`)
    return {
      ...base,
      payload: w.payload,
      contentText: w.contentText,
      ifMatch: w.expectedVersion,
      lastTouchedAt: Date.now(),
    }
  }

  private handleError(e: unknown): void {
    const status = (e as { status?: number } | null)?.status ?? 0
    if (status >= 500 || status === 0) {
      this.phase = 'degraded'
    }
  }
}
