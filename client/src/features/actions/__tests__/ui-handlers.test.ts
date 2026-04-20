import { describe, it, expect, beforeEach } from 'vitest'
import { getClientHandler } from '../registry'
import { uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import '../ui-handlers'

function stubObject(objectId: string, stateValue: unknown): UIObject {
  return {
    type: 'stub', objectId, title: objectId,
    read: () => stateValue,
    patch: async () => ({ status: 'applied' }),
    exec: async () => ({ success: true, data: { ok: 1 } }),
  }
}

describe('ui-handlers', () => {
  beforeEach(() => {
    uiRouter.registerInstance('stub1', stubObject('stub1', { foo: 'bar' }))
  })

  it('ui_read handler returns router data', async () => {
    const h = getClientHandler('datatalk.ui.read')!
    const out = await h({ object: 'stub', target: 'stub1', mode: 'state' }, { sessionId: 's1' })
    expect(out).toEqual({ foo: 'bar' })
  })

  it('ui_exec handler returns data', async () => {
    const h = getClientHandler('datatalk.ui.exec')!
    const out = await h({ object: 'stub', target: 'stub1', action: 'x' }, { sessionId: 's1' })
    expect(out).toEqual({ success: true, data: { ok: 1 } })
  })

  it('ui_read on unknown target throws', async () => {
    const h = getClientHandler('datatalk.ui.read')!
    await expect(h({ object: 'stub', target: 'nope', mode: 'state' }, { sessionId: 's1' })).rejects.toThrow()
  })

  it('ui_list returns array', async () => {
    const h = getClientHandler('datatalk.ui.list')!
    const out = await h({ filter: { type: 'stub' } }, { sessionId: 's1' })
    expect(Array.isArray(out)).toBe(true)
  })
})
