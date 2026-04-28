import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getClientHandler } from '../registry'
import { uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { WorkspaceAdapter } from '@/features/stage/adapters/WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: {
    ensureHydrated: vi.fn().mockResolvedValue(undefined),
    flush: vi.fn().mockResolvedValue(undefined),
    scheduleMetadataWrite: vi.fn(),
    scheduleContentWrite: vi.fn(),
  },
}))

import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
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
    vi.clearAllMocks()
    useStageStore.setState({ activeTabId: 'stub1' } as never)
    uiRouter.registerInstance('stub1', stubObject('stub1', { foo: 'bar' }))
    uiRouter.registerInstance('workspace', new WorkspaceAdapter(() => 's1'))
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

  it('preserves structured router error detail for ui_patch failures', async () => {
    uiRouter.registerInstance('query-1', {
      type: 'query_editor',
      objectId: 'query-1',
      title: 'Query 1',
      patchCapabilities: [{ pathPattern: '/content', ops: ['replace'] }],
      read: (mode) => {
        if (mode === 'actions') {
          return [
            { name: 'apply_text_edits', description: '', paramsSchema: { type: 'object', properties: {} } },
            { name: 'set_context', description: '', paramsSchema: { type: 'object', properties: {} } },
          ]
        }
        return { content: 'select 1' }
      },
      patch: async () => ({ status: 'applied' }),
      exec: async () => ({ success: true }),
    })

    const h = getClientHandler('datatalk.ui.patch')!

    try {
      await h({
        object: 'query_editor',
        target: 'query-1',
        ops: [{ op: 'replace', path: '/title', value: 'bad' }],
      }, { sessionId: 's1' })
      throw new Error('expected handler to reject')
    } catch (err) {
      expect(err).toBeInstanceOf(Error)
      expect((err as Error).message).toContain('Unsupported')
      expect(err).toMatchObject({
        code: 'unsupported_patch',
        details: expect.objectContaining({
          code: 'unsupported_patch',
          hint: expect.stringContaining('/content'),
          availableActions: ['apply_text_edits', 'set_context'],
        }),
      })
    }
  })

  it('ui_exec returns cancelled result for workspace choose_connection', async () => {
    vi.spyOn(useDataSourcePickerStore.getState(), 'requestPick').mockResolvedValue({ cancelled: true })
    const h = getClientHandler('datatalk.ui.exec')!
    const out = await h({ object: 'workspace', target: 'workspace', action: 'choose_connection' }, { sessionId: 's1' })
    expect(out).toEqual({ success: true, data: { cancelled: true } })
  })

  it('ensureHydrated -> forward -> flush ordering for patch handler', async () => {
    uiRouter.registerInstance('order-test', stubObject('order-test', { patched: true }))

    const h = getClientHandler('datatalk.ui.patch')!
    const out = await h({
      object: 'stub',
      target: 'order-test',
      ops: [{ op: 'replace', path: '/content', value: 'new' }],
    }, { sessionId: 's1' })

    expect(out).toEqual({ status: 'applied' })

    // ensureHydrated called before flush
    const hydrateOrder = (coordinator.ensureHydrated as ReturnType<typeof vi.fn>).mock.invocationCallOrder[0]
    const flushOrder = (coordinator.flush as ReturnType<typeof vi.fn>).mock.invocationCallOrder[0]
    expect(hydrateOrder).toBeLessThan(flushOrder)
  })
})
