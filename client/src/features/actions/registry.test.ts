import { describe, it, expect, beforeEach, vi } from 'vitest'
import { registerAction, registerClientHandler, getRenderers, getClientHandler } from './registry'

describe('action registry', () => {
  beforeEach(() => {
    // Clear maps between tests by re-importing module
    vi.resetModules()
  })

  it('registers and retrieves renderers', () => {
    const renderers = { leftCard: () => null as any }
    registerAction('test-action', renderers)
    expect(getRenderers('test-action')).toBe(renderers)
  })

  it('registers and retrieves client handlers', async () => {
    const handler = vi.fn().mockResolvedValue({ ok: true })
    registerClientHandler('test-handler', handler)
    const retrieved = getClientHandler('test-handler')
    expect(retrieved).toBeDefined()
    await retrieved?.({}, { sessionId: 's-1' })
    expect(handler).toHaveBeenCalledWith({}, { sessionId: 's-1' })
  })

  it('returns undefined for unknown actions', () => {
    expect(getRenderers('nonexistent')).toBeUndefined()
    expect(getClientHandler('nonexistent')).toBeUndefined()
  })
})
