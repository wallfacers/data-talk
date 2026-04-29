import { beforeEach, describe, expect, it, vi } from 'vitest'

const flushSpy = vi.fn().mockResolvedValue(undefined)
const ensureHydratedSpy = vi.fn().mockResolvedValue(undefined)

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: {
    flush: (...args: unknown[]) => flushSpy(...args),
    ensureHydrated: (...args: unknown[]) => ensureHydratedSpy(...args),
  },
}))

const routerHandle = vi.fn()

vi.mock('@/services/ui-router', () => ({
  uiRouter: {
    handle: (req: unknown) => routerHandle(req),
  },
}))

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({ activeTabId: null }),
  },
}))

import { getClientHandler } from '../registry'
import '../ui-handlers'

describe('ui-handlers - ER mutating exec', () => {
  beforeEach(() => {
    flushSpy.mockClear()
    ensureHydratedSpy.mockClear()
    routerHandle.mockReset()
  })

  it('open_er_inspector flushes the newly-created tabId returned in result', async () => {
    routerHandle.mockResolvedValue({ data: { tabId: 'er_inspector_new', payloadVersion: 1 } })

    const handler = getClientHandler('datatalk.ui.exec')
    expect(handler).toBeDefined()

    await handler!({
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: 'c1', tables: ['users'] },
    }, { sessionId: 's1' })

    expect(flushSpy).toHaveBeenCalledWith('er_inspector_new')
  })

  it('refresh on an inspector tab flushes the input target', async () => {
    routerHandle.mockResolvedValue({ data: { payloadVersion: 7 } })

    const handler = getClientHandler('datatalk.ui.exec')
    expect(handler).toBeDefined()

    await handler!({
      object: 'er_inspector',
      target: 'er-1',
      action: 'refresh',
    }, { sessionId: 's1' })

    expect(flushSpy).toHaveBeenCalledWith('er-1')
  })
})
