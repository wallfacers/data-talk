import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { StageTabApi, UpsertRequest } from '../stage-tab-api'
import { StagePersistenceCoordinator } from '../stage-persistence-coordinator'

function createMockApi(overrides?: Partial<StageTabApi>): StageTabApi {
  return {
    listWorkspaceTabs: vi.fn().mockResolvedValue({ items: [] }),
    listSessionTabs: vi.fn().mockResolvedValue({ items: [] }),
    upsert: vi.fn().mockResolvedValue({ id: '', payloadVersion: 1 }),
    putPayload: vi.fn().mockResolvedValue({ id: '', payloadVersion: 1 }),
    delete: vi.fn().mockResolvedValue(undefined),
    getPayload: vi.fn().mockResolvedValue({ payload: {}, contentText: '', payloadVersion: 1 }),
    setArchived: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

function stubSnapshot(tabId: string): UpsertRequest {
  return {
    id: tabId,
    type: 'query_editor',
    scope: 'workspace',
    title: 'Test',
    createdAt: Date.now(),
    lastTouchedAt: Date.now(),
  }
}

describe('StagePersistenceCoordinator', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('start() hydrates workspace tabs and transitions to live phase', async () => {
    const items = [{ id: 't1', title: 'Q1' }]
    const api = createMockApi({
      listWorkspaceTabs: vi.fn().mockResolvedValue({ items }),
    })
    const onHydrated = vi.fn()
    const coord = new StagePersistenceCoordinator(api)
    coord.onHydrated = onHydrated

    await coord.start()

    expect(coord.phase).toBe('live')
    expect(onHydrated).toHaveBeenCalledWith(items)
  })

  it('content writes debounce 1s and coalesce', async () => {
    const api = createMockApi()
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = () => stubSnapshot('t1')
    coord.phase = 'live'

    coord.scheduleContentWrite('t1', { payload: { sql: 'v1' }, contentText: 'v1' })
    coord.scheduleContentWrite('t1', { payload: { sql: 'v2' }, contentText: 'v2' })

    // Not yet written
    expect(api.putPayload).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)

    // Only the last write should be persisted
    expect(api.putPayload).toHaveBeenCalledTimes(1)
    const calledReq = (api.putPayload as ReturnType<typeof vi.fn>).mock.calls[0][0] as UpsertRequest
    expect(calledReq.contentText).toBe('v2')
  })

  it('flush() cancels pending debounce and writes immediately', async () => {
    const api = createMockApi()
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = () => stubSnapshot('t1')
    coord.phase = 'live'

    coord.scheduleContentWrite('t1', { payload: {}, contentText: 'flush-test' })
    await coord.flush('t1')

    expect(api.putPayload).toHaveBeenCalledTimes(1)
    const calledReq = (api.putPayload as ReturnType<typeof vi.fn>).mock.calls[0][0] as UpsertRequest
    expect(calledReq.contentText).toBe('flush-test')

    // Advance timer to confirm no double-write
    await vi.advanceTimersByTimeAsync(1500)
    expect(api.putPayload).toHaveBeenCalledTimes(1)
  })

  it('metadata writes are immediate (no debounce)', async () => {
    const api = createMockApi()
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = () => stubSnapshot('t1')
    coord.phase = 'live'

    coord.scheduleMetadataWrite('t1', { title: 'New Title' })

    // Metadata should be written immediately without timer
    await vi.advanceTimersByTimeAsync(0)
    expect(api.upsert).toHaveBeenCalledTimes(1)
  })

  it('ensureHydrated is idempotent under concurrency', async () => {
    let resolvePayload: (v: unknown) => void
    const payloadPromise = new Promise((r) => { resolvePayload = r })
    const api = createMockApi({
      getPayload: vi.fn().mockReturnValue(payloadPromise),
    })
    const onPayloadHydrated = vi.fn()
    const coord = new StagePersistenceCoordinator(api)
    coord.onPayloadHydrated = onPayloadHydrated

    // Two concurrent calls
    const p1 = coord.ensureHydrated('t1')
    const p2 = coord.ensureHydrated('t1')

    resolvePayload!({ payload: { sql: 'SELECT 1' }, contentText: 'SELECT 1', payloadVersion: 3 })

    await Promise.all([p1, p2])

    // API should be called only once
    expect(api.getPayload).toHaveBeenCalledTimes(1)
    expect(onPayloadHydrated).toHaveBeenCalledWith('t1', { sql: 'SELECT 1' }, 3)
  })

  it('phase=hydrating queues writes and flushes after live', async () => {
    const api = createMockApi({
      listWorkspaceTabs: vi.fn().mockImplementation(() => {
        return new Promise((resolve) => {
          setTimeout(() => resolve({ items: [] }), 50)
        })
      }),
    })
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = () => stubSnapshot('t1')

    const startPromise = coord.start()
    expect(coord.phase).toBe('hydrating')

    // Queue a write while hydrating
    coord.scheduleMetadataWrite('t1', { title: 'Queued' })

    // Advance past the hydration delay
    await vi.advanceTimersByTimeAsync(50)
    await startPromise

    expect(coord.phase).toBe('live')
    // The queued write should have been dispatched
    expect(api.upsert).toHaveBeenCalledTimes(1)
  })

  it('falls into degraded mode on persistent 5xx', async () => {
    const err5xx = Object.assign(new Error('stage-tab-api 500'), { status: 500 })
    const api = createMockApi({
      upsert: vi.fn().mockRejectedValue(err5xx),
    })
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = () => stubSnapshot('t1')
    coord.phase = 'live'

    coord.scheduleMetadataWrite('t1', { title: 'Trigger 5xx' })

    // Allow async to settle
    await vi.advanceTimersByTimeAsync(0)
    await Promise.resolve()

    expect(coord.phase).toBe('degraded')

    // Further writes should be no-ops
    coord.scheduleContentWrite('t1', { payload: {}, contentText: 'nope' })
    expect(api.putPayload).not.toHaveBeenCalled()
  })

  it('flushAll resolves all pending content + metadata writes', async () => {
    const api = createMockApi()
    const coord = new StagePersistenceCoordinator(api)
    coord.resolveTabSnapshot = (tabId: string) => stubSnapshot(tabId)
    coord.phase = 'live'

    coord.scheduleContentWrite('t1', { payload: {}, contentText: 'c1' })
    coord.scheduleContentWrite('t2', { payload: {}, contentText: 'c2' })
    coord.scheduleMetadataWrite('t3', { title: 'm3' })

    await coord.flushAll()

    expect(api.putPayload).toHaveBeenCalledTimes(2)
    expect(api.upsert).toHaveBeenCalledTimes(1)
  })
})
